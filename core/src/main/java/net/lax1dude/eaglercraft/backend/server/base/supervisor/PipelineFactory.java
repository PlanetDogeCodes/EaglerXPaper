/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.bootstrap.Bootstrap
 *  io.netty.buffer.ByteBufAllocator
 *  io.netty.buffer.PooledByteBufAllocator
 *  io.netty.channel.Channel
 *  io.netty.channel.ChannelException
 *  io.netty.channel.ChannelFuture
 *  io.netty.channel.ChannelHandler
 *  io.netty.channel.ChannelInitializer
 *  io.netty.channel.ChannelOption
 *  io.netty.channel.ChannelPipeline
 *  io.netty.handler.timeout.ReadTimeoutHandler
 *  io.netty.util.AttributeKey
 */
package net.lax1dude.eaglercraft.backend.server.base.supervisor;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;
import java.net.SocketAddress;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServer;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.SupervisorClientHandshakeHandler;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.SupervisorService;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.netty.SupervisorDecoder;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.netty.SupervisorEncoder;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.netty.SupervisorPacketHandler;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.netty.Varint21FrameDecoder;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.netty.Varint21FrameEncoder;

public class PipelineFactory {
    public static final AttributeKey<SupervisorPacketHandler> HANDLER = AttributeKey.valueOf((String)"Handler");
    public static final Object MARK = PipelineFactory.createWriteBufferWaterMark(524288, 0x100000);

    private static Object createWriteBufferWaterMark(int low, int high) {
        try {
            Class<?> clz = Class.forName("io.netty.channel.WriteBufferWaterMark");
            return clz.getConstructor(Integer.TYPE, Integer.TYPE).newInstance(low, high);
        }
        catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    public static void initiateConnection(EaglerXServer<?> server, SocketAddress addr, SupervisorService<?> controller, int connectTimeout, int readTimeout) {
        ((Bootstrap)((Bootstrap)((Bootstrap)server.bootstrapClient(addr).handler(PipelineFactory.getChildInitializer(controller, readTimeout))).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)).option(ChannelOption.TCP_NODELAY, true)).connect().addListener(future -> {
            if (future.isSuccess()) {
                controller.handleChannelOpen((SupervisorPacketHandler)((Object)((Object)((ChannelFuture)future).channel().attr(HANDLER).get())));
            } else {
                controller.handleChannelFailure();
            }
        });
    }

    public static ChannelInitializer<Channel> getChildInitializer(final SupervisorService<?> controller, final int readTimeout) {
        return new ChannelInitializer<Channel>(){

            protected void initChannel(Channel channel) throws Exception {
                channel.config().setAllocator((ByteBufAllocator)PooledByteBufAllocator.DEFAULT);
                if (MARK != null) {
                    try {
                        channel.config().getClass().getMethod("setWriteBufferWaterMark", Class.forName("io.netty.channel.WriteBufferWaterMark")).invoke((Object)channel.config(), MARK);
                    }
                    catch (ReflectiveOperationException reflectiveOperationException) {
                        // empty catch block
                    }
                }
                try {
                    channel.config().setOption(ChannelOption.IP_TOS, 24);
                }
                catch (ChannelException channelException) {
                    // empty catch block
                }
                ChannelPipeline pipeline = channel.pipeline();
                pipeline.addLast("ReadTimeoutHandler", (ChannelHandler)new ReadTimeoutHandler(readTimeout));
                pipeline.addLast("Varint21FrameDecoder", (ChannelHandler)new Varint21FrameDecoder());
                pipeline.addLast("Varint21FrameEncoder", (ChannelHandler)new Varint21FrameEncoder());
                SupervisorDecoder dec = new SupervisorDecoder(1);
                pipeline.addLast("SupervisorDecoder", (ChannelHandler)dec);
                SupervisorEncoder enc = new SupervisorEncoder(0);
                pipeline.addLast("SupervisorEncoder", (ChannelHandler)enc);
                SupervisorPacketHandler h = new SupervisorPacketHandler(controller.logger(), channel, dec, enc, null);
                h.setConnectionHandler(new SupervisorClientHandshakeHandler(controller, h));
                pipeline.channel().attr(HANDLER).set(h);
                pipeline.addLast("SupervisorPacketHandler", (ChannelHandler)h);
            }
        };
    }
}

