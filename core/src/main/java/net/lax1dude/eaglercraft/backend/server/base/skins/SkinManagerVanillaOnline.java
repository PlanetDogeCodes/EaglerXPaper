/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.skins;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.lax1dude.eaglercraft.backend.server.api.IBasePlayer;
import net.lax1dude.eaglercraft.backend.server.api.IOptional;
import net.lax1dude.eaglercraft.backend.server.api.skins.EnumPresetCapes;
import net.lax1dude.eaglercraft.backend.server.api.skins.EnumPresetSkins;
import net.lax1dude.eaglercraft.backend.server.api.skins.EnumSkinModel;
import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerCape;
import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerSkin;
import net.lax1dude.eaglercraft.backend.server.api.skins.ISkinManagerBase;
import net.lax1dude.eaglercraft.backend.server.api.skins.ISkinManagerEagler;
import net.lax1dude.eaglercraft.backend.server.api.skins.ISkinService;
import net.lax1dude.eaglercraft.backend.server.api.skins.TexturesResult;
import net.lax1dude.eaglercraft.backend.server.base.BasePlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.skins.GameProfileUtil;
import net.lax1dude.eaglercraft.backend.server.base.skins.ISkinManagerImpl;
import net.lax1dude.eaglercraft.backend.server.base.skins.MultiSkinResolver;
import net.lax1dude.eaglercraft.backend.server.base.skins.SkinManagerHelper;
import net.lax1dude.eaglercraft.backend.server.base.skins.type.InternUtils;
import net.lax1dude.eaglercraft.backend.server.base.skins.type.PresetCapePlayer;
import net.lax1dude.eaglercraft.backend.server.base.skins.type.PresetSkinPlayer;
import net.lax1dude.eaglercraft.backend.server.util.KeyedConcurrentLazyLoader;

