/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.voice;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.stream.Collectors;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformLogger;
import net.lax1dude.eaglercraft.backend.server.api.IEaglerPlayer;
import net.lax1dude.eaglercraft.backend.server.api.voice.EnumVoiceState;
import net.lax1dude.eaglercraft.backend.server.api.voice.IVoiceChannel;
import net.lax1dude.eaglercraft.backend.server.api.voice.IVoiceService;
import net.lax1dude.eaglercraft.backend.server.base.EaglerPlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.voice.IVoiceManagerImpl;
import net.lax1dude.eaglercraft.backend.server.base.voice.SerializationContext;
import net.lax1dude.eaglercraft.backend.server.base.voice.ServerV1VCProtocolHandler;
import net.lax1dude.eaglercraft.backend.server.base.voice.ServerVCProtocolHandler;
import net.lax1dude.eaglercraft.backend.server.base.voice.VoiceServiceRemote;
import net.lax1dude.eaglercraft.backend.voice.protocol.EaglerVCProtocol;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.EaglerVCPacket;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.WrongVCPacketException;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.client.CPacketVCCapable;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.client.CPacketVCConnect;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.client.CPacketVCConnectPeer;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.client.CPacketVCDescription;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.client.CPacketVCDisconnect;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.client.CPacketVCDisconnectPeer;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.client.CPacketVCICECandidate;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.server.SPacketVCCapable;
import net.lax1dude.eaglercraft.backend.voice.protocol.pkt.server.SPacketVCPlayerList;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketVoiceSignalAllowedEAG;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketVoiceSignalConnectAnnounceV4EAG;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketVoiceSignalConnectV4EAG;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketVoiceSignalDescEAG;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketVoiceSignalDisconnectPeerEAG;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketVoiceSignalGlobalEAG;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketVoiceSignalICEEAG;

