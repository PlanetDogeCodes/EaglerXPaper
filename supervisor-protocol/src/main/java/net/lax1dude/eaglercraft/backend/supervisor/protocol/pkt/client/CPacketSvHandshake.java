/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.buffer.ByteBuf
 */
package net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.client;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.EaglerSupervisorHandler;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.EaglerSupervisorPacket;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.util.CharSeqCompat;

public class CPacketSvHandshake
implements EaglerSupervisorPacket {
    public int[] supportedProtocols;
    public String secretKey;

    public CPacketSvHandshake() {
    }

    public CPacketSvHandshake(int[] supportedProtocols, String secretKey) {
        this.supportedProtocols = supportedProtocols;
        this.secretKey = secretKey;
    }

    @Override
    public void readPacket(ByteBuf buffer) {
        this.supportedProtocols = new int[buffer.readUnsignedShort()];
        for (int i = 0; i < this.supportedProtocols.length; ++i) {
            this.supportedProtocols[i] = buffer.readUnsignedShort();
        }
        int keyLen = buffer.readUnsignedShort();
        this.secretKey = keyLen > 0 ? CharSeqCompat.readString(buffer, keyLen, StandardCharsets.UTF_8) : null;
    }

    @Override
    public void writePacket(ByteBuf buffer) {
        buffer.writeShort(this.supportedProtocols.length);
        for (int i = 0; i < this.supportedProtocols.length; ++i) {
            buffer.writeShort(this.supportedProtocols[i]);
        }
        if (this.secretKey != null) {
            byte[] bytes = this.secretKey.getBytes(StandardCharsets.UTF_8);
            int keyLen = bytes.length;
            if (keyLen > 65535) {
                throw new UnsupportedOperationException("Secret key is longer than 65535 bytes");
            }
            buffer.writeShort(keyLen);
            if (keyLen > 0) {
                buffer.writeBytes(bytes);
            }
        } else {
            buffer.writeShort(0);
        }
    }

    @Override
    public void handlePacket(EaglerSupervisorHandler handler) {
        handler.handleClient(this);
    }
}

