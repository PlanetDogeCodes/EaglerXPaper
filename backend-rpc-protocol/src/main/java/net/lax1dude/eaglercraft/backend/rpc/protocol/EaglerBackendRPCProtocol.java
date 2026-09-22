/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.rpc.protocol;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.EaglerBackendRPCPacket;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCDisabled;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCDisplayWebViewAliasV2;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCDisplayWebViewBlobV2;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCDisplayWebViewURLV2;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCEnabled;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCGetCapeByURLV2;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCGetSkinByURLV2;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCInjectRawBinaryFrameV2;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCNotifBadgeHide;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCNotifBadgeShow;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCNotifIconRegister;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCNotifIconRelease;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCRedirectPlayer;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCRequestPlayerInfo;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCResetPlayerMulti;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCSendRawMessage;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCSendWebViewMessage;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCSetPauseMenuCustom;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCSetPlayerCape;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCSetPlayerCapePresetV2;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCSetPlayerCookie;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCSetPlayerFNAWEn;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCSetPlayerSkin;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCSetPlayerSkinPresetV2;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCSetPlayerTexturesPresetV2;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCSetPlayerTexturesV2;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCSubscribeEvents;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCEnabledFailure;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCEnabledSuccess;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCEnabledSuccessEaglerV2;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCEnabledSuccessVanillaV2;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCEventToggledVoice;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCEventWebViewMessage;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCEventWebViewOpenClose;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeBrandDataV2;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeBytes;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeCookie;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeError;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeIntegerSingleV2;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeIntegerTupleV2;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeNull;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeString;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeUUID;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeVoiceStatus;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeWebViewStatus;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeWebViewStatusV2;

