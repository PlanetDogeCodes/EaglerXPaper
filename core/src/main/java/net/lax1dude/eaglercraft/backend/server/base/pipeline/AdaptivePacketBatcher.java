/*
 * Copyright (c) 2025 lax1dude. All Rights Reserved.
 *
 * Adaptive packet batcher — reduces WebSocket frame overhead and improves
 * performance for high-latency/mobile Eaglercraft connections by buffering
 * outbound packets for a short period and flushing them as a batch.
 *
 * The batcher monitors outbound packet frequency per-connection. When a
 * connection is sending many small packets rapidly (the common case during
 * chunk loading, entity updates, etc.), the batcher buffers them for up to
 * maxDelayMs milliseconds, then flushes them all at once. This reduces the
 * number of WebSocket frames sent, which significantly reduces per-frame
 * overhead (framing, TLS records, TCP packets).
 *
 * For low-throughput connections (few packets per second), the batcher
 * passes through immediately with zero added latency.
 *
 * The batcher is self-adaptive: it only activates when it detects a burst
 * of packets (more than burstThreshold packets within the monitoring window).
 * This means idle connections see zero overhead, and active connections
 * benefit from automatic batching.
 *
 * v1.1.1 Stable 2 hardening (verified against the wither-in-water
 * chunk-load reproduction harness):
 *  1. Ordering fence: non-ByteBuf writes (WebSocket control frames, raw RPC
 *     frames) arriving from threads OTHER than the event loop are re-routed
 *     through the event loop behind a buffer flush, so they can never
 *     overtake gameplay packets already sitting in the batch buffer.
 *  2. flush() interception: an explicit channel flush now flushes the batch
 *     immediately instead of passing through while packets are still buffered.
 *  3. The scheduled flush task always propagates a flush downstream, so a
 *     write issued right after a buffer flush can never sit in the outbound
 *     buffer with no flush behind it (previously delayed pings indefinitely
 *     on quiet connections).
 *  4. Byte budget: the buffer is bounded by totalBytes as well as packet
 *     count, so bursts of large packets (chunk data) cannot buffer megabytes
 *     of data or delay the flush past the latency target.
 *  5. Large packet fast path: packets larger than largePacketPassthroughBytes
 *     flush the current batch and pass straight through instead of being
 *     batched. Big packets (map chunks) gain nothing from batching but
 *     previously added up to maxDelayMs of latency each.
 */

package net.lax1dude.eaglercraft.backend.server.base.pipeline;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;

/**
 * A per-channel handler that batches outbound ByteBufs to reduce WebSocket
 * frame overhead. Must be inserted AFTER the eagler frame codec in the pipeline
 * (so it receives raw ByteBufs, not BinaryWebSocketFrames).
 *
 * Behavior:
 * - If packets arrive slowly (< burstThreshold per window), pass through immediately.
 * - If packets arrive rapidly (>= burstThreshold per window), switch to batched mode:
 *   buffer packets for up to maxDelayMs, then flush as a single write.
 * - After maxBurstDurationMs of continuous batching, flush immediately to prevent
 *   excessive latency buildup.
 * - Packets larger than largePacketPassthroughBytes are never buffered; they
 *   force a flush of the current batch first (preserving order) and then pass
 *   straight through.
 * - The total buffered byte count is capped at maxBufferedBytes (a hard
 *   memory/latency bound in addition to the packet count bound).
 */
public class AdaptivePacketBatcher extends ChannelDuplexHandler {

    public static final String HANDLER_NAME = "eagler-adaptive-batcher";

    /** Packets at least this large (default 16 KiB) skip the batch. */
    public static final int DEFAULT_LARGE_PACKET_PASSTHROUGH_BYTES = 16 * 1024;
    /** Hard cap on total bytes buffered (default 256 KiB). */
    public static final int DEFAULT_MAX_BUFFERED_BYTES = 256 * 1024;

    // Configuration
    private final int burstThreshold;           // packets per window to trigger batching
    private final long windowMs;                // monitoring window for burst detection
    private final long maxDelayMs;              // max buffer time in batched mode
    private final long maxBurstDurationMs;      // max continuous batching before forced flush
    private final int maxBufferSize;            // max packets buffered before forced flush
    private final int largePacketPassthroughBytes; // packets >= this size bypass the buffer
    private final int maxBufferedBytes;         // total bytes buffered before forced flush

    // State — batchingActive is volatile so the foreign-thread write fence
    // below can safely test it (everything else is event-loop-confined)
    private volatile boolean batchingActive = false;
    private int packetCountInWindow = 0;
    private long windowStartMs = 0;
    private long batchingStartedMs = 0;
    private final List<ByteBuf> buffer = new ArrayList<>();
    private final List<ChannelPromise> promises = new ArrayList<>();
    // Stable 2 fix (round 4): volatile — getBufferedBytes() is read by
    // /eaglerdiagnose from the platform main thread; without volatile a 64-bit
    // long written only on the event loop could tear on 32-bit JVMs.
    private volatile long bufferedBytes = 0;
    private ScheduledFuture<?> flushTask = null;

