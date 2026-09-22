/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.buffer.ByteBuf
 */
package net.lax1dude.eaglercraft.backend.supervisor.protocol.util;

import io.netty.buffer.ByteBuf;
import java.nio.charset.Charset;

public final class CharSeqCompat {
    private CharSeqCompat() {
    }

    public static String readString(ByteBuf buffer, int len, Charset charset) {
        if (len < 0) {
            throw new IndexOutOfBoundsException("length: " + len);
        }
        byte[] arr = new byte[len];
        buffer.readBytes(arr);
        return new String(arr, charset);
    }

    public static int writeString(ByteBuf buffer, CharSequence seq, Charset charset) {
        byte[] arr = seq.toString().getBytes(charset);
        buffer.writeBytes(arr);
        return arr.length;
    }
}

