/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.supervisor;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerCape;
import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerSkin;
import net.lax1dude.eaglercraft.backend.server.base.skins.type.MissingCape;
import net.lax1dude.eaglercraft.backend.server.base.skins.type.MissingSkin;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.SupervisorConnection;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.SupervisorService;
import net.lax1dude.eaglercraft.backend.server.util.KeyedConcurrentLazyLoader;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.client.CPacketSvGetClientBrandUUID;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.client.CPacketSvGetOtherCape;
import net.lax1dude.eaglercraft.backend.supervisor.protocol.pkt.client.CPacketSvGetOtherSkin;

class SupervisorPlayer {
    private final SupervisorConnection connection;
    private final UUID playerUUID;
    private volatile int nodeId = -1;
    private volatile UUID brandUUID = null;
    private KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, UUID> waitingBrandCallbacks = null;
    private volatile IEaglerPlayerSkin skin = null;
    private final Object skinLock = new Object();
    private KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, IEaglerPlayerSkin> waitingSkinCallbacks = null;
    private volatile IEaglerPlayerCape cape = null;
    private final Object capeLock = new Object();
    private KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, IEaglerPlayerCape> waitingCapeCallbacks = null;

    SupervisorPlayer(SupervisorConnection connection, UUID playerUUID) {
        this.connection = connection;
        this.playerUUID = playerUUID;
    }

    public SupervisorConnection getConnection() {
        return this.connection;
    }

    public SupervisorService<?> getController() {
        return this.connection.service;
    }

    public UUID getPlayerUUID() {
        return this.playerUUID;
    }

