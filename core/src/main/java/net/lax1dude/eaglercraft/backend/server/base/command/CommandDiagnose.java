/*
 * Copyright (c) 2025 lax1dude. All Rights Reserved.
 *
 * EaglerXPaper-exclusive command: /eagler diagnose
 * Prints a health check covering all critical subsystems.
 */

package net.lax1dude.eaglercraft.backend.server.base.command;

import net.lax1dude.eaglercraft.backend.server.adapter.IEaglerXServerCommandType;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformCommandSender;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformComponentBuilder.EnumChatColor;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServer;
import net.lax1dude.eaglercraft.backend.server.base.config.EaglerXPaperConfig;

public class CommandDiagnose<PlayerObject> extends EaglerCommand<PlayerObject> {

    public CommandDiagnose(EaglerXServer<PlayerObject> server) {
        super(server, "eaglerdiagnose", "eaglercraft.command.diagnose", "eaglerdiag");
    }

    @Override
    public void handle(IEaglerXServerCommandType<PlayerObject> command, IPlatformCommandSender<PlayerObject> sender,
                    String[] args) {
        EaglerXServer<PlayerObject> srv = getServer();

        send(sender, EnumChatColor.GOLD, "=== EaglerXPaper Diagnostics ===");

        send(sender, EnumChatColor.AQUA, "Plugin: ", EnumChatColor.WHITE,
                        srv.getServerBrand() + " " + srv.getServerVersion());

        send(sender, EnumChatColor.AQUA, "Platform: ", EnumChatColor.WHITE, srv.getPlatformType().getName());

        // Config details
        boolean tlsEnabled = false, forwardIP = false, dualStack = false;
        int frameSize = -1, minProto = -1, maxProto = -1;
        try {
            var settings = srv.getConfig().getSettings();
            frameSize = settings.getHTTPWebSocketMaxFrameLength();
            minProto = settings.getProtocols().getMinMinecraftProtocol();
            maxProto = settings.getProtocols().getMaxMinecraftProtocol();
            for (var listener : srv.getConfig().getListeners().values()) {
                if (listener.isEnableTLS()) tlsEnabled = true;
                if (listener.isForwardIP()) forwardIP = true;
                if (listener.isDualStack()) dualStack = true;
            }
        } catch (Exception e) {
            send(sender, EnumChatColor.RED, "Config read failed: " + e.getMessage());
        }

        send(sender, EnumChatColor.AQUA, "TLS: ",
                        tlsEnabled ? EnumChatColor.GREEN : EnumChatColor.YELLOW,
                        tlsEnabled ? "Enabled (built-in)" : "Disabled (use reverse proxy for wss://)");

        send(sender, EnumChatColor.AQUA, "IP Forwarding: ",
                        forwardIP ? EnumChatColor.GREEN : EnumChatColor.YELLOW,
                        forwardIP ? "Enabled" : "Disabled");

        send(sender, EnumChatColor.AQUA, "Dual-Stack: ",
                        dualStack ? EnumChatColor.GREEN : EnumChatColor.YELLOW,
                        dualStack ? "Enabled" : "Disabled");

        send(sender, EnumChatColor.AQUA, "WS Frame Limit: ", EnumChatColor.WHITE,
                        frameSize >= 0 ? frameSize + " bytes (" + (frameSize / 1024) + " KB)" : "unknown");

        send(sender, EnumChatColor.AQUA, "Allowed MC Protocols: ", EnumChatColor.WHITE,
                        minProto >= 0 ? minProto + "-" + maxProto : "unknown");

        send(sender, EnumChatColor.AQUA, "Skin Prewarming: ",
                        EaglerXPaperConfig.enableSkinPrewarm ? EnumChatColor.GREEN : EnumChatColor.YELLOW,
                        EaglerXPaperConfig.enableSkinPrewarm ? "Enabled" : "Disabled");

        send(sender, EnumChatColor.AQUA, "Adaptive Batching: ",
                        EaglerXPaperConfig.enableAdaptiveBatching ? EnumChatColor.GREEN : EnumChatColor.YELLOW,
                        EaglerXPaperConfig.enableAdaptiveBatching ? "Enabled" : "Disabled");

        // ViaVersion detection (via static flag set by PlatformPluginBukkit)
        Boolean viaVersion = ViaVersionDetector.isInstalled();
        Boolean viaBackwards = ViaVersionDetector.isBackwardsInstalled();
        Boolean viaRewind = ViaVersionDetector.isRewindInstalled();

        if (viaVersion != null) {
            send(sender, EnumChatColor.AQUA, "ViaVersion: ",
                            viaVersion ? EnumChatColor.GREEN : EnumChatColor.RED,
                            viaVersion ? "Installed" : "NOT INSTALLED! (Eaglercraft clients can't join!)");
        }
        if (viaBackwards != null) {
            send(sender, EnumChatColor.AQUA, "ViaBackwards: ",
                            viaBackwards ? EnumChatColor.GREEN : EnumChatColor.YELLOW,
                            viaBackwards ? "Installed" : "Not installed (recommended)");
        }
        if (viaRewind != null) {
            send(sender, EnumChatColor.AQUA, "ViaRewind: ",
                            viaRewind ? EnumChatColor.GREEN : EnumChatColor.YELLOW,
                            viaRewind ? "Installed" : "Not installed (recommended)");
        }

        // 6. Player count
        int playerCount = srv.getEaglerPlayerCount();
        send(sender, EnumChatColor.AQUA, "Eaglercraft Players Online: ", EnumChatColor.WHITE, String.valueOf(playerCount));

        send(sender, EnumChatColor.GOLD, "=== End Diagnostics ===");
    }

    private void send(IPlatformCommandSender<PlayerObject> sender, EnumChatColor c, String text) {
        sender.sendMessage(getChatBuilder().buildTextComponent().beginStyle().color(c).end().text(text).end());
    }

    private void send(IPlatformCommandSender<PlayerObject> sender, EnumChatColor labelColor, String label,
                    EnumChatColor valueColor, String value) {
        sender.sendMessage(getChatBuilder().buildTextComponent()
                        .beginStyle().color(labelColor).end().text(label)
                        .appendTextComponent().beginStyle().color(valueColor).end().text(value).end().end());
    }
}
