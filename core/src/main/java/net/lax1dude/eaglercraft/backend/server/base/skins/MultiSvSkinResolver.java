/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.skins;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerCape;
import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerSkin;
import net.lax1dude.eaglercraft.backend.server.base.skins.ISkinManagerImpl;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.ISupervisorResolverImpl;

abstract class MultiSvSkinResolver<SkinManager extends ISkinManagerImpl, PlayerObject> {
    private static final AtomicIntegerFieldUpdater<MultiSvSkinResolver> COUNT_DOWN_HANDLE = AtomicIntegerFieldUpdater.newUpdater(MultiSvSkinResolver.class, "countDownValue");
    private volatile int countDownValue = 2;
    private final SkinManager skinManager;
    private IEaglerPlayerSkin skin;
    private IEaglerPlayerCape cape;

    protected MultiSvSkinResolver(SkinManager skinManager, ISupervisorResolverImpl lookup, UUID lookupUUID, UUID uuid) {
        this.skinManager = skinManager;
        lookup.resolvePlayerSkinKeyed(uuid, lookupUUID, res -> {
            this.skin = res;
            this.countDown();
        });
        lookup.resolvePlayerCapeKeyed(uuid, lookupUUID, res -> {
            this.cape = res;
            this.countDown();
        });
    }

    private void countDown() {
        if (COUNT_DOWN_HANDLE.getAndAdd(this, -1) == 1) {
            this.onComplete(this.skinManager, this.skin, this.cape);
        }
    }

    protected abstract void onComplete(SkinManager var1, IEaglerPlayerSkin var2, IEaglerPlayerCape var3);
}

