/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonSyntaxException
 *  io.netty.buffer.ByteBuf
 *  io.netty.buffer.Unpooled
 *  io.netty.channel.Channel
 *  io.netty.channel.ChannelFutureListener
 *  io.netty.channel.ChannelHandlerContext
 *  io.netty.channel.ChannelInboundHandlerAdapter
 *  io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
 *  io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
 *  io.netty.handler.codec.http.websocketx.TextWebSocketFrame
 *  io.netty.util.ReferenceCountUtil
 *  io.netty.util.concurrent.GenericFutureListener
 */
package net.lax1dude.eaglercraft.backend.server.base.pipeline;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.GenericFutureListener;
import java.net.SocketAddress;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformTask;
import net.lax1dude.eaglercraft.backend.server.api.EnumWebSocketHeader;
import net.lax1dude.eaglercraft.backend.server.api.INettyChannel;
import net.lax1dude.eaglercraft.backend.server.api.attribute.IAttributeKey;
import net.lax1dude.eaglercraft.backend.server.api.query.IDuplexBaseHandler;
import net.lax1dude.eaglercraft.backend.server.api.query.IDuplexBinaryHandler;
import net.lax1dude.eaglercraft.backend.server.api.query.IDuplexJSONHandler;
import net.lax1dude.eaglercraft.backend.server.api.query.IDuplexStringHandler;
import net.lax1dude.eaglercraft.backend.server.api.query.IQueryConnection;
import net.lax1dude.eaglercraft.backend.server.api.query.IQueryHandler;
import net.lax1dude.eaglercraft.backend.server.base.EaglerListener;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServer;
import net.lax1dude.eaglercraft.backend.server.base.IIdentifiedConnection;
import net.lax1dude.eaglercraft.backend.server.base.NettyPipelineData;
import net.lax1dude.eaglercraft.backend.server.base.query.MOTDConnectionWrapper;
import net.lax1dude.eaglercraft.backend.server.util.Util;

