/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.channel.Channel
 *  io.netty.channel.ChannelDuplexHandler
 *  io.netty.channel.ChannelHandler
 *  io.netty.channel.ChannelHandlerContext
 *  io.netty.channel.ChannelPipeline
 *  io.netty.channel.ChannelPromise
 */
package net.lax1dude.eaglercraft.backend.server.base.pipeline;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import java.util.Map;
import net.lax1dude.eaglercraft.backend.server.api.EnumPipelineEvent;
import net.lax1dude.eaglercraft.backend.server.base.NettyPipelineData;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.NOPDummyHandler;
import net.lax1dude.eaglercraft.backend.server.util.Util;

public class EaglerCompressionGuardHandler
extends ChannelDuplexHandler {
    public static final String HANDLER_NAME = "eagler-compression-guard";
    private static final Object COMPRESSION_ENABLED_EVENT;
    private final NettyPipelineData pipelineData;
    private boolean stripLog = true;
    // Hot-path optimization: pipeline.get(name) is a linear walk, so stop re-scanning on
    // every write once the connection has been confirmed compression-free for a while.
    // Any vanilla compression install fires COMPRESSION_THRESHOLD_SET (Paper 1.20.2+) or
    // is caught by the 0x03 login-packet observer, both of which re-arm the write check.
    private static final int DISARM_AFTER_CLEAN_CHECKS = 256;
    private int cleanCheckStreak = 0;
    private boolean writeCheckDisarmed = false;

    public EaglerCompressionGuardHandler(NettyPipelineData pipelineData) {
        this.pipelineData = pipelineData;
    }

    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt == EnumPipelineEvent.EAGLER_ENTERED_PLAY_STATE || evt == EnumPipelineEvent.EAGLER_HANDSHAKE_COMPLETE || COMPRESSION_ENABLED_EVENT != null && evt == COMPRESSION_ENABLED_EVENT) {
            this.writeCheckDisarmed = false;
            this.cleanCheckStreak = 0;
            if (ctx.channel().eventLoop().inEventLoop()) {
                this.swapCompressionHandlers(ctx.pipeline());
            } else {
                ctx.channel().eventLoop().execute(() -> this.swapCompressionHandlers(ctx.pipeline()));
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (!this.writeCheckDisarmed) {
            this.checkQuickStrip(ctx.pipeline());
        }
        ctx.write(msg, promise);
    }

    public static void scheduleStrip(Channel channel) {
        if (channel == null || !channel.isActive()) {
            return;
        }
        ChannelHandler handler = channel.pipeline().get(HANDLER_NAME);
        if (!(handler instanceof EaglerCompressionGuardHandler)) {
            return;
        }
        channel.eventLoop().execute(() -> {
            ChannelHandler h = channel.pipeline().get(HANDLER_NAME);
            if (h instanceof EaglerCompressionGuardHandler) {
                ((EaglerCompressionGuardHandler)h).swapCompressionHandlers(channel.pipeline());
            }
        });
    }

    private void checkQuickStrip(ChannelPipeline pipeline) {
        ChannelHandler compress = pipeline.get("compress");
        if (compress != null && compress != NOPDummyHandler.INSTANCE && EaglerCompressionGuardHandler.isCompressionCodec(compress)) {
            this.swapCompressionHandlers(pipeline);
            return;
        }
        ChannelHandler decompress = pipeline.get("decompress");
        if (decompress != null && decompress != NOPDummyHandler.INSTANCE && EaglerCompressionGuardHandler.isCompressionCodec(decompress)) {
            this.swapCompressionHandlers(pipeline);
            return;
        }
        if (++this.cleanCheckStreak >= DISARM_AFTER_CLEAN_CHECKS) {
            this.writeCheckDisarmed = true;
        }
    }

    private static boolean isCompressionCodec(ChannelHandler handler) {
        return handler.getClass().getSimpleName().contains("ompress");
    }

    private void swapCompressionHandlers(ChannelPipeline pipeline) {
        boolean stripped = false;
        stripped |= this.swap(pipeline, "decompress");
        stripped |= this.swap(pipeline, "compress");
        if ((stripped |= this.scanAndSwap(pipeline)) && this.pipelineData != null && this.pipelineData.connectionLogger != null) {
            this.pipelineData.connectionLogger.info("Vanilla compression codecs stripped from Eaglercraft connection");
        }
    }

    private boolean swap(ChannelPipeline pipeline, String name) {
        ChannelHandler handler = pipeline.get(name);
        if (handler != null && EaglerCompressionGuardHandler.isCompressionCodec(handler)) {
            try {
                pipeline.replace(name, name, (ChannelHandler)NOPDummyHandler.INSTANCE);
                return true;
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        return false;
    }

    private boolean scanAndSwap(ChannelPipeline pipeline) {
        boolean stripped = false;
        for (Map.Entry entry : pipeline.toMap().entrySet()) {
            Class<?> clz;
            String clzName;
            ChannelHandler handler = (ChannelHandler)entry.getValue();
            if (handler == null || handler == this || handler == NOPDummyHandler.INSTANCE || handler instanceof EaglerCompressionGuardHandler || !EaglerCompressionGuardHandler.isCompressionCodec(handler) || !(clzName = (clz = handler.getClass()).getName()).startsWith("net.minecraft") && !clzName.startsWith("io.netty")) continue;
            String name = (String)entry.getKey();
            try {
                pipeline.replace(name, name, (ChannelHandler)NOPDummyHandler.INSTANCE);
                stripped = true;
            }
            catch (Throwable throwable) {}
        }
        return stripped;
    }

    static {
        Object evt = null;
        if (Util.classExists("io.papermc.paper.network.ConnectionEvent")) {
            try {
                evt = Class.forName("io.papermc.paper.network.ConnectionEvent").getDeclaredField("COMPRESSION_THRESHOLD_SET").get(null);
            }
            catch (ReflectiveOperationException reflectiveOperationException) {
                // empty catch block
            }
        }
        COMPRESSION_ENABLED_EVENT = evt;
    }
}

