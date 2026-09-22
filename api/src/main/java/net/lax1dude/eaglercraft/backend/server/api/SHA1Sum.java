/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nonnull
 */
package net.lax1dude.eaglercraft.backend.server.api;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.annotation.Nonnull;

public final class SHA1Sum {
    private final int a;
    private final int b;
    private final int c;
    private final int d;
    private final int e;
    private static final char[] hex = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    @Nonnull
    public static SHA1Sum ofData(@Nonnull byte[] data) {
        return SHA1Sum.ofData(data, 0, data.length);
    }

    @Nonnull
    public static SHA1Sum ofData(@Nonnull byte[] data, int offset, int length) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(data, offset, length);
            byte[] ret = digest.digest();
            return SHA1Sum.create(ret, 0);
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Incompatible JRE", e);
        }
    }

    @Nonnull
    public static SHA1Sum create(@Nonnull byte[] checksum) {
        return SHA1Sum.create(checksum, 0);
    }

    @Nonnull
    public static SHA1Sum create(@Nonnull byte[] checksum, int offset) {
        if (offset < 0) {
            throw new ArrayIndexOutOfBoundsException(offset);
        }
        if (offset + 20 > checksum.length) {
            throw new ArrayIndexOutOfBoundsException(offset + 20);
        }
        return SHA1Sum.create(SHA1Sum.intHelper(checksum, offset), SHA1Sum.intHelper(checksum, offset + 4), SHA1Sum.intHelper(checksum, offset + 8), SHA1Sum.intHelper(checksum, offset + 12), SHA1Sum.intHelper(checksum, offset + 16));
    }

    @Nonnull
    public static SHA1Sum create(int a, int b, int c, int d, int e) {
        return new SHA1Sum(a, b, c, d, e);
    }

    private SHA1Sum(int a, int b, int c, int d, int e) {
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.e = e;
    }

    public int getBitsA() {
        return this.a;
    }

    public int getBitsB() {
        return this.b;
    }

    public int getBitsC() {
        return this.c;
    }

    public int getBitsD() {
        return this.d;
    }

    public int getBitsE() {
        return this.e;
    }

    @Nonnull
    public byte[] asBytes() {
        byte[] ret = new byte[20];
        this.asBytes(ret, 0);
        return ret;
    }

    public void asBytes(@Nonnull byte[] dst, int off) {
        if (off < 0) {
            throw new ArrayIndexOutOfBoundsException(off);
        }
        if (off + 20 > dst.length) {
            throw new ArrayIndexOutOfBoundsException(off + 20);
        }
        SHA1Sum.byteHelper(dst, this.a, off);
        SHA1Sum.byteHelper(dst, this.b, off + 4);
        SHA1Sum.byteHelper(dst, this.c, off + 8);
        SHA1Sum.byteHelper(dst, this.d, off + 12);
        SHA1Sum.byteHelper(dst, this.e, off + 16);
    }

    private static int intHelper(byte[] src, int off) {
        return (src[off] & 0xFF) << 24 | (src[off + 1] & 0xFF) << 16 | (src[off + 2] & 0xFF) << 8 | src[off + 3] & 0xFF;
    }

    private static void byteHelper(byte[] dst, int a, int off) {
        dst[off] = (byte)(a >>> 24);
        dst[off + 1] = (byte)(a >>> 16);
        dst[off + 2] = (byte)(a >>> 8);
        dst[off + 3] = (byte)a;
    }

    @Nonnull
    public String toString() {
        char[] ret = new char[40];
        SHA1Sum.hexHelper(ret, 0, this.a);
        SHA1Sum.hexHelper(ret, 8, this.b);
        SHA1Sum.hexHelper(ret, 16, this.c);
        SHA1Sum.hexHelper(ret, 24, this.d);
        SHA1Sum.hexHelper(ret, 32, this.e);
        return new String(ret);
    }

    private static void hexHelper(char[] ret, int i, int j) {
        ret[i] = hex[j >>> 28 & 0xF];
        ret[i + 1] = hex[j >>> 24 & 0xF];
        ret[i + 2] = hex[j >>> 20 & 0xF];
        ret[i + 3] = hex[j >>> 16 & 0xF];
        ret[i + 4] = hex[j >>> 12 & 0xF];
        ret[i + 5] = hex[j >>> 8 & 0xF];
        ret[i + 6] = hex[j >>> 4 & 0xF];
        ret[i + 7] = hex[j & 0xF];
    }

    public int hashCode() {
        return (((this.a * 31 + this.b) * 31 + this.c) * 31 + this.d) * 31 + this.e;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SHA1Sum)) {
            return false;
        }
        SHA1Sum other = (SHA1Sum)obj;
        return other.a == this.a && other.b == this.b && other.c == this.c && other.d == this.d && other.e == this.e;
    }
}

