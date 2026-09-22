/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.buffer.ByteBuf
 *  io.netty.buffer.ByteBufInputStream
 */
package net.lax1dude.eaglercraft.backend.server.base.message;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.BufferUtils;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.GamePacketInputBuffer;

public class ByteBufInputWrapper
implements GamePacketInputBuffer {
    public ByteBuf buffer;

    public ByteBufInputWrapper() {
    }

    public ByteBufInputWrapper(ByteBuf buffer) {
        this.buffer = buffer;
    }

    @Override
    public void readFully(byte[] b) throws IOException {
        this.buffer.readBytes(b);
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws IOException {
        this.buffer.readBytes(b, off, len);
    }

    @Override
    public int skipBytes(int n) throws IOException {
        this.buffer.skipBytes(n);
        return n;
    }

    @Override
    public boolean readBoolean() throws IOException {
        return this.buffer.readBoolean();
    }

    @Override
    public byte readByte() throws IOException {
        return this.buffer.readByte();
    }

    @Override
    public int readUnsignedByte() throws IOException {
        return this.buffer.readUnsignedByte();
    }

    @Override
    public short readShort() throws IOException {
        return this.buffer.readShort();
    }

    @Override
    public int readUnsignedShort() throws IOException {
        return this.buffer.readUnsignedShort();
    }

    @Override
    public char readChar() throws IOException {
        return this.buffer.readChar();
    }

    @Override
    public int readInt() throws IOException {
        return this.buffer.readInt();
    }

    @Override
    public long readLong() throws IOException {
        return this.buffer.readLong();
    }

    @Override
    public float readFloat() throws IOException {
        return this.buffer.readFloat();
    }

    @Override
    public double readDouble() throws IOException {
        return this.buffer.readDouble();
    }

    @Override
    public String readLine() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String readUTF() throws IOException {
        return DataInputStream.readUTF(this);
    }

    @Override
    public void skipAllBytes(int n) throws IOException {
        this.buffer.skipBytes(n);
    }

    @Override
    public int readVarInt() throws IOException {
        return BufferUtils.readVarInt(this.buffer, 5);
    }

    @Override
    public long readVarLong() throws IOException {
        return BufferUtils.readVarLong(this.buffer, 10);
    }

    @Override
    public String readStringMC(int maxLen) throws IOException {
        return BufferUtils.readMCString(this.buffer, maxLen);
    }

    @Override
    public String readStringEaglerASCII8() throws IOException {
        int len = this.readUnsignedByte();
        char[] ret = new char[len];
        for (int i = 0; i < len; ++i) {
            ret[i] = (char)this.readByte();
        }
        return new String(ret);
    }

    @Override
    public String readStringEaglerASCII16() throws IOException {
        int len = this.readUnsignedShort();
        char[] ret = new char[len];
        for (int i = 0; i < len; ++i) {
            ret[i] = (char)this.readByte();
        }
        return new String(ret);
    }

    @Override
    @Deprecated
    public byte[] readByteArrayMC() throws IOException {
        int i = BufferUtils.readVarInt(this.buffer, 5);
        if (i < 0 || i > this.buffer.readableBytes()) {
            throw new IOException("Byte array has an invalid length: " + i);
        }
        byte[] abyte = new byte[i];
        this.buffer.readBytes(abyte);
        return abyte;
    }

    @Override
    public byte[] readByteArrayMC(int maxLen) throws IOException {
        int i = BufferUtils.readVarInt(this.buffer, 5);
        if (i < 0 || i > maxLen) {
            throw new IOException("Byte array is too long: " + i + " > " + maxLen);
        }
        byte[] abyte = new byte[i];
        this.buffer.readBytes(abyte);
        return abyte;
    }

    @Override
    public int available() throws IOException {
        return this.buffer.readableBytes();
    }

    @Override
    public InputStream stream() {
        return new ByteBufInputStream(this.buffer);
    }

    @Override
    public byte[] toByteArray() throws IOException {
        byte[] ret = new byte[this.buffer.readableBytes()];
        this.buffer.readBytes(ret);
        return ret;
    }
}

