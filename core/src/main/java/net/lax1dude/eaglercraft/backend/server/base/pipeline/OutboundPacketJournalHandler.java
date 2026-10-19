/*
 * Copyright (c) 2025 lax1dude. All Rights Reserved.
 *
 * Outbound packet journal (EaglerXPaper v1.1.1 Stable 2).
 *
 * A zero-overhead-when-disabled per-connection ring journal that records
 * the outbound frame stream an Eaglercraft client received, so that when a
 * player is kicked by a client-side packet decode failure (for example the
 * reported "kicked whenever they load a chunk where a wither's hitbox is
 * inside water" class of bug) the server owner can capture EXACTLY which
 * frames were sent in the final seconds before the disconnect and hand them
 * to the EaglerXPaper maintainers.
 *
 * Round 4 fix: the journal is installed HEAD-ward of "eagler-frame-codec",
 * so it observes every encoded BinaryWebSocketFrame handed to the WebSocket
 * transport — vanilla Minecraft packets AND the Eagler-protocol plugin frames
 * (skins, capes, BackendRPC, webview, voice) — in the exact order and byte
 * content the client received, after the adaptive batcher has forwarded its
 * buffers. (The previous tail-of-batcher position was structurally blind to
 * the injected plugin stream and to true wire interleaving.)
 *
 * The journal is installed only when
 * EaglerXPaperConfig.enablePacketJournal is true (settings.yml:
 * outbound_packet_journal.enable, default false). While enabled it records,
 * per outbound binary frame: a timestamp, the frame type, its length, and the
 * first `snapshotBytes` bytes of the payload. Bytes are PEEKED with
 * getByte() so the readerIndex of live pipeline buffers is never touched.
 *
 * When the connection closes, the journal is written (asynchronously, on a
 * shared daemon thread — never on the Netty event loop) to
 * <plugin data folder>/logs/journal-<name>-<timestamp>.log
 * (at most MAX_JOURNAL_FILES are retained; the oldest are deleted).
 *
 * The journal is fail-safe by design: every recording/dump operation is
 * wrapped so that no journal problem can ever disturb the connection it is
 * observing — on any internal failure the journal silently disables itself
 * for that channel.
 */

package net.lax1dude.eaglercraft.backend.server.base.pipeline;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.concurrent.GenericFutureListener;
import net.lax1dude.eaglercraft.backend.server.base.NettyPipelineData;
import net.lax1dude.eaglercraft.backend.server.base.config.EaglerXPaperConfig;

public class OutboundPacketJournalHandler extends ChannelDuplexHandler {

    public static final String HANDLER_NAME = "eagler-packet-journal";

    // Stable 2 fix (round 5): the dump executor is now LAZY and shut down from
    // EaglerXServer.disableHandler(). A statically-created executor that is never
    // terminated pins the old plugin classloader across /reload (its worker thread
    // holds a lambda defined by the old loader). Lazily re-created on demand, so
    // shared-JVM multi-instance setups keep working after one instance disables.
    private static volatile ExecutorService dumpExecutor;

    private static ExecutorService dumpExecutor() {
        ExecutorService ex = dumpExecutor;
        if (ex == null) {
            synchronized (OutboundPacketJournalHandler.class) {
                ex = dumpExecutor;
                if (ex == null) {
                    ex = Executors.newSingleThreadExecutor(r -> {
                        Thread t = new Thread(r, "EaglerXPaper-PacketJournal-Writer");
                        t.setDaemon(true);
                        t.setPriority(Thread.MIN_PRIORITY);
                        return t;
                    });
                    dumpExecutor = ex;
                }
            }
        }
        return ex;
    }

    /**
     * Stable 2 fix (round 5): called from EaglerXServer.disableHandler() so a
     * /reload or plugman disable does not pin the old plugin classloader through
     * the dump thread (the thread only ever exists if a dump actually ran).
     */
    public static void shutdownDumpExecutor() {
        synchronized (OutboundPacketJournalHandler.class) {
            if (dumpExecutor != null) {
                try {
                    dumpExecutor.shutdownNow();
                }
                catch (Throwable ignored) {
                    // best effort
                }
                dumpExecutor = null;
            }
        }
    }

    private static final int MAX_TOTAL_SNAPSHOT_BYTES = 65536; // per-connection byte ceiling for snapshots

    /** Round 4 fix: retention cap — one file is written per disconnect; without a cap a
     * busy debug session (bot waves, reconnect loops) can fill the disk. */
    private static final int MAX_JOURNAL_FILES = 200;

    private final NettyPipelineData pipelineData;
    private final int maxPackets;
    private final int snapshotBytes;

    private final Queue<JournalEntry> entries = new ArrayDeque<>();
    private long totalSnapshotBytes = 0;
    private long totalPacketsRecorded = 0;
    private final AtomicBoolean dumped = new AtomicBoolean(false);
    private volatile boolean broken = false;

