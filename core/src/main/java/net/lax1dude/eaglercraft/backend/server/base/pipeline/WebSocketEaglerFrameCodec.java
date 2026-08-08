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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

@ChannelHandler.Sharable
public class WebSocketEaglerFrameCodec extends ChannelDuplexHandler {

        public static final WebSocketEaglerFrameCodec INSTANCE = new WebSocketEaglerFrameCodec();

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof BinaryWebSocketFrame msg1) {
                        // Bug #34 fix: explicitly transfer ownership of the content ByteBuf
                        // by retaining it before firing downstream, then release the frame.
                        // The standard Netty pattern is:
                        //   ctx.fireChannelRead(msg1.content().retain());
                        //   msg1.release();
                        // Without this, if the downstream handler forgets to release the
                        // ByteBuf (a downstream bug), both the ByteBuf and the frame leak —
                        // the frame's refcount never reaches 0.
                        ctx.fireChannelRead(msg1.content().retain());
                        msg1.release();
                } else if (msg instanceof WebSocketFrame msg2) {
                        // Text or close frames
                        msg2.release();
                        ctx.close();
                } else {
                        ctx.fireChannelRead(msg);
                }
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (msg instanceof ByteBuf buf) {
                        // Bug #33 fix: wrap empty ByteBufs in a BinaryWebSocketFrame too,
                        // for consistency. The downstream WebSocket frame encoder expects
                        // either a ByteBuf (to wrap) or a WebSocketFrame (to pass through).
                        // Previously, empty ByteBufs fell through to ctx.write(msg, promise)
                        // which could send a raw non-WebSocket byte stream and corrupt the
                        // WebSocket protocol.
                        ctx.write(new BinaryWebSocketFrame(buf), promise);
                        return;
                }
                ctx.write(msg, promise);
        }

}