    public int getNodeId() {
        return this.nodeId;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void loadBrandUUID(UUID requester, Consumer<UUID> callback) {
        UUID val = this.brandUUID;
        if (val != null) {
            callback.accept(val);
        } else {
            SupervisorPlayer supervisorPlayer = this;
            synchronized (supervisorPlayer) {
                val = this.brandUUID;
                if (val != null) {
                } else {
                    if (this.waitingBrandCallbacks != null) {
                        this.waitingBrandCallbacks.add(requester, callback);
                        return;
                    }
                    this.waitingBrandCallbacks = new KeyedConcurrentLazyLoader.KeyedConsumerList();
                    this.waitingBrandCallbacks.add(requester, callback);
                }
            }
            if (val != null) {
                callback.accept(val);
                return;
            }
            this.connection.sendSupervisorPacket(new CPacketSvGetClientBrandUUID(this.playerUUID));
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void loadSkinData(UUID requester, Consumer<IEaglerPlayerSkin> callback) {
        IEaglerPlayerSkin val = this.skin;
        if (val != null) {
            callback.accept(val);
        } else {
            Object object = this.skinLock;
            synchronized (object) {
                val = this.skin;
                if (val != null) {
                } else {
                    if (this.waitingSkinCallbacks != null) {
                        this.waitingSkinCallbacks.add(requester, callback);
                        return;
                    }
                    this.waitingSkinCallbacks = new KeyedConcurrentLazyLoader.KeyedConsumerList();
                    this.waitingSkinCallbacks.add(requester, callback);
                }
            }
            if (val != null) {
                callback.accept(val);
                return;
            }
            this.connection.sendSupervisorPacket(new CPacketSvGetOtherSkin(this.playerUUID));
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void loadCapeData(UUID requester, Consumer<IEaglerPlayerCape> callback) {
        IEaglerPlayerCape val = this.cape;
        if (val != null) {
            callback.accept(val);
        } else {
            Object object = this.capeLock;
            synchronized (object) {
                val = this.cape;
                if (val != null) {
                } else {
                    if (this.waitingCapeCallbacks != null) {
                        this.waitingCapeCallbacks.add(requester, callback);
                        return;
                    }
                    this.waitingCapeCallbacks = new KeyedConcurrentLazyLoader.KeyedConsumerList();
                    this.waitingCapeCallbacks.add(requester, callback);
                }
            }
            if (val != null) {
                callback.accept(val);
                return;
            }
            this.connection.sendSupervisorPacket(new CPacketSvGetOtherCape(this.playerUUID));
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void onSkinReceived(IEaglerPlayerSkin skin) {
        KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, IEaglerPlayerSkin> toCall;
        Object object = this.skinLock;
        synchronized (object) {
            if (this.skin != null) {
                return;
            }
            this.skin = skin;
            toCall = this.waitingSkinCallbacks;
            this.waitingSkinCallbacks = null;
        }
        if (toCall != null) {
            List<Consumer<IEaglerPlayerSkin>> toCallList = toCall.getList();
            int l = toCallList.size();
            for (int i = 0; i < l; ++i) {
                try {
                    toCallList.get(i).accept(skin);
                    continue;
                }
                catch (Exception ex) {
                    this.connection.logger().error("Caught error from lazy load callback", ex);
                }
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void onSkinError() {
        if (this.nodeId == -1) {
            this.connection.onDropPlayer(this.playerUUID);
        } else {
            KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, IEaglerPlayerSkin> toCall;
            Object object = this.skinLock;
            synchronized (object) {
                if (this.skin != null) {
                    return;
                }
                toCall = this.waitingSkinCallbacks;
                this.waitingSkinCallbacks = null;
            }
            if (toCall != null) {
                List<Consumer<IEaglerPlayerSkin>> toCallList = toCall.getList();
                int l = toCallList.size();
                for (int i = 0; i < l; ++i) {
                    try {
                        toCallList.get(i).accept(MissingSkin.MISSING_SKIN);
                        continue;
                    }
                    catch (Exception ex) {
                        this.connection.logger().error("Caught error from lazy load callback", ex);
                    }
                }
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void onCapeReceived(IEaglerPlayerCape cape) {
        KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, IEaglerPlayerCape> toCall;
        Object object = this.capeLock;
        synchronized (object) {
            if (this.cape != null) {
                return;
            }
            this.cape = cape;
            toCall = this.waitingCapeCallbacks;
            this.waitingCapeCallbacks = null;
        }
        if (toCall != null) {
            List<Consumer<IEaglerPlayerCape>> toCallList = toCall.getList();
            int l = toCallList.size();
            for (int i = 0; i < l; ++i) {
                try {
                    toCallList.get(i).accept(cape);
                    continue;
                }
                catch (Exception ex) {
                    this.connection.logger().error("Caught error from lazy load callback", ex);
                }
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void onCapeError() {
        if (this.nodeId == -1) {
            this.connection.onDropPlayer(this.playerUUID);
        } else {
            KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, IEaglerPlayerCape> toCall;
            Object object = this.capeLock;
            synchronized (object) {
                if (this.cape != null) {
                    return;
                }
                toCall = this.waitingCapeCallbacks;
                this.waitingCapeCallbacks = null;
            }
            if (toCall != null) {
                List<Consumer<IEaglerPlayerCape>> toCallList = toCall.getList();
                int l = toCallList.size();
                for (int i = 0; i < l; ++i) {
                    try {
                        toCallList.get(i).accept(MissingCape.MISSING_CAPE);
                        continue;
                    }
                    catch (Exception ex) {
                        this.connection.logger().error("Caught error from lazy load callback", ex);
                    }
                }
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void onNodeIDReceived(int nodeId, UUID brandUUID) {
        KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, UUID> toCall;
        this.nodeId = nodeId;
        SupervisorPlayer supervisorPlayer = this;
        synchronized (supervisorPlayer) {
            if (this.brandUUID != null) {
                return;
            }
            this.brandUUID = brandUUID;
            toCall = this.waitingBrandCallbacks;
            this.waitingBrandCallbacks = null;
        }
        if (toCall != null) {
            List<Consumer<UUID>> toCallList = toCall.getList();
            int l = toCallList.size();
            for (int i = 0; i < l; ++i) {
                try {
                    toCallList.get(i).accept(brandUUID);
                    continue;
                }
                catch (Exception ex) {
                    this.connection.logger().error("Caught error from lazy load callback", ex);
                }
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void onNodeIDError() {
        if (this.nodeId == -1) {
            this.connection.onDropPlayer(this.playerUUID);
        } else {
            KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, UUID> toCall;
            SupervisorPlayer supervisorPlayer = this;
            synchronized (supervisorPlayer) {
                if (this.brandUUID != null) {
                    return;
                }
                toCall = this.waitingBrandCallbacks;
                this.waitingBrandCallbacks = null;
            }
            if (toCall != null) {
                List<Consumer<UUID>> toCallList = toCall.getList();
                int l = toCallList.size();
                for (int i = 0; i < l; ++i) {
                    try {
                        toCallList.get(i).accept(null);
                        continue;
                    }
                    catch (Exception ex) {
                        this.connection.logger().error("Caught error from lazy load callback", ex);
                    }
                }
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void playerDropped() {
        KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, IEaglerPlayerCape> toCallC;
        KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, IEaglerPlayerSkin> toCallB;
        KeyedConcurrentLazyLoader.KeyedConsumerList<UUID, UUID> toCallA;
        synchronized (this) {
            toCallA = this.waitingBrandCallbacks;
            this.waitingBrandCallbacks = null;
        }
        if (toCallA != null) {
            List<Consumer<UUID>> toCallAList = toCallA.getList();
            for (int i = 0, l = toCallAList.size(); i < l; ++i) {
                try {
                    toCallAList.get(i).accept(null);
                    continue;
                }
                catch (Exception ex) {
                    this.connection.logger().error("Caught error from lazy load callback", ex);
                }
            }
        }
        synchronized (this.skinLock) {
            toCallB = this.waitingSkinCallbacks;
            this.waitingSkinCallbacks = null;
        }
        if (toCallB != null) {
            List<Consumer<IEaglerPlayerSkin>> toCallBList = toCallB.getList();
            for (int i = 0, l = toCallBList.size(); i < l; ++i) {
                try {
                    toCallBList.get(i).accept(null);
                    continue;
                }
                catch (Exception ex) {
                    this.connection.logger().error("Caught error from lazy load callback", ex);
                }
            }
        }
        synchronized (this.capeLock) {
            toCallC = this.waitingCapeCallbacks;
            this.waitingCapeCallbacks = null;
        }
        if (toCallC != null) {
            List<Consumer<IEaglerPlayerCape>> toCallCList = toCallC.getList();
            for (int i = 0, l = toCallCList.size(); i < l; ++i) {
                try {
                    toCallCList.get(i).accept(null);
                    continue;
                }
                catch (Exception ex) {
                    this.connection.logger().error("Caught error from lazy load callback", ex);
                }
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void onDropPartial(boolean skin, boolean cape) {
        Object object;
        if (skin) {
            object = this.skinLock;
            synchronized (object) {
                this.skin = null;
            }
        }
        if (cape) {
            object = this.capeLock;
            synchronized (object) {
                this.cape = null;
            }
        }
    }
}

