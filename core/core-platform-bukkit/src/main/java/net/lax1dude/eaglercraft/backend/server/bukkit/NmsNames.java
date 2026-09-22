/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.bukkit;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class NmsNames {
    public static final Set<String> PLAYER_CONNECTION = NmsNames.setOf("ServerGamePacketListenerImpl", "PlayerConnection");
    public static final Set<String> NETWORK_MANAGER = NmsNames.setOf("Connection", "NetworkManager");
    public static final Set<String> ENTITY_PLAYER = NmsNames.setOf("ServerPlayer", "EntityPlayer");
    public static final Set<String> LOGIN_LISTENER = NmsNames.setOf("ServerLoginPacketListenerImpl", "LoginListener");
    public static final Set<String> CONFIGURATION_LISTENER = NmsNames.setOf("ServerConfigurationPacketListenerImpl");
    public static final Set<String> HANDSHAKE_LISTENER = NmsNames.setOf("ServerHandshakePacketListenerImpl", "HandshakeListener");
    public static final Set<String> PLAY_LISTENER = PLAYER_CONNECTION;
    public static final Set<String> PACKET_LOGIN_SUCCESS = NmsNames.setOf("ClientboundLoginFinishedPacket", "ClientboundLoginSuccessPacket", "ClientboundGameProfilePacket", "PacketLoginOutSuccess");
    public static final Set<String> PACKET_LOGIN_DISCONNECT = NmsNames.setOf("ClientboundLoginDisconnectPacket", "PacketLoginOutDisconnect");
    public static final Set<String> PACKET_LOGIN_SET_COMPRESSION = NmsNames.setOf("ClientboundLoginCompressionPacket", "PacketLoginOutSetCompression");
    public static final Set<String> PACKET_PLAY_DISCONNECT = NmsNames.setOf("ClientboundDisconnectPacket", "PacketPlayOutKickDisconnect");
    public static final Set<String> LOGIN_STATE_ENUM_SIMPLE = NmsNames.setOf("State", "EnumProtocolState");
    public static final Set<String> PROTOCOL_DIRECTION = NmsNames.setOf("PacketFlow", "EnumProtocolDirection");

    private NmsNames() {
    }

    private static Set<String> setOf(String ... names) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(names)));
    }

    public static boolean matches(Class<?> type, Set<String> candidates) {
        return type != null && candidates.contains(type.getSimpleName());
    }

    public static boolean matches(Object obj, Set<String> candidates) {
        return obj != null && NmsNames.matches(obj.getClass(), candidates);
    }
}

