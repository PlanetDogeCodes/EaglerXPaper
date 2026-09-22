/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.supervisor;

import java.util.UUID;
import net.lax1dude.eaglercraft.backend.server.api.IBasePlayer;
import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerCape;
import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerSkin;
import net.lax1dude.eaglercraft.backend.server.api.skins.ISkinManagerBase;
import net.lax1dude.eaglercraft.backend.server.base.BasePlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.skins.SkinManagerVanillaOnline;
import net.lax1dude.eaglercraft.backend.server.base.skins.type.UnsafeUtil;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.SupervisorConnection;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.SupervisorService;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.client.CPacketSvOtherCapeCustom;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.client.CPacketSvOtherCapePreset;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.client.CPacketSvOtherCapeURL;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.client.CPacketSvOtherSkinCustom;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.client.CPacketSvOtherSkinPreset;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.client.CPacketSvOtherSkinURL;

public class SupervisorLookupHandler<PlayerObject> {
    private final SupervisorService<PlayerObject> service;
    private final SupervisorConnection connection;

    SupervisorLookupHandler(SupervisorService<PlayerObject> service, SupervisorConnection connection) {
        this.service = service;
        this.connection = connection;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    void handleSupervisorSkinLookup(UUID uuid) {
        IBasePlayer player = this.service.getEaglerXServer().getPlayerByUUID(uuid);
        if (player != null) {
            ISkinManagerBase skinMgr = ((BasePlayerInstance)player).getSkinManager();
            if (skinMgr.isEaglerPlayer()) {
                this.sendSkinResponse(uuid, skinMgr.getPlayerSkinIfLoaded());
                return;
            }
            if (skinMgr instanceof SkinManagerVanillaOnline) {
                String url;
                SkinManagerVanillaOnline vanillaOnlineMgr = (SkinManagerVanillaOnline)skinMgr;
                int mdl = 0;
                IEaglerPlayerSkin skin = null;
                ISkinManagerBase iSkinManagerBase = skinMgr;
                synchronized (iSkinManagerBase) {
                    url = vanillaOnlineMgr.getEffectiveSkinURLInternal();
                    if (url == null) {
                        skin = skinMgr.getPlayerSkinIfLoaded();
                    } else {
                        mdl = vanillaOnlineMgr.getEffectiveSkinModelInternal().getId();
                    }
                }
                if (url != null) {
                    this.connection.sendSupervisorPacket(new CPacketSvOtherSkinURL(uuid, mdl, url));
                    return;
                } else {
                    if (skin == null) throw new IllegalStateException("SkinManagerVanillaOnline skin is in a bad state for: " + uuid + " (" + ((BasePlayerInstance)player).getUsername() + ")");
                    this.sendSkinResponse(uuid, skin);
                }
                return;
            }
            IEaglerPlayerSkin skin = skinMgr.getPlayerSkinIfLoaded();
            if (skin != null) {
                this.sendSkinResponse(uuid, skin);
                return;
            } else {
                skinMgr.resolvePlayerSkin(skin2 -> this.sendSkinResponse(uuid, (IEaglerPlayerSkin)skin2));
            }
            return;
        }
        this.service.logger().warn("Received skin lookup request from supervisor for unknown player: " + uuid);
        this.connection.sendSupervisorPacket(new CPacketSvOtherSkinPreset(uuid, (uuid.hashCode() & 1) != 0 ? 1 : 0));
    }

    private void sendSkinResponse(UUID uuid, IEaglerPlayerSkin skin) {
        if (skin.isSkinPreset()) {
            this.connection.sendSupervisorPacket(new CPacketSvOtherSkinPreset(uuid, skin.getPresetSkinId()));
        } else {
            this.connection.sendSupervisorPacket(new CPacketSvOtherSkinCustom(uuid, skin.getCustomSkinRawModelId(), UnsafeUtil.unsafeGetPixelsDirect(skin)));
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    void handleSupervisorCapeLookup(UUID uuid) {
        IBasePlayer player = this.service.getEaglerXServer().getPlayerByUUID(uuid);
        if (player != null) {
            ISkinManagerBase skinMgr = ((BasePlayerInstance)player).getSkinManager();
            if (skinMgr.isEaglerPlayer()) {
                this.sendCapeResponse(uuid, skinMgr.getPlayerCapeIfLoaded());
                return;
            }
            if (skinMgr instanceof SkinManagerVanillaOnline) {
                String url;
                SkinManagerVanillaOnline vanillaOnlineMgr = (SkinManagerVanillaOnline)skinMgr;
                IEaglerPlayerCape cape = null;
                Object object = vanillaOnlineMgr.capeLock;
                synchronized (object) {
                    url = vanillaOnlineMgr.getEffectiveCapeURLInternal();
                    if (url == null) {
                        cape = skinMgr.getPlayerCapeIfLoaded();
                    }
                }
                if (url != null) {
                    this.connection.sendSupervisorPacket(new CPacketSvOtherCapeURL(uuid, url));
                    return;
                } else {
                    if (cape == null) throw new IllegalStateException("SkinManagerVanillaOnline cape is in a bad state for: " + uuid + " (" + ((BasePlayerInstance)player).getUsername() + ")");
                    this.sendCapeResponse(uuid, cape);
                }
                return;
            }
            IEaglerPlayerCape cape = skinMgr.getPlayerCapeIfLoaded();
            if (cape != null) {
                this.sendCapeResponse(uuid, cape);
                return;
            } else {
                skinMgr.resolvePlayerCape(cape2 -> this.sendCapeResponse(uuid, (IEaglerPlayerCape)cape2));
            }
            return;
        }
        this.service.logger().warn("Received skin lookup request from supervisor for unknown player: " + uuid);
        this.connection.sendSupervisorPacket(new CPacketSvOtherCapePreset(uuid, (uuid.hashCode() & 1) != 0 ? 1 : 0));
    }

    private void sendCapeResponse(UUID uuid, IEaglerPlayerCape cape) {
        if (cape.isCapePreset()) {
            this.connection.sendSupervisorPacket(new CPacketSvOtherCapePreset(uuid, cape.getPresetCapeId()));
        } else {
            this.connection.sendSupervisorPacket(new CPacketSvOtherCapeCustom(uuid, UnsafeUtil.unsafeGetPixelsDirect(cape)));
        }
    }
}

