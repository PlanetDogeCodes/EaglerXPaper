/*
 * Copyright (c) 2025 lax1dude. All Rights Reserved.
 *
 * Candidate class-name table for cross-version NMS reflection.
 *
 * EaglerXServer's Bukkit platform uses runtime reflection anchored on the
 * *simple names* of NMS classes (e.g. "PlayerConnection", "NetworkManager")
 * to locate fields and methods. This works on CraftBukkit-mapped servers
 * (1.12 through 1.16.5) but breaks on Paper 1.17+, which adopted Mojang
 * mappings at runtime (e.g. "ServerGamePacketListenerImpl", "Connection").
 *
 * This class exposes, for every NMS symbol EaglerXServer reflects against,
 * the full set of simple names that symbol has been known by across all
 * supported server versions. Reflection sites consult these sets rather
 * than comparing against a single literal.
 *
 * CONVENTIONS:
 *   - Sets are unmodifiable and use Set.of(...) for O(1) contains().
 *   - The helper matches(Class, Set) is the single entry point used by
 *     reflection sites; all string comparisons go through it.
 */

package net.lax1dude.eaglercraft.backend.server.bukkit;

import java.util.Set;

public final class NmsNames {

    private NmsNames() {
    }

    // ---- Entity / Player ----

    /**
     * The "player connection" field on the server-side player entity.
     * - 1.12-1.16.5: net.minecraft.server.v1_12_R1.PlayerConnection
     * - 1.17+      : net.minecraft.server.level.ServerPlayer.connection
     *                (field type: ServerGamePacketListenerImpl)
     */
    public static final Set<String> PLAYER_CONNECTION = Set.of(
            "ServerGamePacketListenerImpl", // 1.17+ Mojang mappings
            "PlayerConnection"              // 1.12-1.16.5 CraftBukkit mappings
    );

    /**
     * The network manager / connection class. Lives as a field on the
     * player connection (1.12-1.16.5) or on ServerCommonPacketListenerImpl
     * (1.20.2+, the shared parent of game and configuration listeners).
     * - 1.12-1.16.5: net.minecraft.server.v1_12_R1.NetworkManager
     * - 1.17+      : net.minecraft.network.Connection
     */
    public static final Set<String> NETWORK_MANAGER = Set.of(
            "Connection",      // 1.17+
            "NetworkManager"   // 1.12-1.16.5
    );

    /**
     * The server-side player entity class. Return type of CraftPlayer.getHandle().
     * - 1.12-1.16.5: net.minecraft.server.v1_12_R1.EntityPlayer
     * - 1.17+      : net.minecraft.server.level.ServerPlayer
     */
    public static final Set<String> ENTITY_PLAYER = Set.of(
            "ServerPlayer",  // 1.17+
            "EntityPlayer"   // 1.12-1.16.5
    );

    // ---- Listeners ----

    /**
     * The LOGIN protocol-phase listener.
     * - 1.12-1.16.5: net.minecraft.server.v1_12_R1.LoginListener
     * - 1.17+      : net.minecraft.server.network.ServerLoginPacketListenerImpl
     */
    public static final Set<String> LOGIN_LISTENER = Set.of(
            "ServerLoginPacketListenerImpl", // 1.17+
            "LoginListener"                  // 1.12-1.16.5
    );

    /**
     * The CONFIGURATION protocol-phase listener. NEW in 1.20.2.
     * Between LOGIN and PLAY, the client and server exchange configuration
     * packets (registries, brand, resource packs, etc.) before entering play.
     * This listener has no 1.12-1.16.5 equivalent.
     */
    public static final Set<String> CONFIGURATION_LISTENER = Set.of(
            "ServerConfigurationPacketListenerImpl" // 1.20.2+ only
    );

