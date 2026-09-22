/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.skins.type;

import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerCape;

abstract class BasePresetCape
implements IEaglerPlayerCape {
    BasePresetCape() {
    }

    protected abstract int presetId();

    public int hashCode() {
        return this.presetId();
    }

    public boolean equals(Object obj) {
        return this == obj || obj instanceof BasePresetCape && this.presetId() == ((BasePresetCape)obj).presetId();
    }
}