public class VoiceManagerRemote<PlayerObject>
extends SerializationContext
implements IVoiceManagerImpl<PlayerObject> {
    private static final AtomicIntegerFieldUpdater<VoiceManagerRemote> STATE_HANDLE = AtomicIntegerFieldUpdater.newUpdater(VoiceManagerRemote.class, "state");
    final EaglerPlayerInstance<PlayerObject> player;
    final VoiceServiceRemote<PlayerObject> voice;
    private volatile int state = -1;
    private ServerVCProtocolHandler handler;
    final boolean isBroken;
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    VoiceManagerRemote(EaglerPlayerInstance<PlayerObject> player, VoiceServiceRemote<PlayerObject> voice) {
        super(player.getSerializationContext());
        this.player = player;
        this.voice = voice;
        this.isBroken = player.getEaglerProtocol().ver < 5;
    }

    @Override
    protected IPlatformLogger logger() {
        return this.player.logger();
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
        return true;
    }

    private int stateXchg(int newValue) {
        return STATE_HANDLE.getAndSet(this, newValue);
    }

    private boolean stateCmpXchg(int oldValue, int newValue) {
        return STATE_HANDLE.compareAndSet(this, oldValue, newValue);
    }

    @Override
    public EnumVoiceState getVoiceState() {
        switch (this.state) {
            case 1: {
                return EnumVoiceState.DISABLED;
            }
            case 2: {
                return EnumVoiceState.ENABLED;
            }
        }
        return EnumVoiceState.SERVER_DISABLE;
    }

    private boolean isVoiceEnabled() {
        return this.state == 2;
    }

    @Override
    public IVoiceChannel getVoiceChannel() {
        throw VoiceServiceRemote.backendRelayMode();
    }

    @Override
    public void setVoiceChannel(IVoiceChannel channel) {
        throw VoiceServiceRemote.backendRelayMode();
    }

    @Override
    public boolean isServerManaged() {
        throw VoiceServiceRemote.backendRelayMode();
    }

    @Override
    public void setServerManaged(boolean managed) {
        throw VoiceServiceRemote.backendRelayMode();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void handleBackendMessage(byte[] data) {
        EaglerVCPacket pkt;
        block12: {
            if (this.state == -1) {
                VoiceManagerRemote voiceManagerRemote = this;
                synchronized (voiceManagerRemote) {
                    EaglerVCPacket pkt2;
                    if (this.state != -1) {
                        break block12;
                    }
                    try {
                        pkt2 = this.deserialize(EaglerVCProtocol.INIT, data);
                    }
                    catch (Exception e) {
                        this.player.logger().warn("Dropping invalid voice RPC packet on uninitialized connection: " + e);
                        return;
                    }
                    this.handleBackendHandshake(pkt2);
                    return;
                }
            }
        }
        try {
            pkt = this.deserialize(EaglerVCProtocol.V1, data);
        }
        catch (Exception e) {
            this.handleException(e);
            return;
        }
        if (this.handler == null) {
            this.player.logger().warn("Received voice RPC packet before handshake completed: " + pkt.getClass().getSimpleName());
            return;
        }
        try {
            pkt.handlePacket(this.handler);
        }
        catch (Exception e) {
            this.handleException(new IllegalStateException("Failed to handle inbound voice RPC packet: " + pkt.getClass().getSimpleName(), e));
        }
    }

    private void handleBackendHandshake(EaglerVCPacket packet) {
        if (packet instanceof SPacketVCCapable) {
            SPacketVCCapable pkt = (SPacketVCCapable)packet;
            if (pkt.version != 1) {
                throw new IllegalStateException("Wrong protocol version selected: " + pkt.version);
            }
            this.handler = new ServerV1VCProtocolHandler(this, VoiceManagerRemote.internStrings(pkt.iceServers), pkt.overrideICE);
            if (pkt.allowed) {
                this.state = 1;
                this.voiceEnabled();
            } else {
                this.state = 0;
            }
        } else {
            throw new WrongVCPacketException();
        }
    }

    private static String[] internStrings(String[] strs) {
        if (strs.length == 0) {
            return EMPTY_STRING_ARRAY;
        }
        for (int i = 0; i < strs.length; ++i) {
            strs[i] = strs[i].intern();
        }
        return strs;
    }

    private void handleException(Exception e) {
        this.player.logger().error("Caught exception handling voice RPC packet from backend", e);
    }

    private void sendBackendMessage(EaglerVCPacket packet) {
        this.sendBackendMessage(EaglerVCProtocol.V1, packet);
    }

    private void sendBackendMessage(EaglerVCProtocol protocol, EaglerVCPacket packet) {
        byte[] pkt;
        try {
            pkt = this.serialize(protocol, packet);
        }
        catch (IOException e) {
            this.handleException(e);
            return;
        }
        this.player.getPlatformPlayer().sendDataBackend(this.voice.getRPCChannel(), pkt);
    }

    @Override
    public void handleServerPreConnect() {
        int lastState = this.stateXchg(-1);
        this.handler = null;
        if (lastState != 0 && lastState != -1) {
            this.voiceDisabled(lastState == 2);
        }
    }

    @Override
    public void handleServerPostConnect(String serverName) {
        this.sendBackendMessage(EaglerVCProtocol.INIT, new CPacketVCCapable(new int[]{1}));
    }

    private String[] concatICEServers() {
        ServerVCProtocolHandler h = this.handler;
        if (h != null) {
            String[] iceServers = h.iceServerStash;
            if (h.iceServerOverride) {
                return iceServers;
            }
            HashSet<String> joined = new HashSet<String>();
            VoiceManagerRemote.addAll(joined, this.voice.iceServersStr());
            VoiceManagerRemote.addAll(joined, iceServers);
            return joined.toArray(new String[joined.size()]);
        }
        return this.voice.iceServersStr();
    }

    private static void addAll(Set<String> set, String[] strs) {
        for (int i = 0; i < strs.length; ++i) {
            set.add(strs[i]);
        }
    }

    private void voiceEnabled() {
        this.player.sendEaglerMessage(new SPacketVoiceSignalAllowedEAG(true, this.concatICEServers()));
        this.player.getEaglerXServer().eventDispatcher().dispatchVoiceChangeEvent(this.player, EnumVoiceState.SERVER_DISABLE, EnumVoiceState.DISABLED, null);
    }

    private void voiceConnected() {
        this.player.getEaglerXServer().eventDispatcher().dispatchVoiceChangeEvent(this.player, EnumVoiceState.DISABLED, EnumVoiceState.ENABLED, null);
    }

    private void voiceDisconnected() {
        this.player.getEaglerXServer().eventDispatcher().dispatchVoiceChangeEvent(this.player, EnumVoiceState.ENABLED, EnumVoiceState.DISABLED, null);
    }

    private void voiceDisabled(boolean wasConnected) {
        this.player.sendEaglerMessage(new SPacketVoiceSignalAllowedEAG(false, null));
        this.player.getEaglerXServer().eventDispatcher().dispatchVoiceChangeEvent(this.player, wasConnected ? EnumVoiceState.ENABLED : EnumVoiceState.DISABLED, EnumVoiceState.SERVER_DISABLE, null);
    }

    @Override
    public void destroyVoiceManager() {
        int lastState = this.stateXchg(-1);
        this.handler = null;
        if (lastState == 1 || lastState == 2) {
            try {
                this.sendBackendMessage(new CPacketVCDisconnect());
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        if (lastState == 2) {
            this.voiceDisconnected();
        }
    }

    private boolean ratelimitCon() {
        return this.player.getRateLimits().ratelimitVoiceCon();
    }

    private boolean ratelimitReqV5() {
        return this.isBroken || this.player.getRateLimits().ratelimitVoiceReq();
    }

    private boolean ratelimitICE() {
        return this.player.getRateLimits().ratelimitVoiceICE();
    }

    @Override
    public void handlePlayerSignalPacketTypeConnect() {
        if (this.ratelimitCon() && this.stateCmpXchg(1, 2)) {
            this.sendBackendMessage(new CPacketVCConnect());
            this.voiceConnected();
        }
    }

    @Override
    public void handlePlayerSignalPacketTypeRequest(long playerUUIDMost, long playerUUIDLeast) {
        if (this.isVoiceEnabled() && this.ratelimitReqV5()) {
            this.sendBackendMessage(new CPacketVCConnectPeer(playerUUIDMost, playerUUIDLeast));
        }
    }

    @Override
    public void handlePlayerSignalPacketTypeICE(long playerUUIDMost, long playerUUIDLeast, byte[] str) {
        if (this.isVoiceEnabled() && this.ratelimitICE()) {
            this.sendBackendMessage(new CPacketVCICECandidate(playerUUIDMost, playerUUIDLeast, str));
        }
    }

    @Override
    public void handlePlayerSignalPacketTypeDesc(long playerUUIDMost, long playerUUIDLeast, byte[] str) {
        if (this.isVoiceEnabled() && this.ratelimitICE()) {
            this.sendBackendMessage(new CPacketVCDescription(playerUUIDMost, playerUUIDLeast, str));
        }
    }

    @Override
    public void handlePlayerSignalPacketTypeDisconnectPeer(long playerUUIDMost, long playerUUIDLeast) {
        if (this.isVoiceEnabled()) {
            this.sendBackendMessage(new CPacketVCDisconnectPeer(playerUUIDMost, playerUUIDLeast));
        }
    }

    @Override
    public void handlePlayerSignalPacketTypeDisconnect() {
        if (this.stateCmpXchg(2, 1)) {
            this.sendBackendMessage(new CPacketVCDisconnect());
            this.voiceDisconnected();
        }
    }

    public void handleBackendSignalPacketAllowed(boolean allowed) {
        if (allowed) {
            if (this.stateCmpXchg(0, 1)) {
                this.voiceEnabled();
            }
        } else {
            int lastState = this.stateXchg(0);
            if (lastState != 0) {
                if (lastState == -1) {
                    this.state = -1;
                    throw new IllegalStateException("shit");
                }
                this.voiceDisabled(lastState == 2);
            }
        }
    }

    public void handleBackendSignalPacketPlayerList(Collection<SPacketVCPlayerList.UserData> users) {
        if (this.isVoiceEnabled()) {
            this.player.sendEaglerMessage(new SPacketVoiceSignalGlobalEAG(users.stream().map(data -> new SPacketVoiceSignalGlobalEAG.UserData(data.uuidMost, data.uuidLeast, data.username)).collect(Collectors.toList())));
        }
    }

    public void handleBackendSignalPacketAnnounce(long uuidMost, long uuidLeast) {
        if (this.isVoiceEnabled()) {
            this.player.sendEaglerMessage(new SPacketVoiceSignalConnectAnnounceV4EAG(uuidMost, uuidLeast));
        }
    }

    public void handleBackendSignalPacketConnectPeer(long uuidMost, long uuidLeast, boolean offer) {
        if (this.isVoiceEnabled()) {
            this.player.sendEaglerMessage(new SPacketVoiceSignalConnectV4EAG(uuidMost, uuidLeast, offer));
        }
    }

    public void handleBackendSignalPacketDisconnectPeer(long uuidMost, long uuidLeast) {
        if (this.isVoiceEnabled()) {
            this.player.sendEaglerMessage(new SPacketVoiceSignalDisconnectPeerEAG(uuidMost, uuidLeast));
        }
    }

    public void handleBackendSignalPacketDescription(long uuidMost, long uuidLeast, byte[] desc) {
        if (this.isVoiceEnabled()) {
            this.player.sendEaglerMessage(new SPacketVoiceSignalDescEAG(uuidMost, uuidLeast, desc));
        }
    }

    public void handleBackendSignalPacketICECandidate(long uuidMost, long uuidLeast, byte[] ice) {
        if (this.isVoiceEnabled()) {
            this.player.sendEaglerMessage(new SPacketVoiceSignalICEEAG(uuidMost, uuidLeast, ice));
        }
    }
}

