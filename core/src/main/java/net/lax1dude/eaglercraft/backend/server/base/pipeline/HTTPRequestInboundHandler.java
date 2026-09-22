/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.ImmutableMap
 *  io.netty.buffer.ByteBuf
 *  io.netty.buffer.Unpooled
 *  io.netty.channel.ChannelHandlerContext
 *  io.netty.channel.ChannelInboundHandlerAdapter
 *  io.netty.handler.codec.http.DefaultFullHttpResponse
 *  io.netty.handler.codec.http.FullHttpRequest
 *  io.netty.handler.codec.http.FullHttpResponse
 *  io.netty.handler.codec.http.HttpHeaders
 *  io.netty.handler.codec.http.HttpMessage
 *  io.netty.handler.codec.http.HttpMethod
 *  io.netty.handler.codec.http.HttpRequest
 *  io.netty.handler.codec.http.HttpResponseStatus
 *  io.netty.handler.codec.http.HttpVersion
 *  io.netty.handler.timeout.ReadTimeoutException
 *  io.netty.util.ReferenceCountUtil
 */
package net.lax1dude.eaglercraft.backend.server.base.pipeline;

import com.google.common.collect.ImmutableMap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.ReferenceCountUtil;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import net.lax1dude.eaglercraft.backend.server.api.EnumRequestMethod;
import net.lax1dude.eaglercraft.backend.server.api.webserver.IRequestHandler;
import net.lax1dude.eaglercraft.backend.server.base.CompoundRateLimiterMap;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServer;
import net.lax1dude.eaglercraft.backend.server.base.NettyPipelineData;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.BufferUtils;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.HTTPInitialInboundHandler;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.HTTPMessageUtils;
import net.lax1dude.eaglercraft.backend.server.base.webserver.RequestContext;
import net.lax1dude.eaglercraft.backend.server.base.webserver.ResponseOrdering;
import net.lax1dude.eaglercraft.backend.server.base.webserver.RouteMap;
import net.lax1dude.eaglercraft.backend.server.base.webserver.RouteProcessor;
import net.lax1dude.eaglercraft.backend.server.util.EnumRateLimitState;

