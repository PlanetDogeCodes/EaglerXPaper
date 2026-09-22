/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.rpc;

import java.util.UUID;
import java.util.stream.Collectors;
import net.lax1dude.eaglercraft.backend.rpc.protocol.EaglerBackendRPCProtocol;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCEnabledSuccess;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCEnabledSuccessEaglerV2;
import net.lax1dude.eaglercraft.backend.server.base.EaglerPlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.rpc.BackendRPCService;
import net.lax1dude.eaglercraft.backend.server.base.rpc.BasePlayerRPCManager;
import net.lax1dude.eaglercraft.backend.server.base.rpc.EaglerPlayerRPCContext;

public class EaglerPlayerRPCManager<PlayerObject>
extends BasePlayerRPCManager<PlayerObject> {
    private final EaglerPlayerInstance<PlayerObject> player;

    EaglerPlayerRPCManager(BackendRPCService<PlayerObject> service, EaglerPlayerInstance<PlayerObject> player) {
        super(service);
        this.player = player;
    }

    @Override
    public EaglerPlayerInstance<PlayerObject> getPlayer() {
        return this.player;
    }

    @Override
    public boolean isEaglerPlayer() {
        return true;
    }

    @Override
    protected void handleEnabled(EaglerBackendRPCProtocol protocol) {
        if (protocol == EaglerBackendRPCProtocol.V1) {
            this.sendRPCInitPacket(new SPacketRPCEnabledSuccess(protocol.vers, this.player.getEaglerProtocol().ver));
        } else {
            this.sendRPCInitPacket(new SPacketRPCEnabledSuccessEaglerV2(protocol.vers, this.player.getMinecraftProtocol(), this.player.getEaglerXServer().getSupervisorService().getNodeId(), this.player.getHandshakeEaglerProtocol(), this.player.getEaglerProtocol().ver, this.player.getRewindProtocolVersion(), this.player.getCapabilityMask(), this.player.getCapabilityVers(), this.player.getExtCapabilities().entrySet().stream().map(etr -> new SPacketRPCEnabledSuccessEaglerV2.ExtCapability((UUID)etr.getKey(), (int)((Byte)etr.getValue() & 0xFF))).collect(Collectors.toList())));
        }
        this.handleEnableContext(new EaglerPlayerRPCContext(this, protocol));
    }

    @Override
    protected void sendReadyMessage() {
        int renderDistance = this.player.getEaglerXServer().getConfig().getSettings().getEaglerPlayersViewDistance();
        this.player.getPlatformPlayer().sendDataBackend(this.service.getReadyChannel(), new byte[]{1, (byte)renderDistance});
    }
}