    /**
     * The HANDSHAKE protocol-phase listener. Created first when a client connects.
     * - 1.12-1.16.5: net.minecraft.server.v1_12_R1.HandshakeListener
     * - 1.17+      : net.minecraft.server.network.ServerHandshakePacketListenerImpl
     */
    public static final Set<String> HANDSHAKE_LISTENER = Set.of(
            "ServerHandshakePacketListenerImpl", // 1.17+
            "HandshakeListener"                  // 1.12-1.16.5
    );

    /**
     * The PLAY protocol-phase listener. The "main" listener active during gameplay.
     * Same class as PLAYER_CONNECTION above (they are the same thing).
     */
    public static final Set<String> PLAY_LISTENER = PLAYER_CONNECTION; // alias

    // ---- Packets (LOGIN phase) ----

    /**
     * Server -> client packet sent when login succeeds. Contains the GameProfile.
     * - 1.12-1.16.5: net.minecraft.server.v1_12_R1.PacketLoginOutSuccess
     * - 1.17+      : net.minecraft.network.protocol.login.ClientboundGameProfilePacket
     */
    public static final Set<String> PACKET_LOGIN_SUCCESS = Set.of(
            "ClientboundGameProfilePacket", // 1.17+
            "PacketLoginOutSuccess"         // 1.12-1.16.5
    );

    /**
     * Server -> client login disconnect packet.
     * - 1.12-1.16.5: net.minecraft.server.v1_12_R1.PacketLoginOutDisconnect
     * - 1.17+      : net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket
     */
    public static final Set<String> PACKET_LOGIN_DISCONNECT = Set.of(
            "ClientboundLoginDisconnectPacket", // 1.17+
            "PacketLoginOutDisconnect"          // 1.12-1.16.5
    );

    /**
     * Server -> client login set-compression packet.
     * - 1.12-1.16.5: net.minecraft.server.v1_12_R1.PacketLoginOutSetCompression
     * - 1.17+      : net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket
     */
    public static final Set<String> PACKET_LOGIN_SET_COMPRESSION = Set.of(
            "ClientboundLoginCompressionPacket", // 1.17+
            "PacketLoginOutSetCompression"       // 1.12-1.16.5
    );

    // ---- Packets (PLAY phase) ----

    /**
     * Server -> client play-phase disconnect packet.
     * - 1.12-1.16.5: net.minecraft.server.v1_12_R1.PacketPlayOutKickDisconnect
     * - 1.17+      : net.minecraft.network.protocol.game.ClientboundDisconnectPacket
     */
    public static final Set<String> PACKET_PLAY_DISCONNECT = Set.of(
            "ClientboundDisconnectPacket", // 1.17+
            "PacketPlayOutKickDisconnect"  // 1.12-1.16.5
    );

    // ---- Enum / nested-type name candidates ----

    /**
     * The login listener's state enum simple name.
     * - 1.12-1.16.5: LoginListener$EnumProtocolState
     * - 1.17+      : ServerLoginPacketListenerImpl$State
     */
    public static final Set<String> LOGIN_STATE_ENUM_SIMPLE = Set.of(
            "State",            // 1.17+
            "EnumProtocolState" // 1.12-1.16.5
    );

    /**
     * The protocol direction enum simple name (field on Connection/NetworkManager).
     * - 1.12-1.20.x: EnumProtocolDirection
     * - 1.21+ (Paper 26.x): PacketFlow
     */
    public static final Set<String> PROTOCOL_DIRECTION = Set.of(
            "PacketFlow",           // 1.21+ (Paper 26.x)
            "EnumProtocolDirection" // 1.12-1.20.x
    );

    // ---- Helpers ----

    /**
     * Returns true iff the given class's simple name is in the candidate set.
     */
    public static boolean matches(Class<?> type, Set<String> candidates) {
        return type != null && candidates.contains(type.getSimpleName());
    }

    /**
     * Returns true iff the given object's class's simple name is in the
     * candidate set.
     */
    public static boolean matches(Object obj, Set<String> candidates) {
        return obj != null && matches(obj.getClass(), candidates);
    }
}
