/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.skins.type;

import java.util.Arrays;
import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerSkin;

abstract class BaseCustomSkin
implements IEaglerPlayerSkin {
    private int hashCode;
    private boolean hashZero;

    BaseCustomSkin() {
    }

    protected abstract int modelId();

    protected abstract byte[] textureDataV3();

    protected abstract byte[] textureDataV4();

    public int hashCode() {
        if (this.hashCode == 0 && !this.hashZero) {
            this.hashCode = Arrays.hashCode(this.textureDataV4());
            if (this.hashCode == 0) {
                this.hashZero = true;
            }
        }
        return this.hashCode;
    }

    public boolean equals(Object obj) {
        return this == obj || obj instanceof BaseCustomSkin && this.hashCode() == ((BaseCustomSkin)obj).hashCode() && this.modelId() == ((BaseCustomSkin)obj).modelId() && Arrays.equals(this.textureDataV4(), ((BaseCustomSkin)obj).textureDataV4());
    }
}