public enum EaglerBackendRPCProtocol {
    INIT(0, EaglerBackendRPCProtocol.define_CLIENT_(0, CPacketRPCEnabled.class), EaglerBackendRPCProtocol.define_SERVER_(1, SPacketRPCEnabledSuccess.class), EaglerBackendRPCProtocol.define_SERVER_(2, SPacketRPCEnabledFailure.class), EaglerBackendRPCProtocol.define_SERVER_(3, SPacketRPCEnabledSuccessVanillaV2.class), EaglerBackendRPCProtocol.define_SERVER_(4, SPacketRPCEnabledSuccessEaglerV2.class)),
    V1(1, EaglerBackendRPCProtocol.define_CLIENT_(3, CPacketRPCDisabled.class), EaglerBackendRPCProtocol.define_CLIENT_(4, CPacketRPCRequestPlayerInfo.class), EaglerBackendRPCProtocol.define_CLIENT_(5, CPacketRPCSubscribeEvents.class), EaglerBackendRPCProtocol.define_CLIENT_(6, CPacketRPCSetPlayerSkin.class), EaglerBackendRPCProtocol.define_CLIENT_(7, CPacketRPCSetPlayerCape.class), EaglerBackendRPCProtocol.define_CLIENT_(8, CPacketRPCSetPlayerCookie.class), EaglerBackendRPCProtocol.define_CLIENT_(9, CPacketRPCSetPlayerFNAWEn.class), EaglerBackendRPCProtocol.define_CLIENT_(10, CPacketRPCSetPauseMenuCustom.class), EaglerBackendRPCProtocol.define_CLIENT_(11, CPacketRPCRedirectPlayer.class), EaglerBackendRPCProtocol.define_CLIENT_(12, CPacketRPCResetPlayerMulti.class), EaglerBackendRPCProtocol.define_SERVER_(13, SPacketRPCResponseTypeNull.class), EaglerBackendRPCProtocol.define_SERVER_(14, SPacketRPCResponseTypeBytes.class), EaglerBackendRPCProtocol.define_SERVER_(15, SPacketRPCResponseTypeString.class), EaglerBackendRPCProtocol.define_SERVER_(16, SPacketRPCResponseTypeUUID.class), EaglerBackendRPCProtocol.define_SERVER_(17, SPacketRPCResponseTypeCookie.class), EaglerBackendRPCProtocol.define_SERVER_(18, SPacketRPCResponseTypeVoiceStatus.class), EaglerBackendRPCProtocol.define_SERVER_(19, SPacketRPCResponseTypeWebViewStatus.class), EaglerBackendRPCProtocol.define_SERVER_(20, SPacketRPCResponseTypeError.class), EaglerBackendRPCProtocol.define_CLIENT_(21, CPacketRPCSendWebViewMessage.class), EaglerBackendRPCProtocol.define_SERVER_(22, SPacketRPCEventWebViewOpenClose.class), EaglerBackendRPCProtocol.define_SERVER_(23, SPacketRPCEventWebViewMessage.class), EaglerBackendRPCProtocol.define_SERVER_(24, SPacketRPCEventToggledVoice.class), EaglerBackendRPCProtocol.define_CLIENT_(25, CPacketRPCNotifIconRegister.class), EaglerBackendRPCProtocol.define_CLIENT_(26, CPacketRPCNotifIconRelease.class), EaglerBackendRPCProtocol.define_CLIENT_(27, CPacketRPCNotifBadgeShow.class), EaglerBackendRPCProtocol.define_CLIENT_(28, CPacketRPCNotifBadgeHide.class), EaglerBackendRPCProtocol.define_CLIENT_(29, CPacketRPCSendRawMessage.class)),
    V2(2, EaglerBackendRPCProtocol.define_CLIENT_(1, CPacketRPCDisabled.class), EaglerBackendRPCProtocol.define_CLIENT_(2, CPacketRPCRequestPlayerInfo.class), EaglerBackendRPCProtocol.define_CLIENT_(3, CPacketRPCSubscribeEvents.class), EaglerBackendRPCProtocol.define_CLIENT_(4, CPacketRPCSetPlayerSkin.class), EaglerBackendRPCProtocol.define_CLIENT_(5, CPacketRPCSetPlayerSkinPresetV2.class), EaglerBackendRPCProtocol.define_CLIENT_(6, CPacketRPCSetPlayerCape.class), EaglerBackendRPCProtocol.define_CLIENT_(7, CPacketRPCSetPlayerCapePresetV2.class), EaglerBackendRPCProtocol.define_CLIENT_(8, CPacketRPCSetPlayerTexturesV2.class), EaglerBackendRPCProtocol.define_CLIENT_(9, CPacketRPCSetPlayerTexturesPresetV2.class), EaglerBackendRPCProtocol.define_CLIENT_(10, CPacketRPCSetPlayerCookie.class), EaglerBackendRPCProtocol.define_CLIENT_(11, CPacketRPCSetPlayerFNAWEn.class), EaglerBackendRPCProtocol.define_CLIENT_(12, CPacketRPCSetPauseMenuCustom.class), EaglerBackendRPCProtocol.define_CLIENT_(13, CPacketRPCRedirectPlayer.class), EaglerBackendRPCProtocol.define_CLIENT_(14, CPacketRPCResetPlayerMulti.class), EaglerBackendRPCProtocol.define_CLIENT_(15, CPacketRPCSendWebViewMessage.class), EaglerBackendRPCProtocol.define_CLIENT_(16, CPacketRPCNotifIconRegister.class), EaglerBackendRPCProtocol.define_CLIENT_(17, CPacketRPCNotifIconRelease.class), EaglerBackendRPCProtocol.define_CLIENT_(18, CPacketRPCNotifBadgeShow.class), EaglerBackendRPCProtocol.define_CLIENT_(19, CPacketRPCNotifBadgeHide.class), EaglerBackendRPCProtocol.define_CLIENT_(20, CPacketRPCSendRawMessage.class), EaglerBackendRPCProtocol.define_CLIENT_(21, CPacketRPCInjectRawBinaryFrameV2.class), EaglerBackendRPCProtocol.define_CLIENT_(22, CPacketRPCDisplayWebViewURLV2.class), EaglerBackendRPCProtocol.define_CLIENT_(23, CPacketRPCDisplayWebViewBlobV2.class), EaglerBackendRPCProtocol.define_CLIENT_(24, CPacketRPCDisplayWebViewAliasV2.class), EaglerBackendRPCProtocol.define_CLIENT_(25, CPacketRPCGetSkinByURLV2.class), EaglerBackendRPCProtocol.define_CLIENT_(26, CPacketRPCGetCapeByURLV2.class), EaglerBackendRPCProtocol.define_SERVER_(1, SPacketRPCResponseTypeNull.class), EaglerBackendRPCProtocol.define_SERVER_(2, SPacketRPCResponseTypeBytes.class), EaglerBackendRPCProtocol.define_SERVER_(3, SPacketRPCResponseTypeIntegerSingleV2.class), EaglerBackendRPCProtocol.define_SERVER_(4, SPacketRPCResponseTypeIntegerTupleV2.class), EaglerBackendRPCProtocol.define_SERVER_(5, SPacketRPCResponseTypeString.class), EaglerBackendRPCProtocol.define_SERVER_(6, SPacketRPCResponseTypeBrandDataV2.class), EaglerBackendRPCProtocol.define_SERVER_(7, SPacketRPCResponseTypeUUID.class), EaglerBackendRPCProtocol.define_SERVER_(8, SPacketRPCResponseTypeCookie.class), EaglerBackendRPCProtocol.define_SERVER_(9, SPacketRPCResponseTypeVoiceStatus.class), EaglerBackendRPCProtocol.define_SERVER_(10, SPacketRPCResponseTypeWebViewStatusV2.class), EaglerBackendRPCProtocol.define_SERVER_(11, SPacketRPCResponseTypeError.class), EaglerBackendRPCProtocol.define_SERVER_(12, SPacketRPCEventWebViewOpenClose.class), EaglerBackendRPCProtocol.define_SERVER_(13, SPacketRPCEventWebViewMessage.class), EaglerBackendRPCProtocol.define_SERVER_(14, SPacketRPCEventToggledVoice.class));

