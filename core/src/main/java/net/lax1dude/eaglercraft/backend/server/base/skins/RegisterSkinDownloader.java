/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.skins;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.BiConsumer;
import net.lax1dude.eaglercraft.backend.server.api.skins.EnumSkinModel;
import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerCape;
import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerSkin;
import net.lax1dude.eaglercraft.backend.server.base.EaglerPlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.skins.RegisterSkinDelegate;
import net.lax1dude.eaglercraft.backend.server.base.skins.SkinService;

class RegisterSkinDownloader {
    private static final AtomicIntegerFieldUpdater<RegisterSkinDownloader> COUNT_DOWN_HANDLE = AtomicIntegerFieldUpdater.newUpdater(RegisterSkinDownloader.class, "countDownValue");
    private volatile int countDownValue = 2;
    private final SkinService<?> skinService;
    private final EaglerPlayerInstance<?> player;
    private final RegisterSkinDelegate state;
    private final BiConsumer<IEaglerPlayerSkin, IEaglerPlayerCape> onComplete;
    private IEaglerPlayerSkin skinResult;
    private IEaglerPlayerCape capeResult;

    RegisterSkinDownloader(SkinService<?> skinService, EaglerPlayerInstance<?> player, RegisterSkinDelegate state, BiConsumer<IEaglerPlayerSkin, IEaglerPlayerCape> onComplete) {
        this.skinService = skinService;
        this.player = player;
        this.state = state;
        this.onComplete = onComplete;
    }

    public void run() {
        if (this.state.skinURL != null) {
            this.skinService.loadPlayerSkinFromURL(this.state.skinURL, this.player.getUniqueId(), this.state.skinModel != null ? this.state.skinModel : EnumSkinModel.STEVE, skin -> {
                this.skinResult = skin.isSuccess() ? skin : this.state.skinOriginal;
                this.countDown();
            });
        } else {
            this.skinResult = this.state.skin != null ? this.state.skin : this.state.skinOriginal;
            this.countDown();
        }
        if (this.state.capeURL != null) {
            this.skinService.loadPlayerCapeFromURL(this.state.capeURL, this.player.getUniqueId(), cape -> {
                this.capeResult = cape.isSuccess() ? cape : this.state.capeOriginal;
                this.countDown();
            });
        } else {
            this.capeResult = this.state.cape != null ? this.state.cape : this.state.capeOriginal;
            this.countDown();
        }
    }

    private void countDown() {
        if (COUNT_DOWN_HANDLE.getAndAdd(this, -1) == 1) {
            this.state.handleComplete(this.player, this.skinResult, this.capeResult, this.onComplete);
        }
    }
}

