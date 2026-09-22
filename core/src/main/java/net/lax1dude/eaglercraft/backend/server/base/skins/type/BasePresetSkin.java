/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.skins.type;

import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerSkin;

abstract class BasePresetSkin
implements IEaglerPlayerSkin {
    BasePresetSkin() {
    }

    protected abstract int presetId();

    public int hashCode() {
        return this.presetId();
    }

    public boolean equals(Object obj) {
        return this == obj || obj instanceof BasePresetSkin && this.presetId() == ((BasePresetSkin)obj).presetId();
    }
}

