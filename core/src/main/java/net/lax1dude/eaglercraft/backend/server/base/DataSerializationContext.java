/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.util.ReusableByteArrayInputStream;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.util.ReusableByteArrayOutputStream;

public class DataSerializationContext {
    public final ReusableByteArrayInputStream byteInputStreamSingleton = new ReusableByteArrayInputStream();
    public final ReusableByteArrayOutputStream byteOutputStreamSingleton = new ReusableByteArrayOutputStream();
    public final DataInputStream inputStreamSingleton = new DataInputStream(this.byteInputStreamSingleton);
    public final DataOutputStream outputStreamSingleton = new DataOutputStream(this.byteOutputStreamSingleton);
    public final byte[] outputTempBuffer = new byte[512];
    private volatile int inputStreamLock;
    private volatile int outputStreamLock;
    private static final AtomicIntegerFieldUpdater<DataSerializationContext> IS_LOCK_HANDLE = AtomicIntegerFieldUpdater.newUpdater(DataSerializationContext.class, "inputStreamLock");
    private static final AtomicIntegerFieldUpdater<DataSerializationContext> OS_LOCK_HANDLE = AtomicIntegerFieldUpdater.newUpdater(DataSerializationContext.class, "outputStreamLock");

    public final boolean aquireInputStream() {
        return IS_LOCK_HANDLE.compareAndSet(this, 0, 1);
    }

    public final void releaseInputStream() {
        this.inputStreamLock = 0;
    }

    public final boolean aquireOutputStream() {
        return OS_LOCK_HANDLE.compareAndSet(this, 0, 1);
    }

    public final void releaseOutputStream() {
        this.outputStreamLock = 0;
    }
}

