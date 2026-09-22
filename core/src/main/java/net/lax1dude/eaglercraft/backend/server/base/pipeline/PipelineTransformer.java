/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.channel.Channel
 *  io.netty.channel.ChannelHandler
 *  io.netty.channel.ChannelPipeline
 *  io.netty.handler.codec.http.HttpObjectAggregator
 *  io.netty.handler.codec.http.HttpServerCodec
 *  io.netty.handler.ssl.SslHandler
 *  io.netty.handler.timeout.IdleStateHandler
 */
package net.lax1dude.eaglercraft.backend.server.base.pipeline;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import net.lax1dude.eaglercraft.backend.server.adapter.IPipelineComponent;
import net.lax1dude.eaglercraft.backend.server.api.EnumPlatformType;
import net.lax1dude.eaglercraft.backend.server.base.EaglerListener;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServer;
import net.lax1dude.eaglercraft.backend.server.base.ISSLContextProvider;
import net.lax1dude.eaglercraft.backend.server.base.NettyPipelineData;
import net.lax1dude.eaglercraft.backend.server.base.RewindService;
import net.lax1dude.eaglercraft.backend.server.base.config.ConfigDataSettings;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.HAProxyDetectionHandler;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.HTTPInitialInboundHandler;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.MultiStackInitialInboundHandler;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.NOPDummyHandler;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.OutboundPacketThrowHandler;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.WebSocketExtensionWrappers;
import net.lax1dude.eaglercraft.backend.server.util.Util;

public class PipelineTransformer {
    public static final String HANDLER_MULTI_STACK_INITIAL = "eagler-multistack-initial";
    public static final String HANDLER_HAPROXY_DETECTION = "eagler-haproxy-detection";
    public static final String HANDLER_HTTP_SSL = "eagler-ssl-handler";
    public static final String HANDLER_HTTP_SERVER_CODEC = "eagler-http-codec";
    public static final String HANDLER_HTTP_AGGREGATOR = "eagler-http-aggregator";
    public static final String HANDLER_WS_AGGREGATOR = "eagler-ws-aggregator";
    public static final String HANDLER_WS_COMPRESSION = "eagler-ws-compression";
    public static final String HANDLER_HTTP_INITIAL = "eagler-http-initial";
    public static final String HANDLER_WS_INITIAL = "eagler-ws-initial";
    public static final String HANDLER_WS_PING = "eagler-ws-ping-handler";
    public static final String HANDLER_HANDSHAKE = "eagler-handshake";
    public static final String HANDLER_OUTBOUND_THROW = "eagler-outbound-throw";
    public static final String HANDLER_QUERY = "eagler-query";
    public static final String HANDLER_HTTP = "eagler-http";
    public static final String HANDLER_FRAME_CODEC = "eagler-frame-codec";
    public static final String HANDLER_REWIND_CODEC = "eagler-rewind-codec";
    public static final String HANDLER_REWIND_DECODER = "eagler-rewind-decoder";
    public static final String HANDLER_REWIND_ENCODER = "eagler-rewind-encoder";
    public static final String HANDLER_REWIND_INJECTOR = "eagler-rewind-injector";
    public static final String HANDLER_INJECTED = "eagler-v5-msg-handler";
    protected static final Set<IPipelineComponent.EnumPipelineComponent> VANILLA_FRAME_DECODERS = EnumSet.of(IPipelineComponent.EnumPipelineComponent.FRAME_DECODER, new IPipelineComponent.EnumPipelineComponent[]{IPipelineComponent.EnumPipelineComponent.FRAME_ENCODER, IPipelineComponent.EnumPipelineComponent.BUKKIT_LEGACY_HANDLER, IPipelineComponent.EnumPipelineComponent.BUNGEE_LEGACY_HANDLER, IPipelineComponent.EnumPipelineComponent.BUNGEE_LEGACY_KICK_ENCODER, IPipelineComponent.EnumPipelineComponent.VELOCITY_LEGACY_PING_ENCODER});
    private static final boolean SUPPORTS_COMPRESSION_FRAME = Util.classExists("io.netty.handler.codec.http.websocketx.extensions.compression.DeflateFrameServerExtensionHandshaker");
    private static final boolean SUPPORTS_COMPRESSION_MESSAGE = Util.classExists("io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker");
    private static final boolean NEW_AGGREGATOR_CTOR;
    public final EaglerXServer<?> server;
    public final RewindService<?> rewind;
    private final Object[] enabledExtensions;
    private Runnable nag;

