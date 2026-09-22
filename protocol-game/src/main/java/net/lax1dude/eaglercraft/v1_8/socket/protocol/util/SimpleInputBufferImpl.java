/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.v1_8.socket.protocol.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.GamePacketInputBuffer;

public class SimpleInputBufferImpl
extends DataInputStream
implements GamePacketInputBuffer {
    protected byte[] toByteArrayReturns;
    protected int toByteArrayLength = -1;

    public SimpleInputBufferImpl(InputStream in) {
        super(in);
        this.toByteArrayReturns = null;
        this.toByteArrayLength = -1;
    }

    public SimpleInputBufferImpl(InputStream in, byte[] toByteArrayReturns) {
        super(in);
        this.toByteArrayReturns = toByteArrayReturns;
        this.toByteArrayLength = -1;
    }

    public SimpleInputBufferImpl(InputStream in, int toByteArrayLength) {
        super(in);
        this.toByteArrayReturns = null;
        this.toByteArrayLength = toByteArrayLength;
    }

    public void setStream(InputStream parent) {
        this.in = parent;
        this.toByteArrayReturns = null;
        this.toByteArrayLength = -1;
    }

    public void setToByteArrayReturns(byte[] toByteArrayReturns) {
        this.toByteArrayReturns = toByteArrayReturns;
        this.toByteArrayLength = -1;
    }

    public void setToByteArrayReturns(int len) {
        this.toByteArrayReturns = null;
        this.toByteArrayLength = len;
    }

    @Override
    public void skipAllBytes(int n) throws IOException {
        if (this.skipBytes(n) != n) {
            throw new EOFException();
        }
    }

    @Override
    public int readVarInt() throws IOException {
        int b0;
        int i = 0;
        int j = 0;
        do {
            if ((b0 = this.in.read()) < 0) {
                throw new EOFException();
            }
            i |= (b0 & 0x7F) << j++ * 7;
            if (j <= 5) continue;
            throw new IOException("VarInt too big");
        } while ((b0 & 0x80) == 128);
        return i;
    }

    @Override
    public long readVarLong() throws IOException {
        int b0;
        long i = 0L;
        int j = 0;
        do {
            if ((b0 = this.in.read()) < 0) {
                throw new EOFException();
            }
            i |= (long)(b0 & 0x7F) << j++ * 7;
            if (j <= 10) continue;
            throw new IOException("VarLong too big");
        } while ((b0 & 0x80) == 128);
        return i;
    }

    @Override
    public String readStringMC(int maxLen) throws IOException {
        int i = this.readVarInt();
        if (i > maxLen << 2) {
            throw new IOException("The received encoded string buffer length is longer than maximum allowed (" + i + " > " + (maxLen << 2) + ")");
        }
        if (i < 0) {
            throw new IOException("The received encoded string buffer length is less than zero! Weird string!");
        }
        byte[] toRead = new byte[i];
        this.readFully(toRead);
        String s = new String(toRead, StandardCharsets.UTF_8);
        if (s.length() > maxLen) {
            throw new IOException("The received string length is longer than maximum allowed (" + i + " > " + maxLen + ")");
        }
        return s;
    }

    @Override
    public String readStringEaglerASCII8() throws IOException {
        int len = this.in.read();
        if (len < 0) {
            throw new EOFException();
        }
        char[] ret = new char[len];
        for (int i = 0; i < len; ++i) {
            int j = this.in.read();
            if (j < 0) {
                throw new EOFException();
            }
            ret[i] = (char)j;
        }
        return new String(ret);
    }

    @Override
    public String readStringEaglerASCII16() throws IOException {
        int len = this.readUnsignedShort();
        char[] ret = new char[len];
        for (int i = 0; i < len; ++i) {
            int j = this.in.read();
            if (j < 0) {
                throw new EOFException();
            }
            ret[i] = (char)j;
        }
        return new String(ret);
    }

    @Override
    @Deprecated
    public byte[] readByteArrayMC() throws IOException {
        byte[] abyte = new byte[this.readVarInt()];
        this.readFully(abyte);
        return abyte;
    }

    @Override
    public byte[] readByteArrayMC(int maxLen) throws IOException {
        int i = this.readVarInt();
        if (i > maxLen) {
            throw new IOException("Byte array is too long: " + i + " > " + maxLen);
        }
        byte[] abyte = new byte[i];
        this.readFully(abyte);
        return abyte;
    }

    @Override
    public InputStream stream() {
        return this.in;
    }

    @Override
    public byte[] toByteArray() throws IOException {
        int j;
        if (this.toByteArrayReturns != null) {
            return this.toByteArrayReturns;
        }
        if (this.toByteArrayLength != -1) {
            byte[] ret = new byte[this.toByteArrayLength];
            this.in.read(ret);
            return ret;
        }
        if (this.in instanceof ByteArrayInputStream) {
            ByteArrayInputStream bis = (ByteArrayInputStream)this.in;
            byte[] ret = new byte[bis.available()];
            bis.read(ret);
            return ret;
        }
        ByteArrayOutputStream bao = null;
        byte[] copyBuffer = new byte[this.in.available()];
        int i = this.in.read(copyBuffer);
        if (i == copyBuffer.length) {
            j = this.in.read();
            if (j == -1) {
                return copyBuffer;
            }
            int k = Math.max(copyBuffer.length, 64);
            bao = new ByteArrayOutputStream(k + 1);
            bao.write(copyBuffer);
            bao.write(j);
            if (k != copyBuffer.length) {
                copyBuffer = new byte[k];
            }
        } else {
            j = Math.max(copyBuffer.length, 64);
            bao = new ByteArrayOutputStream(j);
            bao.write(copyBuffer);
            if (j != copyBuffer.length) {
                copyBuffer = new byte[j];
            }
        }
        while ((i = this.in.read(copyBuffer)) != -1) {
            bao.write(copyBuffer, 0, i);
        }
        return bao.toByteArray();
    }
}

