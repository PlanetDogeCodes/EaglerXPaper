/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nonnull
 *  javax.annotation.Nullable
 */
package net.lax1dude.eaglercraft.backend.server.api.webserver;

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.lax1dude.eaglercraft.backend.server.api.EnumRequestMethod;

public final class RouteDesc {
    @Nonnull
    public static final RouteDesc DEFAULT_404 = new RouteDesc();
    @Nonnull
    public static final RouteDesc DEFAULT_429 = new RouteDesc();
    @Nonnull
    public static final RouteDesc DEFAULT_500 = new RouteDesc();
    private final String listenerName;
    private final String pattern;
    private final int methods;

    @Nonnull
    public static RouteDesc create(@Nonnull String pattern) {
        if (pattern == null) {
            throw new NullPointerException("pattern");
        }
        return new RouteDesc(null, pattern, 63);
    }

    @Nonnull
    public static RouteDesc create(@Nonnull String pattern, @Nonnull EnumRequestMethod method) {
        if (pattern == null) {
            throw new NullPointerException("pattern");
        }
        if (method == null) {
            throw new NullPointerException("method");
        }
        return new RouteDesc(null, pattern, method.bit());
    }

    @Nonnull
    public static RouteDesc create(@Nonnull String pattern, EnumRequestMethod ... method) {
        if (pattern == null) {
            throw new NullPointerException("pattern");
        }
        return new RouteDesc(null, pattern, EnumRequestMethod.toBits(method));
    }

    @Nonnull
    public static RouteDesc create(@Nonnull String listenerName, @Nonnull String pattern) {
        if (listenerName == null) {
            throw new NullPointerException("listenerName");
        }
        if (pattern == null) {
            throw new NullPointerException("pattern");
        }
        return new RouteDesc(listenerName, pattern, 63);
    }

    @Nonnull
    public static RouteDesc create(@Nonnull String listenerName, @Nonnull String pattern, @Nonnull EnumRequestMethod method) {
        if (listenerName == null) {
            throw new NullPointerException("listenerName");
        }
        if (pattern == null) {
            throw new NullPointerException("pattern");
        }
        if (method == null) {
            throw new NullPointerException("method");
        }
        return new RouteDesc(listenerName, pattern, method.bit());
    }

    @Nonnull
    public static RouteDesc create(@Nonnull String listenerName, @Nonnull String pattern, EnumRequestMethod ... method) {
        if (listenerName == null) {
            throw new NullPointerException("listenerName");
        }
        if (pattern == null) {
            throw new NullPointerException("pattern");
        }
        return new RouteDesc(listenerName, pattern, EnumRequestMethod.toBits(method));
    }

    private RouteDesc() {
        this(null, null, 0);
    }

    private RouteDesc(String listenerName, String pattern, int methods) {
        this.listenerName = listenerName;
        this.pattern = pattern;
        this.methods = methods;
    }

    public boolean isAllListeners() {
        return this.listenerName == null;
    }

    @Nullable
    public String getListenerName() {
        return this.listenerName;
    }

    @Nonnull
    public String getPattern() {
        return this.pattern;
    }

    @Nonnull
    public EnumRequestMethod[] getMethods() {
        return EnumRequestMethod.fromBits(this.methods);
    }

    public boolean isMethod(@Nonnull EnumRequestMethod meth) {
        return (this.methods & meth.bit()) != 0;
    }

    public boolean isAllMethods() {
        return this.methods == 63;
    }

    public int hashCode() {
        int i = 0;
        if (this.listenerName != null) {
            i += this.listenerName.hashCode();
        }
        i *= 31;
        if (this.pattern != null) {
            i += this.pattern.hashCode();
        }
        return (i *= 31) + this.methods;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RouteDesc)) {
            return false;
        }
        RouteDesc r = (RouteDesc)obj;
        return Objects.equals(r.pattern, this.pattern) && Objects.equals(r.listenerName, this.listenerName) && r.methods == this.methods;
    }
}

