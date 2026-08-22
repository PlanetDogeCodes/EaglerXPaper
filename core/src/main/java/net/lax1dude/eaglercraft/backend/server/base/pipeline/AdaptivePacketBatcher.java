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
 * frame overhead. Must be inserted AFTER the eagler frame codec in the
 * pipeline (so it receives raw ByteBufs, not BinaryWebSocketFrames).
 *
 * Behavior:
 * - If packets arrive slowly (< burstThreshold per window), pass through immediately.
 * - If packets arrive rapidly (>= burstThreshold per window), switch to batched mode:
 *   buffer packets for up to maxDelayMs, then flush as a single write.
 * - After maxBurstDurationMs of continuous batching, flush immediately to prevent
 *   excessive latency buildup.
 */
public class AdaptivePacketBatcher extends ChannelDuplexHandler {

    public static final String HANDLER_NAME = "eagler-adaptive-batcher";

    // Configuration
    private final int burstThreshold;           // packets per window to trigger batching
    private final long windowMs;                // monitoring window for burst detection
    private final long maxDelayMs;              // max buffer time in batched mode
    private final long maxBurstDurationMs;      // max continuous batching before forced flush
    private final int maxBufferSize;            // max packets buffered before forced flush

    // State — all accessed only from the channel's event loop thread
    private int packetCountInWindow = 0;
    private long windowStartMs = 0;
    private boolean batchingActive = false;
    private long batchingStartedMs = 0;
    private final List<ByteBuf> buffer = new ArrayList<>();
    private final List<ChannelPromise> promises = new ArrayList<>();
    private ScheduledFuture<?> flushTask = null;

    /**
     * Creates a batcher with the specified configuration.
     */
    public AdaptivePacketBatcher(int burstThreshold, long windowMs, long maxDelayMs,
            long maxBurstDurationMs, int maxBufferSize) {
        this.burstThreshold = Math.max(1, burstThreshold);
        this.windowMs = Math.max(10, windowMs);
        this.maxDelayMs = Math.max(1, maxDelayMs);
        this.maxBurstDurationMs = Math.max(maxDelayMs, maxBurstDurationMs);
        this.maxBufferSize = Math.max(1, maxBufferSize);
    }

    /**
     * Creates a batcher with sensible defaults for Eaglercraft connections.
     */
    public AdaptivePacketBatcher() {
        this(16, 100, 2, 200, 16);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        // Clean up if removed from pipeline without channelInactive
        cancelFlushTask();
        flushBuffer(ctx, true);
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
            // Non-ByteBuf writes (like BinaryWebSocketFrame) pass through immediately
            ctx.write(msg, promise);
            return;
        }

        ByteBuf buf = (ByteBuf) msg;
        if (buf.readableBytes() == 0) {
            // Empty buffers pass through (matches WebSocketEaglerFrameCodec behavior)
            ctx.write(msg, promise);
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

            // Check if we need to force a flush
            if (buffer.size() >= maxBufferSize || (now - batchingStartedMs) >= maxBurstDurationMs) {
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
            // If still in batching mode, schedule the next flush
            if (batchingActive && channel.isActive() && !buffer.isEmpty()) {
                scheduleFlush(ctx);
            } else if (batchingActive && buffer.isEmpty()) {
                // No more packets — exit batching mode
                batchingActive = false;
                batchingStartedMs = 0;
            }
        }, maxDelayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private void cancelFlushTask() {
        if (flushTask != null) {
            flushTask.cancel(false);
            flushTask = null;
        }
    }

    /**
     * Flushes all buffered packets. If discardPromises is true (channel inactive
     * or handler removed), the buffer is cleared without writing and all promises
     * are failed.
     *
     * Uses try/finally to guarantee buffer/promises are cleared even if
     * ctx.write throws — prevents ByteBuf leaks and hung promises.
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
            return;
        }

        // Write all buffered packets, then flush once.
        // try/finally ensures buffer/promises are cleared even if write throws.
        try {
            int size = buffer.size();
            for (int i = 0; i < size; i++) {
                ctx.write(buffer.get(i), promises.get(i));
            }
            ctx.flush();
        } finally {
            buffer.clear();
            promises.clear();
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
}
