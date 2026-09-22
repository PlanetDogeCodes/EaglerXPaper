/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.buffer.ByteBuf
 */
package net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.server;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.EaglerSupervisorHandler;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.EaglerSupervisorPacket;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.util.CharSeqCompat;

public class SPacketSvDropPlayerPartial
implements EaglerSupervisorPacket {
    public static final int DROP_PLAYER_SKIN = 1;
    public static final int DROP_PLAYER_CAPE = 2;
    public UUID uuid;
    public String serverNotify;
    public int bitmask;

    public SPacketSvDropPlayerPartial() {
    }

    public SPacketSvDropPlayerPartial(UUID uuid, String serverNotify, int bitmask) {
        this.uuid = uuid;
        this.serverNotify = serverNotify;
        this.bitmask = bitmask;
    }

    @Override
    public void readPacket(ByteBuf buffer) {
        this.uuid = new UUID(buffer.readLong(), buffer.readLong());
        int notifyLen = EaglerSupervisorPacket.readVarInt(buffer);
        this.serverNotify = notifyLen > 0 ? CharSeqCompat.readString(buffer, notifyLen, StandardCharsets.US_ASCII) : null;
        this.bitmask = buffer.readUnsignedByte();
    }

    @Override
    public void writePacket(ByteBuf buffer) {
        buffer.writeLong(this.uuid.getMostSignificantBits());
        buffer.writeLong(this.uuid.getLeastSignificantBits());
        if (this.serverNotify != null && this.serverNotify.length() > 0) {
            byte[] asciiBytes = this.serverNotify.getBytes(StandardCharsets.US_ASCII);
            EaglerSupervisorPacket.writeVarInt(buffer, asciiBytes.length);
            buffer.writeBytes(asciiBytes);
        } else {
            buffer.writeByte(0);
        }
        buffer.writeByte(this.bitmask);
    }

    @Override
    public void handlePacket(EaglerSupervisorHandler handler) {
        handler.handleServer(this);
    }
}

