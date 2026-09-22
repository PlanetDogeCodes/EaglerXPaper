/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.rpc;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import net.lax1dude.eaglercraft.backend.rpc.protocol.EaglerBackendRPCProtocol;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.EaglerBackendRPCHandler;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.EaglerBackendRPCPacket;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.WrongRPCPacketException;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCNotifBadgeShow;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCNotifIconRegister;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCSetPauseMenuCustom;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeBytes;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeIntegerSingleV2;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeIntegerTupleV2;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeNull;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeString;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeUUID;
import net.lax1dude.eaglercraft.backend.server.api.EnumWebSocketHeader;
import net.lax1dude.eaglercraft.backend.server.api.SHA1Sum;
import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerCape;
import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerSkin;
import net.lax1dude.eaglercraft.backend.server.api.skins.ISkinManagerBase;
import net.lax1dude.eaglercraft.backend.server.api.voice.EnumVoiceState;
import net.lax1dude.eaglercraft.backend.server.api.webview.EnumWebViewPerms;
import net.lax1dude.eaglercraft.backend.server.base.DataSerializationContext;
import net.lax1dude.eaglercraft.backend.server.base.rpc.BasePlayerRPCManager;
import net.lax1dude.eaglercraft.backend.server.base.rpc.SerializationContext;
import net.lax1dude.eaglercraft.backend.server.base.rpc.ServerV1RPCProtocolHandler;
import net.lax1dude.eaglercraft.backend.server.base.rpc.ServerV2RPCProtocolHandler;
import net.lax1dude.eaglercraft.backend.server.base.rpc.TextureDataHelper;
import net.lax1dude.eaglercraft.backend.server.base.skins.type.InternUtils;
import net.lax1dude.eaglercraft.backend.server.base.skins.type.MissingCape;
import net.lax1dude.eaglercraft.backend.server.base.skins.type.MissingSkin;

