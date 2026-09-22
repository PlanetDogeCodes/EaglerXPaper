/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.channel.ChannelHandler
 *  io.netty.channel.ChannelHandler$Sharable
 *  io.netty.channel.ChannelHandlerContext
 *  io.netty.channel.ChannelInboundHandlerAdapter
 *  io.netty.channel.ChannelPipeline
 *  io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
 *  io.netty.handler.codec.http.websocketx.TextWebSocketFrame
 *  io.netty.util.ReferenceCountUtil
 */
package net.lax1dude.eaglercraft.backend.server.base.pipeline;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.ReferenceCountUtil;
import net.lax1dude.eaglercraft.backend.server.adapter.PipelineAttributes;
import net.lax1dude.eaglercraft.backend.server.api.EnumPipelineEvent;
import net.lax1dude.eaglercraft.backend.server.base.NettyPipelineData;
import net.lax1dude.eaglercraft.backend.server.base.config.EaglerXPaperConfig;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.AdaptivePacketBatcher;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.EaglerCompressionGuardHandler;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.WebSocketEaglerFrameCodec;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.WebSocketEaglerInitialHandler;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.WebSocketQueryHandler;

@ChannelHandler.Sharable
public class WebSocketInitialHandler
extends ChannelInboundHandlerAdapter {
    public static final WebSocketInitialHandler INSTANCE = new WebSocketInitialHandler();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        block21: {
            try {
                if (!ctx.channel().isActive()) {
                    return;
                }
                if (msg instanceof BinaryWebSocketFrame) {
                    BinaryWebSocketFrame msg2 = (BinaryWebSocketFrame)msg;
                    NettyPipelineData pipelineData = (NettyPipelineData)ctx.channel().attr(PipelineAttributes.pipelineData()).get();
                    if (pipelineData.initStall) {
                        return;
                    }
                    if (!pipelineData.processRealAddress()) {
                        pipelineData.initStall = true;
                        ctx.close();
                        return;
                    }
                    if (!pipelineData.processLoginRatelimit(ctx)) {
                        pipelineData.initStall = true;
                        return;
                    }
                    ChannelPipeline pipeline = ctx.pipeline();
                    pipeline.addAfter("eagler-ws-initial", "eagler-handshake", (ChannelHandler)new WebSocketEaglerInitialHandler(pipelineData.server, pipelineData));
                    pipeline.fireUserEventTriggered((Object)EnumPipelineEvent.EAGLER_STATE_WEBSOCKET_PLAYER);
                    pipeline.replace("eagler-ws-initial", "eagler-frame-codec", (ChannelHandler)WebSocketEaglerFrameCodec.INSTANCE);
                    pipeline.addAfter("eagler-frame-codec", "eagler-compression-guard", (ChannelHandler)new EaglerCompressionGuardHandler(pipelineData));
                    if (EaglerXPaperConfig.enableAdaptiveBatching) {
                        try {
                            pipeline.addAfter("eagler-compression-guard", "eagler-adaptive-batcher", (ChannelHandler)new AdaptivePacketBatcher());
                        }
                        catch (Exception e) {
                            pipelineData.server.logger().warn("Could not install the adaptive packet batcher", e);
                        }
                    }
                    pipeline.fireUserEventTriggered((Object)EnumPipelineEvent.EAGLER_INJECTED_FRAME_HANDLERS);
                    ctx.fireChannelRead((Object)msg2.content().retain());
                    break block21;
                }
                if (msg instanceof TextWebSocketFrame) {
                    TextWebSocketFrame msg2 = (TextWebSocketFrame)msg;
                    NettyPipelineData pipelineData = (NettyPipelineData)ctx.channel().attr(PipelineAttributes.pipelineData()).get();
                    if (pipelineData.initStall) {
                        return;
                    }
                    if (!pipelineData.processQueryRatelimit(ctx)) {
                        pipelineData.initStall = true;
                        return;
                    }
                    ChannelPipeline pipeline = ctx.pipeline();
                    pipelineData.server.getPipelineTransformer().removeVanillaHandlers(pipeline);
                    pipeline.remove("eagler-ws-initial");
                    pipeline.addLast("eagler-query", (ChannelHandler)new WebSocketQueryHandler(pipelineData.server, pipelineData));
                    pipeline.fireUserEventTriggered((Object)EnumPipelineEvent.EAGLER_STATE_WEBSOCKET_QUERY);
                    ctx.fireChannelRead((Object)msg2.retain());
                } else {
                    ctx.close();
                }
            }
            finally {
                ReferenceCountUtil.release((Object)msg);
            }
        }
    }
}

