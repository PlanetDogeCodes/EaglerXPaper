/*
 * Copyright (c) 2025 lax1dude. All Rights Reserved.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 */

package net.lax1dude.eaglercraft.backend.server.base.pipeline;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.ScheduledFuture;
import net.lax1dude.eaglercraft.backend.server.base.NettyPipelineData;

/**
 * Bridges the gap between Eagler handshake completion and the vanilla server's
 * protocol state machine reaching PLAY/CONFIGURATION.
 *
 * On MC 1.8-1.20.1 the vanilla server only flips its decoder to the play state
 * a few ticks AFTER LoginSuccess is sent (when the play listener is installed).
 * The Eagler client, however, considers the login finished as soon as it gets
 * the finish-login packet and immediately sends play packets (ClientSettings,
 * brand plugin messages, etc). On fast/local connections those packets can
 * reach the vanilla PacketDecoder while it is still in LOGIN state, producing
 * "DecoderException: Bad packet id N" and killing the connection.
 *
 * This handler sits at the old handshake position and buffers inbound client
 * bytes until the server's NetworkManager protocol attribute (the same
 * attribute the vanilla PacketDecoder reads) reports PLAY or CONFIGURATION,
 * then releases them in order. It polls every 50ms and discards whatever is
 * still buffered after 5 seconds (a login that slow is already doomed; dropping
 * ClientSettings/brand keeps the connection alive instead of killing it). If the protocol
 * attribute cannot be located reflectively the handler passes everything
 * through immediately (matching the previous behavior).
 */
public class EaglerPlayStateSyncHandler extends ChannelInboundHandlerAdapter {

        public static final String HANDLER_NAME = "eagler-play-state-sync";

        private static final long CHECK_INTERVAL_MS = 50L;
        private static final long FORCE_DISCARD_MS = 5000L;

        private static volatile AttributeKey<Object> cachedProtocolKey = null;
        private static volatile boolean protocolKeyResolved = false;

        private final NettyPipelineData pipelineData;
        private final List<ByteBuf> buffered = new ArrayList<ByteBuf>(4);
        private boolean flushed = false;
        private ScheduledFuture<?> checkTask = null;
        private long firstBufferedAt = 0L;

        public EaglerPlayStateSyncHandler(NettyPipelineData pipelineData) {
                this.pipelineData = pipelineData;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (this.flushed || !(msg instanceof ByteBuf)) {
                        ctx.fireChannelRead(msg);
                        return;
                }
                AttributeKey<Object> key = EaglerPlayStateSyncHandler.resolveProtocolKey(ctx.channel());
                if (key == null || EaglerPlayStateSyncHandler.isPlayOrConfig(ctx.channel(), key)) {
                        this.flushDownstream(ctx);
                        ctx.fireChannelRead(msg);
                        return;
                }
                // NOTE: no retain() here — fireChannelRead transferred sole ownership of msg to
                // this handler; flushDownstream re-transfers it downstream and discardBuffered
                // releases it, so the refcounts balance exactly.
                this.buffered.add((ByteBuf) msg);
                if (this.firstBufferedAt == 0L) {
                        this.firstBufferedAt = System.currentTimeMillis();
                }
                this.scheduleCheck(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                this.discardBuffered();
                super.channelInactive(ctx);
        }

        @Override
        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
                this.discardBuffered();
        }

