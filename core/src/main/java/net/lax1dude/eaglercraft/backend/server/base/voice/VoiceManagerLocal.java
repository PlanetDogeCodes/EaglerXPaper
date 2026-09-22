/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.voice;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import net.lax1dude.eaglercraft.backend.server.api.IEaglerPlayer;
import net.lax1dude.eaglercraft.backend.server.api.voice.EnumVoiceState;
import net.lax1dude.eaglercraft.backend.server.api.voice.IVoiceChannel;
import net.lax1dude.eaglercraft.backend.server.api.voice.IVoiceService;
import net.lax1dude.eaglercraft.backend.server.base.EaglerPlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.rpc.BasePlayerRPCManager;
import net.lax1dude.eaglercraft.backend.server.base.voice.DisabledChannel;
import net.lax1dude.eaglercraft.backend.server.base.voice.IVoiceManagerImpl;
import net.lax1dude.eaglercraft.backend.server.base.voice.VoiceChannel;
import net.lax1dude.eaglercraft.backend.server.base.voice.VoiceServiceLocal;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketVoiceSignalAllowedEAG;

public class VoiceManagerLocal<PlayerObject>
implements IVoiceManagerImpl<PlayerObject> {
    private static final AtomicIntegerFieldUpdater<VoiceManagerLocal> SERVER_ENABLE_HANDLE = AtomicIntegerFieldUpdater.newUpdater(VoiceManagerLocal.class, "isServerEnable");
    private static final AtomicReferenceFieldUpdater<VoiceManagerLocal, EnumVoiceState> LAST_STATE_HANDLE = AtomicReferenceFieldUpdater.newUpdater(VoiceManagerLocal.class, EnumVoiceState.class, "lastVoiceState");
    private static final AtomicReferenceFieldUpdater<VoiceManagerLocal, VoiceChannel.Context> ACTIVE_CHANNEL_HANDLE = AtomicReferenceFieldUpdater.newUpdater(VoiceManagerLocal.class, VoiceChannel.Context.class, "activeChannel");
    final EaglerPlayerInstance<PlayerObject> player;
    final VoiceServiceLocal<PlayerObject> voice;
    final boolean isBroken;
    private boolean isAlive = true;
    private boolean isManaged = true;
    private volatile int isServerEnable = 0;
    private volatile EnumVoiceState lastVoiceState = EnumVoiceState.SERVER_DISABLE;
    private volatile VoiceChannel.Context activeChannel = null;
    private volatile IVoiceChannel currentVoiceChannel = DisabledChannel.INSTANCE;

    final VoiceChannel.Context aquireActiveChannel() {
        return ACTIVE_CHANNEL_HANDLE.get(this);
    }

    final VoiceChannel.Context xchgActiveChannel(VoiceChannel.Context newValue) {
        return ACTIVE_CHANNEL_HANDLE.getAndSet(this, newValue);
    }

    VoiceManagerLocal(EaglerPlayerInstance<PlayerObject> player, VoiceServiceLocal<PlayerObject> voice) {
        this.player = player;
        this.voice = voice;
        this.isBroken = player.getEaglerProtocol().ver < 5;
    }

    @Override
    public IEaglerPlayer<PlayerObject> getPlayer() {
        return this.player;
    }

    @Override
    public IVoiceService<PlayerObject> getVoiceService() {
        return this.voice;
    }

    @Override
    public boolean isBackendRelayMode() {
        return false;
    }

    @Override
    public EnumVoiceState getVoiceState() {
        VoiceChannel.Context ch;
        if (this.currentVoiceChannel != DisabledChannel.INSTANCE && (ch = this.aquireActiveChannel()) != null) {
            return ch.isConnected() ? EnumVoiceState.ENABLED : EnumVoiceState.DISABLED;
        }
        return EnumVoiceState.SERVER_DISABLE;
    }

    @Override
    public IVoiceChannel getVoiceChannel() {
        return this.currentVoiceChannel;
    }

    @Override
    public void setVoiceChannel(IVoiceChannel channel) {
        this.setVoiceChannel0(channel);
        this.onStateChanged();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void setVoiceChannel0(IVoiceChannel channel) {
        IVoiceChannel oldChannel;
        if (channel == null) {
            throw new NullPointerException("Voice channel cannot be null!");
        }
        if (!(channel == DisabledChannel.INSTANCE || channel instanceof VoiceChannel && ((VoiceChannel)channel).owner == this.voice)) {
            throw new IllegalArgumentException("Unknown voice channel");
        }
        VoiceManagerLocal voiceManagerLocal = this;
        synchronized (voiceManagerLocal) {
            if (!this.isAlive) {
                return;
            }
            oldChannel = this.currentVoiceChannel;
            if (channel == oldChannel) {
                return;
            }
            this.currentVoiceChannel = channel;
        }
        this.switchChannels(oldChannel, channel);
    }

    @Override
    public boolean isServerManaged() {
        return this.isManaged;
    }

    @Override
    public void setServerManaged(boolean managed) {
        this.isManaged = managed;
    }

    @Override
    public void handleServerPreConnect() {
        if (this.isManaged) {
            this.setVoiceChannel0(DisabledChannel.INSTANCE);
        }
    }

    @Override
    public void handleServerPostConnect(String serverName) {
        if (this.isManaged) {
            this.setVoiceChannel0(this.voice.getServerVoiceChannel(serverName));
            this.onStateChanged();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void destroyVoiceManager() {
        IVoiceChannel oldChannel;
        VoiceManagerLocal voiceManagerLocal = this;
        synchronized (voiceManagerLocal) {
            if (!this.isAlive) {
                return;
            }
            this.isAlive = false;
            oldChannel = this.currentVoiceChannel;
            if (DisabledChannel.INSTANCE == oldChannel) {
                return;
            }
            this.currentVoiceChannel = DisabledChannel.INSTANCE;
        }
        ((VoiceChannel)oldChannel).removeFromChannel(this, true);
    }

    private void switchChannels(IVoiceChannel oldChannel, IVoiceChannel newChannel) {
        if (newChannel != DisabledChannel.INSTANCE) {
            if (oldChannel == DisabledChannel.INSTANCE) {
                this.enableVoice();
            }
            ((VoiceChannel)newChannel).addToChannel(this);
        } else {
            if (oldChannel != DisabledChannel.INSTANCE) {
                ((VoiceChannel)oldChannel).removeFromChannel(this, true);
            }
            this.disableVoice();
        }
    }

    private void enableVoice() {
        if (SERVER_ENABLE_HANDLE.compareAndSet(this, 0, 1)) {
            String[] iceServers = this.voice.getICEServersStr();
            if (iceServers == null) {
                iceServers = new String[]{};
            }
            this.player.sendEaglerMessage(new SPacketVoiceSignalAllowedEAG(true, iceServers));
        }
    }

    private void disableVoice() {
        if (SERVER_ENABLE_HANDLE.compareAndSet(this, 1, 0)) {
            this.player.sendEaglerMessage(new SPacketVoiceSignalAllowedEAG(false, null));
        }
    }

    void onStateChanged() {
        this.onStateChanged(this.getVoiceState());
    }

    void onStateChanged(EnumVoiceState newState) {
        EnumVoiceState oldState = LAST_STATE_HANDLE.getAndSet(this, newState);
        if (newState != oldState) {
            this.player.getEaglerXServer().eventDispatcher().dispatchVoiceChangeEvent(this.player, oldState, newState, null);
            BasePlayerRPCManager rpcMgr = this.player.getPlayerRPCManager();
            if (rpcMgr != null) {
                rpcMgr.fireToggleVoice(oldState, newState);
            }
        }
    }

    boolean ratelimitCon() {
        return this.player.getRateLimits().ratelimitVoiceCon();
    }

    boolean ratelimitReqV5() {
        return this.isBroken || this.player.getRateLimits().ratelimitVoiceReq();
    }

    boolean ratelimitICE() {
        return this.player.getRateLimits().ratelimitVoiceICE();
    }

    @Override
    public void handlePlayerSignalPacketTypeConnect() {
        VoiceChannel.Context ch = this.aquireActiveChannel();
        if (ch != null) {
            ch.handleVoiceSignalPacketTypeConnect();
        }
    }

    @Override
    public void handlePlayerSignalPacketTypeRequest(long playerUUIDMost, long playerUUIDLeast) {
        VoiceChannel.Context ch = this.aquireActiveChannel();
        if (ch != null) {
            ch.handleVoiceSignalPacketTypeRequest(new UUID(playerUUIDMost, playerUUIDLeast));
        }
    }

    @Override
    public void handlePlayerSignalPacketTypeICE(long playerUUIDMost, long playerUUIDLeast, byte[] str) {
        VoiceChannel.Context ch = this.aquireActiveChannel();
        if (ch != null) {
            ch.handleVoiceSignalPacketTypeICE(new UUID(playerUUIDMost, playerUUIDLeast), str);
        }
    }

    @Override
    public void handlePlayerSignalPacketTypeDesc(long playerUUIDMost, long playerUUIDLeast, byte[] str) {
        VoiceChannel.Context ch = this.aquireActiveChannel();
        if (ch != null) {
            ch.handleVoiceSignalPacketTypeDesc(new UUID(playerUUIDMost, playerUUIDLeast), str);
        }
    }

    @Override
    public void handlePlayerSignalPacketTypeDisconnectPeer(long playerUUIDMost, long playerUUIDLeast) {
        VoiceChannel.Context ch = this.aquireActiveChannel();
        if (ch != null) {
            ch.handleVoiceSignalPacketTypeDisconnectPeer(new UUID(playerUUIDMost, playerUUIDLeast));
        }
    }

    @Override
    public void handlePlayerSignalPacketTypeDisconnect() {
        VoiceChannel.Context ch = this.aquireActiveChannel();
        if (ch != null) {
            ch.handleVoiceSignalPacketTypeDisconnect();
        }
    }

    @Override
    public void handleBackendMessage(byte[] data) {
        this.player.logger().warn("Ignoring plugin message from backend on voice RPC channel, server is not in backend-relayed mode");
    }
}

