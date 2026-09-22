/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.cache.Cache
 *  com.google.common.cache.CacheBuilder
 *  com.google.common.collect.ImmutableList
 */
package net.lax1dude.eaglercraft.backend.server.base.update;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import net.lax1dude.eaglercraft.backend.server.api.SHA1Sum;
import net.lax1dude.eaglercraft.backend.server.base.update.IUpdateCertificateImpl;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketUpdateCertEAG;

public final class UpdateCertificate
implements IUpdateCertificateImpl {
    private static final Cache<SHA1Sum, UpdateCertificate> interner = CacheBuilder.newBuilder().softValues().build();
    private final SHA1Sum checksum;
    private final byte[] data;
    private final int hash;
    private final SPacketUpdateCertEAG packet;

    public static IUpdateCertificateImpl intern(byte[] data) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        SHA1Sum sum = SHA1Sum.ofData(data);
        try {
            return (IUpdateCertificateImpl)interner.get(sum, () -> new UpdateCertificate(data, sum));
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException)cause;
            }
            throw new RuntimeException(cause);
        }
    }

    static IUpdateCertificateImpl internUnsafe(SHA1Sum sum, byte[] data) {
        try {
            return (IUpdateCertificateImpl)interner.get(sum, () -> new UpdateCertificate(data, sum));
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException)cause;
            }
            throw new RuntimeException(cause);
        }
    }

    public static List<IUpdateCertificateImpl> dumpAll() {
        return ImmutableList.copyOf(interner.asMap().values());
    }

    private UpdateCertificate(byte[] data, SHA1Sum sum) {
        this.checksum = sum;
        this.data = data;
        this.hash = sum.hashCode();
        this.packet = new SPacketUpdateCertEAG(data);
    }

    @Override
    public int getLength() {
        return this.data.length;
    }

    @Override
    public void getBytes(byte[] dst, int offset) {
        System.arraycopy(this.data, 0, dst, offset, this.data.length);
    }

    @Override
    public void getBytes(int srcOffset, byte[] dst, int dstOffset, int length) {
        System.arraycopy(this.data, srcOffset, dst, dstOffset, length);
    }

    public int hashCode() {
        return this.hash;
    }

    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public SPacketUpdateCertEAG packet() {
        return this.packet;
    }

    @Override
    public SHA1Sum checkSum() {
        return this.checksum;
    }
}