        private void scheduleCheck(ChannelHandlerContext ctx) {
                if (this.checkTask != null || !ctx.channel().isActive()) {
                        return;
                }
                try {
                        this.checkTask = ctx.channel().eventLoop().scheduleAtFixedRate(() -> {
                                try {
                                        AttributeKey<Object> key = EaglerPlayStateSyncHandler.resolveProtocolKey(ctx.channel());
                                        long elapsed = System.currentTimeMillis() - this.firstBufferedAt;
                                        boolean ready = key != null
                                                        && EaglerPlayStateSyncHandler.isPlayOrConfig(ctx.channel(), key);
                                        if (ready) {
                                                this.cancelTask();
                                                this.flushDownstream(ctx);
                                        } else if (elapsed >= FORCE_DISCARD_MS || !ctx.channel().isActive()) {
                                                // The server never reached play state in time. The buffered
                                                // packets (ClientSettings/brand) would be rejected by the
                                                // login-state decoder, so drop them and keep the connection
                                                // alive rather than converting a slow login into a kick.
                                                this.cancelTask();
                                                this.discardBuffered();
                                        }
                                } catch (Throwable t) {
                                        this.cancelTask();
                                        this.flushDownstream(ctx);
                                }
                        }, CHECK_INTERVAL_MS, CHECK_INTERVAL_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (Throwable rejected) {
                        // event loop shutting down (server stop/reload) — release anything
                        // buffered, we will never get a poll callback to flush it
                        this.discardBuffered();
                }
        }

        private void cancelTask() {
                if (this.checkTask != null) {
                        this.checkTask.cancel(false);
                        this.checkTask = null;
                }
        }

        private void flushDownstream(ChannelHandlerContext ctx) {
                if (this.flushed) {
                        return;
                }
                this.flushed = true;
                this.cancelTask();
                if (this.buffered.isEmpty()) {
                        return;
                }
                List<ByteBuf> toFlush = new ArrayList<ByteBuf>(this.buffered);
                this.buffered.clear();
                if (this.pipelineData != null && this.pipelineData.connectionLogger != null) {
                        this.pipelineData.connectionLogger.info(
                                        "Released " + toFlush.size() + " early play-state packet(s) to the vanilla pipeline");
                }
                for (ByteBuf buf : toFlush) {
                        try {
                                ctx.fireChannelRead(buf);
                        } catch (Throwable t) {
                                ReferenceCountUtil.release(buf);
                        }
                }
        }

        private void discardBuffered() {
                this.flushed = true;
                this.cancelTask();
                if (!this.buffered.isEmpty()) {
                        for (ByteBuf buf : this.buffered) {
                                ReferenceCountUtil.release(buf);
                        }
                        this.buffered.clear();
                }
        }

        /**
         * Resolves the vanilla protocol attribute key from the channel's
         * packet_handler (NetworkManager/Connection or a subclass proxy) and
         * caches successful resolutions for the JVM. Returns null when it cannot
         * be found, in which case this handler passes everything through for the
         * rest of the connection; the next connection retries the lookup.
         */
        public static AttributeKey<Object> resolveProtocolKey(Channel channel) {
                if (EaglerPlayStateSyncHandler.protocolKeyResolved) {
                        return EaglerPlayStateSyncHandler.cachedProtocolKey;
                }
                synchronized (EaglerPlayStateSyncHandler.class) {
                        if (EaglerPlayStateSyncHandler.protocolKeyResolved) {
                                return EaglerPlayStateSyncHandler.cachedProtocolKey;
                        }
                        AttributeKey<Object> found = null;
                        try {
                                ChannelHandler nm = channel.pipeline().get("packet_handler");
                                Class<?> clz = nm != null ? nm.getClass() : null;
                                while (clz != null && clz != Object.class) {
                                        for (Field f : clz.getDeclaredFields()) {
                                                if (!Modifier.isStatic(f.getModifiers())
                                                                || f.getType() != AttributeKey.class) {
                                                        continue;
                                                }
                                                try {
                                                        f.setAccessible(true);
                                                        AttributeKey<?> key = (AttributeKey<?>) f.get(null);
                                                        if (key == null) {
                                                                continue;
                                                        }
                                                        Object v = channel.attr(key).get();
                                                        if (v instanceof Enum && EaglerPlayStateSyncHandler
                                                                        .isProtocolEnumName(((Enum<?>) v).name())) {
                                                                found = (AttributeKey<Object>) key;
                                                                break;
                                                        }
                                                } catch (Throwable ignored) {
                                                }
                                        }
                                        if (found != null) {
                                                break;
                                        }
                                        clz = clz.getSuperclass();
                                }
                        } catch (Throwable ignored) {
                        }
                        if (found != null) {
                                EaglerPlayStateSyncHandler.cachedProtocolKey = found;
                                EaglerPlayStateSyncHandler.protocolKeyResolved = true;
                        }
                        return found;
                }
        }

        private static boolean isPlayOrConfig(Channel channel, AttributeKey<Object> key) {
                Object v = channel.attr(key).get();
                if (v instanceof Enum) {
                        String n = ((Enum<?>) v).name();
                        return "PLAY".equals(n) || "CONFIGURATION".equals(n);
                }
                return false;
        }

        private static boolean isProtocolEnumName(String name) {
                return "HANDSHAKING".equals(name) || "STATUS".equals(name) || "LOGIN".equals(name)
                                || "PLAY".equals(name) || "CONFIGURATION".equals(name);
        }

}
