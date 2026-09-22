/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.voice.protocol;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.EaglerVCPacket;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.client.CPacketVCCapable;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.client.CPacketVCConnect;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.client.CPacketVCConnectPeer;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.client.CPacketVCDescription;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.client.CPacketVCDisconnect;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.client.CPacketVCDisconnectPeer;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.client.CPacketVCICECandidate;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.server.SPacketVCAllowed;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.server.SPacketVCAnnounce;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.server.SPacketVCCapable;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.server.SPacketVCConnectPeer;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.server.SPacketVCDescription;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.server.SPacketVCDisconnectPeer;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.server.SPacketVCICECandidate;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.server.SPacketVCPlayerList;

public enum EaglerVCProtocol {
    INIT(0, EaglerVCProtocol.define_CLIENT_(0, CPacketVCCapable.class), EaglerVCProtocol.define_SERVER_(1, SPacketVCCapable.class)),
    V1(1, EaglerVCProtocol.define_CLIENT_(1, CPacketVCConnect.class), EaglerVCProtocol.define_CLIENT_(2, CPacketVCConnectPeer.class), EaglerVCProtocol.define_CLIENT_(3, CPacketVCDisconnect.class), EaglerVCProtocol.define_CLIENT_(4, CPacketVCDisconnectPeer.class), EaglerVCProtocol.define_CLIENT_(5, CPacketVCDescription.class), EaglerVCProtocol.define_CLIENT_(6, CPacketVCICECandidate.class), EaglerVCProtocol.define_SERVER_(1, SPacketVCAllowed.class), EaglerVCProtocol.define_SERVER_(2, SPacketVCPlayerList.class), EaglerVCProtocol.define_SERVER_(3, SPacketVCAnnounce.class), EaglerVCProtocol.define_SERVER_(4, SPacketVCConnectPeer.class), EaglerVCProtocol.define_SERVER_(5, SPacketVCDisconnectPeer.class), EaglerVCProtocol.define_SERVER_(6, SPacketVCDescription.class), EaglerVCProtocol.define_SERVER_(7, SPacketVCICECandidate.class));

    public static final String CHANNEL_NAME = "EAG|1.8-Voice-RPC";
    public static final String CHANNEL_NAME_MODERN = "eagler:1-8-voice-rpc";
    public static final int CLIENT_TO_SERVER = 0;
    public static final int SERVER_TO_CLIENT = 1;
    public final int vers;
    private final PacketDef[][] idMap = new PacketDef[2][32];
    private final Map<Class<? extends EaglerVCPacket>, PacketDef> classMap = new HashMap<Class<? extends EaglerVCPacket>, PacketDef>();

    private EaglerVCProtocol(int vers, PacketDef ... pkts) {
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

    private static PacketDef define_CLIENT_(int id, Class<? extends EaglerVCPacket> pkt) {
        return new PacketDef(id, 0, pkt);
    }

    private static PacketDef define_SERVER_(int id, Class<? extends EaglerVCPacket> pkt) {
        return new PacketDef(id, 1, pkt);
    }

    public EaglerVCPacket readPacket(DataInput buffer, int dir) throws IOException {
        EaglerVCPacket newPkt;
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
            newPkt = (EaglerVCPacket)pp.ctor.newInstance(new Object[0]);
        }
        catch (IllegalAccessException | IllegalArgumentException | InstantiationException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        newPkt.readPacket(buffer);
        return newPkt;
    }

    public void writePacket(DataOutput buffer, int dir, EaglerVCPacket packet) throws IOException {
        Class<?> clazz = packet.getClass();
        PacketDef def = this.classMap.get(clazz);
        if (def == null || def.dir != dir) {
            throw new IOException("Unknown packet type or wrong direction: " + clazz);
        }
        buffer.writeByte(def.id);
        packet.writePacket(buffer);
    }

    public static EaglerVCProtocol getByID(int id) {
        switch (id) {
            case 0: {
                return INIT;
            }
            case 1: {
                return V1;
            }
        }
        return null;
    }

    private static class PacketDef {
        private final int id;
        private final int dir;
        private final Class<? extends EaglerVCPacket> pkt;
        private final Constructor<? extends EaglerVCPacket> ctor;

        private PacketDef(int id, int dir, Class<? extends EaglerVCPacket> pkt) {
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