    /**
     * Creates a batcher with the specified configuration.
     */
    public AdaptivePacketBatcher(int burstThreshold, long windowMs, long maxDelayMs,
            long maxBurstDurationMs, int maxBufferSize) {
        this(burstThreshold, windowMs, maxDelayMs, maxBurstDurationMs, maxBufferSize,
                DEFAULT_LARGE_PACKET_PASSTHROUGH_BYTES, DEFAULT_MAX_BUFFERED_BYTES);
    }

    public AdaptivePacketBatcher(int burstThreshold, long windowMs, long maxDelayMs,
            long maxBurstDurationMs, int maxBufferSize, int largePacketPassthroughBytes,
            int maxBufferedBytes) {
        this.burstThreshold = Math.max(1, burstThreshold);
        this.windowMs = Math.max(10, windowMs);
        this.maxDelayMs = Math.max(1, maxDelayMs);
        this.maxBurstDurationMs = Math.max(maxDelayMs, maxBurstDurationMs);
        this.maxBufferSize = Math.max(1, maxBufferSize);
        this.largePacketPassthroughBytes = Math.max(0, largePacketPassthroughBytes);
        this.maxBufferedBytes = Math.max(this.largePacketPassthroughBytes, maxBufferedBytes);
    }

    /**
     * Creates a batcher with sensible defaults for Eaglercraft connections.
     */
    public AdaptivePacketBatcher() {
        this(16, 100, 2, 200, 16);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        // Stable 2 fix: a plugin rebuilding/removing this pipeline section while
        // the channel is still active must not drop the buffered packets — a
        // dropped packet mid-stream is exactly the client-side stream
        // misalignment class this fork fights. Forward them instead; the
        // discard path stays reserved for dead channels.
        cancelFlushTask();
        flushBuffer(ctx, !ctx.channel().isActive());
        super.handlerRemoved(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        cancelFlushTask();
        flushBuffer(ctx, true);
        super.channelInactive(ctx);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!(msg instanceof ByteBuf)) {
            if (!ctx.channel().eventLoop().inEventLoop()) {
                // Foreign-thread write (RPC/webview/scheduler calling the handler
                // directly — standard channel/ctx writes are marshaled by Netty and
                // never reach this branch, but defensive plugins and reflection can).
                // Re-enter write() ON the event loop so the buffered packets are
                // flushed first and ordering is preserved.
                if (batchingActive) {
                    try {
                        ctx.channel().eventLoop().execute(() -> {
                            try {
                                write(ctx, msg, promise);
                            } catch (Throwable t) {
                                ReferenceCountUtil.release(msg);
                                try {
                                    promise.setFailure(t);
                                } catch (Exception ignored) {
                                }
                            }
                        });
                    } catch (Throwable rejected) {
                        // event loop is shutting down — release cleanly instead of
                        // leaking the message through a rejected task
                        ReferenceCountUtil.release(msg);
                        try {
                            promise.setFailure(rejected);
                        } catch (Exception ignored) {
                        }
                    }
                    return;
                }
                ctx.write(msg, promise);
                return;
            }
            // Flush anything buffered first so the packet stream stays ordered
            // around non-ByteBuf writes that bypass the buffer
            if (batchingActive && !buffer.isEmpty()) {
                cancelFlushTask();
                flushBuffer(ctx, false);
                if (ctx.channel().isActive()) {
                    batchingStartedMs = System.currentTimeMillis();
                    scheduleFlush(ctx);
                }
            }
            // Non-ByteBuf writes (like BinaryWebSocketFrame) pass through immediately,
            // followed by a flush so they cannot sit unflushed behind us
            ctx.write(msg, promise);
            ctx.flush();
            return;
        }

        ByteBuf buf = (ByteBuf) msg;
        if (buf.readableBytes() == 0) {
            // Empty buffers pass through (matches WebSocketEaglerFrameCodec behavior)
            ctx.write(msg, promise);
            return;
        }

        // Large-packet fast path (Stable 2): a packet this big gains nothing
        // from batching but adds up to maxDelayMs of latency and inflates the
        // memory bound. Flush what we have (preserving order), then let this
        // packet through unbatched and flushed immediately.
        if (this.largePacketPassthroughBytes > 0 && buf.readableBytes() >= this.largePacketPassthroughBytes) {
            if (batchingActive) {
                if (!buffer.isEmpty()) {
                    cancelFlushTask();
                    flushBuffer(ctx, false);
                }
                if (ctx.channel().isActive()) {
                    batchingStartedMs = System.currentTimeMillis();
                    scheduleFlush(ctx);
                }
            }
            ctx.write(msg, promise);
            ctx.flush();
            return;
        }

        long now = System.currentTimeMillis();

        // Track packet frequency
        if (windowStartMs == 0) {
            windowStartMs = now;
            packetCountInWindow = 1;
        } else if (now - windowStartMs > windowMs) {
            // Window expired — reset
            windowStartMs = now;
            packetCountInWindow = 1;
            // If we were batching but the burst ended, deactivate
            if (batchingActive && buffer.isEmpty()) {
                batchingActive = false;
                batchingStartedMs = 0;
                cancelFlushTask();
            }
        } else {
            packetCountInWindow++;
        }

        // Check if we should activate batching
        if (!batchingActive && packetCountInWindow >= burstThreshold) {
            batchingActive = true;
            batchingStartedMs = now;
            scheduleFlush(ctx);
        }

        if (batchingActive) {
            // Buffer this packet
            buffer.add(buf);
            promises.add(promise);
            bufferedBytes += buf.readableBytes();

            // Check if we need to force a flush (count, bytes, or duration)
            if (buffer.size() >= maxBufferSize || bufferedBytes >= maxBufferedBytes
                    || (now - batchingStartedMs) >= maxBurstDurationMs) {
                cancelFlushTask();
                flushBuffer(ctx, false);
                // Stay in batching mode — reschedule flush for next batch
                if (ctx.channel().isActive()) {
                    batchingStartedMs = System.currentTimeMillis(); // reset burst timer
                    scheduleFlush(ctx);
                }
            }
        } else {
            // Pass through immediately — low-throughput connection
            ctx.write(msg, promise);
        }
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        // Stable 2: an explicit flush from upstream (vanilla per-tick flush,
        // keep-alive writers, etc.) must not sail past a full batch buffer.
        // Netty only invokes us here on the event loop thread, so the state
        // access below is safe.
        if (!buffer.isEmpty()) {
            cancelFlushTask();
            flushBuffer(ctx, false);
            if (batchingActive && ctx.channel().isActive()) {
                batchingStartedMs = System.currentTimeMillis();
                scheduleFlush(ctx);
            }
        }
        ctx.flush();
    }

