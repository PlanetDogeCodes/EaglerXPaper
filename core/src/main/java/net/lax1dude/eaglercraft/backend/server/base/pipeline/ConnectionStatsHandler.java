/*
 * Copyright (c) 2025 lax1dude. All Rights Reserved.
 *
 * Per-connection traffic statistics (EaglerXPaper v1.1.1 Stable 2 — restored
 * feature). Counts bytes and frames in both directions and remembers the
 * connect time so /eaglerclients and /eaglerdiagnose can report duration
 * and live bandwidth per Eaglercraft connection. This handler was part of
 * the H24-era WebSocketEaglerFrameCodec byte counters that were lost when
 * the source tree was rebuilt from decompiled bytecode for v1.1.1 Stable 1;
 * it is reinstated here as a standalone, always-installed, near-zero-cost
 * handler (two AtomicLongs per direction).
 *
 * Round 4 fix: installed HEAD-ward of "eagler-frame-codec" so it counts the
 * COMPLETE wire stream — encoded BinaryWebSocketFrames, i.e. vanilla packets
 * AND the Eagler-protocol plugin frames (skins, capes, BackendRPC, webview,
 * voice) — instead of only the vanilla packet path. Inbound is counted
 * pre-decode (true wire bytes in); outbound post-encode (true wire bytes out).
 */

package net.lax1dude.eaglercraft.backend.server.base.pipeline;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

public class ConnectionStatsHandler extends ChannelDuplexHandler {

    public static final String HANDLER_NAME = "eagler-connection-stats";

    private final long connectedAtMs = System.currentTimeMillis();
    private final AtomicLong bytesIn = new AtomicLong();
    private final AtomicLong bytesOut = new AtomicLong();
    private final AtomicLong packetsIn = new AtomicLong();
    private final AtomicLong packetsOut = new AtomicLong();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        int len = wireLength(msg);
        if (len > 0) {
            this.bytesIn.addAndGet(len);
            this.packetsIn.incrementAndGet();
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        int len = wireLength(msg);
        if (len > 0) {
            this.bytesOut.addAndGet(len);
            this.packetsOut.incrementAndGet();
        }
        ctx.write(msg, promise);
    }

    private static int wireLength(Object msg) {
        if (msg instanceof BinaryWebSocketFrame) {
            return ((BinaryWebSocketFrame) msg).content().readableBytes();
        }
        if (msg instanceof ByteBuf) {
            return ((ByteBuf) msg).readableBytes();
        }
        return 0;
    }

    public long getConnectedAtMs() {
        return this.connectedAtMs;
    }

    public long getConnectedSeconds() {
        return Math.max(1L, (System.currentTimeMillis() - this.connectedAtMs) / 1000L);
    }

    public long getBytesIn() {
        return this.bytesIn.get();
    }

    public long getBytesOut() {
        return this.bytesOut.get();
    }

    public long getPacketsIn() {
        return this.packetsIn.get();
    }

    public long getPacketsOut() {
        return this.packetsOut.get();
    }

    /** Average outbound bandwidth in bytes per second since connect. */
    public long getBytesOutPerSecond() {
        return this.bytesOut.get() / this.getConnectedSeconds();
    }

    /** Average inbound bandwidth in bytes per second since connect. */
    public long getBytesInPerSecond() {
        return this.bytesIn.get() / this.getConnectedSeconds();
    }

    public static String formatBytes(long bytes) {
        // Stable 2 fix (round 5): Locale.ROOT — chat output must not inherit the
        // JVM default locale (e.g. Turkish/German decimal commas in /eaglerdiagnose).
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.2f MiB", bytes / 1048576.0);
        }
        return String.format(Locale.ROOT, "%.2f GiB", bytes / 1073741824.0);
    }
}
