/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.buffer.ByteBuf
 *  io.netty.channel.ChannelDuplexHandler
 *  io.netty.channel.ChannelHandler$Sharable
 *  io.netty.channel.ChannelHandlerContext
 *  io.netty.channel.ChannelPromise
 *  io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
 *  io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
 *  io.netty.handler.codec.http.websocketx.WebSocketFrame
 */
package net.lax1dude.eaglercraft.backend.server.base.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

@ChannelHandler.Sharable
public class WebSocketEaglerFrameCodec
extends ChannelDuplexHandler {
    public static final WebSocketEaglerFrameCodec INSTANCE = new WebSocketEaglerFrameCodec();

    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof BinaryWebSocketFrame) {
            ctx.fireChannelRead((Object)((BinaryWebSocketFrame)msg).content());
        } else if (msg instanceof WebSocketFrame) {
            ((WebSocketFrame)msg).release();
            if (msg instanceof CloseWebSocketFrame) {
                ctx.close();
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ByteBuf buf;
        if (msg instanceof ByteBuf && (buf = (ByteBuf)msg).readableBytes() > 0) {
            ctx.write((Object)new BinaryWebSocketFrame(buf), promise);
            return;
        }
        ctx.write(msg, promise);
    }
}

