/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.rpc;

import java.io.IOException;
import net.lax1dude.eaglercraft.backend.rpc.protocol.EaglerBackendRPCProtocol;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.EaglerBackendRPCPacket;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCEnabled;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCEnabledFailure;
import net.lax1dude.eaglercraft.backend.server.api.voice.EnumVoiceState;
import net.lax1dude.eaglercraft.backend.server.base.BasePlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.rpc.BackendRPCService;
import net.lax1dude.eaglercraft.backend.server.base.rpc.BasePlayerRPCContext;

public abstract class BasePlayerRPCManager<PlayerObject> {
    protected final BackendRPCService<PlayerObject> service;
    protected volatile BasePlayerRPCContext<PlayerObject> context;

    BasePlayerRPCManager(BackendRPCService<PlayerObject> service) {
        this.service = service;
    }

    public abstract BasePlayerInstance<PlayerObject> getPlayer();

    public abstract boolean isEaglerPlayer();

    public void sendRPCInitPacket(EaglerBackendRPCPacket packet) {
        byte[] data;
        try {
            data = this.service.handshakeCtx.serialize(packet);
        }
        catch (IOException e) {
            this.handleException(e);
            return;
        }
        this.getPlayer().getPlatformPlayer().sendDataBackend(this.service.getRPCChannel(), data);
    }

    public void sendRPCPacket(EaglerBackendRPCPacket packet) {
        BasePlayerRPCContext<PlayerObject> ctx = this.context;
        if (ctx != null) {
            byte[] data;
            try {
                data = ctx.serialize(packet);
            }
            catch (IOException e) {
                this.handleException(e);
                return;
            }
            this.getPlayer().getPlatformPlayer().sendDataBackend(this.service.getRPCChannel(), data);
        }
    }

    public void handleRPCPacketData(byte[] data) {
        BasePlayerRPCContext<PlayerObject> ctx = this.context;
        if (ctx != null) {
            EaglerBackendRPCPacket packet;
            try {
                packet = ctx.deserialize(data);
            }
            catch (IOException e) {
                this.handleException(e);
                return;
            }
            try {
                packet.handlePacket(ctx.packetHandler());
            }
            catch (Exception e) {
                this.handleException(e);
            }
        } else {
            EaglerBackendRPCPacket packet;
            try {
                packet = this.service.handshakeCtx.deserialize(data);
            }
            catch (IOException e) {
                this.handleException(e);
                return;
            }
            if (packet instanceof CPacketRPCEnabled) {
                CPacketRPCEnabled pkt = (CPacketRPCEnabled)packet;
                boolean V1 = false;
                boolean V2 = false;
                for (int i : pkt.supportedProtocols) {
                    if (i == 1) {
                        V1 = true;
                    }
                    if (i == 2) {
                        V2 = true;
                    }
                    if (V2) break;
                }
                if (V2) {
                    this.handleEnabled(EaglerBackendRPCProtocol.V2);
                } else if (V1) {
                    this.handleEnabled(EaglerBackendRPCProtocol.V1);
                } else {
                    this.sendRPCInitPacket(new SPacketRPCEnabledFailure(2));
                }
            } else {
                this.handleException(new IllegalStateException("Unexpected packet type for handshake: " + packet.getClass().getName()));
                this.sendRPCInitPacket(new SPacketRPCEnabledFailure(255));
            }
        }
    }

    BasePlayerRPCContext<PlayerObject> context() {
        BasePlayerRPCContext<PlayerObject> ctx = this.context;
        if (ctx != null) {
            return ctx;
        }
        throw new IllegalStateException();
    }

    void handleException(Exception ex) {
        this.getPlayer().getEaglerXServer().logger().error("Exception thrown while handling backend RPC packet for \"" + this.getPlayer().getUsername() + "\"!", ex);
    }

    protected abstract void handleEnabled(EaglerBackendRPCProtocol var1);

    protected void handleEnableContext(BasePlayerRPCContext<PlayerObject> ctx) {
        this.context = ctx;
    }

    void handleDisabled() {
        this.context = null;
    }

    public void handleServerPreConnect() {
        BasePlayerRPCContext<PlayerObject> ctx = this.context;
        if (ctx != null) {
            ctx.handleDisabled();
        }
    }

    public void handleServerPostConnect() {
        this.sendReadyMessage();
    }

    protected abstract void sendReadyMessage();

    public void fireWebViewOpenClose(boolean open, String channel) {
        BasePlayerRPCContext<PlayerObject> ctx = this.context;
        if (ctx != null) {
            ctx.fireWebViewOpenClose(open, channel);
        }
    }

    public void fireWebViewMessage(String channel, boolean binary, byte[] data) {
        BasePlayerRPCContext<PlayerObject> ctx = this.context;
        if (ctx != null) {
            ctx.fireWebViewMessage(channel, binary, data);
        }
    }

    public void fireToggleVoice(EnumVoiceState oldVoiceState, EnumVoiceState newVoiceState) {
        BasePlayerRPCContext<PlayerObject> ctx = this.context;
        if (ctx != null) {
            ctx.fireToggleVoice(oldVoiceState, newVoiceState);
        }
    }
}

