/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.buffer.ByteBuf
 *  io.netty.channel.ChannelHandlerContext
 *  io.netty.handler.codec.ByteToMessageDecoder
 *  io.netty.handler.codec.compression.DecompressionException
 *  io.netty.handler.codec.compression.ZlibDecoder
 *  io.netty.handler.codec.compression.ZlibWrapper
 *  io.netty.util.internal.SystemPropertyUtil
 */
package net.lax1dude.eaglercraft.backend.server.base.pipeline;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.compression.DecompressionException;
import io.netty.handler.codec.compression.ZlibDecoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import io.netty.util.internal.SystemPropertyUtil;
import java.lang.reflect.Method;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class LegacyJdkZlibDecoder
extends ZlibDecoder {
    protected final int maxAllocation;
    private static final Method DISCARD_SOME_READ_BYTES;
    private Inflater inflater;
    private final byte[] dictionary;
    private boolean needsRead;
    private static final int DEFAULT_MAX_FORWARD_BYTES;
    private final int maxForwardBytes;
    private volatile boolean finished;
    private boolean decideZlibOrNone;

    private void discardSomeReadBytesCompat() {
        if (DISCARD_SOME_READ_BYTES == null) {
            return;
        }
        try {
            DISCARD_SOME_READ_BYTES.invoke((Object)this, new Object[0]);
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            // empty catch block
        }
    }

    private static int checkPositiveOrZero(int i) {
        if (i < 0) {
            throw new IllegalArgumentException("expected a positive or zero value but got " + i);
        }
        return i;
    }

    protected ByteBuf prepareDecompressBuffer(ChannelHandlerContext ctx, ByteBuf buffer, int preferredSize) {
        if (buffer == null) {
            if (this.maxAllocation == 0) {
                return ctx.alloc().heapBuffer(preferredSize);
            }
            return ctx.alloc().heapBuffer(Math.min(preferredSize, this.maxAllocation), this.maxAllocation);
        }
        if (buffer.ensureWritable(preferredSize, true) == 1) {
            this.decompressionBufferExhausted(buffer.duplicate());
            buffer.skipBytes(buffer.readableBytes());
            throw new DecompressionException("Decompression buffer has reached maximum size: " + buffer.maxCapacity());
        }
        return buffer;
    }

    public LegacyJdkZlibDecoder(byte[] dictionary, int maxAllocation) {
        this(ZlibWrapper.ZLIB, dictionary, maxAllocation);
    }

    public LegacyJdkZlibDecoder(ZlibWrapper wrapper, int maxAllocation) {
        this(wrapper, null, maxAllocation);
    }

    private LegacyJdkZlibDecoder(ZlibWrapper wrapper, byte[] dictionary, int maxAllocation) {
        this.maxAllocation = LegacyJdkZlibDecoder.checkPositiveOrZero(maxAllocation);
        int n = this.maxForwardBytes = maxAllocation > 0 ? maxAllocation : DEFAULT_MAX_FORWARD_BYTES;
        if (wrapper == null) {
            throw new NullPointerException("wrapper");
        }
        switch (wrapper) {
            case GZIP: {
                throw new IllegalStateException();
            }
            case NONE: {
                this.inflater = new Inflater(true);
                break;
            }
            case ZLIB: {
                this.inflater = new Inflater();
                break;
            }
            case ZLIB_OR_NONE: {
                this.decideZlibOrNone = true;
                break;
            }
            default: {
                throw new IllegalArgumentException("Only GZIP or ZLIB is supported, but you used " + wrapper);
            }
        }
        this.dictionary = dictionary;
    }

    public boolean isClosed() {
        return this.finished;
    }

    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        this.needsRead = true;
        if (this.finished) {
            in.skipBytes(in.readableBytes());
            return;
        }
        int readableBytes = in.readableBytes();
        if (readableBytes == 0) {
            return;
        }
        if (this.decideZlibOrNone) {
            if (readableBytes < 2) {
                return;
            }
            boolean nowrap = !LegacyJdkZlibDecoder.looksLikeZlib(in.getShort(in.readerIndex()));
            this.inflater = new Inflater(nowrap);
            this.decideZlibOrNone = false;
        }
        if (this.inflater.needsInput()) {
            if (in.hasArray()) {
                this.inflater.setInput(in.array(), in.arrayOffset() + in.readerIndex(), readableBytes);
            } else {
                byte[] array = new byte[readableBytes];
                in.getBytes(in.readerIndex(), array);
                this.inflater.setInput(array);
            }
        }
        ByteBuf decompressed = this.prepareDecompressBuffer(ctx, null, this.inflater.getRemaining() << 1);
        try {
            while (!this.inflater.needsInput()) {
                int writable;
                byte[] outArray = decompressed.array();
                int writerIndex = decompressed.writerIndex();
                int outIndex = decompressed.arrayOffset() + writerIndex;
                int outputLength = this.inflater.inflate(outArray, outIndex, writable = decompressed.writableBytes());
                if (outputLength > 0) {
                    decompressed.writerIndex(writerIndex + outputLength);
                    if (this.maxAllocation == 0 && decompressed.readableBytes() >= this.maxForwardBytes) {
                        ByteBuf buffer = decompressed;
                        decompressed = null;
                        this.needsRead = false;
                        ctx.fireChannelRead((Object)buffer);
                    }
                } else if (this.inflater.needsDictionary()) {
                    if (this.dictionary == null) {
                        throw new DecompressionException("decompression failure, unable to set dictionary as non was specified");
                    }
                    this.inflater.setDictionary(this.dictionary);
                }
                if (this.inflater.finished()) {
                    this.finished = true;
                    break;
                }
                decompressed = this.prepareDecompressBuffer(ctx, decompressed, this.inflater.getRemaining() << 1);
            }
            in.skipBytes(readableBytes - this.inflater.getRemaining());
        }
        catch (DataFormatException e) {
            throw new DecompressionException("decompression failure", (Throwable)e);
        }
        finally {
            if (decompressed != null) {
                if (decompressed.isReadable()) {
                    this.needsRead = false;
                    ctx.fireChannelRead((Object)decompressed);
                } else {
                    decompressed.release();
                }
            }
        }
    }

    protected void decompressionBufferExhausted(ByteBuf buffer) {
        this.finished = true;
    }

    protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved0(ctx);
        if (this.inflater != null) {
            this.inflater.end();
        }
    }

    private static boolean looksLikeZlib(short cmf_flg) {
        return (cmf_flg & 0x7800) == 30720 && cmf_flg % 31 == 0;
    }

    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        this.discardSomeReadBytesCompat();
        if (this.needsRead && !ctx.channel().config().isAutoRead()) {
            ctx.read();
        }
        ctx.fireChannelReadComplete();
    }

    static {
        Method m = null;
        try {
            m = ByteToMessageDecoder.class.getDeclaredMethod("discardSomeReadBytes", new Class[0]);
            m.setAccessible(true);
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            // empty catch block
        }
        DISCARD_SOME_READ_BYTES = m;
        DEFAULT_MAX_FORWARD_BYTES = SystemPropertyUtil.getInt((String)"io.netty.compression.defaultMaxForwardBytes", (int)65536);
    }

    private static enum GzipState {
        HEADER_START,
        HEADER_END,
        FLG_READ,
        XLEN_READ,
        SKIP_FNAME,
        SKIP_COMMENT,
        PROCESS_FHCRC,
        FOOTER_START;

    }
}

