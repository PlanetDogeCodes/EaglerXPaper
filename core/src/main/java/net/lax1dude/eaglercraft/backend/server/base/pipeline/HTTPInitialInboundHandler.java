/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.net.InetAddresses
 *  io.netty.channel.Channel
 *  io.netty.channel.ChannelFutureListener
 *  io.netty.channel.ChannelHandler
 *  io.netty.channel.ChannelHandler$Sharable
 *  io.netty.channel.ChannelHandlerContext
 *  io.netty.channel.ChannelInboundHandlerAdapter
 *  io.netty.channel.ChannelPipeline
 *  io.netty.handler.codec.http.FullHttpRequest
 *  io.netty.handler.codec.http.HttpHeaders
 *  io.netty.handler.codec.http.HttpMessage
 *  io.netty.handler.codec.http.HttpRequest
 *  io.netty.handler.codec.http.HttpVersion
 *  io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator
 *  io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker
 *  io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory
 *  io.netty.util.ReferenceCountUtil
 *  io.netty.util.concurrent.GenericFutureListener
 */
package net.lax1dude.eaglercraft.backend.server.base.pipeline;

import com.google.common.net.InetAddresses;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.GenericFutureListener;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import net.lax1dude.eaglercraft.backend.server.adapter.PipelineAttributes;
import net.lax1dude.eaglercraft.backend.server.adapter.event.IEventDispatchAdapter;
import net.lax1dude.eaglercraft.backend.server.api.EnumPipelineEvent;
import net.lax1dude.eaglercraft.backend.server.base.CompoundRateLimiterMap;
import net.lax1dude.eaglercraft.backend.server.base.EaglerListener;
import net.lax1dude.eaglercraft.backend.server.base.NettyPipelineData;
import net.lax1dude.eaglercraft.backend.server.base.config.ConfigDataListener;
import net.lax1dude.eaglercraft.backend.server.base.config.ConfigDataSettings;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.HTTPMessageUtils;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.HTTPRequestInboundHandler;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.WebSocketActivePingFrameHandler;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.WebSocketInitialHandler;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.WebSocketPingFrameHandler;

