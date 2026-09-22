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

public class CPacketSvProxyBrand
implements EaglerSupervisorPacket {
    public static final int PROXY_TYPE_3RD_PARTY = 0;
    public static final int PROXY_TYPE_BUNGEE = 1;
    public static final int PROXY_TYPE_VELOCITY = 2;
    public static final int PROXY_TYPE_EAGLER_STANDALONE = 3;
    public static final int PLUGIN_TYPE_3RD_PARTY = 0;
    public static final int PLUGIN_TYPE_EAGLERXBUNGEE = 1;
    public static final int PLUGIN_TYPE_EAGLERXVELOCITY = 2;
    public static final int PLUGIN_TYPE_EAGLERXSERVER = 3;
    public int proxyType;
    public String proxyVersion;
    public int pluginType;
    public String pluginBrand;
    public String pluginVersion;

    public CPacketSvProxyBrand() {
    }

    public CPacketSvProxyBrand(int proxyType, String proxyVersion, int pluginType, String pluginBrand, String pluginVersion) {
        this.proxyType = proxyType;
        this.proxyVersion = proxyVersion;
        this.pluginType = pluginType;
        this.pluginBrand = pluginBrand;
        this.pluginVersion = pluginVersion;
    }

    @Override
    public void readPacket(ByteBuf buffer) {
        this.proxyType = buffer.readUnsignedByte();
        this.pluginType = buffer.readUnsignedByte();
        this.proxyVersion = CharSeqCompat.readString(buffer, buffer.readShort(), StandardCharsets.UTF_8);
        this.pluginBrand = CharSeqCompat.readString(buffer, buffer.readShort(), StandardCharsets.UTF_8);
        this.pluginVersion = CharSeqCompat.readString(buffer, buffer.readShort(), StandardCharsets.UTF_8);
    }

    @Override
    public void writePacket(ByteBuf buffer) {
        buffer.writeByte(this.proxyType);
        buffer.writeByte(this.pluginType);
        byte[] b = this.proxyVersion.getBytes(StandardCharsets.UTF_8);
        buffer.writeShort(b.length);
        buffer.writeBytes(b);
        b = this.pluginBrand.getBytes(StandardCharsets.UTF_8);
        buffer.writeShort(b.length);
        buffer.writeBytes(b);
        b = this.pluginVersion.getBytes(StandardCharsets.UTF_8);
        buffer.writeShort(b.length);
        buffer.writeBytes(b);
    }

    @Override
    public void handlePacket(EaglerSupervisorHandler handler) {
        handler.handleClient(this);
    }
}

