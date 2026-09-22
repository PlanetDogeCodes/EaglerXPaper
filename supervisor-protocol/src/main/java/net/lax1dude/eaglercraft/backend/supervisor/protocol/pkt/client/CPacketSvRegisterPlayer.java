/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.buffer.ByteBuf
 */
package net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.client;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.EaglerSupervisorHandler;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.EaglerSupervisorPacket;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.util.CharSeqCompat;

public class CPacketSvRegisterPlayer
implements EaglerSupervisorPacket {
    public UUID playerUUID;
    public UUID brandUUID;
    public int gameProtocol;
    public int eaglerProtocol;
    public String username;

    public CPacketSvRegisterPlayer() {
    }

    public CPacketSvRegisterPlayer(UUID playerUUID, UUID brandUUID, int gameProtocol, int eaglerProtocol, String username) {
        this.playerUUID = playerUUID;
        this.brandUUID = brandUUID;
        this.gameProtocol = gameProtocol;
        this.eaglerProtocol = eaglerProtocol;
        this.username = username;
    }

    @Override
    public void readPacket(ByteBuf buffer) {
        this.playerUUID = new UUID(buffer.readLong(), buffer.readLong());
        this.brandUUID = new UUID(buffer.readLong(), buffer.readLong());
        this.gameProtocol = EaglerSupervisorPacket.readVarInt(buffer);
        this.eaglerProtocol = EaglerSupervisorPacket.readVarInt(buffer);
        short usernameLen = buffer.readUnsignedByte();
        this.username = CharSeqCompat.readString(buffer, usernameLen, StandardCharsets.US_ASCII);
    }

    @Override
    public void writePacket(ByteBuf buffer) {
        buffer.writeLong(this.playerUUID.getMostSignificantBits());
        buffer.writeLong(this.playerUUID.getLeastSignificantBits());
        buffer.writeLong(this.brandUUID.getMostSignificantBits());
        buffer.writeLong(this.brandUUID.getLeastSignificantBits());
        EaglerSupervisorPacket.writeVarInt(buffer, this.gameProtocol);
        EaglerSupervisorPacket.writeVarInt(buffer, this.eaglerProtocol);
        byte[] usernameBytes = this.username.getBytes(StandardCharsets.US_ASCII);
        int len = usernameBytes.length;
        if (len > 16) {
            throw new UnsupportedOperationException("Username is longer than 16 bytes");
        }
        buffer.writeByte(len);
        buffer.writeBytes(usernameBytes);
    }

    @Override
    public void handlePacket(EaglerSupervisorHandler handler) {
        handler.handleClient(this);
    }
}

