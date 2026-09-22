/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.cache.Cache
 *  com.google.common.cache.CacheBuilder
 *  io.netty.bootstrap.Bootstrap
 *  io.netty.buffer.ByteBuf
 *  io.netty.channel.Channel
 *  io.netty.channel.ChannelFuture
 *  io.netty.channel.ChannelFutureListener
 *  io.netty.channel.ChannelHandler
 *  io.netty.channel.ChannelHandlerContext
 *  io.netty.channel.ChannelInitializer
 *  io.netty.channel.ChannelOption
 *  io.netty.channel.SimpleChannelInboundHandler
 *  io.netty.handler.codec.http.DefaultHttpRequest
 *  io.netty.handler.codec.http.HttpClientCodec
 *  io.netty.handler.codec.http.HttpContent
 *  io.netty.handler.codec.http.HttpMethod
 *  io.netty.handler.codec.http.HttpObject
 *  io.netty.handler.codec.http.HttpResponse
 *  io.netty.handler.codec.http.HttpVersion
 *  io.netty.handler.codec.http.LastHttpContent
 *  io.netty.handler.ssl.SslContext
 *  io.netty.handler.ssl.SslHandler
 *  io.netty.handler.timeout.ReadTimeoutHandler
 *  io.netty.util.concurrent.GenericFutureListener
 */
package net.lax1dude.eaglercraft.backend.skin_cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.GenericFutureListener;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.net.ssl.SSLEngine;
import net.lax1dude.eaglercraft.backend.skin_cache.IHTTPClient;

