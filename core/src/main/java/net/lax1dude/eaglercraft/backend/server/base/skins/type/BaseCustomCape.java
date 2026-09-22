/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.skins.type;

import java.util.Arrays;
import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerCape;

abstract class BaseCustomCape
implements IEaglerPlayerCape {
    private int hashCode;
    private boolean hashZero;

    BaseCustomCape() {
    }

    protected abstract byte[] textureData();

    public int hashCode() {
        if (this.hashCode == 0 && !this.hashZero) {
            this.hashCode = Arrays.hashCode(this.textureData());
            if (this.hashCode == 0) {
                this.hashZero = true;
            }
        }
        return this.hashCode;
    }

    public boolean equals(Object obj) {
        return this == obj || obj instanceof BaseCustomCape && this.hashCode() == ((BaseCustomCape)obj).hashCode() && Arrays.equals(this.textureData(), ((BaseCustomCape)obj).textureData());
    }
}