public class WebSocketQueryHandler
extends ChannelInboundHandlerAdapter
implements IQueryConnection,
IIdentifiedConnection,
INettyChannel.NettyUnsafe {
    private static final AtomicIntegerFieldUpdater<WebSocketQueryHandler> WAITING_PROMISE_HANDLE = AtomicIntegerFieldUpdater.newUpdater(WebSocketQueryHandler.class, "waitingPromiseCount");
    private static final AtomicIntegerFieldUpdater<WebSocketQueryHandler> DISCONNECT_CALLED_HANDLE = AtomicIntegerFieldUpdater.newUpdater(WebSocketQueryHandler.class, "disconnectCalled");
    private volatile int waitingPromiseCount = 1;
    private volatile int disconnectCalled = 0;
    private final EaglerXServer<?> server;
    private final NettyPipelineData pipelineData;
    private final long createdAt;
    private boolean initial = true;
    private boolean handled = false;
    private String accepted = null;
    private IDuplexStringHandler stringHandler = null;
    private IDuplexJSONHandler jsonHandler = null;
    private IDuplexBinaryHandler binaryHandler = null;
    private long maxAge = -1L;
    private IPlatformTask closeTask = null;
    private final ChannelFutureListener writeListener = e -> this.checkClose();

    public WebSocketQueryHandler(EaglerXServer<?> server, NettyPipelineData pipelineData) {
        this.server = server;
        this.pipelineData = pipelineData;
        this.createdAt = Util.steadyTime();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        block20: {
            try {
                if (msg instanceof CloseWebSocketFrame) {
                    this.waitingPromiseCount = 0;
                    ctx.close();
                    break block20;
                }
                if (!this.handled) {
                    TextWebSocketFrame msg2;
                    String accept;
                    this.handled = true;
                    if (msg instanceof TextWebSocketFrame && (accept = (msg2 = (TextWebSocketFrame)msg).text()).length() < 128 && (accept = accept.toLowerCase(Locale.US)).startsWith("accept:") && (accept = accept.substring(7).trim()).length() > 0) {
                        if ("motd".equals(accept) || accept.startsWith("motd.")) {
                            this.acceptMOTD(ctx, accept);
                            return;
                        }
                        this.acceptQuery(ctx, accept);
                        return;
                    }
                    this.waitingPromiseCount = 0;
                    ctx.close();
                    break block20;
                }
                if (msg instanceof TextWebSocketFrame) {
                    TextWebSocketFrame msg2 = (TextWebSocketFrame)msg;
                    String txt = msg2.text();
                    if (this.jsonHandler != null) {
                        JsonObject el = null;
                        try {
                            el = (JsonObject)EaglerXServer.GSON_PRETTY.fromJson(txt, JsonObject.class);
                        }
                        catch (JsonSyntaxException jsonSyntaxException) {
                            // empty catch block
                        }
                        if (el != null) {
                            this.jsonHandler.handleJSONObject(this, el.getAsJsonObject());
                            return;
                        }
                    }
                    if (this.stringHandler != null) {
                        this.stringHandler.handleString(this, txt);
                    } else {
                        this.waitingPromiseCount = 0;
                        ctx.close();
                    }
                    break block20;
                }
                if (msg instanceof BinaryWebSocketFrame) {
                    BinaryWebSocketFrame msg2 = (BinaryWebSocketFrame)msg;
                    if (this.binaryHandler != null) {
                        ByteBuf buf = msg2.content();
                        byte[] data = new byte[buf.readableBytes()];
                        buf.readBytes(data);
                        this.binaryHandler.handleBinary(this, data);
                    } else {
                        this.waitingPromiseCount = 0;
                        ctx.close();
                    }
                }
            }
            finally {
                ReferenceCountUtil.release((Object)msg);
            }
        }
    }

    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (ctx.channel().isActive()) {
            this.pipelineData.connectionLogger.error("Uncaught exception in handler pipeline", cause);
        }
    }

    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        this.waitingPromiseCount = 0;
        super.channelInactive(ctx);
    }

    private void acceptMOTD(ChannelHandlerContext ctx, String type) {
        if (this.pipelineData.listenerInfo.isAllowMOTD()) {
            this.accepted = type;
            MOTDConnectionWrapper motdConnection = new MOTDConnectionWrapper(this);
            motdConnection.setDefaults(this.server);
            this.server.eventDispatcher().dispatchMOTDEvent(motdConnection, (motdEvent, err) -> {
                try {
                    if (err != null) {
                        this.maxAge = -1L;
                        this.pipelineData.connectionLogger.error("MOTD event handler raised an exception", err);
                    } else {
                        motdEvent.getMOTDConnection().sendToUser();
                    }
                }
                finally {
                    if (this.maxAge <= 0L) {
                        this.disconnect();
                    }
                    this.initial = false;
                }
            });
        } else {
            this.waitingPromiseCount = 0;
            ctx.close();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void acceptQuery(ChannelHandlerContext ctx, String type) {
        IQueryHandler handler;
        if (this.server.testServerListConfirmCode(type)) {
            this.waitingPromiseCount = 0;
            ctx.writeAndFlush((Object)new TextWebSocketFrame("OK")).addListener((GenericFutureListener)ChannelFutureListener.CLOSE);
            return;
        }
        if (this.pipelineData.listenerInfo.isAllowQuery() && (handler = this.server.getQueryServer().getHandlerFor(type)) != null) {
            try {
                this.accepted = type;
                handler.handleQuery(this);
                return;
            }
            finally {
                if (this.maxAge <= 0L) {
                    this.disconnect();
                }
                this.initial = false;
            }
        }
        this.waitingPromiseCount = 0;
        ctx.close();
    }

    @Override
    public Object getIdentityToken() {
        return this.pipelineData.attributeHolder;
    }

    public int hashCode() {
        return System.identityHashCode(this.pipelineData.attributeHolder);
    }

    public boolean equals(Object o) {
        return this == o || o instanceof IIdentifiedConnection && ((IIdentifiedConnection)o).getIdentityToken() == this.pipelineData.attributeHolder;
    }

    @Override
    public <T> T get(IAttributeKey<T> key) {
        return this.pipelineData.attributeHolder.get(key);
    }

    @Override
    public <T> void set(IAttributeKey<T> key, T value) {
        this.pipelineData.attributeHolder.set(key, value);
    }

    @Override
    public boolean isConnected() {
        return this.waitingPromiseCount > 0 && this.pipelineData.channel.isActive();
    }

    @Override
    public void disconnect() {
        if (DISCONNECT_CALLED_HANDLE.getAndSet(this, 1) == 0) {
            this.checkClose();
        }
    }

    @Override
    public SocketAddress getSocketAddress() {
        return this.pipelineData.channel.remoteAddress();
    }

    @Override
    public String getRealAddress() {
        return this.pipelineData.realAddress;
    }

    @Override
    public EaglerListener getListenerInfo() {
        return this.pipelineData.listenerInfo;
    }

    @Override
    public String getAccept() {
        return this.accepted;
    }

    @Override
    public boolean isWebSocketSecure() {
        return this.pipelineData.wss;
    }

    @Override
    public String getWebSocketHeader(EnumWebSocketHeader header) {
        return this.pipelineData.getWebSocketHeader(header);
    }

    @Override
    public String getWebSocketPath() {
        return this.pipelineData.getWebSocketPath();
    }

    @Override
    public void setHandlers(IDuplexBaseHandler compositeHandler) {
        if (compositeHandler instanceof IDuplexStringHandler) {
            this.stringHandler = (IDuplexStringHandler)compositeHandler;
        }
        if (compositeHandler instanceof IDuplexJSONHandler) {
            this.jsonHandler = (IDuplexJSONHandler)compositeHandler;
        }
        if (compositeHandler instanceof IDuplexBinaryHandler) {
            this.binaryHandler = (IDuplexBinaryHandler)compositeHandler;
        }
    }

    @Override
    public void setHandlers(IDuplexBaseHandler ... compositeHandlers) {
        for (int i = 0; i < compositeHandlers.length; ++i) {
            this.setHandlers(compositeHandlers[i]);
        }
    }

    @Override
    public void setStringHandler(IDuplexStringHandler handler) {
        this.stringHandler = handler;
    }

    @Override
    public void setJSONHandler(IDuplexJSONHandler handler) {
        this.jsonHandler = handler;
    }

    @Override
    public void setBinaryHandler(IDuplexBinaryHandler handler) {
        this.binaryHandler = handler;
    }

    @Override
    public long getAge() {
        return Util.steadyTime() - this.createdAt;
    }

    @Override
    public void setMaxAge(long millis) {
        if (this.waitingPromiseCount > 0 && this.maxAge != millis) {
            this.maxAge = millis;
            if (this.closeTask != null) {
                this.closeTask.cancel();
            }
            if (millis > 0L) {
                long closeAfter = this.maxAge - this.getAge();
                if (closeAfter > 0L) {
                    this.closeTask = this.server.getPlatform().getScheduler().executeAsyncDelayedTask(this::disconnect, closeAfter);
                } else {
                    this.disconnect();
                }
            } else if (!this.initial) {
                this.disconnect();
            }
        }
    }

    @Override
    public long getMaxAge() {
        return this.maxAge;
    }

    private boolean aquireSend() {
        int i;
        while ((i = this.waitingPromiseCount) > 0) {
            if (!WAITING_PROMISE_HANDLE.compareAndSet(this, i, i + 1)) continue;
            return true;
        }
        return false;
    }

    @Override
    public void send(String string) {
        if (string == null) {
            throw new NullPointerException("string");
        }
        if (this.aquireSend()) {
            this.pipelineData.channel.eventLoop().execute(() -> this.pipelineData.channel.writeAndFlush((Object)new TextWebSocketFrame(string)).addListener((GenericFutureListener)this.writeListener));
        }
    }

    @Override
    public void send(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        if (this.aquireSend()) {
            this.pipelineData.channel.eventLoop().execute(() -> this.pipelineData.channel.writeAndFlush((Object)new BinaryWebSocketFrame(Unpooled.wrappedBuffer((byte[])bytes))).addListener((GenericFutureListener)this.writeListener));
        }
    }

    @Override
    public void sendResponse(String type, String str) {
        if (type == null) {
            throw new NullPointerException("type");
        }
        if (str == null) {
            throw new NullPointerException("str");
        }
        if (this.aquireSend()) {
            this.pipelineData.channel.eventLoop().execute(() -> this.pipelineData.channel.writeAndFlush((Object)new TextWebSocketFrame(this.server.getQueryServer().createStringResponse(type, str).toString())).addListener((GenericFutureListener)this.writeListener));
        }
    }

    @Override
    public void sendResponse(String type, JsonObject jsonObject) {
        if (type == null) {
            throw new NullPointerException("type");
        }
        if (jsonObject == null) {
            throw new NullPointerException("jsonObject");
        }
        if (this.aquireSend()) {
            this.pipelineData.channel.eventLoop().execute(() -> this.pipelineData.channel.writeAndFlush((Object)new TextWebSocketFrame(this.server.getQueryServer().createJsonObjectResponse(type, jsonObject).toString())).addListener((GenericFutureListener)this.writeListener));
        }
    }

    @Override
    public INettyChannel.NettyUnsafe netty() {
        return this;
    }

    @Override
    public Channel getChannel() {
        return this.pipelineData.channel;
    }

    private void checkClose() {
        if (WAITING_PROMISE_HANDLE.getAndAdd(this, -1) == 1) {
            this.pipelineData.channel.close();
        }
    }
}

