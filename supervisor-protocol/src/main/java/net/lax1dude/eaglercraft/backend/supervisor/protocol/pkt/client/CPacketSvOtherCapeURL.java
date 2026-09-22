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

public class CPacketSvOtherCapeURL
implements EaglerSupervisorPacket {
    public UUID uuid;
    public String url;

    public CPacketSvOtherCapeURL() {
    }

    public CPacketSvOtherCapeURL(UUID uuid, String url) {
        this.uuid = uuid;
        this.url = url;
    }

    @Override
    public void readPacket(ByteBuf buffer) {
        this.uuid = new UUID(buffer.readLong(), buffer.readLong());
        int urlLen = buffer.readUnsignedShort();
        this.url = CharSeqCompat.readString(buffer, urlLen, StandardCharsets.US_ASCII);
    }

    @Override
    public void writePacket(ByteBuf buffer) {
        buffer.writeLong(this.uuid.getMostSignificantBits());
        buffer.writeLong(this.uuid.getLeastSignificantBits());
        byte[] urlBytes = this.url.getBytes(StandardCharsets.US_ASCII);
        int len = urlBytes.length;
        if (len > 65535) {
            throw new UnsupportedOperationException("Cape URL is longer than 65535 bytes");
        }
        buffer.writeShort(len);
        buffer.writeBytes(urlBytes);
    }

    @Override
    public void handlePacket(EaglerSupervisorHandler handler) {
        handler.handleClient(this);
    }
}

