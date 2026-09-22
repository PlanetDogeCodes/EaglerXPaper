/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.buffer.ByteBuf
 *  io.netty.util.ReferenceCounted
 */
package net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.client;

import io.netty.buffer.ByteBuf;
import io.netty.util.ReferenceCounted;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.EaglerSupervisorHandler;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.EaglerSupervisorPacket;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.util.CharSeqCompat;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.util.IInjectedPayload;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.util.IRefCountedHolder;

public class CPacketSvRPCExecutePlayerName
implements EaglerSupervisorPacket,
IRefCountedHolder {
    public UUID requestUUID;
    public int timeout;
    public String playerName;
    public int nameLength;
    public ByteBuf payload;
    private IInjectedPayload injected;

    public CPacketSvRPCExecutePlayerName() {
    }

    public CPacketSvRPCExecutePlayerName(UUID requestUUID, int timeout, String playerName, int nameLength, ByteBuf payload) {
        this.requestUUID = requestUUID;
        this.timeout = timeout;
        this.playerName = playerName;
        this.nameLength = nameLength;
        this.payload = payload;
    }

    public CPacketSvRPCExecutePlayerName(UUID requestUUID, int timeout, String playerName, IInjectedPayload injected) {
        this.requestUUID = requestUUID;
        this.timeout = timeout;
        this.playerName = playerName;
        this.injected = injected;
    }

    @Override
    public void readPacket(ByteBuf buffer) {
        this.timeout = EaglerSupervisorPacket.readVarInt(buffer);
        this.requestUUID = this.timeout > 0 ? new UUID(buffer.readLong(), buffer.readLong()) : null;
        short len = buffer.readUnsignedByte();
        this.playerName = CharSeqCompat.readString(buffer, len, StandardCharsets.US_ASCII);
        this.nameLength = buffer.readUnsignedByte();
        this.payload = buffer.readSlice(buffer.readUnsignedMedium()).retain();
    }

    @Override
    public void writePacket(ByteBuf buffer) {
        byte[] nameBytes;
        int len;
        EaglerSupervisorPacket.writeVarInt(buffer, this.timeout);
        if (this.timeout > 0) {
            buffer.writeLong(this.requestUUID.getMostSignificantBits());
            buffer.writeLong(this.requestUUID.getLeastSignificantBits());
        }
        if ((len = (nameBytes = this.playerName.getBytes(StandardCharsets.US_ASCII)).length) > 16) {
            throw new UnsupportedOperationException("Username is longer than 16 bytes");
        }
        buffer.writeByte(len);
        buffer.writeBytes(nameBytes);
        if (this.injected != null) {
            buffer.writeInt(Integer.reverseBytes(0));
            int pos = buffer.writerIndex();
            buffer.setByte(pos - 4, this.injected.writePayload(buffer));
            buffer.setMedium(pos - 3, buffer.writerIndex() - pos);
        } else {
            buffer.writeByte(this.nameLength);
            int l = this.payload.readableBytes();
            buffer.writeMedium(l);
            buffer.writeBytes(this.payload, this.payload.readerIndex(), l);
        }
    }

    @Override
    public void handlePacket(EaglerSupervisorHandler handler) {
        handler.handleClient(this);
    }

    @Override
    public ReferenceCounted delegate() {
        return this.payload;
    }
}