public class HTTPRequestInboundHandler
extends ChannelInboundHandlerAdapter {
    private static final HttpResponseStatus PERMANENT_REDIRECT = new HttpResponseStatus(308, "Permanent Redirect");
    private static final ImmutableMap<HttpMethod, EnumRequestMethod> methodLookup = ImmutableMap.<HttpMethod, EnumRequestMethod>builder().put(HttpMethod.GET, EnumRequestMethod.GET).put(HttpMethod.HEAD, EnumRequestMethod.HEAD).put(HttpMethod.PUT, EnumRequestMethod.PUT).put(HttpMethod.DELETE, EnumRequestMethod.DELETE).put(HttpMethod.POST, EnumRequestMethod.POST).put(HttpMethod.PATCH, EnumRequestMethod.PATCH).build();
    private final EaglerXServer<?> server;
    private final NettyPipelineData pipelineData;
    private ResponseOrdering ordering;
    private RouteProcessor processor;
    private RequestContext context;
    private boolean isFirst;

    private static int utf8Bytes(CharSequence chars) {
        int n = 0;
        int len = chars.length();
        for (int i = 0; i < len; ++i) {
            char c = chars.charAt(i);
            if (c < '\u0080') {
                ++n;
                continue;
            }
            if (c < '\u0800') {
                n += 2;
                continue;
            }
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < len && Character.isLowSurrogate(chars.charAt(i + 1))) {
                    ++i;
                    n += 4;
                    continue;
                }
                n += 3;
                continue;
            }
            if (Character.isLowSurrogate(c)) {
                n += 3;
                continue;
            }
            n += 3;
        }
        return n;
    }

    public HTTPRequestInboundHandler(EaglerXServer<?> server, NettyPipelineData pipelineData) {
        this.server = server;
        this.pipelineData = pipelineData;
        this.isFirst = true;
    }

    private RouteProcessor processor() {
        if (this.processor == null) {
            this.processor = new RouteProcessor();
            return this.processor;
        }
        return this.processor;
    }

    private RequestContext context() {
        if (this.context == null) {
            this.context = new RequestContext(this.server.getWebServer());
            return this.context;
        }
        return this.context;
    }

    public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
        if (this.ordering != null) {
            this.ordering.release();
        }
        this.ordering = new ResponseOrdering(){

            @Override
            protected void send(FullHttpResponse data) {
                if (ctx.channel().isActive()) {
                    ctx.writeAndFlush((Object)data);
                } else {
                    data.release();
                }
            }
        };
    }

    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        if (this.ordering != null) {
            this.ordering.release();
            this.ordering = null;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void channelRead(ChannelHandlerContext ctx, Object msgRaw) throws Exception {
        try {
            if (!ctx.channel().isActive()) {
                return;
            }
            if (msgRaw instanceof FullHttpRequest) {
                String query;
                String path;
                FullHttpRequest msg = (FullHttpRequest)msgRaw;
                if (HTTPMessageUtils.getProtocolVersion((HttpMessage)msg) != HttpVersion.HTTP_1_1) {
                    ctx.close();
                    return;
                }
                String uri = HTTPMessageUtils.getURI((HttpRequest)msg);
                int i = uri.indexOf(63);
                if (i != -1) {
                    path = uri.substring(0, i);
                    query = uri.substring(i);
                } else {
                    path = uri;
                    query = "";
                }
                HttpMethod method = HTTPMessageUtils.getMethod((HttpRequest)msg);
                EnumRequestMethod meth = methodLookup.get(method);
                ResponseOrdering.Slot responseSlot = this.ordering.push();
                CompoundRateLimiterMap rateLimiter = this.pipelineData.listenerInfo.getRateLimiter();
                if (rateLimiter != null) {
                    EnumRateLimitState rateLimit;
                    if (!this.isFirst && !HTTPInitialInboundHandler.recheckRatelimitAddress(ctx, this.pipelineData, msg)) {
                        ctx.close();
                        return;
                    }
                    if (this.pipelineData.rateLimits != null && !(rateLimit = this.pipelineData.rateLimits.rateLimitHTTP()).isOk()) {
                        if (rateLimit == EnumRateLimitState.BLOCKED || rateLimit == EnumRateLimitState.BLOCKED_LOCKED) {
                            if (meth == null) {
                                meth = EnumRequestMethod.GET;
                            }
                            this.handleRequest(ctx, msg, meth, null, uri, path, query, this.server.getWebServer().get429Handler(), responseSlot, false);
                        } else {
                            ctx.close();
                        }
                        return;
                    }
                }
                if (meth == null) {
                    if (method == HttpMethod.OPTIONS) {
                        this.handleOptions(ctx, uri, path, query, msg, responseSlot);
                    } else {
                        this.handleUnexpectedMeth(ctx, method, responseSlot);
                    }
                    return;
                }
                RouteMap.Result<IRequestHandler> handlerResult = this.server.getWebServer().resolveInternal(this.pipelineData.listenerInfo, meth.id(), path, this.processor());
                boolean dir = path.endsWith("/");
                if (dir != handlerResult.directory) {
                    FullHttpResponse res = this.createResponse(PERMANENT_REDIRECT, null, 0);
                    res.headers().add("location", (Object)this.redirDir(path, query, dir));
                    responseSlot.complete(res);
                    return;
                }
                this.handleRequest(ctx, msg, meth, null, uri, path, query, (IRequestHandler)handlerResult.result, responseSlot, false);
            }
        }
        finally {
            ReferenceCountUtil.release((Object)msgRaw);
            this.isFirst = false;
        }
    }

    private String redirDir(String path, String query, boolean dir) {
        if (dir) {
            int i;
            for (i = path.length(); i > 0 && path.charAt(i - 1) == '/'; --i) {
            }
            if (i <= 0) {
                return query;
            }
            StringBuilder stringBuilder = new StringBuilder(i + query.length());
            stringBuilder.append(path, 0, i);
            stringBuilder.append(query);
            return stringBuilder.toString();
        }
        return path + "/" + query;
    }

    private void handleOptions(ChannelHandlerContext ctx, String uri, String path, String query, FullHttpRequest msg, ResponseOrdering.Slot responseSlot) {
        HttpHeaders headers = msg.headers();
        String reqMethod = headers.get("access-control-request-method");
        if (reqMethod != null) {
            EnumRequestMethod corsMethod;
            try {
                corsMethod = EnumRequestMethod.valueOf(reqMethod);
            }
            catch (IllegalArgumentException ex) {
                corsMethod = null;
            }
            if (corsMethod != null && corsMethod != EnumRequestMethod.OPTIONS && headers.contains("origin")) {
                RouteMap.Result<IRequestHandler> handlerResult = this.server.getWebServer().resolveInternal(this.pipelineData.listenerInfo, corsMethod.id(), path, this.processor());
                boolean dir = path.endsWith("/");
                if (dir != handlerResult.directory) {
                    FullHttpResponse res = this.createResponse(PERMANENT_REDIRECT, null, 0);
                    res.headers().add("location", (Object)this.redirDir(path, query, dir));
                    responseSlot.complete(res);
                    return;
                }
                this.handleRequest(ctx, msg, EnumRequestMethod.OPTIONS, corsMethod, uri, path, query, (IRequestHandler)handlerResult.result, responseSlot, false);
            } else {
                responseSlot.complete(this.createResponse(HttpResponseStatus.BAD_REQUEST, null, 0));
            }
            return;
        }
        if ("*".equals(path) && query.isEmpty()) {
            FullHttpResponse res = this.createResponse(HttpResponseStatus.OK, null, 0);
            res.headers().add("allow", RouteMap.allMethods);
            responseSlot.complete(res);
            return;
        }
        RouteMap.Result<List<EnumRequestMethod>> optionsResult = this.server.getWebServer().optionsInternal(this.pipelineData.listenerInfo, path, this.processor());
        if (optionsResult.result != null) {
            boolean dir = path.endsWith("/");
            if (dir != optionsResult.directory) {
                FullHttpResponse res = this.createResponse(PERMANENT_REDIRECT, null, 0);
                res.headers().add("location", (Object)this.redirDir(path, query, dir));
                responseSlot.complete(res);
                return;
            }
            if (!((List)optionsResult.result).isEmpty()) {
                FullHttpResponse res = this.createResponse(HttpResponseStatus.OK, null, 0);
                res.headers().add("allow", (Iterable)optionsResult.result);
                responseSlot.complete(res);
                return;
            }
        }
        responseSlot.complete(this.createResponse(HttpResponseStatus.FORBIDDEN, null, 0));
    }

    private void handleRequest(RequestContext oldContext, IRequestHandler requestHandler, ResponseOrdering.Slot responseSlot, boolean isFailing) {
        this.handleRequest(oldContext.ctx, oldContext.request, oldContext.meth, oldContext.pfMeth, oldContext.uri, oldContext.path, oldContext.query, requestHandler, responseSlot, isFailing);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void handleRequest(ChannelHandlerContext ctx, FullHttpRequest msg, EnumRequestMethod meth, EnumRequestMethod pfMeth, String uri, String path, String query, IRequestHandler requestHandler, ResponseOrdering.Slot responseSlot, boolean isFailing) {
        if (pfMeth != null && !requestHandler.enablePreflight()) {
            responseSlot.complete(this.createResponse(HttpResponseStatus.FORBIDDEN, null, 0));
            return;
        }
        RequestContext context = this.context();
        context.failing = isFailing;
        context.setContext(this.pipelineData.listenerInfo, meth, pfMeth, uri, path, query, ctx, msg, this.pipelineData.realAddress);
        try {
            context.requestHandlerInternal = requestHandler;
            context.suspendable = true;
            try {
                if (pfMeth != null) {
                    requestHandler.handlePreflight(context);
                } else {
                    requestHandler.handleRequest(context);
                }
            }
            finally {
                context.suspendable = false;
            }
        }
        catch (Throwable ex) {
            if (context.contextPromise != null) {
                this.context = null;
            }
            this.completeRequest(context, ex, responseSlot);
            return;
        }
        RequestContext.ContextPromise promise = context.contextPromise;
        if (promise != null) {
            this.context = null;
            context.responseSlotTmp = responseSlot;
            promise.onResumeInternal(this::completeRequest);
        } else {
            this.completeRequest(context, null, responseSlot);
        }
    }

    private void completeRequest(RequestContext context, Throwable err) {
        this.completeRequest(context, err, context.responseSlotTmp);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    private void completeRequest(RequestContext context, Throwable err, ResponseOrdering.Slot responseSlot) {
        boolean rc = context.contextPromise != null;
        try {
            if (err != null) {
                this.pipelineData.connectionLogger.error("Request handler " + context.requestHandlerInternal + " raised an exception while handling " + context.meth.name() + " \"" + context.uri + "\"", err);
                if (!context.failing) {
                    this.handleRequest(context, this.server.getWebServer().get500Handler(), responseSlot, true);
                    return;
                } else {
                    responseSlot.complete(this.createResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, null, 0));
                }
                return;
            }
            if (context.responseCode == -1) {
                this.pipelineData.connectionLogger.error("Request handler " + context.requestHandlerInternal + " set no response code for " + context.meth.name() + " \"" + context.uri + "\"");
                if (!context.failing) {
                    this.handleRequest(context, this.server.getWebServer().get500Handler(), responseSlot, true);
                    return;
                } else {
                    responseSlot.complete(this.createResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR, null, 0));
                }
                return;
            }
            HttpResponseStatus status = HttpResponseStatus.valueOf((int)context.responseCode);
            switch (context.response) {
                default: {
                    this.pipelineData.connectionLogger.error("Request handler " + context.requestHandlerInternal + " made no response for " + context.meth.name() + " \"" + context.uri + "\"");
                    this.handleRequest(context, this.server.getWebServer().get500Handler(), responseSlot, true);
                    return;
                }
                case 1: {
                    if (context.meth == EnumRequestMethod.HEAD) {
                        responseSlot.complete(this.populateHeadersFrom(this.createResponse(status, null, context.responsePrepared.buffer.readableBytes()), context));
                        return;
                    }
                    ByteBuf buf = Unpooled.wrappedBuffer((ByteBuf)context.responsePrepared.buffer.retain());
                    try {
                        responseSlot.complete(this.populateHeadersFrom(this.createResponse(status, buf, 0), context).retain());
                        return;
                    }
                    finally {
                        buf.release();
                    }
                }
                case 2: {
                    if (context.meth == EnumRequestMethod.HEAD) {
                        responseSlot.complete(this.populateHeadersFrom(this.createResponse(status, null, context.responseData.length), context));
                        return;
                    } else {
                        responseSlot.complete(this.populateHeadersFrom(this.createResponse(status, Unpooled.wrappedBuffer((byte[])context.responseData), 0), context));
                        return;
                    }
                }
                case 3: {
                    if (context.meth == EnumRequestMethod.HEAD) {
                        responseSlot.complete(this.populateHeadersFrom(this.createResponse(status, null, this.stringByteLength(context.responseChars, context.responseCharsCharset)), context));
                        return;
                    }
                    ByteBuf buf = context.ctx.alloc().buffer();
                    try {
                        BufferUtils.writeCharSequence(buf, context.responseChars, context.responseCharsCharset);
                        responseSlot.complete(this.populateHeadersFrom(this.createResponse(status, buf, 0), context).retain());
                        return;
                    }
                    finally {
                        buf.release();
                    }
                }
                case 4: {
                    responseSlot.complete(this.populateHeadersFrom(this.createResponse(status, null, 0), context));
                    return;
                }
                case 5: {
                    if (context.meth == EnumRequestMethod.HEAD) {
                        responseSlot.complete(this.populateHeadersFrom(this.createResponse(status, null, context.responseUnsafeByteBuf.readableBytes()), context));
                        return;
                    } else {
                        responseSlot.complete(this.populateHeadersFrom(this.createResponse(status, context.responseUnsafeByteBuf, 0), context).retain());
                        return;
                    }
                }
                case 6: {
                    responseSlot.complete(context.responseUnsafeFull.retain());
                    return;
                }
            }
        }
        finally {
            try {
                if (rc) {
                    context.request.release();
                }
            }
            finally {
                context.clearResult();
            }
        }
    }

    private int stringByteLength(CharSequence chars, Charset charset) {
        if (charset == StandardCharsets.UTF_8) {
            return HTTPRequestInboundHandler.utf8Bytes(chars);
        }
        if (charset == StandardCharsets.US_ASCII || charset == StandardCharsets.ISO_8859_1) {
            return chars.length();
        }
        if (charset == StandardCharsets.UTF_16 || charset == StandardCharsets.UTF_16LE || charset == StandardCharsets.UTF_16BE) {
            return chars.length() * 2;
        }
        return chars.toString().getBytes(charset).length;
    }

    private FullHttpResponse createResponse(HttpResponseStatus code, ByteBuf body, int len) {
        DefaultFullHttpResponse ret = body != null ? new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, code, body) : new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, code);
        HttpHeaders headers = ret.headers();
        headers.set("connection", (Object)"keep-alive");
        headers.set("server", (Object)this.server.getServerVersionString());
        headers.set("date", (Object)new Date());
        if (body != null) {
            headers.set("content-length", (Object)body.readableBytes());
        } else {
            headers.set("content-length", (Object)len);
        }
        return ret;
    }

    private FullHttpResponse populateHeadersFrom(FullHttpResponse response, RequestContext context) {
        int sz;
        HttpHeaders headers = response.headers();
        List<Object> obj = context.responseHeaders;
        if (obj != null && (sz = obj.size()) > 0 && (sz & 1) == 0) {
            for (int i = 0; i < sz; i += 2) {
                headers.add((String)obj.get(i), obj.get(i + 1));
            }
        }
        return response;
    }

    private void handleUnexpectedMeth(ChannelHandlerContext ctx, HttpMethod method, ResponseOrdering.Slot responseSlot) {
        responseSlot.complete(this.createResponse(HttpResponseStatus.METHOD_NOT_ALLOWED, null, 0));
    }

    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (!(cause instanceof ReadTimeoutException) && ctx.channel().isActive()) {
            this.pipelineData.connectionLogger.error("Uncaught exception in pipeline", cause);
        }
    }
}

