/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.channel.ChannelHandler
 *  io.netty.channel.ChannelHandlerContext
 *  io.netty.channel.ChannelPipeline
 *  io.netty.channel.embedded.EmbeddedChannel
 *  io.netty.handler.codec.CodecException
 *  io.netty.handler.codec.MessageToMessageDecoder
 *  io.netty.handler.codec.compression.JZlibDecoder
 *  io.netty.handler.codec.compression.JdkZlibDecoder
 *  io.netty.handler.codec.compression.ZlibDecoder
 *  io.netty.handler.codec.compression.ZlibWrapper
 *  io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
 *  io.netty.handler.codec.http.websocketx.TextWebSocketFrame
 *  io.netty.handler.codec.http.websocketx.WebSocketFrame
 *  io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionData
 *  io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionDecoder
 *  io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionEncoder
 *  io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtension
 *  io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandler
 *  io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandshaker
 *  io.netty.handler.codec.http.websocketx.extensions.compression.DeflateFrameServerExtensionHandshaker
 *  io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker
 *  io.netty.util.internal.PlatformDependent
 *  io.netty.util.internal.SystemPropertyUtil
 */
package net.lax1dude.eaglercraft.backend.server.base.pipeline;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CodecException;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.compression.JZlibDecoder;
import io.netty.handler.codec.compression.JdkZlibDecoder;
import io.netty.handler.codec.compression.ZlibDecoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionData;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionDecoder;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketExtensionEncoder;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtension;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandler;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.DeflateFrameServerExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SystemPropertyUtil;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.LegacyJdkZlibDecoder;

public class WebSocketExtensionWrappers {
    public static final boolean PMCE_AVAILABLE;
    private static final boolean hasMaxAllocationHandshaker;
    private static final MethodHandle METH_DECODE;
    private static final Field FIELD_DEFLATER;
    private static final boolean hasMaxAllocation;
    private static final boolean noJdkZlibDecoder;
    public static final int PERMESSAGEDEFLATE_MAX_WINDOW_SIZE = 15;

    public static void installWsCompression(ChannelPipeline pipeline, String anchor, String handlerName, Object[] enabledExtensions) {
        if (!PMCE_AVAILABLE || enabledExtensions == null) {
            return;
        }
        WebSocketServerExtensionHandshaker[] handshakers = new WebSocketServerExtensionHandshaker[enabledExtensions.length];
        for (int i = 0; i < enabledExtensions.length; ++i) {
            handshakers[i] = (WebSocketServerExtensionHandshaker)enabledExtensions[i];
        }
        pipeline.addAfter(anchor, handlerName, (ChannelHandler)new WebSocketServerExtensionHandler(handshakers));
    }

    public static Object createDeflateFrameServerExtensionHandshaker(int compressionLevel, int maxAllocation) {
        if (!PMCE_AVAILABLE) {
            return null;
        }
        if (hasMaxAllocationHandshaker) {
            return new DeflateFrameServerExtensionHandshaker(compressionLevel, maxAllocation);
        }
        return new WebSocketServerExtensionHandshakerWrapper((WebSocketServerExtensionHandshaker)new DeflateFrameServerExtensionHandshaker(compressionLevel), maxAllocation);
    }

    public static Object createPerMessageDeflateServerExtensionHandshaker(int compressionLevel, boolean allowServerWindowSize, int preferredClientWindowSize, boolean allowServerNoContext, boolean preferredClientNoContext, int maxAllocation) {
        if (!PMCE_AVAILABLE) {
            return null;
        }
        if (hasMaxAllocationHandshaker) {
            return new PerMessageDeflateServerExtensionHandshaker(compressionLevel, allowServerWindowSize, preferredClientWindowSize, allowServerNoContext, preferredClientNoContext, maxAllocation);
        }
        return new WebSocketServerExtensionHandshakerWrapper((WebSocketServerExtensionHandshaker)new PerMessageDeflateServerExtensionHandshaker(compressionLevel, allowServerWindowSize, preferredClientWindowSize, allowServerNoContext, preferredClientNoContext), maxAllocation);
    }

