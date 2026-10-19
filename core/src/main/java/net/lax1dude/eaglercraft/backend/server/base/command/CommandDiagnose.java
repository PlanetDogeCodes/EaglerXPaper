/*
 * Copyright (c) 2025 lax1dude. All Rights Reserved.
 *
 * /eaglerdiagnose (EaglerXPaper v1.1.1 Stable 2 — restored feature).
 * With no arguments: prints server-wide EaglerXServer diagnostics (version,
 * platform, listeners, connected Eaglercraft players, feature flags).
 * With a player name argument: prints that player's connection diagnostics
 * (protocols, brand, real address, pipeline handler layout, batcher state,
 * traffic statistics). This command was part of the H24-era feature set that
 * was lost when the source tree was rebuilt from decompiled bytecode for
 * v1.1.1 Stable 1 and is reinstated here.
 */

package net.lax1dude.eaglercraft.backend.server.base.command;

import io.netty.channel.ChannelPipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.lax1dude.eaglercraft.backend.server.adapter.IEaglerXServerCommandType;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformCommandSender;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformComponentBuilder.EnumChatColor;
import net.lax1dude.eaglercraft.backend.server.base.EaglerListener;
import net.lax1dude.eaglercraft.backend.server.base.EaglerPlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServer;
import net.lax1dude.eaglercraft.backend.server.base.config.ConfigDataListener;
import net.lax1dude.eaglercraft.backend.server.base.config.EaglerXPaperConfig;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.AdaptivePacketBatcher;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.ConnectionStatsHandler;

public class CommandDiagnose<PlayerObject> extends EaglerCommand<PlayerObject> {

    public CommandDiagnose(EaglerXServer<PlayerObject> server) {
        super(server, "eaglerdiagnose", "eaglercraft.command.diagnose", "eaglerdiag");
    }

    @Override
    public void handle(IEaglerXServerCommandType<PlayerObject> command, IPlatformCommandSender<PlayerObject> sender,
            String[] args) {
        if (args != null && args.length > 0 && !args[0].isEmpty()) {
            diagnosePlayer(sender, args[0]);
        } else {
            diagnoseServer(sender);
        }
    }

    private void diagnoseServer(IPlatformCommandSender<PlayerObject> sender) {
        send(sender, EnumChatColor.AQUA, "EaglerXServer diagnostics:");
        send(sender, EnumChatColor.GOLD, "  Version: " + getServer().getServerVersion() + " ("
                + getServer().getServerBrand() + ")");
        send(sender, EnumChatColor.GOLD, "  Platform: " + getServer().getPlatformType().getName());
        final int[] count = new int[1];
        final long[] totalOut = new long[1];
        final long[] totalIn = new long[1];
        getServer().forEachEaglerPlayerInternal(player -> {
            count[0]++;
            if (player.getChannel() != null) {
                ConnectionStatsHandler stats = (ConnectionStatsHandler) player.getChannel().pipeline()
                                .get(ConnectionStatsHandler.HANDLER_NAME);
                if (stats != null) {
                    totalOut[0] += stats.getBytesOut();
                    totalIn[0] += stats.getBytesIn();
                }
            }
        });
        send(sender, EnumChatColor.GOLD, "  Eagler players connected: " + count[0]);
        // Round 4: traffic now covers the complete wire stream (vanilla MC packets
        // + Eagler-protocol plugin frames), counted head-ward of the frame codec
        send(sender, EnumChatColor.GOLD, "  Total traffic (wire): out " + ConnectionStatsHandler.formatBytes(totalOut[0])
                + ", in " + ConnectionStatsHandler.formatBytes(totalIn[0]));
        List<ConfigDataListener> listeners = new ArrayList<>(getServer().getConfig().getListeners().values());
        send(sender, EnumChatColor.GOLD, "  Listeners: " + listeners.size());
        for (ConfigDataListener listener : listeners) {
            send(sender, EnumChatColor.YELLOW, "    - " + listener.getListenerName() + " @ "
                    + listener.getInjectAddress());
        }
        send(sender, EnumChatColor.YELLOW, "  Adaptive packet batching: "
                + (EaglerXPaperConfig.enableAdaptiveBatching ? "enabled" : "disabled"));
        send(sender, EnumChatColor.YELLOW, "  Outbound packet journal: "
                + (EaglerXPaperConfig.enablePacketJournal ? "enabled" : "disabled"));
        send(sender, EnumChatColor.YELLOW, "  Skin cache pre-warm: "
                + (EaglerXPaperConfig.enableSkinPrewarm ? "enabled" : "disabled"));
        send(sender, EnumChatColor.AQUA, "Use /eaglerdiagnose <player> for per-player connection details");
    }

