/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.buffer.ByteBuf
 *  io.netty.buffer.Unpooled
 *  io.netty.channel.EventLoop
 */
package net.lax1dude.eaglercraft.backend.server.base.message;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoop;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import net.lax1dude.eaglercraft.backend.server.base.EaglerPlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.message.MessageController;
import net.lax1dude.eaglercraft.backend.server.base.message.PlayerChannelHelper;
import net.lax1dude.eaglercraft.backend.server.base.message.ServerMessageHandler;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.BufferUtils;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.GamePacketOutputBuffer;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.GamePluginMessageConstants;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.GamePluginMessageProtocol;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.GameMessagePacket;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.util.ReusableByteArrayInputStream;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.util.ReusableByteArrayOutputStream;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.util.SimpleInputBufferImpl;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.util.SimpleOutputBufferImpl;

public class LegacyMessageController
extends MessageController {
    private final ReusableByteArrayInputStream byteInputStreamSingleton = new ReusableByteArrayInputStream();
    private final ReusableByteArrayOutputStream byteOutputStreamSingleton = new ReusableByteArrayOutputStream();
    private final SimpleInputBufferImpl inputStreamSingleton = new SimpleInputBufferImpl(this.byteInputStreamSingleton);
    private final SimpleOutputBufferImpl outputStreamSingleton = new SimpleOutputBufferImpl(this.byteOutputStreamSingleton);
    private final byte[] outputTempBuffer = new byte[512];
    private volatile int inputStreamLock;
    private volatile int outputStreamLock;
    private final boolean modernChannelNames;
    private static final AtomicIntegerFieldUpdater<LegacyMessageController> IS_LOCK_HANDLE = AtomicIntegerFieldUpdater.newUpdater(LegacyMessageController.class, "inputStreamLock");
    private static final AtomicIntegerFieldUpdater<LegacyMessageController> OS_LOCK_HANDLE = AtomicIntegerFieldUpdater.newUpdater(LegacyMessageController.class, "outputStreamLock");
    private static final String LEGACY_V4_CHANNEL = "EAG|1.8";
    private static final String MODERN_V4_CHANNEL = GamePluginMessageConstants.getModernName("EAG|1.8");

    public LegacyMessageController(GamePluginMessageProtocol protocol, ServerMessageHandler handler, EventLoop eventLoop, int defragSendDelay, int maxPackets, boolean modernChannelNames) {
        super(protocol, handler, eventLoop, defragSendDelay, maxPackets);
        this.modernChannelNames = modernChannelNames;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled aggressive exception aggregation
     */
    public boolean readPacket(String channel, byte[] data) {
        if (data.length == 0) {
            return false;
        }
        try {
            GameMessagePacket pkt;
            block25: {
                if (IS_LOCK_HANDLE.compareAndSet(this, 0, 1)) {
                    try {
                        this.byteInputStreamSingleton.feedBuffer(data);
                        if (data[0] == -1 && channel.equals(LEGACY_V4_CHANNEL)) {
                            int i;
                            this.inputStreamSingleton.readByte();
                            int count = this.inputStreamSingleton.readVarInt();
                            for (i = 0; i < count; ++i) {
                                if (i >= this.maxPackets) {
                                    boolean bl = true;
                                    return bl;
                                }
                                int j = this.inputStreamSingleton.readVarInt();
                                this.inputStreamSingleton.setToByteArrayReturns(j - 1);
                                int k = this.byteInputStreamSingleton.getReaderIndex() + j;
                                if (j < 0 || j > this.inputStreamSingleton.available()) {
                                    throw new IOException("Packet fragment is too long: " + j + " > " + this.inputStreamSingleton.available());
                                }
                                GameMessagePacket pkt2 = this.protocol.readPacket(channel, 0, this.inputStreamSingleton);
                                if (pkt2 == null) {
                                    throw new IOException("Unknown packet type in fragment!");
                                }
                                this.handlePacket(pkt2);
                                if (this.byteInputStreamSingleton.getReaderIndex() == k) continue;
                                throw new IOException("Packet fragment was the wrong length: " + (j + this.byteInputStreamSingleton.getReaderIndex() - k) + " != " + j);
                            }
                            if (this.inputStreamSingleton.available() > 0) {
                                throw new IOException("Leftover data after reading multi-packet! (" + this.inputStreamSingleton.available() + " bytes)");
                            }
                            i = 1;
                            return i != 0;
                        }
                        this.inputStreamSingleton.setToByteArrayReturns(data);
                        pkt = this.protocol.readPacket(channel, 0, this.inputStreamSingleton);
                        if (pkt != null && this.byteInputStreamSingleton.available() != 0) {
                            throw new IOException("Packet was the wrong length: " + pkt.getClass().getSimpleName());
                        }
                        break block25;
                    }
                    finally {
                        this.byteInputStreamSingleton.feedBuffer(null);
                        this.inputStreamSingleton.setToByteArrayReturns(null);
                        IS_LOCK_HANDLE.set(this, 0);
                    }
                }
                ReusableByteArrayInputStream inputStream = new ReusableByteArrayInputStream();
                inputStream.feedBuffer(data);
                SimpleInputBufferImpl inputBuffer = new SimpleInputBufferImpl((InputStream)inputStream, data);
                if (data[0] == -1 && channel.equals(LEGACY_V4_CHANNEL)) {
                    inputBuffer.readByte();
                    int count = inputBuffer.readVarInt();
                    for (int i = 0; i < count; ++i) {
                        if (i >= this.maxPackets) {
                            return true;
                        }
                        int j = inputBuffer.readVarInt();
                        inputBuffer.setToByteArrayReturns(j - 1);
                        int k = inputStream.getReaderIndex() + j;
                        if (j < 0 || j > inputBuffer.available()) {
                            throw new IOException("Packet fragment is too long: " + j + " > " + inputBuffer.available());
                        }
                        GameMessagePacket pkt3 = this.protocol.readPacket(channel, 0, inputBuffer);
                        if (pkt3 == null) {
                            throw new IOException("Unknown packet type in fragment!");
                        }
                        if (inputStream.getReaderIndex() != k) {
                            throw new IOException("Packet fragment was the wrong length: " + (j + inputStream.getReaderIndex() - k) + " != " + j);
                        }
                        this.handlePacket(pkt3);
                    }
                    if (inputBuffer.available() > 0) {
                        throw new IOException("Leftover data after reading multi-packet! (" + inputBuffer.available() + " bytes)");
                    }
                    return true;
                }
                pkt = this.protocol.readPacket(channel, 0, inputBuffer);
                if (pkt != null && inputStream.available() != 0) {
                    throw new IOException("Packet was the wrong length: " + pkt.getClass().getSimpleName());
                }
            }
            if (pkt != null) {
                this.handlePacket(pkt);
                return true;
            }
            return false;
        }
        catch (IOException ex) {
            this.onException(ex);
            return true;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    protected void writePacket(GameMessagePacket packet) throws IOException {
        byte[] data;
        String chan;
        int len = packet.length() + 1;
        if (OS_LOCK_HANDLE.compareAndSet(this, 0, 1)) {
            try {
                this.byteOutputStreamSingleton.feedBuffer(len == 0 ? this.outputTempBuffer : new byte[len]);
                chan = this.protocol.writePacket(1, this.outputStreamSingleton, packet);
                data = len == 0 ? this.byteOutputStreamSingleton.returnBufferCopied() : this.byteOutputStreamSingleton.returnBuffer();
            }
            finally {
                this.byteOutputStreamSingleton.feedBuffer(null);
                OS_LOCK_HANDLE.set(this, 0);
            }
        } else {
            ReusableByteArrayOutputStream bao = new ReusableByteArrayOutputStream();
            bao.feedBuffer(new byte[len == 0 ? 64 : len]);
            SimpleOutputBufferImpl outputStream = new SimpleOutputBufferImpl(bao);
            chan = this.protocol.writePacket(1, outputStream, packet);
            data = bao.returnBuffer();
        }
        EaglerPlayerInstance<?> player = ((ServerMessageHandler)this.handler).eaglerHandle;
        if (len != 0 && data.length != len && (this.protocol.ver > 3 || data.length + 1 != len)) {
            player.getEaglerXServer().logger().warn("Packet " + packet.getClass().getSimpleName() + " was the wrong length after serialization, " + data.length + " != " + len);
        }
        if (this.modernChannelNames) {
            chan = PlayerChannelHelper.mapModernName(chan);
        }
        player.getPlatformPlayer().sendDataClient(chan, data);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    protected void writeMultiPacket(GameMessagePacket[] packets) throws IOException {
        byte[] dat;
        int total = packets.length;
        EaglerPlayerInstance<?> player = ((ServerMessageHandler)this.handler).eaglerHandle;
        byte[][] buffer = new byte[total][];
        if (OS_LOCK_HANDLE.compareAndSet(this, 0, 1)) {
            try {
                for (int i = 0; i < total; ++i) {
                    GameMessagePacket packet = packets[i];
                    int len = packet.length() + 1;
                    this.byteOutputStreamSingleton.feedBuffer(len == 0 ? this.outputTempBuffer : new byte[len]);
                    this.protocol.writePacket(1, this.outputStreamSingleton, packet);
                    byte[] byArray = dat = len == 0 ? this.byteOutputStreamSingleton.returnBufferCopied() : this.byteOutputStreamSingleton.returnBuffer();
                    if (len != 0 && dat.length != len) {
                        player.getEaglerXServer().logger().warn("Packet " + packet.getClass().getSimpleName() + " was the wrong length after serialization, " + dat.length + " != " + len);
                    }
                    buffer[i] = dat;
                }
            }
            finally {
                this.byteOutputStreamSingleton.feedBuffer(null);
                OS_LOCK_HANDLE.set(this, 0);
            }
        } else {
            ReusableByteArrayOutputStream bao = new ReusableByteArrayOutputStream();
            SimpleOutputBufferImpl outputStream = new SimpleOutputBufferImpl(bao);
            for (int i = 0; i < total; ++i) {
                GameMessagePacket packet = packets[i];
                int len = packet.length() + 1;
                bao.feedBuffer(new byte[len == 0 ? 64 : len]);
                this.protocol.writePacket(1, outputStream, packet);
                dat = bao.returnBuffer();
                if (len != 0 && dat.length != len) {
                    player.getEaglerXServer().logger().warn("Packet " + packet.getClass().getSimpleName() + " was the wrong length after serialization, " + dat.length + " != " + len);
                }
                buffer[i] = dat;
            }
        }
        int start = 0;
        while (total > start) {
            int i;
            int lastLen;
            int sendCount = 0;
            int totalLen = 0;
            while ((totalLen += (lastLen = GamePacketOutputBuffer.getVarIntSize(i = buffer[start + sendCount].length) + i)) < 32760 && ++sendCount < total - start && sendCount < this.maxPackets) {
            }
            if (totalLen >= 32760) {
                --sendCount;
                totalLen -= lastLen;
            }
            if (sendCount <= 1) {
                player.getPlatformPlayer().sendDataClient(this.modernChannelNames ? MODERN_V4_CHANNEL : LEGACY_V4_CHANNEL, buffer[start++]);
                continue;
            }
            byte[] toSend = new byte[1 + totalLen + GamePacketOutputBuffer.getVarIntSize(sendCount)];
            ByteBuf sendBuffer = Unpooled.wrappedBuffer((byte[])toSend);
            try {
                sendBuffer.writerIndex(0);
                sendBuffer.writeByte(255);
                BufferUtils.writeVarInt(sendBuffer, sendCount);
                for (int j = 0; j < sendCount; ++j) {
                    dat = buffer[start++];
                    BufferUtils.writeVarInt(sendBuffer, dat.length);
                    sendBuffer.writeBytes(dat);
                }
                player.getPlatformPlayer().sendDataClient(this.modernChannelNames ? MODERN_V4_CHANNEL : LEGACY_V4_CHANNEL, toSend);
            }
            finally {
                sendBuffer.release();
            }
        }
    }
}

