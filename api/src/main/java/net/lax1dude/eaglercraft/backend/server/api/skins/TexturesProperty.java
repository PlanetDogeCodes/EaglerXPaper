/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nonnull
 */
package net.lax1dude.eaglercraft.backend.server.api.skins;

import javax.annotation.Nonnull;

public final class TexturesProperty {
    private final String value;
    private final String signature;

    public static TexturesProperty create(@Nonnull String value, @Nonnull String signature) {
        if (value == null) {
            throw new IllegalArgumentException("value cannot be null");
        }
        if (signature == null) {
            throw new IllegalArgumentException("signature cannot be null");
        }
        return new TexturesProperty(value, signature);
    }

    private TexturesProperty(String value, String signature) {
        this.value = value;
        this.signature = signature;
    }

    @Nonnull
    public String getValue() {
        return this.value;
    }

    @Nonnull
    public String getSignature() {
        return this.signature;
    }

    public int hashCode() {
        return this.value.hashCode() * 31 + this.signature.hashCode();
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TexturesProperty)) {
            return false;
        }
        TexturesProperty t = (TexturesProperty)o;
        return t.value.equals(this.value) && t.signature.equals(this.signature);
    }
}