    public static final String CHANNEL_NAME = "EAG|1.8-RPC";
    public static final String CHANNEL_NAME_READY = "EAG|1.8-Ready";
    public static final String CHANNEL_NAME_MODERN = "eagler:1-8-rpc";
    public static final String CHANNEL_NAME_READY_MODERN = "eagler:1-8-ready";
    public static final int CLIENT_TO_SERVER = 0;
    public static final int SERVER_TO_CLIENT = 1;
    public final int vers;
    private final PacketDef[][] idMap = new PacketDef[2][32];
    private final Map<Class<? extends EaglerBackendRPCPacket>, PacketDef> classMap = new HashMap<Class<? extends EaglerBackendRPCPacket>, PacketDef>();

    private EaglerBackendRPCProtocol(int vers, PacketDef ... pkts) {
        this.vers = vers;
        for (int i = 0; i < pkts.length; ++i) {
            PacketDef def = pkts[i];
            if (this.idMap[def.dir][def.id] != null) {
                throw new IllegalArgumentException("Packet ID " + def.id + " registered twice!");
            }
            this.idMap[((PacketDef)def).dir][((PacketDef)def).id] = def;
            if (this.classMap.put(def.pkt, def) == null) continue;
            throw new IllegalArgumentException("Packet class " + def.pkt.getSimpleName() + " registered twice!");
        }
    }

    private static PacketDef define_CLIENT_(int id, Class<? extends EaglerBackendRPCPacket> pkt) {
        return new PacketDef(id, 0, pkt);
    }

    private static PacketDef define_SERVER_(int id, Class<? extends EaglerBackendRPCPacket> pkt) {
        return new PacketDef(id, 1, pkt);
    }

    public EaglerBackendRPCPacket readPacket(DataInput buffer, int dir) throws IOException {
        EaglerBackendRPCPacket newPkt;
        PacketDef[] defs;
        int pktId = buffer.readUnsignedByte();
        if (pktId >= (defs = this.idMap[dir]).length) {
            throw new IOException("Packet ID is out of range: 0x" + Integer.toHexString(pktId));
        }
        PacketDef pp = defs[pktId];
        if (pp == null) {
            throw new IOException("Unknown packet ID: 0x" + Integer.toHexString(pktId));
        }
        try {
            newPkt = (EaglerBackendRPCPacket)pp.ctor.newInstance(new Object[0]);
        }
        catch (IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        newPkt.readPacket(buffer);
        return newPkt;
    }

    public void writePacket(DataOutput buffer, int dir, EaglerBackendRPCPacket packet) throws IOException {
        Class<?> clazz = packet.getClass();
        PacketDef def = this.classMap.get(clazz);
        if (def == null || def.dir != dir) {
            throw new IOException("Unknown packet type or wrong direction: " + clazz);
        }
        buffer.writeByte(def.id);
        packet.writePacket(buffer);
    }

    public static EaglerBackendRPCProtocol getByID(int id) {
        switch (id) {
            case 0: {
                return INIT;
            }
            case 1: {
                return V1;
            }
            case 2: {
                return V2;
            }
        }
        return null;
    }

    private static class PacketDef {
        private final int id;
        private final int dir;
        private final Class<? extends EaglerBackendRPCPacket> pkt;
        private final Constructor<? extends EaglerBackendRPCPacket> ctor;

        private PacketDef(int id, int dir, Class<? extends EaglerBackendRPCPacket> pkt) {
            this.id = id;
            this.dir = dir;
            this.pkt = pkt;
            try {
                this.ctor = pkt.getConstructor(new Class[0]);
            }
            catch (NoSuchMethodException | SecurityException e) {
                throw new RuntimeException("Packet does not have a default constructor: " + pkt.getName(), e);
            }
        }
    }
}

