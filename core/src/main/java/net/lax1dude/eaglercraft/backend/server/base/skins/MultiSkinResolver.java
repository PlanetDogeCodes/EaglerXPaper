/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.skins;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerCape;
import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerSkin;
import net.lax1dude.eaglercraft.backend.server.base.skins.ISkinManagerImpl;

abstract class MultiSkinResolver<SkinManager extends ISkinManagerImpl, PlayerObject> {
    private static final AtomicIntegerFieldUpdater<MultiSkinResolver> COUNT_DOWN_HANDLE = AtomicIntegerFieldUpdater.newUpdater(MultiSkinResolver.class, "countDownValue");
    private volatile int countDownValue = 2;
    private final SkinManager skinManager;
    private IEaglerPlayerSkin skin;
    private IEaglerPlayerCape cape;

    protected MultiSkinResolver(SkinManager skinManager, ISkinManagerImpl lookup, IEaglerPlayerSkin skin, IEaglerPlayerCape cape, UUID uuid) {
        this.skinManager = skinManager;
        if (skin == null) {
            lookup.resolvePlayerSkinKeyed(uuid, res -> {
                this.skin = res;
                this.countDown();
            });
        } else {
            this.skin = skin;
            this.countDown();
        }
        if (cape == null) {
            lookup.resolvePlayerCapeKeyed(uuid, res -> {
                this.cape = res;
                this.countDown();
            });
        } else {
            this.cape = cape;
            this.countDown();
        }
    }

    private void countDown() {
        if (COUNT_DOWN_HANDLE.getAndAdd(this, -1) == 1) {
            this.onComplete(this.skinManager, this.skin, this.cape);
        }
    }

    protected abstract void onComplete(SkinManager var1, IEaglerPlayerSkin var2, IEaglerPlayerCape var3);
}

