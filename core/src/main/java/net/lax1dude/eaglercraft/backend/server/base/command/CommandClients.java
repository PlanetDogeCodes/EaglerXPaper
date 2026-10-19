/*
 * Copyright (c) 2025 lax1dude. All Rights Reserved.
 *
 * /eaglerclients (EaglerXPaper v1.1.1 Stable 2 — restored feature).
 * Lists every connected Eaglercraft player with username, client brand,
 * client version, protocols, real IP, connection duration and traffic
 * statistics. This command was part of the H24-era feature set that was
 * lost when the source tree was rebuilt from decompiled bytecode for
 * v1.1.1 Stable 1 and is reinstated here.
 */

package net.lax1dude.eaglercraft.backend.server.base.command;

import net.lax1dude.eaglercraft.backend.server.adapter.IEaglerXServerCommandType;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformCommandSender;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformComponentBuilder.EnumChatColor;
import net.lax1dude.eaglercraft.backend.server.base.EaglerPlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServer;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.ConnectionStatsHandler;

public class CommandClients<PlayerObject> extends EaglerCommand<PlayerObject> {

    public CommandClients(EaglerXServer<PlayerObject> server) {
        super(server, "eaglerclients", "eaglercraft.command.clients", "eaglerplayers");
    }

    @Override
    public void handle(IEaglerXServerCommandType<PlayerObject> command, IPlatformCommandSender<PlayerObject> sender,
            String[] args) {
        final int[] count = new int[1];
        getServer().forEachEaglerPlayerInternal(player -> {
            count[0]++;
            ConnectionStatsHandler stats = null;
            // Stable 2 fix (round 5): the pipeline is event-loop-confined; reading it
            // from the command thread is best-effort and must never throw into the
            // command handler.
            try {
                stats = player.getChannel() != null
                                ? (ConnectionStatsHandler) player.getChannel().pipeline().get(ConnectionStatsHandler.HANDLER_NAME)
                                : null;
            }
            catch (Throwable t) {
                stats = null;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(player.getPlatformPlayer().getUsername());
            sb.append(" - ").append(player.getEaglerBrandString());
            sb.append(" ").append(player.getEaglerVersionString());
            sb.append(" (MC proto ").append(player.getMinecraftProtocol());
            sb.append(", EAG proto ").append(player.getEaglerProtocol()).append(")");
            String addr = player.getRealAddress();
            if (addr == null || addr.isEmpty()) {
                addr = "unknown";
            }
            sb.append(" - IP: ").append(addr);
            if (stats != null) {
                sb.append(" - up ").append(stats.getConnectedSeconds()).append("s, out ");
                sb.append(ConnectionStatsHandler.formatBytes(stats.getBytesOut()));
                sb.append(" (").append(ConnectionStatsHandler.formatBytes(stats.getBytesOutPerSecond())).append("/s)");
                sb.append(", in ");
                sb.append(ConnectionStatsHandler.formatBytes(stats.getBytesIn()));
                sb.append(" (").append(ConnectionStatsHandler.formatBytes(stats.getBytesInPerSecond())).append("/s)");
            } else {
                sb.append(" - no stats handler");
            }
            sender.sendMessage(getChatBuilder().buildTextComponent().beginStyle().color(EnumChatColor.GOLD).end()
                    .text(sb.toString()).end());
        });
        if (count[0] == 0) {
            sender.sendMessage(getChatBuilder().buildTextComponent().beginStyle().color(EnumChatColor.YELLOW).end()
                    .text("No Eaglercraft players are connected").end());
        } else {
            sender.sendMessage(getChatBuilder().buildTextComponent().beginStyle().color(EnumChatColor.AQUA).end()
                    .text(count[0] + " Eaglercraft player(s) connected").end());
        }
    }
}
