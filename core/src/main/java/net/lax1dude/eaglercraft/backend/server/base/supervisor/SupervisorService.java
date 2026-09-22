/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.supervisor;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import net.lax1dude.eaglercraft.backend.server.adapter.IEaglerXServerPlayerCountHandler;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformLogger;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformTask;
import net.lax1dude.eaglercraft.backend.server.api.IEaglerPlayer;
import net.lax1dude.eaglercraft.backend.server.api.IEaglerXServerAPI;
import net.lax1dude.eaglercraft.backend.server.api.brand.IBrandRegistry;
import net.lax1dude.eaglercraft.backend.server.api.supervisor.ISupervisorResolver;
import net.lax1dude.eaglercraft.backend.server.base.BasePlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.EaglerPlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServer;
import net.lax1dude.eaglercraft.backend.server.base.config.ConfigDataSupervisor;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.EnumAcceptPlayer;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.ISupervisorResolverImpl;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.ISupervisorServiceImpl;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.PipelineFactory;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.SupervisorClientV1Handler;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.SupervisorConnection;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.SupervisorResolver;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.SupervisorResolverAll;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.SupervisorTimeoutLoop;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.rpc.SupervisorRPCHandler;
import net.lax1dude.eaglercraft.backend.server.util.Util;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.EaglerSupervisorProtocol;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.netty.SupervisorPacketHandler;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.client.CPacketSvHandshake;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.client.CPacketSvProxyBrand;

