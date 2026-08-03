/*
 * Copyright (c) 2025 lax1dude. All Rights Reserved.
 *
 * EaglerXPaper-exclusive command: /eaglerclients
 * Shows a table of all connected Eaglercraft players with their client details.
 */

package net.lax1dude.eaglercraft.backend.server.base.command;

import net.lax1dude.eaglercraft.backend.server.adapter.IEaglerXServerCommandType;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformCommandSender;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformComponentBuilder.EnumChatColor;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServer;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.GamePluginMessageProtocol;

public class CommandClients<PlayerObject> extends EaglerCommand<PlayerObject> {

    public CommandClients(EaglerXServer<PlayerObject> server) {
        super(server, "eaglerclients", "eaglercraft.command.clients", "eaglerclientlist");
    }

    @Override
    public void handle(IEaglerXServerCommandType<PlayerObject> command, IPlatformCommandSender<PlayerObject> sender,
                    String[] args) {
        EaglerXServer<PlayerObject> srv = getServer();

        sender.sendMessage(getChatBuilder().buildTextComponent()
                        .beginStyle().color(EnumChatColor.GOLD).end()
                        .text("=== Eaglercraft Players ===").end());

        sender.sendMessage(getChatBuilder().buildTextComponent()
                        .beginStyle().color(EnumChatColor.YELLOW).end()
                        .text(String.format("%-16s %-14s %-8s %-12s %-16s",
                                        "Name", "Brand", "MC Ver", "Proto", "Real IP")).end());

        final int[] count = {0};
        srv.forEachEaglerPlayer(player -> {
            try {
                count[0]++;

                String name = player.getUsername();
                if (name == null) name = "Unknown";
                if (name.length() > 16) name = name.substring(0, 15) + "~";

                String brand = player.getEaglerBrandString();
                if (brand == null || brand.isEmpty()) brand = "Unknown";
                if (brand.length() > 14) brand = brand.substring(0, 13) + "~";

                int mcProto = player.getMinecraftProtocol();
                String mcVer = mcProtoToVersion(mcProto);
                if (mcVer.length() > 8) mcVer = mcVer.substring(0, 7) + "~";

                GamePluginMessageProtocol eaglerProto = player.getEaglerProtocol();
                String protoStr = eaglerProto != null ? "v" + eaglerProto.ver : "v?";

                boolean isRewind = player.isEaglerXRewindPlayer();
                if (isRewind) {
                    protoStr += " +1.5.2";
                }
                if (protoStr.length() > 12) protoStr = protoStr.substring(0, 11) + "~";

                String realIP = player.getRealAddress();
                if (realIP == null || realIP.isEmpty()) realIP = "unknown";
                if (realIP.length() > 16) realIP = realIP.substring(0, 15) + "~";

                sender.sendMessage(getChatBuilder().buildTextComponent()
                                .beginStyle().color(EnumChatColor.WHITE).end()
                                .text(String.format("%-16s %-14s %-8s %-12s %-16s",
                                                name, brand, mcVer, protoStr, realIP)).end());
            } catch (Exception e) {
                sender.sendMessage(getChatBuilder().buildTextComponent()
                                .beginStyle().color(EnumChatColor.RED).end()
                                .text("(error reading player: " + e.getMessage() + ")").end());
            }
        });

        if (count[0] == 0) {
            sender.sendMessage(getChatBuilder().buildTextComponent()
                            .beginStyle().color(EnumChatColor.GRAY).end()
                            .text("No Eaglercraft players currently online.").end());
        }

        sender.sendMessage(getChatBuilder().buildTextComponent()
                        .beginStyle().color(EnumChatColor.GOLD).end()
                        .text("Total: " + count[0] + " Eaglercraft player(s)").end());
    }

    private static String mcProtoToVersion(int protocol) {
        switch (protocol) {
            case 3: return "1.6.x";
            case 4: return "1.7.2-5";
            case 5: return "1.7.6-10";
            case 47: return "1.8";
            case 107: return "1.9";
            case 108: return "1.9.1";
            case 109: return "1.9.2";
            case 110: return "1.9.3-4";
            case 210: return "1.10";
            case 315: return "1.11";
            case 316: return "1.11.1-2";
            case 335: return "1.12";
            case 338: return "1.12.1";
            case 340: return "1.12.2";
            case 393: return "1.13";
            case 401: return "1.13.1";
            case 404: return "1.13.2";
            case 477: return "1.14";
            case 480: return "1.14.1";
            case 485: return "1.14.2";
            case 490: return "1.14.3";
            case 498: return "1.14.4";
            case 573: return "1.15";
            case 575: return "1.15.1";
            case 578: return "1.15.2";
            case 735: return "1.16";
            case 736: return "1.16.1";
            case 751: return "1.16.2";
            case 753: return "1.16.3";
            case 754: return "1.16.4-5";
            case 755: return "1.17";
            case 756: return "1.17.1";
            case 757: return "1.18";
            case 758: return "1.18.1-2";
            case 759: return "1.19";
            case 760: return "1.19.1-2";
            case 761: return "1.19.3";
            case 762: return "1.19.4";
            case 763: return "1.20";
            case 764: return "1.20.2";
            case 765: return "1.20.3-4";
            case 766: return "1.20.5-6";
            case 767: return "1.21";
            case 768: return "1.21.2-3";
            case 769: return "1.21.4";
            case 770: return "1.21.5";
            case 771: return "1.21.6";
            case 772: return "1.21.7";
            case 773: return "1.21.8";
            default:
                if (protocol >= 774) return "1.21.9+";
                return "v" + protocol;
        }
    }
}