    static {
        boolean pmce = false;
        boolean hasMaxAllocHandshaker = false;
        MethodHandle methDecode = null;
        Field fieldDeflater = null;
        boolean hasMaxAlloc = false;
        boolean noJdkZlib = false;
        try {
            Class<?> clzHandshaker = Class.forName("io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker");
            try {
                clzHandshaker.getConstructor(Integer.TYPE, Integer.TYPE);
                hasMaxAllocHandshaker = true;
            }
            catch (ReflectiveOperationException reflectiveOperationException) {
                // empty catch block
            }
            Class<?> clz = Class.forName("io.netty.handler.codec.http.websocketx.extensions.compression.DeflateDecoder");
            Method m = MessageToMessageDecoder.class.getDeclaredMethod("decode", ChannelHandlerContext.class, Object.class, List.class);
            m.setAccessible(true);
            methDecode = MethodHandles.lookup().unreflect(m);
            Field fd = clz.getDeclaredField("decoder");
            fd.setAccessible(true);
            fieldDeflater = fd;
            try {
                ZlibDecoder.class.getConstructor(Integer.TYPE);
                hasMaxAlloc = true;
            }
            catch (ReflectiveOperationException reflectiveOperationException) {
                // empty catch block
            }
            noJdkZlib = PlatformDependent.javaVersion() < 7 || SystemPropertyUtil.getBoolean((String)"io.netty.noJdkZlibDecoder", (boolean)false);
            pmce = true;
        }
        catch (NoClassDefFoundError | ReflectiveOperationException throwable) {
            // empty catch block
        }
        if (pmce && !hasMaxAlloc && noJdkZlib) {
            throw new IllegalStateException("Your Netty version is too old to use the JZlib decoder!");
        }
        PMCE_AVAILABLE = pmce;
        hasMaxAllocationHandshaker = hasMaxAllocHandshaker;
        METH_DECODE = methDecode;
        FIELD_DEFLATER = fieldDeflater;
        hasMaxAllocation = hasMaxAlloc;
        noJdkZlibDecoder = noJdkZlib;
    }

    public static class WebSocketServerExtensionHandshakerWrapper
    implements WebSocketServerExtensionHandshaker {
        private final WebSocketServerExtensionHandshaker delegate;
        private final int maxAllocation;

        public WebSocketServerExtensionHandshakerWrapper(WebSocketServerExtensionHandshaker delegate, int maxAllocation) {
            this.delegate = delegate;
            this.maxAllocation = maxAllocation;
        }

        public WebSocketServerExtension handshakeExtension(WebSocketExtensionData extensionData) {
            WebSocketServerExtension ext = this.delegate.handshakeExtension(extensionData);
            return ext != null ? new WebSocketServerExtensionWrapper(ext, this.maxAllocation) : null;
        }
    }

    public static class WebSocketExtensionDecoderWrapper
    extends WebSocketExtensionDecoder {
        private final WebSocketExtensionDecoder delegate;
        private final int maxAllocation;

        public WebSocketExtensionDecoderWrapper(WebSocketExtensionDecoder delegate, int maxAllocation) {
            this.delegate = delegate;
            this.maxAllocation = maxAllocation;
        }

        protected void decode(ChannelHandlerContext ctx, WebSocketFrame msg, List<Object> out) throws Exception {
            if (!PMCE_AVAILABLE) {
                return;
            }
            if (FIELD_DEFLATER.get(this.delegate) == null) {
                if (!(msg instanceof TextWebSocketFrame) && !(msg instanceof BinaryWebSocketFrame)) {
                    throw new CodecException("unexpected initial frame type: " + msg.getClass().getName());
                }
                FIELD_DEFLATER.set(this.delegate, new EmbeddedChannel(new ChannelHandler[]{this.newWrappedZlibDecoder()}));
            }
            try {
                METH_DECODE.invoke(this.delegate, ctx, msg, out);
            }
            catch (Error | Exception ex) {
                throw ex;
            }
            catch (Throwable exx) {
                throw new Error(exx);
            }
        }

        public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
            this.delegate.handlerRemoved(ctx);
        }

        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            this.delegate.channelInactive(ctx);
        }

        public boolean acceptInboundMessage(Object msg) throws Exception {
            return this.delegate.acceptInboundMessage(msg);
        }

        private ZlibDecoder newWrappedZlibDecoder() {
            if (hasMaxAllocation) {
                if (noJdkZlibDecoder) {
                    return new JZlibDecoder(ZlibWrapper.NONE, this.maxAllocation);
                }
                return new JdkZlibDecoder(ZlibWrapper.NONE, true, this.maxAllocation);
            }
            return new LegacyJdkZlibDecoder(ZlibWrapper.NONE, this.maxAllocation);
        }
    }

    public static class WebSocketServerExtensionWrapper
    implements WebSocketServerExtension {
        private final WebSocketServerExtension delegate;
        private final int maxAllocation;

        public WebSocketServerExtensionWrapper(WebSocketServerExtension delegate, int maxAllocation) {
            this.delegate = delegate;
            this.maxAllocation = maxAllocation;
        }

        public int rsv() {
            return this.delegate.rsv();
        }

        public WebSocketExtensionEncoder newExtensionEncoder() {
            return this.delegate.newExtensionEncoder();
        }

        public WebSocketExtensionDecoder newExtensionDecoder() {
            return new WebSocketExtensionDecoderWrapper(this.delegate.newExtensionDecoder(), this.maxAllocation);
        }

        public WebSocketExtensionData newReponseData() {
            return this.delegate.newReponseData();
        }
    }
}