    private static boolean zlibSupportsWindowSizeAndMemLevel() {
        try {
            return (Boolean)Class.forName("io.netty.handler.codec.compression.ZlibCodecFactory").getMethod("isSupportingWindowSizeAndMemLevel", new Class[0]).invoke(null, new Object[0]);
        }
        catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    private static HttpObjectAggregator newAggregatorWithClose(int maxContentLength) {
        try {
            return (HttpObjectAggregator)HttpObjectAggregator.class.getConstructor(Integer.TYPE, Boolean.TYPE).newInstance(maxContentLength, true);
        }
        catch (ReflectiveOperationException ex) {
            throw new RuntimeException("Failed to create HttpObjectAggregator", ex);
        }
    }

    public PipelineTransformer(EaglerXServer<?> server, RewindService<?> rewind) {
        this.server = server;
        this.rewind = rewind;
        if (!SUPPORTS_COMPRESSION_FRAME && !SUPPORTS_COMPRESSION_MESSAGE) {
            if (server.getPlatformType() == EnumPlatformType.BUKKIT) {
                this.nag = () -> {
                    server.logger().error("WebSocket compression is not supported by the Netty bundled with this version of Bukkit");
                    server.logger().error("Excessive bandwidth will be used, and you will have a bad time trying to play on wireless devices");
                    server.logger().error("Please try a newer version of Bukkit (preferably Paper) such as 1.12.2 to fix this issue");
                    server.logger().error("Alternatively, you can add a newer version of Netty to the classpath as a JAR file using java -cp");
                    server.logger().error("You have been warned, don't be a fool");
                };
                this.nag.run();
            } else {
                server.logger().error("WebSocket compression is not supported on this platform");
            }
            this.enabledExtensions = null;
        } else {
            int compressionLevel = Math.min(server.getConfig().getSettings().getHTTPWebSocketCompressionLevel(), 9);
            if (compressionLevel > 0) {
                int frameLimit = server.getConfig().getSettings().getHTTPWebSocketMaxFrameLength();
                ArrayList<Object> extensions = new ArrayList<Object>();
                if (SUPPORTS_COMPRESSION_FRAME && WebSocketExtensionWrappers.PMCE_AVAILABLE) {
                    extensions.add(WebSocketExtensionWrappers.createDeflateFrameServerExtensionHandshaker(compressionLevel, frameLimit));
                }
                if (SUPPORTS_COMPRESSION_MESSAGE && WebSocketExtensionWrappers.PMCE_AVAILABLE) {
                    extensions.add(WebSocketExtensionWrappers.createPerMessageDeflateServerExtensionHandshaker(compressionLevel, PipelineTransformer.zlibSupportsWindowSizeAndMemLevel(), 15, false, false, frameLimit));
                }
                this.enabledExtensions = extensions.toArray(new Object[extensions.size()]);
            } else {
                this.enabledExtensions = null;
            }
        }
    }

    public void nagAgain() {
        if (this.nag != null) {
            this.nag.run();
            this.server.logger().error("(Warning has been repeated in case you don't know how to scroll up)");
            this.nag = null;
        }
    }

    public void injectSingleStack(List<IPipelineComponent> components, Channel channel, NettyPipelineData pipelineData) {
        ChannelPipeline pipeline = channel.pipeline();
        String before = null;
        String first = null;
        boolean e = false;
        IPipelineComponent haproxy = null;
        String bungeeHack = null;
        for (IPipelineComponent comp : components) {
            ChannelHandler tmp;
            if (VANILLA_FRAME_DECODERS.contains(comp.getIdentifiedType())) {
                if (pipelineData.isCompressionDisable()) {
                    try {
                        pipeline.replace(comp.getHandle(), comp.getName(), (ChannelHandler)NOPDummyHandler.INSTANCE);
                    }
                    catch (Throwable t) {
                        try {
                            pipeline.addFirst(comp.getName(), (ChannelHandler)NOPDummyHandler.INSTANCE);
                        }
                        catch (Throwable throwable) {}
                    }
                } else {
                    pipeline.remove(comp.getHandle());
                }
                if (comp.getIdentifiedType() != IPipelineComponent.EnumPipelineComponent.BUNGEE_LEGACY_KICK_ENCODER) continue;
                bungeeHack = comp.getName();
                continue;
            }
            if (!e) {
                if (comp.getIdentifiedType() != IPipelineComponent.EnumPipelineComponent.HAPROXY_HANDLER) {
                    first = before;
                    e = true;
                } else {
                    haproxy = comp;
                }
                before = comp.getName();
            }
            if (comp.getIdentifiedType() != IPipelineComponent.EnumPipelineComponent.READ_TIMEOUT_HANDLER || !((tmp = comp.getHandle()) instanceof IdleStateHandler)) continue;
            pipelineData.idleStateHandler = (IdleStateHandler)tmp;
        }
        if (bungeeHack != null) {
            pipeline.addLast(bungeeHack, (ChannelHandler)NOPDummyHandler.INSTANCE);
        }
        if (!e) {
            return;
        }
        EaglerListener eagListener = pipelineData.listenerInfo;
        if (eagListener.isTLSEnabled()) {
            ISSLContextProvider ssl = eagListener.getSSLContext();
            if (ssl == null) {
                throw new IllegalStateException();
            }
            if (!eagListener.isTLSRequired()) {
                MultiStackInitialInboundHandler ms = new MultiStackInitialInboundHandler(this, pipelineData, null, null, null);
                if (first == null) {
                    channel.pipeline().addFirst(HANDLER_MULTI_STACK_INITIAL, (ChannelHandler)ms);
                } else {
                    channel.pipeline().addAfter(first, HANDLER_MULTI_STACK_INITIAL, (ChannelHandler)ms);
                }
            } else {
                this.initializeHTTPHandler(pipelineData, ssl, pipeline, first);
            }
        } else {
            this.initializeHTTPHandler(pipelineData, null, pipeline, first);
        }
        if (haproxy != null) {
            if (eagListener.getConfigData().isForceDisableHAProxy()) {
                pipeline.remove(haproxy.getHandle());
            } else if (eagListener.getConfigData().isDualStackHAProxyDetection()) {
                pipeline.addFirst(HANDLER_HAPROXY_DETECTION, (ChannelHandler)new HAProxyDetectionHandler(haproxy.getHandle()));
            }
        }
        pipeline.addLast(HANDLER_OUTBOUND_THROW, (ChannelHandler)OutboundPacketThrowHandler.INSTANCE);
    }

    public void injectDualStack(List<IPipelineComponent> components, Channel channel, NettyPipelineData pipelineData) {
        ArrayList<ChannelHandler> toRemove = new ArrayList<ChannelHandler>(4);
        ArrayList<IPipelineComponent> toNOP = new ArrayList<IPipelineComponent>(4);
        String first = null;
        String bungeeHack = null;
        IPipelineComponent haproxy = null;
        for (IPipelineComponent comp : components) {
            ChannelHandler tmp;
            if (VANILLA_FRAME_DECODERS.contains(comp.getIdentifiedType())) {
                if (pipelineData.isCompressionDisable()) {
                    toNOP.add(comp);
                } else {
                    toRemove.add(comp.getHandle());
                }
                if (comp.getIdentifiedType() != IPipelineComponent.EnumPipelineComponent.BUNGEE_LEGACY_KICK_ENCODER) continue;
                bungeeHack = comp.getName();
                continue;
            }
            if (first == null) {
                if (comp.getIdentifiedType() != IPipelineComponent.EnumPipelineComponent.HAPROXY_HANDLER) {
                    first = comp.getName();
                } else {
                    haproxy = comp;
                }
            }
            if (comp.getIdentifiedType() != IPipelineComponent.EnumPipelineComponent.READ_TIMEOUT_HANDLER || !((tmp = comp.getHandle()) instanceof IdleStateHandler)) continue;
            pipelineData.idleStateHandler = (IdleStateHandler)tmp;
        }
        if (first == null) {
            return;
        }
        channel.pipeline().addBefore(first, HANDLER_MULTI_STACK_INITIAL, (ChannelHandler)new MultiStackInitialInboundHandler(this, pipelineData, toRemove, toNOP, bungeeHack));
        EaglerListener eagListener = pipelineData.listenerInfo;
        if (haproxy != null) {
            if (eagListener.getConfigData().isForceDisableHAProxy()) {
                channel.pipeline().remove(haproxy.getHandle());
            } else if (eagListener.getConfigData().isDualStackHAProxyDetection()) {
                channel.pipeline().addFirst(HANDLER_HAPROXY_DETECTION, (ChannelHandler)new HAProxyDetectionHandler(haproxy.getHandle()));
            }
        }
        channel.pipeline().addLast(HANDLER_OUTBOUND_THROW, (ChannelHandler)OutboundPacketThrowHandler.INSTANCE);
    }

    protected void initializeHTTPHandler(NettyPipelineData pipelineData, ISSLContextProvider context, ChannelPipeline pipeline, String after) {
        if (context != null) {
            SslHandler sslHandler = context.newHandler(pipeline.channel().alloc());
            if (sslHandler == null) {
                pipeline.channel().close();
                return;
            }
            if (after == null) {
                pipeline.addFirst(HANDLER_HTTP_SSL, (ChannelHandler)sslHandler);
            } else {
                pipeline.addAfter(after, HANDLER_HTTP_SSL, (ChannelHandler)sslHandler);
            }
            after = HANDLER_HTTP_SSL;
            pipelineData.wss = true;
        }
        ConfigDataSettings settings = this.server.getConfig().getSettings();
        HttpServerCodec serverCodec = new HttpServerCodec(settings.getHTTPMaxInitialLineLength(), settings.getHTTPMaxHeaderSize(), settings.getHTTPMaxChunkSize());
        if (after == null) {
            pipeline.addFirst(HANDLER_HTTP_SERVER_CODEC, (ChannelHandler)serverCodec);
        } else {
            pipeline.addAfter(after, HANDLER_HTTP_SERVER_CODEC, (ChannelHandler)serverCodec);
        }
        after = HANDLER_HTTP_SERVER_CODEC;
        HttpObjectAggregator ag = NEW_AGGREGATOR_CTOR ? PipelineTransformer.newAggregatorWithClose(settings.getHTTPMaxContentLength()) : new HttpObjectAggregator(settings.getHTTPMaxContentLength());
        pipeline.addAfter(after, HANDLER_HTTP_AGGREGATOR, (ChannelHandler)ag);
        after = HANDLER_HTTP_AGGREGATOR;
        if (this.enabledExtensions != null && WebSocketExtensionWrappers.PMCE_AVAILABLE) {
            WebSocketExtensionWrappers.installWsCompression(pipeline, after, HANDLER_WS_COMPRESSION, this.enabledExtensions);
            after = HANDLER_WS_COMPRESSION;
        }
        pipeline.addAfter(after, HANDLER_HTTP_INITIAL, (ChannelHandler)HTTPInitialInboundHandler.INSTANCE);
    }

    protected void removeVanillaHandlers(ChannelPipeline pipeline) {
        Iterator keyItr = pipeline.names().iterator();
        while (keyItr.hasNext()) {
            String nm = (String)keyItr.next();
            if (!HANDLER_HTTP_INITIAL.equals(nm) && !HANDLER_WS_INITIAL.equals(nm)) continue;
            while (keyItr.hasNext()) {
                nm = (String)keyItr.next();
                ChannelHandler handler = pipeline.get(nm);
                if (handler instanceof IdleStateHandler) continue;
                try {
                    pipeline.remove(nm);
                }
                catch (NoSuchElementException noSuchElementException) {}
            }
        }
    }

    static {
        boolean b = false;
        try {
            HttpObjectAggregator.class.getConstructor(Integer.TYPE, Boolean.TYPE);
            b = true;
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            // empty catch block
        }
        NEW_AGGREGATOR_CTOR = b;
    }
}

