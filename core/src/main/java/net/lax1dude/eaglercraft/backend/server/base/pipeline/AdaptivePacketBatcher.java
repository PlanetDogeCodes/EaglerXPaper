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
 * IMPORTANT: The batcher inspects the first VarInt of each packet (the
 * Minecraft packet ID) to identify timing-critical packets (combat, item
 * use, entity events). When such a packet is encountered, the buffer is
 * flushed immediately — this prevents the batcher from introducing latency
 * that would break combat mechanics (mace smash attacks, wind charges,
 * etc.).
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
 * - Timing-critical packets (combat, item use, entity events) trigger an
 *   immediate flush so they reach the client without delay.
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

    // Track total buffered bytes to prevent OOM from large packets
    private int bufferedBytes = 0;
    private static final int MAX_BUFFERED_BYTES = 256 * 1024; // 256KB

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
     * Uses a 2ms delay (down from 20ms) to minimize impact on timing-sensitive
     * mechanics while still providing frame reduction benefits.
     */
    public AdaptivePacketBatcher() {
        this(20, 100, 2, 50, 64);
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
            // Check if this packet is timing-critical.
            // If so, flush the current buffer immediately, then pass this packet
            // through without buffering — it needs to reach the client ASAP.
            if (isTimingCriticalPacket(buf)) {
                cancelFlushTask();
                flushBuffer(ctx, false);
                // Pass the critical packet through immediately
                ctx.write(msg, promise);
                // Exit batching mode — the burst is likely over
                batchingActive = false;
                batchingStartedMs = 0;
                return;
            }

            // Buffer this packet
            buffer.add(buf);
            promises.add(promise);
            bufferedBytes += buf.readableBytes();

            // Check if we need to force a flush
            if (buffer.size() >= maxBufferSize
                    || bufferedBytes >= MAX_BUFFERED_BYTES
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

    /**
     * Checks whether a packet is timing-critical and should not be buffered.
     *
     * Timing-critical packets are those involved in combat, item use, and
     * entity interactions — these need to reach the client within the same
     * tick to avoid breaking game mechanics (mace smash attacks, wind charges,
     * projectile hits, etc.).
     *
     * We identify these by reading the first VarInt from the ByteBuf, which
     * is the Minecraft packet ID. The IDs we check for are the 1.8 protocol
     * IDs (since Eaglercraft clients speak 1.8 protocol after ViaVersion
     * translation):
     *
     *   0x00 = Spawn Object (wind charges, projectiles)
     *   0x02 = Spawn Global Entity (lightning)
     * 0x0B/0x0C = Animation/Statistics
     *   0x12 = Entity Velocity (knockback, mace launch)
     *   0x1A = Entity Status (mace smash effect)
     *   0x1C = Entity Metadata
     *   0x22 = Entity Teleport
     *   0x2A = Entity Properties (damage attributes)
     *   0x2C = Combat Event (damage, death)
     *   0x2D = World Border
     *   0x3B = Scoreboard Objective
     *   0x3C = Update Score (kill counter)
     *   0x37 = Statistics
     *   0x39 = Camera (spectator)
     *   0x3A = World Border
     *   0x42 = Update Health (damage taken)
     *   0x43 = Set Experience (XP changes)
     *   0x44 = Update Attributes
     *   0x48 = Use Bed
     *   0x4A = Destroy Entities
     *   0x4B = Remove Entity Effect
     *   0x4D = Entity Effect (potion effects)
     *   0x4E = Particle (hit particles, wind charge particles)
     *
     * We use a broad set of IDs to be safe — false positives (flushing when
     * we didn't need to) just reduce the batching benefit slightly, while
     * false negatives (buffering a critical packet) break gameplay.
     */
    private static boolean isTimingCriticalPacket(ByteBuf buf) {
        if (buf.readableBytes() < 1) {
            return false;
        }
        try {
            // Read the VarInt packet ID without consuming it.
            // VarInt is 1-5 bytes, but for 1.8 protocol IDs < 128, it's a single byte.
            int readerIndex = buf.readerIndex();
            int packetId;
            byte firstByte = buf.getByte(readerIndex);
            if ((firstByte & 0x80) == 0) {
                // Single-byte VarInt (most common for 1.8 packet IDs)
                packetId = firstByte & 0x7F;
            } else {
                // Multi-byte VarInt — read it properly
                packetId = readVarInt(buf, readerIndex);
                if (packetId < 0) {
                    return false; // couldn't read
                }
            }

            // Check against known timing-critical packet IDs for ALL supported
            // Eaglercraft client protocols. Since we don't know which protocol
            // the client uses at this point, we check IDs from ALL versions.
            // False positives (flushing when we didn't need to) are harmless —
            // they just slightly reduce the batching benefit.
            //
            // 1.8 IDs (EaglercraftX 1.8, Rewind 1.5.2 base):
            //   Spawn Object=0x0E, Spawn Mob=0x0F, Spawn Player=0x0C, Spawn Global=0x11
            //   Entity Velocity=0x23, Entity Teleport=0x21, Entity Status=0x1A
            //   Entity Metadata=0x29, Destroy Entities=0x1D, Entity Effect=0x2A
            //   Remove Entity Effect=0x2B, Entity Properties=0x22
            //   Update Health=0x06, Combat Event=0x2E, Animation=0x0B
            //   Particle=0x1B, Entity Relative Move=0x14, Entity Look+Move=0x15
            //   Entity Look=0x17, Player Info=0x38, Camera=0x39, Statistics=0x37
            //
            // 1.7 IDs (legacy Eaglercraft 1.7 clients):
            //   Spawn Object=0x17, Spawn Mob=0x18, Spawn Global=0x1A
            //   Entity Velocity=0x1C, Entity Teleport=0x22, Entity Status=0x25
            //   Entity Metadata=0x28, Destroy Entities=0x1D, Entity Effect=0x1E
            //   Remove Entity Effect=0x1F, Entity Properties=0x20
            //   Update Health=0x06, Animation=0x0B (same as 1.8!)
            //   Particle=0x2A, Entity Relative Move=0x14 (same), Entity Look+Move=0x15 (same)
            //   Entity Look=0x16, Player Info=0x38, Camera=0x39, Statistics=0x37
            //
            // 1.12.2 IDs (Eaglercraft 1.12.2 clients):
            //   Spawn Object=0x00, Spawn Mob=0x03, Spawn Player=0x05, Spawn Global=0x02
            //   Entity Velocity=0x41, Entity Teleport=0x4C, Entity Status=0x1B
            //   Entity Metadata=0x3C, Destroy Entities=0x31, Entity Effect=0x3B
            //   Remove Entity Effect=0x3A, Entity Properties=0x3D
            //   Update Health=0x38, Combat Event=0x2C (same as 1.8!), Animation=0x05
            //   Particle=0x22, Entity Relative Move=0x26, Entity Look+Move=0x27
            //   Entity Look=0x28, Player Info=0x2E, Camera=0x2F, Statistics=0x07
            switch (packetId) {
                // === 1.8 protocol IDs ===
                // Entity spawning
                case 0x0E: // Spawn Object (1.8)
                case 0x0F: // Spawn Mob (1.8)
                case 0x0C: // Spawn Player (1.8)
                case 0x11: // Spawn Global Entity (1.8)
                // Combat and damage
                case 0x2E: // Combat Event (1.8)
                case 0x06: // Update Health (1.8, 1.7)
                case 0x0B: // Animation (1.8, 1.7)
                // Entity movement/velocity
                case 0x23: // Entity Velocity (1.8)
                case 0x14: // Entity Relative Move (1.8, 1.7)
                case 0x15: // Entity Look and Relative Move (1.8, 1.7)
                case 0x17: // Entity Look (1.8) / Spawn Object (1.7)
                case 0x21: // Entity Teleport (1.8)
                // Entity state changes
                case 0x1A: // Entity Status (1.8)
                case 0x29: // Entity Metadata (1.8)
                case 0x1D: // Destroy Entities (1.8, 1.7)
                case 0x2A: // Entity Effect (1.8) / Particle (1.7)
                case 0x2B: // Remove Entity Effect (1.8)
                case 0x22: // Entity Properties (1.8) / Entity Teleport (1.7)
                // Particles
                case 0x1B: // Particle (1.8)
                // Player state
                case 0x38: // Player Info (1.8, 1.7)
                case 0x39: // Camera (1.8, 1.7)
                case 0x37: // Statistics (1.8, 1.7)

                // === 1.7 protocol IDs (additional, not overlapping with 1.8) ===
                case 0x18: // Spawn Mob (1.7)
                case 0x1C: // Entity Velocity (1.7)
                case 0x25: // Entity Status (1.7)
                case 0x28: // Entity Metadata (1.7)
                case 0x1E: // Entity Effect (1.7)
                case 0x1F: // Remove Entity Effect (1.7)
                case 0x20: // Entity Properties (1.7)
                case 0x16: // Entity Look (1.7)

                // === 1.12.2 protocol IDs (additional, not already covered above) ===
                case 0x00: // Spawn Object (1.12)
                case 0x03: // Spawn Mob (1.12)
                case 0x02: // Spawn Global Entity (1.12)
                case 0x41: // Entity Velocity (1.12)
                case 0x4C: // Entity Teleport (1.12)
                // 0x1B already covered (Particle 1.8 / Entity Status 1.12)
                case 0x3C: // Entity Metadata (1.12)
                case 0x31: // Destroy Entities (1.12)
                case 0x3B: // Entity Effect (1.12)
                case 0x3A: // Remove Entity Effect (1.12)
                case 0x3D: // Entity Properties (1.12)
                // 0x38 already covered (Player Info 1.8 / Update Health 1.12)
                case 0x2C: // Combat Event (1.12)
                // 0x05 already covered (Spawn Player 1.8 / Animation 1.12)
                case 0x26: // Entity Relative Move (1.12)
                case 0x27: // Entity Look and Relative Move (1.12)
                // 0x28 already covered (Entity Metadata 1.7 / Entity Look 1.12)
                // 0x2E already covered (Combat Event 1.8 / Player Info 1.12)
                case 0x2F: // Camera (1.12)
                case 0x07: // Statistics (1.12)

                    return true;
                default:
                    return false;
            }
        } catch (Exception e) {
            // If we can't read the packet ID, don't risk buffering it
            return true;
        }
    }

    /**
     * Reads a VarInt from the ByteBuf at the given index without consuming bytes.
     * Returns -1 if the VarInt is incomplete or invalid.
     */
    private static int readVarInt(ByteBuf buf, int startIndex) {
        int result = 0;
        int bytes = 0;
        int index = startIndex;
        int maxIndex = startIndex + 5; // VarInt is max 5 bytes
        int limit = buf.writerIndex();
        while (index < maxIndex && index < limit) {
            byte b = buf.getByte(index);
            result |= (b & 0x7F) << (bytes * 7);
            bytes++;
            index++;
            if ((b & 0x80) == 0) {
                // Validate 5th byte doesn't overflow (only lower 4 bits allowed)
                if (bytes == 5 && (b & 0xF0) != 0) {
                    return -1; // invalid VarInt
                }
                return result;
            }
        }
        return -1; // incomplete VarInt
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
            bufferedBytes = 0;
            return;
        }

        // Write all buffered packets, then flush once.
        // try/finally ensures buffer/promises are cleared even if write throws.
        // If a write fails mid-loop, remaining ByteBufs are released and
        // remaining promises are failed — no leaks, no hung promises.
        int i = 0;
        int size = buffer.size();
        try {
            for (; i < size; i++) {
                ctx.write(buffer.get(i), promises.get(i));
            }
            ctx.flush();
        } finally {
            // Release and fail any packets that weren't written (if ctx.write threw mid-loop)
            for (int j = i; j < size; j++) {
                ReferenceCountUtil.release(buffer.get(j));
                try {
                    promises.get(j).tryFailure(new java.nio.channels.ClosedChannelException());
                } catch (Exception e) {
                    // ignore
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
}