public class HTTPClient
implements IHTTPClient {
    public static final int MAX_REDIRECTS = 8;
    private final Cache<String, InetAddress> addressCache = CacheBuilder.newBuilder().expireAfterWrite(15L, TimeUnit.MINUTES).build();
    private final Supplier<Bootstrap> bootstrapper;
    private final String userAgent;

    private static SslContext buildClientSslContext() throws Exception {
        Class<?> builderClass = Class.forName("io.netty.handler.ssl.SslContextBuilder");
        Object builder = builderClass.getMethod("forClient", new Class[0]).invoke(null, new Object[0]);
        return (SslContext)builderClass.getMethod("build", new Class[0]).invoke(builder, new Object[0]);
    }

    public HTTPClient(Supplier<Bootstrap> bootstrapper, String userAgent) {
        this.bootstrapper = bootstrapper;
        this.userAgent = userAgent;
    }

    @Override
    public void asyncRequest(String method, URI uri, Consumer<IHTTPClient.Response> responseCallback) {
        this.asyncRequest(uri, responseCallback, new RedirectTracker(method));
    }

    private void asyncRequest(URI uri, Consumer<IHTTPClient.Response> responseCallback, RedirectTracker redirectTracker) {
        String host;
        InetAddress inetHost;
        int port = uri.getPort();
        boolean ssl = false;
        String scheme = uri.getScheme();
        if (scheme == null) {
            responseCallback.accept(new IHTTPClient.Response(new UnsupportedOperationException("URI is missing a scheme: " + uri)));
            return;
        }
        switch (scheme) {
            case "http": {
                if (port != -1) break;
                port = 80;
                break;
            }
            case "https": {
                if (port == -1) {
                    port = 443;
                }
                ssl = true;
                break;
            }
            default: {
                responseCallback.accept(new IHTTPClient.Response(new UnsupportedOperationException("Unsupported scheme: " + scheme)));
                return;
            }
        }
        if ((inetHost = (InetAddress)this.addressCache.getIfPresent(host = uri.getHost())) == null) {
            try {
                inetHost = InetAddress.getByName(host);
            }
            catch (UnknownHostException ex) {
                responseCallback.accept(new IHTTPClient.Response(ex));
                return;
            }
            this.addressCache.put(host, inetHost);
        }
        InetSocketAddress addr = new InetSocketAddress(inetHost, port);
        ((Bootstrap)((Bootstrap)((Bootstrap)this.bootstrapper.get().handler((ChannelHandler)new NettyHttpChannelInitializer(responseCallback, redirectTracker, ssl, host, port, uri))).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)).option(ChannelOption.TCP_NODELAY, true)).remoteAddress((SocketAddress)addr).connect().addListener((GenericFutureListener)new NettyHttpChannelFutureListener(redirectTracker.method, uri, responseCallback));
    }

    private static class RedirectTracker {
        private int redirects = 0;
        private String method;

        private RedirectTracker(String method) {
            this.method = method;
        }
    }

    private class NettyHttpChannelInitializer
    extends ChannelInitializer<Channel> {
        protected final Consumer<IHTTPClient.Response> responseCallback;
        protected final RedirectTracker redirectTracker;
        protected final boolean ssl;
        protected final String host;
        protected final int port;
        protected final URI requestURI;

        protected NettyHttpChannelInitializer(Consumer<IHTTPClient.Response> responseCallback, RedirectTracker redirectTracker, boolean ssl, String host, int port, URI requestURI) {
            this.responseCallback = responseCallback;
            this.redirectTracker = redirectTracker;
            this.ssl = ssl;
            this.host = host;
            this.port = port;
            this.requestURI = requestURI;
        }

        protected void initChannel(Channel ch) throws Exception {
            ch.pipeline().addLast("timeout", (ChannelHandler)new ReadTimeoutHandler(5L, TimeUnit.SECONDS));
            if (this.ssl) {
                SSLEngine engine = HTTPClient.buildClientSslContext().newEngine(ch.alloc(), this.host, this.port);
                ch.pipeline().addLast("ssl", (ChannelHandler)new SslHandler(engine));
            }
            ch.pipeline().addLast("http", (ChannelHandler)new HttpClientCodec());
            ch.pipeline().addLast("handler", (ChannelHandler)new NettyHttpResponseHandler(this.responseCallback, this.redirectTracker, this.requestURI));
        }
    }

    private class NettyHttpChannelFutureListener
    implements ChannelFutureListener {
        protected final String method;
        protected final URI requestURI;
        protected final Consumer<IHTTPClient.Response> responseCallback;

        protected NettyHttpChannelFutureListener(String method, URI requestURI, Consumer<IHTTPClient.Response> responseCallback) {
            this.method = method;
            this.requestURI = requestURI;
            this.responseCallback = responseCallback;
        }

        public void operationComplete(ChannelFuture future) throws Exception {
            if (future.isSuccess()) {
                String path = this.requestURI.getRawPath() + (this.requestURI.getRawQuery() == null ? "" : "?" + this.requestURI.getRawQuery());
                DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.valueOf((String)this.method), path);
                request.headers().set("Host", (Object)this.requestURI.getHost());
                request.headers().set("User-Agent", (Object)HTTPClient.this.userAgent);
                future.channel().writeAndFlush((Object)request);
            } else {
                HTTPClient.this.addressCache.invalidate(this.requestURI.getHost());
                this.responseCallback.accept(new IHTTPClient.Response(new IOException("Connection failed")));
            }
        }
    }

    private class NettyHttpResponseHandler
    extends SimpleChannelInboundHandler<HttpObject> {
        protected final Consumer<IHTTPClient.Response> responseCallback;
        protected final RedirectTracker redirectTracker;
        protected final URI requestURI;
        protected int responseCode = -1;
        protected ByteBuf buffer = null;

        protected NettyHttpResponseHandler(Consumer<IHTTPClient.Response> responseCallback, RedirectTracker redirectTracker, URI requestURI) {
            this.responseCallback = responseCallback;
            this.redirectTracker = redirectTracker;
            this.requestURI = requestURI;
        }

        protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
            if (msg instanceof HttpResponse) {
                HttpResponse response = (HttpResponse)msg;
                this.responseCode = response.getStatus().code();
                if (this.responseCode == 301 || this.responseCode == 302 || this.responseCode == 303 || this.responseCode == 307 || this.responseCode == 308) {
                    ctx.channel().pipeline().remove((ChannelHandler)this);
                    ctx.channel().close();
                    if (this.responseCode == 303) {
                        this.redirectTracker.method = "GET";
                    }
                    this.redirect(response);
                    return;
                }
                if (this.responseCode == 204) {
                    this.done(ctx);
                    return;
                }
            }
            if (msg instanceof HttpContent) {
                HttpContent content = (HttpContent)msg;
                if (this.buffer == null) {
                    this.buffer = ctx.alloc().buffer();
                }
                this.buffer.writeBytes(content.content());
                if (msg instanceof LastHttpContent) {
                    this.done(ctx);
                }
            }
        }

        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            if (this.buffer != null) {
                try {
                    this.buffer.release();
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                this.buffer = null;
            }
            this.responseCallback.accept(new IHTTPClient.Response(cause));
            ctx.close();
        }

        private void redirect(HttpResponse response) {
            if (this.buffer != null) {
                try {
                    this.buffer.release();
                }
                catch (Throwable throwable) {
                    // empty catch block
                }
                this.buffer = null;
            }
            if (++this.redirectTracker.redirects >= 8) {
                this.responseCallback.accept(new IHTTPClient.Response(new IllegalStateException("Too many redirects!")));
            } else {
                String target = response.headers().get("Location");
                if (target != null) {
                    URI uri;
                    try {
                        uri = this.requestURI.resolve(target.toString());
                    }
                    catch (IllegalArgumentException ex) {
                        this.responseCallback.accept(new IHTTPClient.Response(new IllegalStateException("Invalid redirect address in 3xx response!", ex)));
                        return;
                    }
                    HTTPClient.this.asyncRequest(uri, this.responseCallback, this.redirectTracker);
                } else {
                    this.responseCallback.accept(new IHTTPClient.Response(new IllegalStateException("Missing redirect address in 3xx response!")));
                }
            }
        }

        private void done(ChannelHandlerContext ctx) {
            try {
                this.responseCallback.accept(new IHTTPClient.Response(this.responseCode, this.redirectTracker.redirects > 0, this.buffer));
            }
            finally {
                ctx.channel().pipeline().remove((ChannelHandler)this);
                ctx.channel().close();
            }
        }
    }
}

