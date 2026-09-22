/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.channel.ChannelHandlerContext
 *  io.netty.channel.ChannelInboundHandlerAdapter
 *  io.netty.handler.codec.http.websocketx.PingWebSocketFrame
 *  io.netty.handler.codec.http.websocketx.PongWebSocketFrame
 *  io.netty.handler.timeout.IdleStateHandler
 *  io.netty.util.concurrent.EventExecutor
 *  io.netty.util.concurrent.Future
 *  io.netty.util.concurrent.GenericFutureListener
 */
package net.lax1dude.eaglercraft.backend.server.base.pipeline;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.lang.reflect.Method;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.IdleStateCompat;

public class WebSocketPingFrameHandler
extends ChannelInboundHandlerAdapter {
    private long nextPing = 0L;
    private int pingQuota = 3;
    protected final IdleStateHandler readHandlerToNotify;
    protected final IdleStateHandler writeHandlerToNotify;
    protected final long eaglerPingTimeout;
    private static final Method RESET_READ_TIMEOUT;
    private static final Method RESET_WRITE_TIMEOUT;

    private static void resetIdleTimeout(IdleStateHandler handler, boolean read) {
        Method m;
        Method method = m = read ? RESET_READ_TIMEOUT : RESET_WRITE_TIMEOUT;
        if (m == null) {
            return;
        }
        try {
            m.invoke((Object)handler, new Object[0]);
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            // empty catch block
        }
    }

    public WebSocketPingFrameHandler(IdleStateHandler readHandlerToNotify) {
        if (readHandlerToNotify != null) {
            long maxRead = Math.max(readHandlerToNotify.getReaderIdleTimeInMillis(), readHandlerToNotify.getAllIdleTimeInMillis());
            long maxWrite = Math.max(readHandlerToNotify.getWriterIdleTimeInMillis(), readHandlerToNotify.getAllIdleTimeInMillis());
            this.readHandlerToNotify = maxRead > 0L ? readHandlerToNotify : null;
            this.writeHandlerToNotify = maxWrite > 0L ? readHandlerToNotify : null;
            this.eaglerPingTimeout = maxRead > 0L ? Math.max(maxRead / 2L, maxRead - 10000L) * 1000000L : 0L;
        } else {
            this.readHandlerToNotify = null;
            this.writeHandlerToNotify = null;
            this.eaglerPingTimeout = 0L;
        }
    }

    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof PingWebSocketFrame) {
            PingWebSocketFrame msg2 = (PingWebSocketFrame)msg;
            msg2.release();
            long now = System.nanoTime();
            if (now > this.nextPing) {
                this.pingQuota = 3;
                this.nextPing = now + 3000000000L;
            }
            if (this.pingQuota > 0) {
                --this.pingQuota;
                if (this.readHandlerToNotify != null) {
                    IdleStateCompat.resetReadTimeout(this.readHandlerToNotify);
                }
                if (this.writeHandlerToNotify != null) {
                    ctx.writeAndFlush((Object)new PongWebSocketFrame()).addListener((GenericFutureListener)new Hack(ctx.executor()){

                        @Override
                        public void run0() {
                            IdleStateCompat.resetWriteTimeout(WebSocketPingFrameHandler.this.writeHandlerToNotify);
                        }
                    });
                } else {
                    ctx.writeAndFlush((Object)new PongWebSocketFrame(), ctx.voidPromise());
                }
            }
        } else if (!(msg instanceof PongWebSocketFrame)) {
            ctx.fireChannelRead(msg);
        } else {
            ((PongWebSocketFrame)msg).release();
            if (this.readHandlerToNotify != null) {
                IdleStateCompat.resetReadTimeout(this.readHandlerToNotify);
            }
        }
    }

    static {
        Method read = null;
        Method write = null;
        try {
            read = IdleStateHandler.class.getMethod("resetReadTimeout", new Class[0]);
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            // empty catch block
        }
        try {
            write = IdleStateHandler.class.getMethod("resetWriteTimeout", new Class[0]);
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            // empty catch block
        }
        RESET_READ_TIMEOUT = read;
        RESET_WRITE_TIMEOUT = write;
    }

    protected static abstract class Hack
    implements GenericFutureListener<Future<Void>>,
    Runnable {
        private final EventExecutor eventLoop;

        protected Hack(EventExecutor eventLoop) {
            this.eventLoop = eventLoop;
        }

        public void operationComplete(Future<Void> future) throws Exception {
            this.run();
        }

        @Override
        public void run() {
            if (this.eventLoop.inEventLoop()) {
                this.run0();
            } else {
                this.eventLoop.execute((Runnable)this);
            }
        }

        protected abstract void run0();
    }
}