    private void scheduleFlush(ChannelHandlerContext ctx) {
        if (flushTask != null) {
            return; // Already scheduled
        }
        Channel channel = ctx.channel();
        if (!channel.isActive()) {
            return;
        }
        flushTask = channel.eventLoop().schedule(() -> {
            flushTask = null;
            flushBuffer(ctx, false);
            // Reset the burst timer here too, otherwise every write after
            // maxBurstDurationMs of sustained traffic force-flushes and
            // batching degenerates into pass-through
            if (batchingActive) {
                batchingStartedMs = System.currentTimeMillis();
            }
            // If still in batching mode, schedule the next flush
            if (batchingActive && channel.isActive() && !buffer.isEmpty()) {
                scheduleFlush(ctx);
            } else if (batchingActive && buffer.isEmpty()) {
                // No more packets — exit batching mode, but still push a
                // flush downstream: the last write made before the buffer
                // emptied (e.g. a control frame written between batches)
                // otherwise has no flush behind it
                batchingActive = false;
                batchingStartedMs = 0;
            }
            ctx.flush();
        }, maxDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private void cancelFlushTask() {
        if (flushTask != null) {
            flushTask.cancel(false);
            flushTask = null;
        }
    }

    /**
     * Writes all buffered packets, then flushes once. If a write throws, the
     * remaining buffers are released and their promises are failed so nothing
     * leaks and no listener hangs.
     */
    private void flushBuffer(ChannelHandlerContext ctx, boolean discardPromises) {
        if (buffer.isEmpty()) {
            return;
        }

        if (discardPromises) {
            // Channel is inactive or handler removed — release buffers and fail promises
            for (ByteBuf buf : buffer) {
                ReferenceCountUtil.release(buf);
            }
            for (ChannelPromise promise : promises) {
                try {
                    promise.setFailure(new java.nio.channels.ClosedChannelException());
                } catch (Exception e) {
                    // ignore
                }
            }
            buffer.clear();
            promises.clear();
            bufferedBytes = 0;
            return;
        }

        try {
            while (!buffer.isEmpty()) {
                ByteBuf buf = buffer.remove(0);
                bufferedBytes -= buf.readableBytes();
                ChannelPromise promise = promises.remove(0);
                try {
                    ctx.write(buf, promise);
                } catch (Throwable t) {
                    ReferenceCountUtil.release(buf);
                    try {
                        promise.setFailure(t);
                    } catch (Exception ignored) {
                    }
                }
            }
            ctx.flush();
        } finally {
            // Anything still left here was never handed downstream
            for (ByteBuf buf : buffer) {
                ReferenceCountUtil.release(buf);
            }
            for (ChannelPromise promise : promises) {
                try {
                    promise.setFailure(new java.nio.channels.ClosedChannelException());
                } catch (Exception ignored) {
                }
            }
            buffer.clear();
            promises.clear();
            bufferedBytes = 0;
        }
    }

    /**
     * Returns whether this batcher is currently in active batching mode.
     */
    public boolean isBatchingActive() {
        return batchingActive;
    }

    /**
     * Returns the current number of buffered packets.
     */
    public int getBufferSize() {
        return buffer.size();
    }

    /**
     * Returns the current number of buffered bytes (Stable 2).
     */
    public long getBufferedBytes() {
        return bufferedBytes;
    }
}
