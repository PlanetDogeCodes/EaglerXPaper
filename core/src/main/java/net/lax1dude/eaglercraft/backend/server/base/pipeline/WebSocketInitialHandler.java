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
                    if (EaglerXPaperConfig.enablePacketJournal) {
                        // Stable 2 (round 4 fix): the journal is installed HEAD-ward of the
                        // frame codec so it observes every encoded BinaryWebSocketFrame
                        // handed to the WebSocket transport — vanilla Minecraft packets
                        // AND the Eagler-protocol plugin frames (skins, capes, BackendRPC,
                        // webview, voice) injected between the frame codec and the guard,
                        // in exact wire order, after the batcher has forwarded its buffers.
                        // (The previous tail-of-batcher position was blind to the injected
                        // stream — precisely the stream a client-side decode-kick report
                        // needs evidence from.)
                        try {
                            pipeline.addBefore("eagler-frame-codec", OutboundPacketJournalHandler.HANDLER_NAME,
                                            (ChannelHandler)new OutboundPacketJournalHandler(pipelineData));
                        }
                        catch (Exception e) {
                            pipelineData.server.logger().warn("Could not install the outbound packet journal", e);
                        }
                    }
                    // Stable 2 (restored feature): per-connection traffic statistics for
                    // /eaglerclients and /eaglerdiagnose — near-zero cost, always installed.
                    // Round 4 fix: moved head-ward of the frame codec together with the
                    // journal so the reported bandwidth covers the full wire stream.
                    try {
                        pipeline.addBefore("eagler-frame-codec", ConnectionStatsHandler.HANDLER_NAME,
                                        (ChannelHandler)new ConnectionStatsHandler());
                    }
                    catch (Exception e) {
                        pipelineData.server.logger().warn("Could not install the connection stats handler", e);
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
                    // Stable 2 fix: this connection is now a long-lived query/MOTD channel, not
                    // a player login — the 30s login timeout armed at WS-upgrade would otherwise
                    // force-close it mid-session. No cancelling handler exists on this path.
                    pipelineData.cancelLoginTimeoutHelper();
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

