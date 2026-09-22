/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.base.Objects
 *  javax.annotation.Nullable
 */
package net.lax1dude.eaglercraft.backend.server.api.skins;

import com.google.common.base.Objects;
import javax.annotation.Nullable;
import net.lax1dude.eaglercraft.backend.server.api.skins.EnumSkinModel;

public final class TexturesResult {
    private final String skinURL;
    private final EnumSkinModel skinModel;
    private final String capeURL;

    public static TexturesResult create(@Nullable String skinURL, @Nullable EnumSkinModel skinModel, @Nullable String capeURL) {
        return new TexturesResult(skinURL, skinModel, capeURL);
    }

    private TexturesResult(String skinURL, EnumSkinModel skinModel, String capeURL) {
        this.skinURL = skinURL;
        this.skinModel = skinModel;
        this.capeURL = capeURL;
    }

    @Nullable
    public String getSkinURL() {
        return this.skinURL;
    }

    @Nullable
    public EnumSkinModel getSkinModel() {
        return this.skinModel;
    }

    @Nullable
    public String getCapeURL() {
        return this.capeURL;
    }

    public int hashCode() {
        int code = 0;
        if (this.skinURL != null) {
            code += this.skinURL.hashCode();
        }
        code *= 31;
        if (this.skinModel != null) {
            code += this.skinModel.hashCode();
        }
        code *= 31;
        if (this.capeURL != null) {
            code += this.capeURL.hashCode();
        }
        return code;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TexturesResult)) {
            return false;
        }
        TexturesResult t = (TexturesResult)o;
        return Objects.equal((Object)t.skinURL, (Object)this.skinURL) && Objects.equal((Object)((Object)t.skinModel), (Object)((Object)this.skinModel)) && Objects.equal((Object)t.capeURL, (Object)this.capeURL);
    }
}