    private static final class JournalEntry {
        final long timestamp;
        final String type;
        final int length;
        final byte[] snapshot;
        JournalEntry(long timestamp, String type, int length, byte[] snapshot) {
            this.timestamp = timestamp;
            this.type = type;
            this.length = length;
            this.snapshot = snapshot;
        }
    }

    public OutboundPacketJournalHandler(NettyPipelineData pipelineData) {
        this(pipelineData, Math.max(16, EaglerXPaperConfig.journalMaxPackets),
                Math.max(8, EaglerXPaperConfig.journalSnapshotBytes));
    }

    public OutboundPacketJournalHandler(NettyPipelineData pipelineData, int maxPackets, int snapshotBytes) {
        this.pipelineData = pipelineData;
        this.maxPackets = maxPackets;
        // Stable 2: clamp the per-packet snapshot to the total snapshot budget so a
        // silly-large config value cannot make every entry evict itself immediately
        this.snapshotBytes = Math.min(Math.max(8, snapshotBytes), MAX_TOTAL_SNAPSHOT_BYTES);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!ctx.channel().eventLoop().inEventLoop()) {
            // Stable 2 fix (round 4): journal state is event-loop-confined. A defensive
            // plugin (or reflection) invoking write() from a foreign thread must not
            // corrupt the entry queue. Snapshot the bytes HERE, while the buffer is
            // still alive, marshal only the queue-append to the event loop, and always
            // pass the write through immediately — never re-invoke write() with a live
            // msg from a foreign thread (refcount / ordering hazards).
            JournalEntry entry = null;
            try {
                if (!broken) {
                    entry = snapshotEntry(msg);
                }
            }
            catch (Throwable t) {
                broken = true;
                entries.clear();
                totalSnapshotBytes = 0;
            }
            if (entry != null) {
                final JournalEntry safeEntry = entry;
                try {
                    ctx.channel().eventLoop().execute(() -> {
                        try {
                            if (!broken) {
                                append(safeEntry);
                            }
                        }
                        catch (Throwable t) {
                            broken = true;
                        }
                    });
                }
                catch (Throwable rejected) {
                    // event loop shut down — drop the entry silently
                }
            }
            // writeAndFlush (not write): a caller that invokes this handler's write()
            // directly from a foreign thread has no flush scheduling to rely on — a
            // plain ctx.write would strand the message in the outbound buffer until
            // the next unrelated flush. Flushing here is the foolproof choice.
            ctx.writeAndFlush(msg, promise);
            return;
        }
        try {
            if (!broken) {
                JournalEntry entry = snapshotEntry(msg);
                if (entry != null) {
                    append(entry);
                }
            }
        }
        catch (Throwable t) {
            // fail-safe: never let the journal break the connection it observes
            broken = true;
            entries.clear();
            totalSnapshotBytes = 0;
        }
        ctx.write(msg, promise);
    }

    private JournalEntry snapshotEntry(Object msg) {
        ByteBuf buf;
        String type;
        if (msg instanceof BinaryWebSocketFrame) {
            // normal case at the head-ward-of-frame-codec position: fully encoded
            // wire frames — vanilla packets and Eagler-protocol plugin frames alike
            buf = ((BinaryWebSocketFrame)msg).content();
            type = "WIRE";
        }
        else if (msg instanceof ByteBuf) {
            // defensive: unencoded buffers (also keeps the pre-round-4 semantics
            // valid if the handler is ever installed at a different position)
            buf = (ByteBuf)msg;
            type = "PKT";
        }
        else {
            return new JournalEntry(System.currentTimeMillis(), msg.getClass().getSimpleName(), -1, null);
        }
        int len = buf.readableBytes();
        if (len == 0) {
            return null; // empty placeholder buffers are not interesting
        }
        int n = Math.min(len, this.snapshotBytes);
        // PEEK (getByte) — never advance the readerIndex of a live buffer
        byte[] snap = new byte[n];
        for (int i = 0; i < n; i++) {
            snap[i] = buf.getByte(buf.readerIndex() + i);
        }
        return new JournalEntry(System.currentTimeMillis(), type, len, snap);
    }

    private void append(JournalEntry entry) {
        entries.add(entry);
        ++totalPacketsRecorded;
        if (entry.snapshot != null) {
            totalSnapshotBytes += entry.snapshot.length;
        }
        while (entries.size() > maxPackets || totalSnapshotBytes > MAX_TOTAL_SNAPSHOT_BYTES) {
            JournalEntry evicted = entries.poll();
            if (evicted == null) {
                break;
            }
            if (evicted.snapshot != null) {
                totalSnapshotBytes -= evicted.snapshot.length;
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        dump("channel closed");
        super.channelInactive(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        dump("handler removed");
        super.handlerRemoved(ctx);
    }

    private void dump(String reason) {
        if (broken || entries.isEmpty() || !dumped.compareAndSet(false, true)) {
            return;
        }
        try {
            final JournalEntry[] snapshot = entries.toArray(new JournalEntry[0]);
            final NettyPipelineData data = this.pipelineData;
            final String why = reason;
            dumpExecutor().execute(() -> {
                PrintWriter out = null;
                try {
                    File baseFolder;
                    if (data != null && data.server != null && data.server.getPlatform() != null) {
                        try {
                            baseFolder = data.server.getPlatform().getDataFolder();
                        } catch (Throwable t) {
                            baseFolder = null;
                        }
                    } else {
                        baseFolder = null;
                    }
                    if (baseFolder == null) {
                        baseFolder = new File(".");
                    }
                    File folder = new File(baseFolder, "logs");
                    if (!folder.isDirectory() && !folder.mkdirs()) {
                        return;
                    }
                    // Stable 2 fix (round 4): retention — keep at most MAX_JOURNAL_FILES
                    // journal dumps, deleting the oldest. Best-effort; failures never
                    // block the dump itself.
                    try {
                        File[] existing = folder.listFiles((dir, name) ->
                                name.startsWith("journal-") && name.endsWith(".log"));
                        if (existing != null && existing.length >= MAX_JOURNAL_FILES) {
                            java.util.Arrays.sort(existing,
                                    java.util.Comparator.comparingLong(File::lastModified));
                            int excess = existing.length - (MAX_JOURNAL_FILES - 1);
                            for (int i = 0; i < excess; i++) {
                                try {
                                    existing[i].delete();
                                }
                                catch (Throwable ignored) {
                                }
                            }
                        }
                    }
                    catch (Throwable ignored) {
                        // retention must never break the dump
                    }
                    String username = null;
                    String uuid = null;
                    int eaglerProto = 0;
                    int mcProto = 0;
                    if (data != null) {
                        username = data.username;
                        uuid = data.uuid != null ? data.uuid.toString() : null;
                        eaglerProto = data.handshakeProtocol;
                        mcProto = data.minecraftProtocol;
                    }
                    SimpleDateFormat fileFmt = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS");
                    StringBuilder nameBase = new StringBuilder();
                    if (username != null) {
                        String u = username.replaceAll("[^A-Za-z0-9_\\-]", "_");
                        if (u.length() > 32) {
                            u = u.substring(0, 32);
                        }
                        nameBase.append(u);
                    } else {
                        String a = String.valueOf(ctxRemote(data));
                        a = a.replaceAll("[^A-Za-z0-9_\\-\\.]", "_");
                        if (a.length() > 40) {
                            a = a.substring(0, 40);
                        }
                        nameBase.append(a);
                    }
                    File file = new File(folder, "journal-" + nameBase + "-"
                            + fileFmt.format(new Date()) + ".log");
                    out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
                    SimpleDateFormat stampFmt = new SimpleDateFormat("HH:mm:ss.SSS");
                    out.println("# EaglerXPaper v1.1.1 Stable 2 outbound packet journal");
                    out.println("# records every binary WebSocket frame handed to the wire, in exact");
                    out.println("# wire order: vanilla MC packets AND Eagler-protocol plugin frames");
                    out.println("# (type WIRE; head bytes 0xEE.. = Eagler plugin protocol)");
                    out.println("# user: " + (username != null ? username : "<unknown>")
                            + " uuid: " + (uuid != null ? uuid : "<unknown>"));
                    out.println("# eagler protocol: " + eaglerProto
                            + ", minecraft protocol: " + mcProto);
                    out.println("# remote: " + ctxRemote(data));
                    out.println("# reason: " + why + ", journal capacity: " + maxPackets
                            + " packets / " + MAX_TOTAL_SNAPSHOT_BYTES + " bytes of snapshots");
                    out.println("# total packets since connect: " + totalPacketsRecorded
                            + ", retained in journal: " + snapshot.length);
                    out.println("# columns: index timestamp type length head");
                    for (int i = 0; i < snapshot.length; i++) {
                        JournalEntry e = snapshot[i];
                        StringBuilder sb = new StringBuilder(160);
                        sb.append('[').append(i).append("] ");
                        sb.append(stampFmt.format(new Date(e.timestamp))).append(' ');
                        sb.append(e.type).append(' ');
                        if (e.length >= 0) {
                            sb.append("len=").append(e.length).append(' ');
                        }
                        if (e.snapshot != null && e.snapshot.length > 0) {
                            sb.append("head=");
                            for (int j = 0; j < e.snapshot.length; j++) {
                                sb.append(String.format("%02x", e.snapshot[j]));
                            }
                        }
                        out.println(sb.toString());
                    }
                } catch (Throwable t) {
                    // journal must never bubble anything up
                } finally {
                    if (out != null) {
                        try {
                            out.close();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            });
            entries.clear();
            totalSnapshotBytes = 0;
        } catch (Throwable ignored) {
            // fail-safe
        }
    }

    private static Object ctxRemote(NettyPipelineData data) {
        try {
            return data.channel.remoteAddress();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    public long getTotalPacketsRecorded() {
        return totalPacketsRecorded;
    }

    public int getRetainedEntries() {
        return entries.size();
    }
}