@ChannelHandler.Sharable
public class HTTPInitialInboundHandler
extends ChannelInboundHandlerAdapter {
    public static final HTTPInitialInboundHandler INSTANCE = new HTTPInitialInboundHandler();

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void channelRead(ChannelHandlerContext ctx, Object msgRaw) throws Exception {
        block31: {
            NettyPipelineData pipelineData = null;
            try {
                if (!ctx.channel().isActive()) {
                    return;
                }
                pipelineData = (NettyPipelineData)ctx.channel().attr(PipelineAttributes.pipelineData()).get();
                if (pipelineData == null) {
                    ctx.close();
                    return;
                }
                if (!pipelineData.initStall && msgRaw instanceof FullHttpRequest) {
                    FullHttpRequest msg = (FullHttpRequest)msgRaw;
                    if (HTTPMessageUtils.getProtocolVersion((HttpMessage)msg) != HttpVersion.HTTP_1_1) {
                        pipelineData.initStall = true;
                        ctx.close();
                        return;
                    }
                    HttpHeaders headers = msg.headers();
                    EaglerListener listener = pipelineData.listenerInfo;
                    ConfigDataListener conf = listener.getConfigData();
                    if (conf.isForwardSecret() && !conf.getForwardSecretValue().equals(headers.get(conf.getForwardSecretHeader()))) {
                        pipelineData.connectionLogger.error("Connected without a valid forwarding secret header, disconnecting...");
                        pipelineData.initStall = true;
                        ctx.close();
                        return;
                    }
                    if (listener.isForwardIP()) {
                        String forwardedIP = HTTPMessageUtils.getFirstValue(headers, conf.getForwardIPHeader());
                        if (forwardedIP != null) {
                            if (pipelineData.server.getConfig().getSettings().isDebugLogRealIPHeaders()) {
                                pipelineData.connectionLogger.info("Real IP header value: \"" + forwardedIP + "\"");
                            }
                            pipelineData.realAddress = forwardedIP;
                            CompoundRateLimiterMap rateLimiter = pipelineData.listenerInfo.getRateLimiter();
                            if (rateLimiter != null) {
                                InetAddress addr;
                                try {
                                    addr = InetAddresses.forString((String)pipelineData.realAddress);
                                }
                                catch (IllegalArgumentException ex) {
                                    pipelineData.connectionLogger.error("Connected with an invalid \"" + conf.getForwardIPHeader() + "\" header, disconnecting...", ex);
                                    pipelineData.initStall = true;
                                    ctx.close();
                                    return;
                                }
                                pipelineData.realInetAddress = addr;
                                pipelineData.rateLimits = rateLimiter.rateLimit(addr);
                                if (pipelineData.rateLimits == null) {
                                    pipelineData.initStall = true;
                                    ctx.close();
                                    return;
                                }
                            }
                        } else {
                            pipelineData.connectionLogger.error("Connected without a \"" + conf.getForwardIPHeader() + "\" header, disconnecting...");
                            pipelineData.initStall = true;
                            ctx.close();
                            return;
                        }
                    }
                    if (HTTPMessageUtils.containsValue(headers, "connection", "upgrade", true) && HTTPMessageUtils.containsValue(headers, "upgrade", "websocket", false)) {
                        pipelineData.initStall = true;
                        this.handleWebSocket(ctx, pipelineData, msg);
                        return;
                    }
                    this.handleHTTP(ctx, pipelineData, msg);
                    break block31;
                }
                ctx.close();
            }
            catch (Throwable t) {
                if (pipelineData != null) {
                    try {
                        pipelineData.connectionLogger.error("Exception in HTTP initial inbound handler", t);
                    }
                    catch (Throwable t2) {
                        t.printStackTrace();
                    }
                } else {
                    t.printStackTrace();
                }
                ctx.close();
            }
            finally {
                ReferenceCountUtil.release((Object)msgRaw);
            }
        }
    }

    private void handleWebSocket(ChannelHandlerContext ctx, NettyPipelineData pipelineData, FullHttpRequest msg) throws Exception {
        HttpHeaders headers = msg.headers();
        pipelineData.headerHost = headers.get("host");
        pipelineData.headerOrigin = headers.get("origin");
        pipelineData.headerUserAgent = headers.get("user-agent");
        pipelineData.headerCookie = headers.get("cookie");
        pipelineData.headerAuthorization = headers.get("authorization");
        pipelineData.requestPath = HTTPMessageUtils.getURI((HttpRequest)msg);
        ConfigDataSettings settings = pipelineData.server.getConfig().getSettings();
        ChannelPipeline pipeline = ctx.pipeline();
        pipeline.replace("eagler-http-aggregator", "eagler-ws-aggregator", (ChannelHandler)new WebSocketFrameAggregator(settings.getHTTPWebSocketFragmentSize()));
        pipeline.replace("eagler-http-initial", "eagler-ws-initial", (ChannelHandler)WebSocketInitialHandler.INSTANCE);
        pipeline.addBefore("eagler-ws-initial", "eagler-ws-ping-handler", (ChannelHandler)(pipelineData.server.getConfig().getSettings().getHTTPWebSocketPingIntervention() ? new WebSocketActivePingFrameHandler(pipelineData.idleStateHandler) : new WebSocketPingFrameHandler(pipelineData.idleStateHandler)));
        IEventDispatchAdapter<?, ?> dispatch = pipelineData.server.eventDispatcher();
        msg.retain();
        dispatch.dispatchWebSocketOpenEvent(pipelineData, msg, (evt, err) -> ctx.channel().eventLoop().execute(() -> {
            try {
                if (err == null) {
                    if (ctx.channel().isActive()) {
                        if (!evt.isCancelled()) {
                            this.handshakeWebSocket(ctx, pipelineData, msg, settings.getHTTPWebSocketMaxFrameLength());
                        } else {
                            ctx.close();
                        }
                    }
                } else {
                    pipelineData.connectionLogger.error("Exception thrown while handling web socket open event", err);
                    ctx.close();
                }
            }
            finally {
                msg.release();
            }
        }));
    }

    private void handshakeWebSocket(ChannelHandlerContext ctx, NettyPipelineData pipelineData, FullHttpRequest msg, int maxFrameLen) {
        WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory((pipelineData.wss ? "wss://" : "ws://") + pipelineData.headerHost + pipelineData.requestPath, null, true, maxFrameLen);
        WebSocketServerHandshaker hs = factory.newHandshaker((HttpRequest)msg);
        if (hs != null) {
            hs.handshake(ctx.channel(), msg).addListener(future -> {
                if (future.isSuccess()) {
                    pipelineData.initStall = false;
                    pipelineData.scheduleLoginTimeoutHelper();
                } else {
                    ctx.close();
                }
            });
        } else {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse((Channel)ctx.channel()).addListener((GenericFutureListener)ChannelFutureListener.CLOSE);
        }
    }

    private void handleHTTP(ChannelHandlerContext ctx, NettyPipelineData pipelineData, FullHttpRequest msg) throws Exception {
        ChannelPipeline pipeline = ctx.pipeline();
        pipelineData.server.getPipelineTransformer().removeVanillaHandlers(pipeline);
        pipeline.remove("eagler-http-initial");
        pipeline.addLast("eagler-http", (ChannelHandler)new HTTPRequestInboundHandler(pipelineData.server, pipelineData));
        pipeline.fireUserEventTriggered((Object)EnumPipelineEvent.EAGLER_STATE_HTTP_REQUEST);
        ctx.fireChannelRead(ReferenceCountUtil.retain((Object)msg));
    }

    static boolean recheckRatelimitAddress(ChannelHandlerContext ctx, NettyPipelineData pipelineData, FullHttpRequest msg) {
        EaglerListener listener = pipelineData.listenerInfo;
        ConfigDataListener conf = listener.getConfigData();
        HttpHeaders headers = msg.headers();
        if (conf.isForwardSecret() && !conf.getForwardSecretValue().equals(headers.get(conf.getForwardSecretHeader()))) {
            pipelineData.connectionLogger.error("Connected without a valid forwarding secret header, disconnecting...");
            return false;
        }
        if (listener.isForwardIP()) {
            String forwardedIP = HTTPMessageUtils.getFirstValue(headers, conf.getForwardIPHeader());
            if (forwardedIP != null) {
                if (pipelineData.server.getConfig().getSettings().isDebugLogRealIPHeaders()) {
                    pipelineData.connectionLogger.info("Real IP header value: \"" + forwardedIP + "\"");
                }
                pipelineData.realAddress = forwardedIP;
                CompoundRateLimiterMap rateLimiter = pipelineData.listenerInfo.getRateLimiter();
                if (rateLimiter != null) {
                    InetAddress addr;
                    try {
                        addr = InetAddresses.forString((String)pipelineData.realAddress);
                    }
                    catch (IllegalArgumentException ex) {
                        pipelineData.connectionLogger.error("Connected with an invalid \"" + conf.getForwardIPHeader() + "\" header, disconnecting...", ex);
                        return false;
                    }
                    pipelineData.realInetAddress = addr;
                    pipelineData.rateLimits = rateLimiter.getRateLimit(addr);
                }
                return true;
            }
            pipelineData.connectionLogger.error("Connected without a \"" + conf.getForwardIPHeader() + "\" header, disconnecting...");
            return false;
        }
        CompoundRateLimiterMap rateLimiter = pipelineData.listenerInfo.getRateLimiter();
        if (rateLimiter != null) {
            SocketAddress addr = ctx.channel().remoteAddress();
            if (addr instanceof InetSocketAddress) {
                pipelineData.rateLimits = rateLimiter.getRateLimit(((InetSocketAddress)addr).getAddress());
            } else {
                pipelineData.connectionLogger.warn("Unable to ratelimit unknown address type: " + addr.getClass().getName() + " - \"" + addr + "\"");
            }
        }
        return true;
    }
}