public class SupervisorService<PlayerObject>
implements ISupervisorServiceImpl<PlayerObject>,
IEaglerXServerPlayerCountHandler {
    private static final AtomicIntegerFieldUpdater<SupervisorService> SERVICE_STATE_TRACKER_HANDLE = AtomicIntegerFieldUpdater.newUpdater(SupervisorService.class, "serviceStateTracker");
    private static final AtomicReferenceFieldUpdater<SupervisorService, SupervisorConnection> CURRENT_CONNECTION_HANDLE = AtomicReferenceFieldUpdater.newUpdater(SupervisorService.class, SupervisorConnection.class, "currentConnection");
    private final EaglerXServer<PlayerObject> server;
    private final ConfigDataSupervisor config;
    private final SupervisorTimeoutLoop timeoutLoop;
    private final SupervisorRPCHandler rpcHandler;
    final SupervisorResolver resolver;
    final SupervisorResolverAll resolverAll;
    private final boolean ignoreV2UUID;
    private volatile int serviceStateTracker = 0;
    private volatile SupervisorConnection currentConnection = null;
    private IPlatformTask pingTask;
    private IPlatformTask timeoutHandshakeTask;

    public SupervisorService(EaglerXServer<PlayerObject> server) {
        this.server = server;
        this.config = server.getConfig().getSupervisor();
        this.timeoutLoop = new SupervisorTimeoutLoop(server.getPlatform().getScheduler(), 250000000L);
        this.rpcHandler = new SupervisorRPCHandler(this);
        this.resolver = new SupervisorResolver(this);
        this.resolverAll = new SupervisorResolverAll(this.resolver, server);
        this.ignoreV2UUID = this.config.isSupervisorLookupIgnoreV2UUID();
    }

    @Override
    public IEaglerXServerAPI<PlayerObject> getServerAPI() {
        return this.server;
    }

    public EaglerXServer<PlayerObject> getEaglerXServer() {
        return this.server;
    }

    public final IPlatformLogger logger() {
        return this.server.logger();
    }

    public final SupervisorTimeoutLoop timeoutLoop() {
        return this.timeoutLoop;
    }

    @Override
    public boolean isSupervisorEnabled() {
        return true;
    }

    @Override
    public boolean isSupervisorConnected() {
        return this.currentConnection != null;
    }

    @Override
    public SupervisorConnection getConnection() {
        return this.currentConnection;
    }

    @Override
    public int getNodeId() {
        SupervisorConnection conn = this.currentConnection;
        return conn != null ? conn.getNodeId() : -1;
    }

    @Override
    public int getPlayerTotal() {
        SupervisorConnection conn = this.currentConnection;
        return conn != null ? conn.getPlayerTotal() : this.server.getPlatform().getPlayerTotal();
    }

    @Override
    public int getPlayerMax() {
        SupervisorConnection conn = this.currentConnection;
        return conn != null ? conn.getPlayerMax() : this.server.getPlatform().getPlayerMax();
    }

    @Override
    public SupervisorRPCHandler getRPCHandler() {
        return this.rpcHandler;
    }

    @Override
    public ISupervisorResolver getPlayerResolver() {
        return this.resolverAll;
    }

    @Override
    public ISupervisorResolverImpl getRemoteOnlyResolver() {
        return this.resolver;
    }

    @Override
    public void handleEnable() {
        this.initiateConnection();
        if (this.pingTask != null) {
            this.pingTask.cancel();
        }
        this.pingTask = this.server.getPlatform().getScheduler().executeAsyncRepeatingTask(() -> {
            SupervisorConnection conn = this.currentConnection;
            if (conn != null) {
                conn.updatePing(Util.steadyTime());
            }
        }, 500L, 1000L);
        if (this.timeoutHandshakeTask != null) {
            this.timeoutHandshakeTask.cancel();
        }
        this.timeoutHandshakeTask = this.server.getPlatform().getScheduler().executeAsyncRepeatingTask(() -> {
            SupervisorConnection conn = this.currentConnection;
            if (conn != null) {
                conn.expireHandshakes(Util.steadyTime());
            }
        }, 5000L, 5000L);
        this.server.getPlatform().setPlayerCountHandler(this);
    }

    @Override
    public void handleDisable() {
        this.server.logger().info("Attempting to terminate supervisor client");
        this.serviceStateTracker = -1;
        SupervisorConnection handler = CURRENT_CONNECTION_HANDLE.getAndSet(this, null);
        if (handler != null) {
            try {
                handler.getChannel().close().await();
            }
            catch (InterruptedException interruptedException) {
                // empty catch block
            }
        }
        this.onConnectionEnd();
        if (this.pingTask != null) {
            this.pingTask.cancel();
            this.pingTask = null;
        }
        if (this.timeoutHandshakeTask != null) {
            this.timeoutHandshakeTask.cancel();
            this.timeoutHandshakeTask = null;
        }
        this.timeoutLoop.cancelAll();
        this.server.getPlatform().setPlayerCountHandler(null);
    }

    @Override
    public boolean shouldIgnoreUUID(UUID uuid) {
        return this.ignoreV2UUID && uuid.version() == 2;
    }

    public void initiateConnection() {
        if (SERVICE_STATE_TRACKER_HANDLE.compareAndSet(this, 0, 1)) {
            PipelineFactory.initiateConnection(this.server, this.config.getSupervisorAddress(), this, this.config.getSupervisorConnectTimeout(), this.config.getSupervisorReadTimeout());
        }
    }

    void handleChannelOpen(SupervisorPacketHandler h) {
        if (SERVICE_STATE_TRACKER_HANDLE.compareAndSet(this, 1, 2)) {
            this.logger().info("Channel to supervisor opened");
            h.channelWrite(new CPacketSvHandshake(new int[]{EaglerSupervisorProtocol.V1.vers}, this.config.getSupervisorSecret()));
        } else {
            this.logger().error("Unexpected supervisor channel open");
            h.getChannel().close();
        }
    }

    void handleChannelFailure() {
        int state;
        do {
            if ((state = this.serviceStateTracker) > 0) continue;
            return;
        } while (!SERVICE_STATE_TRACKER_HANDLE.compareAndSet(this, state, 0));
        this.logger().error("Failed to open supervisor channel! Retrying...");
        this.server.getPlatform().getScheduler().executeAsyncDelayed(this::initiateConnection, 1000L);
    }

    void handleHandshakeSuccess(SupervisorPacketHandler h, int nodeId) {
        if (SERVICE_STATE_TRACKER_HANDLE.compareAndSet(this, 2, 3)) {
            this.logger().info("Supervisor handshake successful");
            this.logger().info("Assigned node ID " + nodeId);
            this.onNewConnection(h, nodeId);
        } else {
            this.logger().error("Unexpected supervisor handshake success");
            h.getChannel().close();
        }
    }

    void handleHandshakeFailure(SupervisorPacketHandler h, String failureCode) {
        this.logger().error("Supervisor handshake failed, reason: " + failureCode);
        h.getChannel().close();
    }

    void handleDisconnected() {
        int state;
        do {
            if ((state = this.serviceStateTracker) > 0) continue;
            return;
        } while (!SERVICE_STATE_TRACKER_HANDLE.compareAndSet(this, state, 0));
        this.onConnectionEnd();
        this.server.logger().error("Connection to supervisor was lost! Attempting to reconnect...");
        this.server.getPlatform().getScheduler().executeAsyncDelayed(this::initiateConnection, 1000L);
    }

    private void onNewConnection(SupervisorPacketHandler handler, int nodeId) {
        int proxyType;
        SupervisorConnection newConnection = new SupervisorConnection(this, handler, nodeId);
        handler.setConnectionProtocol(EaglerSupervisorProtocol.V1);
        handler.setConnectionHandler(new SupervisorClientV1Handler(newConnection));
        switch (this.server.getPlatform().getType()) {
            case BUNGEE: {
                proxyType = 1;
                break;
            }
            case VELOCITY: {
                proxyType = 2;
                break;
            }
            default: {
                proxyType = 3;
            }
        }
        handler.channelWrite(new CPacketSvProxyBrand(proxyType, this.server.getPlatform().getVersion(), 3, this.server.getServerBrand(), this.server.getServerVersion()));
        for (BasePlayerInstance<PlayerObject> player : this.server.getAllPlayersInternal()) {
            IEaglerPlayer dat = player.asEaglerPlayer();
            newConnection.acceptPlayer(player.getUniqueId(), dat != null ? ((EaglerPlayerInstance)dat).getEaglerBrandUUID() : IBrandRegistry.BRAND_VANILLA, player.getMinecraftProtocol(), dat != null ? ((EaglerPlayerInstance)dat).getEaglerProtocol().ver : 0, player.getUsername(), res -> {
                if (res != EnumAcceptPlayer.ACCEPT) {
                    this.logger().error("Could not reregister player '" + player.getUsername() + "' with supervisor! Result: " + res.name());
                    player.disconnect(this.server.componentBuilder().buildTextComponent().text("Failed to reinitialize connection to supervisor").end());
                }
            });
        }
        this.currentConnection = newConnection;
        this.resolver.flushDeferred();
    }

    private void onConnectionEnd() {
        SupervisorConnection handler = CURRENT_CONNECTION_HANDLE.getAndSet(this, null);
        if (handler != null) {
            handler.onConnectionEnd();
        }
        this.resolver.onConnectionEnd();
    }

    @Override
    public void acceptPlayer(UUID playerUUID, UUID brandUUID, int gameProtocol, int eaglerProtocol, String username, Consumer<EnumAcceptPlayer> callback) {
        SupervisorConnection conn = this.currentConnection;
        if (conn != null) {
            conn.acceptPlayer(playerUUID, brandUUID, gameProtocol, eaglerProtocol, username, callback);
        } else {
            try {
                callback.accept(EnumAcceptPlayer.SUPERVISOR_UNAVAILABLE);
            }
            catch (Exception ex) {
                this.logger().error("Caught exception from supervisor player accept callback", ex);
            }
        }
    }

    @Override
    public void dropOwnPlayer(UUID clientUUID) {
        SupervisorConnection conn = this.currentConnection;
        if (conn != null) {
            conn.dropOwnPlayer(clientUUID);
        }
    }

    @Override
    public void notifySkinChange(UUID playerUUID, String serverName, boolean skin, boolean cape) {
        SupervisorConnection conn = this.currentConnection;
        if (conn != null) {
            conn.notifySkinChange(playerUUID, serverName, skin, cape);
        }
    }
}