public abstract class BasePlayerRPCContext<PlayerObject>
extends SerializationContext {
    private final EaglerBackendRPCHandler packetHandler;

    BasePlayerRPCContext(EaglerBackendRPCProtocol protocol, DataSerializationContext dataCtx) {
        super(protocol, dataCtx);
        switch (protocol) {
            case V1: {
                this.packetHandler = new ServerV1RPCProtocolHandler(this);
                break;
            }
            case V2: {
                this.packetHandler = new ServerV2RPCProtocolHandler(this);
                break;
            }
            default: {
                throw new IllegalStateException();
            }
        }
    }

    protected abstract BasePlayerRPCManager<PlayerObject> manager();

    EaglerBackendRPCHandler packetHandler() {
        return this.packetHandler;
    }

    protected final void sendRPCPacket(EaglerBackendRPCPacket packet) {
        this.manager().sendRPCPacket(packet);
    }

    protected final RuntimeException notEaglerPlayer() {
        return new WrongRPCPacketException("Unexpected RPC operation type for non-eagler player");
    }

    void handleRequestRealUUID(int requestID) {
        this.sendRPCPacket(new SPacketRPCResponseTypeUUID(requestID, this.manager().getPlayer().getUniqueId()));
    }

    void handleRequestRealIP(int requestID) {
        throw this.notEaglerPlayer();
    }

    void handleRequestHeader(int requestID, EnumWebSocketHeader header) {
        throw this.notEaglerPlayer();
    }

    void handleRequestPath(int requestID) {
        throw this.notEaglerPlayer();
    }

    void handleRequestSkinData(int requestID) {
        ISkinManagerBase<PlayerObject> skinMgr = this.manager().getPlayer().getSkinManager();
        IEaglerPlayerSkin skin = skinMgr.getPlayerSkinIfLoaded();
        if (skin != null) {
            this.completeRequestSkinData(requestID, skin);
        } else {
            skinMgr.resolvePlayerSkin(resolvedSkin -> this.completeRequestSkinData(requestID, (IEaglerPlayerSkin)resolvedSkin));
        }
    }

    private void completeRequestSkinData(int requestID, IEaglerPlayerSkin skin) {
        boolean legacy;
        boolean bl = legacy = this.getProtocol() == EaglerBackendRPCProtocol.V1;
        if (!legacy) {
            if (!skin.isSuccess()) {
                this.sendRPCPacket(new SPacketRPCResponseTypeIntegerSingleV2(requestID, -1));
                return;
            }
            if (skin.isSkinPreset()) {
                int id = skin.getPresetSkinId();
                if (id == -1) {
                    id = 0;
                }
                this.sendRPCPacket(new SPacketRPCResponseTypeIntegerSingleV2(requestID, id));
                return;
            }
        }
        this.sendRPCPacket(new SPacketRPCResponseTypeBytes(requestID, TextureDataHelper.encodeSkinData(skin, legacy)));
    }

    void handleRequestCapeData(int requestID) {
        ISkinManagerBase<PlayerObject> skinMgr = this.manager().getPlayer().getSkinManager();
        IEaglerPlayerCape cape = skinMgr.getPlayerCapeIfLoaded();
        if (cape != null) {
            this.completeRequestCapeData(requestID, cape);
        } else {
            skinMgr.resolvePlayerCape(resolvedCape -> this.completeRequestCapeData(requestID, (IEaglerPlayerCape)resolvedCape));
        }
    }

    private void completeRequestCapeData(int requestID, IEaglerPlayerCape cape) {
        if (this.getProtocol() != EaglerBackendRPCProtocol.V1) {
            if (!cape.isSuccess()) {
                this.sendRPCPacket(new SPacketRPCResponseTypeIntegerSingleV2(requestID, -1));
                return;
            }
            if (cape.isCapePreset()) {
                int id = cape.getPresetCapeId();
                if (id == -1) {
                    id = 0;
                }
                this.sendRPCPacket(new SPacketRPCResponseTypeIntegerSingleV2(requestID, id));
                return;
            }
        }
        this.sendRPCPacket(new SPacketRPCResponseTypeBytes(requestID, TextureDataHelper.encodeCapeData(cape)));
    }

    void handleRequestCookie(int requestID) {
        throw this.notEaglerPlayer();
    }

    void handleRequestBrandOld(int requestID) {
        throw this.notEaglerPlayer();
    }

    void handleRequestVersionOld(int requestID) {
        throw this.notEaglerPlayer();
    }

    void handleRequestBrandVersionOld(int requestID) {
        throw this.notEaglerPlayer();
    }

    void handleRequestBrandUUID(int requestID) {
        this.sendRPCPacket(new SPacketRPCResponseTypeUUID(requestID, this.manager().getPlayer().getEaglerBrandUUID()));
    }

    void handleRequestVoiceStatus(int requestID) {
        throw this.notEaglerPlayer();
    }

    void handleRequestWebViewStatus(int requestID) {
        throw this.notEaglerPlayer();
    }

    void handleRequestTextureData(int requestID) {
        ISkinManagerBase<PlayerObject> skinMgr = this.manager().getPlayer().getSkinManager();
        IEaglerPlayerSkin skin = skinMgr.getPlayerSkinIfLoaded();
        IEaglerPlayerCape cape = skinMgr.getPlayerCapeIfLoaded();
        if (skin != null && cape != null) {
            this.completeRequestTextureData(requestID, skin, cape);
        } else {
            skinMgr.resolvePlayerTextures((resolvedSkin, resolvedCape) -> this.completeRequestTextureData(requestID, (IEaglerPlayerSkin)resolvedSkin, (IEaglerPlayerCape)resolvedCape));
        }
    }

    private void completeRequestTextureData(int requestID, IEaglerPlayerSkin skin, IEaglerPlayerCape cape) {
        boolean b;
        boolean a = !skin.isSuccess();
        boolean bl = b = !cape.isSuccess();
        if ((a || skin.isSkinPreset()) && (b || cape.isCapePreset())) {
            int i2;
            int i1;
            if (a) {
                i1 = -1;
            } else {
                i1 = skin.getPresetSkinId();
                if (i1 == -1) {
                    i1 = 0;
                }
            }
            if (b) {
                i2 = -1;
            } else {
                i2 = cape.getPresetCapeId();
                if (i2 == -1) {
                    i2 = 0;
                }
            }
            this.sendRPCPacket(new SPacketRPCResponseTypeIntegerTupleV2(requestID, i1, i2));
        } else {
            this.sendRPCPacket(new SPacketRPCResponseTypeBytes(requestID, TextureDataHelper.encodeTexturesData(skin, cape)));
        }
    }

    void handleRequestBrandData(int requestID) {
        throw this.notEaglerPlayer();
    }

    void handleRequestMinecraftBrand(int requestID) {
        String brand = this.manager().getPlayer().getMinecraftBrand();
        if (brand != null) {
            this.sendRPCPacket(new SPacketRPCResponseTypeString(requestID, brand));
        } else {
            this.sendRPCPacket(new SPacketRPCResponseTypeNull(requestID));
        }
    }

    void handleRequestAuthUsername(int requestID) {
        throw this.notEaglerPlayer();
    }

    void handleRequestWebViewStatusV2(int requestID) {
        throw this.notEaglerPlayer();
    }

    void handleSetSubscribeWebViewOpenClose(boolean enable) {
        throw this.notEaglerPlayer();
    }

    void fireWebViewOpenClose(boolean open, String channel) {
    }

    void handleSetSubscribeWebViewMessage(boolean enable) {
        throw this.notEaglerPlayer();
    }

    void fireWebViewMessage(String channel, boolean binary, byte[] data) {
    }

    void handleSetSubscribeToggleVoice(boolean enable) {
        throw this.notEaglerPlayer();
    }

    void fireToggleVoice(EnumVoiceState oldVoiceState, EnumVoiceState newVoiceState) {
    }

    void handleSetPlayerSkin(byte[] skinPacket, boolean notifyOthers) {
        IEaglerPlayerSkin skin = TextureDataHelper.decodeSkinData(skinPacket, this.getProtocol() == EaglerBackendRPCProtocol.V1);
        if (skin == null) {
            throw new WrongRPCPacketException("Invalid skin texture data recieved");
        }
        this.manager().getPlayer().getSkinManager().changePlayerSkin(skin, notifyOthers);
    }

    void handleSetPlayerSkinPreset(int presetSkinId, boolean notifyOthers) {
        this.manager().getPlayer().getSkinManager().changePlayerSkin(presetSkinId != -1 ? InternUtils.getPresetSkin(presetSkinId) : MissingSkin.MISSING_SKIN, notifyOthers);
    }

    void handleSetPlayerCape(byte[] capePacket, boolean notifyOthers) {
        IEaglerPlayerCape cape = TextureDataHelper.decodeCapeData(capePacket, this.getProtocol() == EaglerBackendRPCProtocol.V1);
        if (cape == null) {
            throw new WrongRPCPacketException("Invalid cape texture data recieved");
        }
        this.manager().getPlayer().getSkinManager().changePlayerCape(cape, notifyOthers);
    }

    void handleSetPlayerCapePreset(int presetCapeId, boolean notifyOthers) {
        this.manager().getPlayer().getSkinManager().changePlayerCape(presetCapeId != -1 ? InternUtils.getPresetCape(presetCapeId) : MissingCape.MISSING_CAPE, notifyOthers);
    }

    void handleSetPlayerTextures(byte[] texturesPacket, boolean notifyOthers) {
        IEaglerPlayerSkin skin = TextureDataHelper.decodeTexturesSkinData(texturesPacket);
        if (skin == null) {
            throw new WrongRPCPacketException("Invalid skin texture data recieved");
        }
        IEaglerPlayerCape cape = TextureDataHelper.decodeTexturesCapeData(texturesPacket, skin);
        if (cape == null) {
            throw new WrongRPCPacketException("Invalid cape texture data recieved");
        }
        this.manager().getPlayer().getSkinManager().changePlayerTextures(skin, cape, notifyOthers);
    }

    void handleSetPlayerTexturesPreset(int presetSkinId, int presetCapeId, boolean notifyOthers) {
        this.manager().getPlayer().getSkinManager().changePlayerTextures(presetSkinId != -1 ? InternUtils.getPresetSkin(presetSkinId) : MissingSkin.MISSING_SKIN, presetCapeId != -1 ? InternUtils.getPresetCape(presetCapeId) : MissingCape.MISSING_CAPE, notifyOthers);
    }

    void handleSetPlayerCookie(byte[] cookieData, long expiresSec, boolean saveToDisk, boolean revokeQuerySupported) {
        throw this.notEaglerPlayer();
    }

    void handleSetPlayerFNAWEn(boolean enable, boolean force) {
        throw this.notEaglerPlayer();
    }

    void handleRedirectPlayer(String redirectURI) {
        throw this.notEaglerPlayer();
    }

    void handleResetPlayerMulti(boolean resetSkin, boolean resetCape, boolean resetFNAWForce, boolean notifyOthers) {
        if (resetSkin || resetCape) {
            ISkinManagerBase<PlayerObject> skinMgr = this.manager().getPlayer().getSkinManager();
            if (resetSkin && resetCape) {
                skinMgr.resetPlayerTextures(notifyOthers);
            } else if (resetSkin) {
                skinMgr.resetPlayerSkin(notifyOthers);
            } else {
                skinMgr.resetPlayerCape(notifyOthers);
            }
        }
    }

    void handleSendWebViewMessage(String channelName, int messageType, byte[] messageContent) {
        throw this.notEaglerPlayer();
    }

    void handleSetPauseMenuCustom(CPacketRPCSetPauseMenuCustom packet) {
        throw this.notEaglerPlayer();
    }

    void handleNotifIconRegister(Collection<CPacketRPCNotifIconRegister.RegisterIcon> notifIcons) {
        throw this.notEaglerPlayer();
    }

    void handleNotifIconRelease(Collection<UUID> icons) {
        throw this.notEaglerPlayer();
    }

    void handleNotifBadgeShow(CPacketRPCNotifBadgeShow packet) {
        throw this.notEaglerPlayer();
    }

    void handleNotifBadgeHide(UUID badge) {
        throw this.notEaglerPlayer();
    }

    void handleSendRawMessage(String channel, byte[] data) {
        this.manager().getPlayer().getPlatformPlayer().sendDataClient(channel, data);
    }

    void handleInjectRawBinaryFrame(byte[] data) {
        throw this.notEaglerPlayer();
    }

    void handleDisplayWebViewURL(String title, String url, Set<EnumWebViewPerms> perms) {
        throw this.notEaglerPlayer();
    }

    void handleDisplayWebViewBlob(String title, SHA1Sum hash, Set<EnumWebViewPerms> perms) {
        throw this.notEaglerPlayer();
    }

    void handleDisplayWebViewAlias(String title, String name, Set<EnumWebViewPerms> perms) {
        throw this.notEaglerPlayer();
    }

    void handleRequestGetSkinByURL(int requestID, String url) {
        this.manager().getPlayer().getServerAPI().getSkinService().loadCacheSkinFromURL(url, res -> this.completeRequestSkinData(requestID, (IEaglerPlayerSkin)res));
    }

    void handleRequestGetCapeByURL(int requestID, String url) {
        this.manager().getPlayer().getServerAPI().getSkinService().loadCacheCapeFromURL(url, res -> this.completeRequestCapeData(requestID, (IEaglerPlayerCape)res));
    }

    void handleDisabled() {
        this.manager().handleDisabled();
    }
}

