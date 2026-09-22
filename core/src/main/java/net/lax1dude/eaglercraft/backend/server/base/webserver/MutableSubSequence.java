/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.webserver;

public class MutableSubSequence
implements CharSequence {
    protected int hash;
    protected boolean hashIsZero;
    protected CharSequence data;
    protected int off;
    protected int len;

    public MutableSubSequence() {
    }

    public MutableSubSequence(CharSequence data, int off, int len) {
        this.set(data, off, len);
    }

    public MutableSubSequence set(CharSequence data, int off, int len) {
        this.hash = 0;
        this.hashIsZero = false;
        this.data = data;
        this.off = off;
        this.len = len;
        return this;
    }

    @Override
    public int length() {
        return this.len;
    }

    @Override
    public char charAt(int index) {
        if (index < 0 || index >= this.len) {
            throw new StringIndexOutOfBoundsException(index);
        }
        return this.data.charAt(index + this.off);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        if (start < 0 || start >= this.len) {
            throw new StringIndexOutOfBoundsException(start);
        }
        if (end < start || end > this.len) {
            throw new StringIndexOutOfBoundsException(end);
        }
        return this.data.subSequence(start + this.off, end + this.off);
    }

    @Override
    public String toString() {
        return this.data.subSequence(this.off, this.off + this.len).toString();
    }

    public int hashCode() {
        if (this.hash == 0 && !this.hashIsZero) {
            int h = 0;
            int l = this.len;
            for (int i = 0; i < l; ++i) {
                h = 31 * h + this.data.charAt(this.off + i);
            }
            if (h == 0) {
                this.hashIsZero = true;
            }
            this.hash = h;
            return this.hash;
        }
        return this.hash;
    }

    public boolean equals(Object obj) {
        return this == obj || obj instanceof CharSequence && this.subEquals((CharSequence)obj);
    }

    private boolean subEquals(CharSequence obj) {
        int l = this.len;
        if (obj.length() != l) {
            return false;
        }
        for (int i = 0; i < l; ++i) {
            if (this.data.charAt(this.off + i) == obj.charAt(i)) continue;
            return false;
        }
        return true;
    }
}