public class SkinManagerVanillaOnline<PlayerObject>
implements ISkinManagerBase<PlayerObject>,
ISkinManagerImpl {
    private final BasePlayerInstance<PlayerObject> player;
    private String skinURL;
    private EnumSkinModel skinModel;
    private String capeURL;
    private volatile IEaglerPlayerSkin skin = null;
    private KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, IEaglerPlayerSkin> waitingSkinCallbacks = null;
    private volatile IEaglerPlayerCape cape = null;
    public final Object capeLock = new Object();
    private KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, IEaglerPlayerCape> waitingCapeCallbacks = null;
    private IEaglerPlayerSkin originalSkin = null;
    private IEaglerPlayerCape originalCape = null;

    SkinManagerVanillaOnline(BasePlayerInstance<PlayerObject> player, String skinURL, EnumSkinModel skinModel, String capeURL) {
        this.player = player;
        UUID uuid = player.getUniqueId();
        this.skinURL = skinURL;
        EnumSkinModel enumSkinModel = this.skinModel = skinURL != null ? skinModel : null;
        this.skin = skinURL == null ? new PresetSkinPlayer(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits(), (uuid.hashCode() & 1) != 0 ? 1 : 0) : null;
        this.capeURL = capeURL;
        this.cape = capeURL == null ? new PresetCapePlayer(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits(), 0) : null;
    }

    @Override
    public IBasePlayer<PlayerObject> getPlayer() {
        return this.player;
    }

    @Override
    public ISkinService<PlayerObject> getSkinService() {
        return this.player.getEaglerXServer().getSkinService();
    }

    @Override
    public boolean isEaglerPlayer() {
        return false;
    }

    @Override
    public ISkinManagerEagler<PlayerObject> asEaglerPlayer() {
        return null;
    }

    @Override
    public IEaglerPlayerSkin getPlayerSkinIfLoaded() {
        return this.skin;
    }

    @Override
    public IEaglerPlayerCape getPlayerCapeIfLoaded() {
        return this.cape;
    }

    @Override
    public void resolvePlayerSkin(Consumer<IEaglerPlayerSkin> callback) {
        this.resolvePlayerSkinKeyed(null, callback);
    }

    @Override
    public void resolvePlayerCape(Consumer<IEaglerPlayerCape> callback) {
        this.resolvePlayerCapeKeyed(null, callback);
    }

    @Override
    public void resolvePlayerTextures(BiConsumer<IEaglerPlayerSkin, IEaglerPlayerCape> callback) {
        this.resolvePlayerTexturesKeyed(null, callback);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void resolvePlayerSkinKeyed(UUID requester, Consumer<IEaglerPlayerSkin> callback) {
        if (callback == null) {
            throw new NullPointerException("callback");
        }
        IEaglerPlayerSkin val = this.skin;
        if (val != null) {
            callback.accept(val);
        } else {
            KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, IEaglerPlayerSkin> expected = null;
            synchronized (this) {
                val = this.skin;
                if (val != null) {
                } else {
                    if (this.waitingSkinCallbacks != null) {
                        this.waitingSkinCallbacks.add(requester, callback);
                        return;
                    }
                    expected = new KeyedConcurrentLazyLoader.KeyedConsumerList();
                    this.waitingSkinCallbacks = expected;
                    this.waitingSkinCallbacks.add(requester, callback);
                }
            }
            if (val != null) {
                callback.accept(val);
                return;
            }
            KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, IEaglerPlayerSkin> expectedFinal = expected;
            this.getSkinService().loadCacheSkinFromURL(this.skinURL, this.skinModel, skin -> {
                KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, IEaglerPlayerSkin> toCall;
                synchronized (this) {
                    toCall = this.waitingSkinCallbacks;
                    if (toCall != null && toCall != expectedFinal) {
                        return;
                    }
                    if (this.skin != null) {
                        if (this.originalSkin == null) {
                            this.originalSkin = skin;
                        }
                        return;
                    }
                    this.originalSkin = skin;
                    this.waitingSkinCallbacks = null;
                }
                if (toCall != null) {
                    List<Consumer<IEaglerPlayerSkin>> toCallList = toCall.getList();
                    int l = toCallList.size();
                    for (int i = 0; i < l; ++i) {
                        try {
                            toCallList.get(i).accept((IEaglerPlayerSkin)skin);
                            continue;
                        }
                        catch (Exception ex) {
                            this.player.getEaglerXServer().logger().error("Caught error from lazy load callback", ex);
                        }
                    }
                }
            });
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void resolvePlayerCapeKeyed(UUID requester, Consumer<IEaglerPlayerCape> callback) {
        if (callback == null) {
            throw new NullPointerException("callback");
        }
        IEaglerPlayerCape val = this.cape;
        if (val != null) {
            callback.accept(val);
        } else {
            KeyedConcurrentLazyLoader.KeyedConsumerList expected = null;
            Object object = this.capeLock;
            synchronized (object) {
                val = this.cape;
                if (val != null) {
                } else {
                    if (this.waitingCapeCallbacks != null) {
                        this.waitingCapeCallbacks.add(requester, callback);
                        return;
                    }
                    expected = new KeyedConcurrentLazyLoader.KeyedConsumerList();
                    this.waitingCapeCallbacks = expected;
                    this.waitingCapeCallbacks.add(requester, callback);
                }
            }
            if (val != null) {
                callback.accept(val);
                return;
            }
            KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, IEaglerPlayerCape> expectedFinal = expected;
            this.getSkinService().loadCacheCapeFromURL(this.capeURL, cape -> {
                KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, IEaglerPlayerCape> toCall;
                synchronized (this.capeLock) {
                    toCall = this.waitingCapeCallbacks;
                    if (toCall != null && toCall != expectedFinal) {
                        return;
                    }
                    if (this.cape != null) {
                        if (this.originalCape == null) {
                            this.originalCape = cape;
                        }
                        return;
                    }
                    this.originalCape = cape;
                    this.waitingCapeCallbacks = null;
                }
                if (toCall != null) {
                    List<Consumer<IEaglerPlayerCape>> toCallList = toCall.getList();
                    int l = toCallList.size();
                    for (int i = 0; i < l; ++i) {
                        try {
                            toCallList.get(i).accept((IEaglerPlayerCape)cape);
                            continue;
                        }
                        catch (Exception ex) {
                            this.player.getEaglerXServer().logger().error("Caught error from lazy load callback", ex);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void resolvePlayerTexturesKeyed(UUID requester, final BiConsumer<IEaglerPlayerSkin, IEaglerPlayerCape> callback) {
        if (callback == null) {
            throw new NullPointerException("callback");
        }
        IEaglerPlayerSkin val1 = this.skin;
        IEaglerPlayerCape val2 = this.cape;
        if (val1 != null && val2 != null) {
            callback.accept(val1, val2);
        } else {
            new MultiSkinResolver<SkinManagerVanillaOnline<PlayerObject>, PlayerObject>(this, this, val1, val2, requester){

                @Override
                protected void onComplete(SkinManagerVanillaOnline<PlayerObject> mgr, IEaglerPlayerSkin skin, IEaglerPlayerCape cape) {
                    callback.accept(skin, cape);
                }
            };
        }
    }

    @Override
    public void changePlayerSkin(IEaglerPlayerSkin newSkin, boolean notifyOthers) {
        if (newSkin == null) {
            throw new NullPointerException("newSkin");
        }
        this.changePlayerSkin0(newSkin, notifyOthers);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void changePlayerSkin0(IEaglerPlayerSkin newSkin, boolean notifyOthers) {
        KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, IEaglerPlayerSkin> toCall = null;
        SkinManagerVanillaOnline skinManagerVanillaOnline = this;
        synchronized (skinManagerVanillaOnline) {
            IEaglerPlayerSkin oldSkin;
            if (newSkin != null) {
                oldSkin = this.skin;
                if (oldSkin != null && newSkin.equals(oldSkin)) {
                    return;
                }
                this.skin = newSkin;
                toCall = this.waitingSkinCallbacks;
                this.waitingSkinCallbacks = null;
            } else if (this.originalSkin != null) {
                oldSkin = this.skin;
                if (oldSkin != null && this.originalSkin.equals(oldSkin)) {
                    return;
                }
                this.skin = this.originalSkin;
                newSkin = this.originalSkin;
                toCall = this.waitingSkinCallbacks;
                this.waitingSkinCallbacks = null;
            } else {
                if (this.skinURL == null || this.skin == null) {
                    return;
                }
                this.skin = null;
            }
        }
        if (notifyOthers) {
            SkinManagerHelper.notifyOthers(this.player, true, false);
        }
        if (toCall != null) {
            List<Consumer<IEaglerPlayerSkin>> toCallList = toCall.getList();
            int l = toCallList.size();
            for (int i = 0; i < l; ++i) {
                try {
                    toCallList.get(i).accept(newSkin);
                    continue;
                }
                catch (Exception ex) {
                    this.player.getEaglerXServer().logger().error("Caught error from lazy load callback", ex);
                }
            }
        }
    }

    @Override
    public void changePlayerSkin(EnumPresetSkins newSkin, boolean notifyOthers) {
        this.changePlayerSkin(InternUtils.getPresetSkin(newSkin.getId()), notifyOthers);
    }

    @Override
    public void changePlayerCape(IEaglerPlayerCape newCape, boolean notifyOthers) {
        if (newCape == null) {
            throw new NullPointerException("newCape");
        }
        this.changePlayerCape0(newCape, notifyOthers);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void changePlayerCape0(IEaglerPlayerCape newCape, boolean notifyOthers) {
        KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, IEaglerPlayerCape> toCall = null;
        Object object = this.capeLock;
        synchronized (object) {
            IEaglerPlayerCape oldCape;
            if (newCape != null) {
                oldCape = this.cape;
                if (oldCape != null && newCape.equals(oldCape)) {
                    return;
                }
                this.cape = newCape;
                toCall = this.waitingCapeCallbacks;
                this.waitingCapeCallbacks = null;
            } else if (this.originalCape != null) {
                oldCape = this.cape;
                if (oldCape != null && this.originalCape.equals(oldCape)) {
                    return;
                }
                this.cape = this.originalCape;
                newCape = this.originalCape;
                toCall = this.waitingCapeCallbacks;
                this.waitingCapeCallbacks = null;
            } else {
                if (this.capeURL == null || this.cape == null) {
                    return;
                }
                this.cape = null;
            }
        }
        if (notifyOthers) {
            SkinManagerHelper.notifyOthers(this.player, false, true);
        }
        if (toCall != null) {
            List<Consumer<IEaglerPlayerCape>> toCallList = toCall.getList();
            int l = toCallList.size();
            for (int i = 0; i < l; ++i) {
                try {
                    toCallList.get(i).accept(newCape);
                    continue;
                }
                catch (Exception ex) {
                    this.player.getEaglerXServer().logger().error("Caught error from lazy load callback", ex);
                }
            }
        }
    }

    @Override
    public void changePlayerCape(EnumPresetCapes newCape, boolean notifyOthers) {
        this.changePlayerCape(InternUtils.getPresetCape(newCape.getId()), notifyOthers);
    }

    @Override
    public void changePlayerTextures(IEaglerPlayerSkin newSkin, IEaglerPlayerCape newCape, boolean notifyOthers) {
        if (newSkin == null) {
            throw new NullPointerException("newSkin");
        }
        if (newCape == null) {
            throw new NullPointerException("newCape");
        }
        this.changePlayerTextures0(newSkin, newCape, notifyOthers);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void changePlayerTextures0(IEaglerPlayerSkin newSkin, IEaglerPlayerCape newCape, boolean notifyOthers) {
        boolean c = false;
        boolean s = false;
        KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, IEaglerPlayerSkin> toCall1 = null;
        KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, IEaglerPlayerCape> toCall2 = null;
        Object object = this;
        synchronized (object) {
            IEaglerPlayerSkin oldSkin;
            if (newSkin != null) {
                oldSkin = this.skin;
                if (oldSkin == null || !newSkin.equals(oldSkin)) {
                    s = true;
                    this.skin = newSkin;
                    toCall1 = this.waitingSkinCallbacks;
                    this.waitingSkinCallbacks = null;
                }
            } else if (this.originalSkin != null) {
                oldSkin = this.skin;
                if (oldSkin == null || !this.originalSkin.equals(oldSkin)) {
                    s = true;
                    this.skin = this.originalSkin;
                    newSkin = this.originalSkin;
                    toCall1 = this.waitingSkinCallbacks;
                    this.waitingSkinCallbacks = null;
                }
            } else if (this.skinURL != null && this.skin != null) {
                this.skin = null;
                s = true;
            }
        }
        object = this.capeLock;
        synchronized (object) {
            IEaglerPlayerCape oldCape;
            if (newCape != null) {
                oldCape = this.cape;
                if (oldCape == null || !newCape.equals(oldCape)) {
                    c = true;
                    this.cape = newCape;
                    toCall2 = this.waitingCapeCallbacks;
                    this.waitingCapeCallbacks = null;
                }
            } else if (this.originalCape != null) {
                oldCape = this.cape;
                if (oldCape == null || !this.originalCape.equals(oldCape)) {
                    c = true;
                    this.cape = this.originalCape;
                    newCape = this.originalCape;
                    toCall2 = this.waitingCapeCallbacks;
                    this.waitingCapeCallbacks = null;
                }
            } else if (this.capeURL != null && this.cape != null) {
                this.cape = null;
                c = true;
            }
        }
        if (notifyOthers && (s || c)) {
            SkinManagerHelper.notifyOthers(this.player, s, c);
        }
        if (toCall1 != null) {
            List<Consumer<IEaglerPlayerSkin>> toCallList = toCall1.getList();
            for (int i = 0, l = toCallList.size(); i < l; ++i) {
                try {
                    toCallList.get(i).accept(newSkin);
                    continue;
                }
                catch (Exception ex) {
                    this.player.getEaglerXServer().logger().error("Caught error from lazy load callback", ex);
                }
            }
        }
        if (toCall2 != null) {
            List<Consumer<IEaglerPlayerCape>> toCallList2 = toCall2.getList();
            for (int i = 0, l = toCallList2.size(); i < l; ++i) {
                try {
                    toCallList2.get(i).accept(newCape);
                    continue;
                }
                catch (Exception ex) {
                    this.player.getEaglerXServer().logger().error("Caught error from lazy load callback", ex);
                }
            }
        }
    }

    @Override
    public void changePlayerTextures(EnumPresetSkins newSkin, EnumPresetCapes newCape, boolean notifyOthers) {
        this.changePlayerTextures(InternUtils.getPresetSkin(newSkin.getId()), InternUtils.getPresetCape(newCape.getId()), notifyOthers);
    }

    @Override
    public void resetPlayerSkin(boolean notifyOthers) {
        this.changePlayerSkin0(null, notifyOthers);
    }

    @Override
    public void resetPlayerCape(boolean notifyOthers) {
        this.changePlayerCape0(null, notifyOthers);
    }

    @Override
    public void resetPlayerTextures(boolean notifyOthers) {
        this.changePlayerTextures0(null, null, notifyOthers);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void handleSRSkinApply(String value, String signature) {
        TexturesResult textures = GameProfileUtil.extractSkinAndCape(value);
        if (textures != null) {
            String capeUrl;
            KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, IEaglerPlayerSkin> toCall1 = null;
            KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, IEaglerPlayerCape> toCall2 = null;
            boolean s = false;
            boolean c = false;
            String skinUrl = textures.getSkinURL();
            if (skinUrl != null) {
                SkinManagerVanillaOnline skinManagerVanillaOnline = this;
                synchronized (skinManagerVanillaOnline) {
                    this.originalSkin = null;
                    if (this.skinURL == null || !skinUrl.equals(this.skinURL)) {
                        s = true;
                    }
                    this.skin = null;
                    this.skinURL = skinUrl;
                    this.skinModel = textures.getSkinModel();
                    if (this.skinModel == null) {
                        this.skinModel = EnumSkinModel.STEVE;
                    }
                    toCall1 = this.waitingSkinCallbacks;
                    this.waitingSkinCallbacks = null;
                }
            }
            UUID uuid = this.player.getUniqueId();
            IEaglerPlayerSkin newSkin = new PresetSkinPlayer(uuid.getMostSignificantBits(), uuid.getLeastSignificantBits(), (uuid.hashCode() & 1) != 0 ? 1 : 0);
            SkinManagerVanillaOnline skinManagerVanillaOnline = this;
            synchronized (skinManagerVanillaOnline) {
                this.originalSkin = newSkin;
                if (this.skin == null || !newSkin.equals(this.skin)) {
                    s = true;
                }
                this.skin = newSkin;
                this.skinURL = null;
                this.skinModel = null;
                toCall1 = this.waitingSkinCallbacks;
                this.waitingSkinCallbacks = null;
            }
            if (toCall1 != null) {
                List<Consumer<IEaglerPlayerSkin>> toCallList = toCall1.getList();
                int l = toCallList.size();
                for (int i = 0; i < l; ++i) {
                    try {
                        toCallList.get(i).accept((IEaglerPlayerSkin)newSkin);
                        continue;
                    }
                    catch (Exception ex) {
                        this.player.getEaglerXServer().logger().error("Caught error from lazy load callback", ex);
                    }
                }
                toCall1 = null;
            }
            if ((capeUrl = textures.getCapeURL()) != null) {
                Object lockObj = this.capeLock;
                synchronized (lockObj) {
                    this.originalCape = null;
                    if (this.capeURL == null || !capeUrl.equals(this.capeURL)) {
                        c = true;
                    }
                    this.cape = null;
                    this.capeURL = capeUrl;
                    toCall2 = this.waitingCapeCallbacks;
                    this.waitingCapeCallbacks = null;
                }
            }
            UUID uuid2 = this.player.getUniqueId();
            PresetCapePlayer newCape = new PresetCapePlayer(uuid2.getMostSignificantBits(), uuid2.getLeastSignificantBits(), 0);
            Object i = this.capeLock;
            synchronized (i) {
                this.originalCape = newCape;
                if (this.cape == null || !((Object)newCape).equals(this.cape)) {
                    c = true;
                }
                this.cape = newCape;
                this.capeURL = null;
                toCall2 = this.waitingCapeCallbacks;
                this.waitingCapeCallbacks = null;
            }
            if (toCall2 != null) {
                List<Consumer<IEaglerPlayerCape>> toCallList = toCall2.getList();
                int l = toCallList.size();
                for (int i2 = 0; i2 < l; ++i2) {
                    try {
                        toCallList.get(i2).accept(newCape);
                        continue;
                    }
                    catch (Exception ex) {
                        this.player.getEaglerXServer().logger().error("Caught error from lazy load callback", ex);
                    }
                }
                toCall2 = null;
            }
            if (s || c) {
                SkinManagerHelper.notifyOthers(this.player, s, c);
            }
            if (toCall1 != null) {
                toCall1.forEach(this::resolvePlayerSkinKeyed);
            }
            if (toCall2 != null) {
                toCall2.forEach(this::resolvePlayerCapeKeyed);
            }
        }
    }

    public String getEffectiveSkinURLInternal() {
        if (this.skin == null || this.originalSkin == this.skin) {
            return this.skinURL;
        }
        return null;
    }

    public EnumSkinModel getEffectiveSkinModelInternal() {
        return this.skinModel;
    }

    public String getEffectiveCapeURLInternal() {
        if (this.cape == null || this.originalCape == this.cape) {
            return this.capeURL;
        }
        return null;
    }
}

