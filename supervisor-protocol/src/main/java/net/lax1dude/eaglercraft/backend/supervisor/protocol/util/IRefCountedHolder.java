/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.util.ReferenceCounted
 */
package net.lax1dude.eaglercraft.backend.supervisor.protocol.util;

import io.netty.util.ReferenceCounted;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.util.TouchCompat;

public interface IRefCountedHolder
extends ReferenceCounted {
    public ReferenceCounted delegate();

    default public int refCnt() {
        ReferenceCounted d = this.delegate();
        return d != null ? d.refCnt() : 0;
    }

    default public ReferenceCounted retain() {
        ReferenceCounted d = this.delegate();
        if (d != null) {
            d.retain();
        }
        return this;
    }

    default public ReferenceCounted retain(int increment) {
        ReferenceCounted d = this.delegate();
        if (d != null) {
            d.retain(increment);
        }
        return this;
    }

    default public boolean release() {
        ReferenceCounted d = this.delegate();
        return d != null && d.release();
    }

    default public boolean release(int decrement) {
        ReferenceCounted d = this.delegate();
        return d != null && d.release(decrement);
    }

    default public ReferenceCounted touch() {
        ReferenceCounted d = this.delegate();
        TouchCompat.touch(d, null);
        return this;
    }

    default public ReferenceCounted touch(Object hint) {
        ReferenceCounted d = this.delegate();
        TouchCompat.touch(d, hint);
        return this;
    }
}

