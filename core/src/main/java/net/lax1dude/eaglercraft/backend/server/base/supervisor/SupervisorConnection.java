/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.MapMaker
 *  io.netty.channel.Channel
 */
package net.lax1dude.eaglercraft.backend.server.base.supervisor;

import com.google.common.collect.MapMaker;
import io.netty.channel.Channel;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.function.Consumer;
import java.util.function.Function;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformLogger;
import net.lax1dude.eaglercraft.backend.server.api.INettyChannel;
import net.lax1dude.eaglercraft.backend.server.api.supervisor.ISupervisorConnection;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServer;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.EnumAcceptPlayer;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.SupervisorLookupHandler;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.SupervisorPlayer;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.SupervisorService;
import net.lax1dude.eaglercraft.backend.server.util.Util;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.netty.SupervisorPacketHandler;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.EaglerSupervisorPacket;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.client.CPacketSvDropPlayer;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.client.CPacketSvDropPlayerPartial;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.client.CPacketSvPing;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.client.CPacketSvProxyStatus;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.client.CPacketSvRegisterPlayer;

public class SupervisorConnection
implements ISupervisorConnection,
INettyChannel.NettyUnsafe {
    private static final AtomicLongFieldUpdater<SupervisorConnection> LAST_PING_HANDLE = AtomicLongFieldUpdater.newUpdater(SupervisorConnection.class, "lastPing");
    final IPlatformLogger logger;
    final SupervisorService<?> service;
    final SupervisorPacketHandler handler;
    final SupervisorLookupHandler<?> lookupHandler;
    final ConcurrentMap<UUID, SupervisorPlayer> remotePlayers;
    final Function<UUID, SupervisorPlayer> playerLoader;
    final Map<UUID, PendingHandshake> pendingHandshakes;
    final Set<UUID> acceptedPlayers;
    final ConcurrentMap<Integer, Set<UUID>> nodeIdAssociations;
    final int nodeId;
    private volatile long lastPing;
    private int proxyPing;
    private int playerTotal;
    private int playerMax;

    SupervisorConnection(SupervisorService<?> service, SupervisorPacketHandler handler, int nodeId) {
        this.logger = service.logger();
        this.service = service;
        this.handler = handler;
        this.nodeId = nodeId;
        this.lookupHandler = new SupervisorLookupHandler(service, this);
        this.remotePlayers = new MapMaker().initialCapacity(2048).concurrencyLevel(16).makeMap();
        this.playerLoader = uuid -> new SupervisorPlayer(this, (UUID)uuid);
        this.pendingHandshakes = new HashMap<UUID, PendingHandshake>(256);
        this.acceptedPlayers = Collections.newSetFromMap(new MapMaker().initialCapacity(1024).concurrencyLevel(8).makeMap());
        this.nodeIdAssociations = new MapMaker().initialCapacity(64).concurrencyLevel(16).makeMap();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return this.handler.getChannel().remoteAddress();
    }

    @Override
    public int getProtocolVersion() {
        return this.handler.getConnectionProtocol().vers;
    }

    @Override
    public int getNodeId() {
        return this.nodeId;
    }

    @Override
    public long getPing() {
        return this.proxyPing;
    }

    public int getPlayerTotal() {
        return this.playerTotal;
    }

    public int getPlayerMax() {
        return this.playerMax;
    }

    @Override
    public INettyChannel.NettyUnsafe netty() {
        return this;
    }

    @Override
    public Channel getChannel() {
        return this.handler.getChannel();
    }

    @Override
    public SocketAddress getSocketAddress() {
        return this.handler.getChannel().remoteAddress();
    }

    public void sendSupervisorPacket(EaglerSupervisorPacket msg) {
        this.handler.channelWrite(msg);
    }

    SupervisorPlayer loadPlayer(UUID playerUUID) {
        return this.remotePlayers.computeIfAbsent(playerUUID, this.playerLoader);
    }

    SupervisorPlayer loadPlayerIfPresent(UUID playerUUID) {
        return (SupervisorPlayer)this.remotePlayers.get(playerUUID);
    }

    void onPongPacket() {
        long result = LAST_PING_HANDLE.getAndSet(this, 0L);
        if (result != 0L) {
            this.proxyPing = (int)(Util.steadyTime() - result);
        }
    }

    void onPlayerCount(int playerTotal, int playerMax) {
        this.playerTotal = playerTotal;
        this.playerMax = playerMax;
    }

    void updatePing(long millis) {
        if (LAST_PING_HANDLE.compareAndSet(this, 0L, millis)) {
            this.handler.channelWrite(new CPacketSvPing());
        }
        this.handler.channelWrite(new CPacketSvProxyStatus(System.currentTimeMillis(), this.service.getEaglerXServer().getPlatform().getPlayerMax()));
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void expireHandshakes(long millis) {
        ArrayList<PendingHandshake> lst = null;
        Map<UUID, PendingHandshake> map = this.pendingHandshakes;
        synchronized (map) {
            if (this.pendingHandshakes.isEmpty()) {
                return;
            }
            Iterator<PendingHandshake> itr = this.pendingHandshakes.values().iterator();
            while (itr.hasNext()) {
                PendingHandshake h = itr.next();
                if (millis - h.createdAt <= 10000L) continue;
                itr.remove();
                if (lst == null) {
                    lst = new ArrayList<PendingHandshake>(4);
                }
                lst.add(h);
            }
        }
        if (lst != null) {
            for (PendingHandshake c : lst) {
                this.safeAccept(c.consumer, EnumAcceptPlayer.SUPERVISOR_UNAVAILABLE);
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void acceptPlayer(UUID playerUUID, UUID brandUUID, int gameProtocol, int eaglerProtocol, String username, Consumer<EnumAcceptPlayer> callback) {
        block6: {
            if (!this.handler.getChannel().isActive()) {
                this.safeAccept(callback, EnumAcceptPlayer.SUPERVISOR_UNAVAILABLE);
                return;
            }
            if (this.acceptedPlayers.contains(playerUUID)) {
                this.safeAccept(callback, EnumAcceptPlayer.REJECT_DUPLICATE_UUID);
                return;
            }
            Map<UUID, PendingHandshake> map = this.pendingHandshakes;
            synchronized (map) {
                if (this.pendingHandshakes.containsKey(playerUUID)) {
                    break block6;
                }
                this.pendingHandshakes.put(playerUUID, new PendingHandshake(callback, Util.steadyTime()));
            }
            this.sendSupervisorPacket(new CPacketSvRegisterPlayer(playerUUID, brandUUID, gameProtocol, eaglerProtocol, username.toLowerCase(Locale.US)));
            return;
        }
        this.safeAccept(callback, EnumAcceptPlayer.REJECT_ALREADY_WAITING);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void onPlayerAccept(UUID playerUUID, EnumAcceptPlayer reason) {
        PendingHandshake c;
        Map<UUID, PendingHandshake> map = this.pendingHandshakes;
        synchronized (map) {
            c = this.pendingHandshakes.remove(playerUUID);
        }
        if (c != null) {
            if (reason == EnumAcceptPlayer.ACCEPT) {
                this.acceptedPlayers.add(playerUUID);
            }
            this.safeAccept(c.consumer, reason);
        } else {
            this.service.logger().warn("Received accept/reject signal for unknown player " + playerUUID);
        }
    }

    void setRemotePlayerNode(UUID playerUUID, int nodeId) {
        this.nodeIdAssociations.compute(nodeId, (k, v) -> {
            if (v == null) {
                v = new HashSet<UUID>(1024);
            }
            v.add(playerUUID);
            return v;
        });
    }

    void dropPlayerFromNode(UUID playerUUID, int nodeId) {
        this.nodeIdAssociations.compute(nodeId, (k, v) -> {
            if (v != null && v.remove(playerUUID) && v.isEmpty()) {
                v = null;
            }
            return v;
        });
    }

    void onDropAllPlayers(int nodeId) {
        Set<UUID> set = this.nodeIdAssociations.remove(nodeId);
        if (set != null) {
            for (UUID uuid : set) {
                SupervisorPlayer player = (SupervisorPlayer)this.remotePlayers.remove(uuid);
                if (player == null) continue;
                player.playerDropped();
            }
        }
    }

    void onDropPlayer(UUID playerUUID) {
        SupervisorPlayer player = (SupervisorPlayer)this.remotePlayers.remove(playerUUID);
        if (player != null) {
            int node = player.getNodeId();
            if (node != -1) {
                this.dropPlayerFromNode(playerUUID, node);
            }
            player.playerDropped();
        }
    }

    void dropOwnPlayer(UUID playerUUID) {
        this.remotePlayers.remove(playerUUID);
        if (this.acceptedPlayers.remove(playerUUID)) {
            this.sendSupervisorPacket(new CPacketSvDropPlayer(playerUUID));
        }
    }

    void notifySkinChange(UUID uuid, String serverName, boolean skin, boolean cape) {
        int mask = 0;
        if (skin) {
            mask |= 1;
        }
        if (cape) {
            mask |= 2;
        }
        if (mask > 0) {
            this.sendSupervisorPacket(new CPacketSvDropPlayerPartial(uuid, serverName, mask));
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void onConnectionEnd() {
        ArrayList<PendingHandshake> lst;
        Map<UUID, PendingHandshake> map = this.pendingHandshakes;
        synchronized (map) {
            lst = new ArrayList<PendingHandshake>(this.pendingHandshakes.values());
            this.pendingHandshakes.clear();
        }
        for (PendingHandshake c : lst) {
            this.safeAccept(c.consumer, EnumAcceptPlayer.SUPERVISOR_UNAVAILABLE);
        }
    }

    private void safeAccept(Consumer<EnumAcceptPlayer> consumer, EnumAcceptPlayer value) {
        try {
            consumer.accept(value);
        }
        catch (Exception ex) {
            this.service.logger().error("Caught exception from supervisor player accept callback", ex);
        }
    }

    IPlatformLogger logger() {
        return this.logger;
    }

    EaglerXServer<?> getEaglerXServer() {
        return this.service.getEaglerXServer();
    }

    private static class PendingHandshake {
        protected final Consumer<EnumAcceptPlayer> consumer;
        protected final long createdAt;

        protected PendingHandshake(Consumer<EnumAcceptPlayer> consumer, long createdAt) {
            this.consumer = consumer;
            this.createdAt = createdAt;
        }
    }
}

