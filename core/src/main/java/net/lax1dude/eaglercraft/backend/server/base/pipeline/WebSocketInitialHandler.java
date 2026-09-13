/*
 * Copyright (c) 2025 lax1dude. All Rights Reserved.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
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

@ChannelHandler.Sharable
public class WebSocketInitialHandler extends ChannelInboundHandlerAdapter {

        public static final WebSocketInitialHandler INSTANCE = new WebSocketInitialHandler();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                try {
                        if (!ctx.channel().isActive()) {
                                return;
                        }
                        if (msg instanceof BinaryWebSocketFrame msg2) {
                                NettyPipelineData pipelineData = ctx.channel()
                                                .attr(PipelineAttributes.<NettyPipelineData>pipelineData()).get();
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
                                pipeline.addAfter(PipelineTransformer.HANDLER_WS_INITIAL, PipelineTransformer.HANDLER_HANDSHAKE,
                                                new WebSocketEaglerInitialHandler(pipelineData.server, pipelineData));
                                pipeline.fireUserEventTriggered(EnumPipelineEvent.EAGLER_STATE_WEBSOCKET_PLAYER);
                                pipeline.replace(PipelineTransformer.HANDLER_WS_INITIAL, PipelineTransformer.HANDLER_FRAME_CODEC,
                                                WebSocketEaglerFrameCodec.INSTANCE);
                                // Watch for the vanilla server enabling MC-level compression on this
                                // connection and swap the compression codecs back out for no-op
                                // placeholders. Eaglercraft clients speak one raw packet per WebSocket
                                // frame and cannot parse the [uncompressedSize] prefix that
                                // CompressionEncoder prepends to every packet.
                                pipeline.addAfter(PipelineTransformer.HANDLER_FRAME_CODEC, EaglerCompressionGuardHandler.HANDLER_NAME,
                                                new EaglerCompressionGuardHandler(pipelineData));
                                // Adaptive packet batcher — coalesces flushes (not frames) under
                                // load: each buffered packet is still written as its own WebSocket
                                // frame downstream of the frame codec, the flushes just happen
                                // together.
                                if (net.lax1dude.eaglercraft.backend.server.base.config.EaglerXPaperConfig.enableAdaptiveBatching) {
                                        try {
                                                pipeline.addAfter(EaglerCompressionGuardHandler.HANDLER_NAME,
                                                                AdaptivePacketBatcher.HANDLER_NAME,
                                                                new AdaptivePacketBatcher());
                                        } catch (Exception e) {
                                                pipelineData.server.logger().warn("Could not install the adaptive packet batcher", e);
                                        }
                                }
                                pipeline.fireUserEventTriggered(EnumPipelineEvent.EAGLER_INJECTED_FRAME_HANDLERS);
                                ctx.fireChannelRead(msg2.content().retain());
                        } else if (msg instanceof TextWebSocketFrame msg2) {
                                NettyPipelineData pipelineData = ctx.channel()
                                                .attr(PipelineAttributes.<NettyPipelineData>pipelineData()).get();
                                if (pipelineData.initStall) {
                                        return;
                                }
                                if (!pipelineData.processQueryRatelimit(ctx)) {
                                        pipelineData.initStall = true;
                                        return;
                                }
                                ChannelPipeline pipeline = ctx.pipeline();
                                pipelineData.server.getPipelineTransformer().removeVanillaHandlers(pipeline);
                                pipeline.remove(PipelineTransformer.HANDLER_WS_INITIAL);
                                pipeline.addLast(PipelineTransformer.HANDLER_QUERY, new WebSocketQueryHandler(pipelineData.server, pipelineData));
                                pipeline.fireUserEventTriggered(EnumPipelineEvent.EAGLER_STATE_WEBSOCKET_QUERY);
                                ctx.fireChannelRead(msg2.retain());
                        } else {
                                ctx.close();
                        }
                } finally {
                        ReferenceCountUtil.release(msg);
                }
        }

}