    private void diagnosePlayer(IPlatformCommandSender<PlayerObject> sender, String name) {
        final String want = name.toLowerCase(Locale.ROOT);
        final EaglerPlayerInstance<PlayerObject>[] found = new EaglerPlayerInstance[1];
        getServer().forEachEaglerPlayerInternal(player -> {
            if (found[0] == null && player.getPlatformPlayer() != null
                            && want.equals(player.getPlatformPlayer().getUsername().toLowerCase(Locale.ROOT))) {
                found[0] = player;
            }
        });
        EaglerPlayerInstance<PlayerObject> player = found[0];
        if (player == null) {
            send(sender, EnumChatColor.RED, "No connected Eaglercraft player named \"" + name + "\"");
            return;
        }
        send(sender, EnumChatColor.AQUA, "Connection diagnostics for " + player.getPlatformPlayer().getUsername() + ":");
        send(sender, EnumChatColor.GOLD, "  Client: " + player.getEaglerBrandString() + " "
                + player.getEaglerVersionString());
        send(sender, EnumChatColor.GOLD, "  Protocols: MC " + player.getMinecraftProtocol() + ", EAG "
                + player.getEaglerProtocol());
        send(sender, EnumChatColor.GOLD, "  Real address: " + (player.getRealAddress() != null
                ? player.getRealAddress() : "unknown"));
        send(sender, EnumChatColor.GOLD, "  Secure WebSocket: " + (player.isWebSocketSecure() ? "yes" : "no"));
        if (player.getChannel() != null) {
            // Stable 2 fix (round 5): the pipeline is event-loop-confined; reading it
            // from the command thread is best-effort and must never throw into the
            // command handler.
            try {
                ChannelPipeline pipeline = player.getChannel().pipeline();
                ConnectionStatsHandler stats = (ConnectionStatsHandler) pipeline.get(ConnectionStatsHandler.HANDLER_NAME);
                if (stats != null) {
                    send(sender, EnumChatColor.YELLOW, "  Up " + stats.getConnectedSeconds() + "s, out "
                            + ConnectionStatsHandler.formatBytes(stats.getBytesOut()) + " ("
                            + ConnectionStatsHandler.formatBytes(stats.getBytesOutPerSecond()) + "/s), in "
                            + ConnectionStatsHandler.formatBytes(stats.getBytesIn()) + " ("
                            + ConnectionStatsHandler.formatBytes(stats.getBytesInPerSecond()) + "/s)");
                    send(sender, EnumChatColor.YELLOW, "  Packets: " + stats.getPacketsOut() + " out, "
                            + stats.getPacketsIn() + " in");
                }
                Object batcherObj = pipeline.get(AdaptivePacketBatcher.HANDLER_NAME);
                if (batcherObj instanceof AdaptivePacketBatcher) {
                    AdaptivePacketBatcher batcher = (AdaptivePacketBatcher) batcherObj;
                    send(sender, EnumChatColor.YELLOW, "  Batcher: active=" + batcher.isBatchingActive()
                            + ", buffered=" + batcher.getBufferSize() + " packets / "
                            + batcher.getBufferedBytes() + " bytes");
                } else {
                    send(sender, EnumChatColor.YELLOW, "  Batcher: not installed");
                }
                boolean frameCodec = pipeline.get("eagler-frame-codec") != null;
                boolean compressionGuard = pipeline.get("eagler-compression-guard") != null;
                boolean journal = pipeline.get("eagler-packet-journal") != null;
                send(sender, EnumChatColor.YELLOW, "  Pipeline: frame-codec=" + (frameCodec ? "ok" : "MISSING")
                        + ", compression-guard=" + (compressionGuard ? "ok" : "MISSING")
                        + ", journal=" + (journal ? "on" : "off"));
                send(sender, EnumChatColor.YELLOW, "  Pipeline handlers: " + pipeline.names());
            }
            catch (Throwable t) {
                send(sender, EnumChatColor.YELLOW, "  Pipeline state unavailable (connection busy): " + t);
            }
        }
    }

    private void send(IPlatformCommandSender<PlayerObject> sender, EnumChatColor color, String text) {
        sender.sendMessage(getChatBuilder().buildTextComponent().beginStyle().color(color).end()
                .text(text).end());
    }
}
