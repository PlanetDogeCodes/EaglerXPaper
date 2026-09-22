/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.ImmutableList
 *  com.google.common.collect.ImmutableList$Builder
 *  com.google.common.collect.ImmutableMap
 */
package net.lax1dude.eaglercraft.backend.server.base.message;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import net.lax1dude.eaglercraft.backend.server.adapter.IEaglerXServerMessageChannel;
import net.lax1dude.eaglercraft.backend.server.adapter.IEaglerXServerMessageHandler;
import net.lax1dude.eaglercraft.backend.server.base.BasePlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.EaglerPlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServer;
import net.lax1dude.eaglercraft.backend.server.base.MessageChannel;
import net.lax1dude.eaglercraft.backend.server.base.message.LegacyMessageController;
import net.lax1dude.eaglercraft.backend.server.base.message.MessageController;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.GamePluginMessageConstants;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.GamePluginMessageProtocol;

public class PlayerChannelHelper {
    static final Map<String, String> CHANNEL_MODERN_NAMES = ImmutableMap.copyOf(GamePluginMessageProtocol.getAllChannels().stream().collect(Collectors.toMap(k -> k, GamePluginMessageConstants::getModernName)));

    public static <PlayerObject> Collection<IEaglerXServerMessageChannel<PlayerObject>> getPlayerChannels(EaglerXServer<PlayerObject> server) {
        IEaglerXServerMessageHandler handler = (ch, player, data) -> {
            MessageController msgController;
            BasePlayerInstance basePlayer = (BasePlayerInstance)player.getPlayerAttachment();
            if (basePlayer.isEaglerPlayer() && (msgController = ((EaglerPlayerInstance)basePlayer.asEaglerPlayer()).getMessageController()) instanceof LegacyMessageController) {
                LegacyMessageController msgController2 = (LegacyMessageController)msgController;
                msgController2.readPacket(ch.getLegacyName(), data);
            }
        };
        ImmutableList.Builder playerChannelBuilder = ImmutableList.builder();
        for (String channel : GamePluginMessageProtocol.getAllChannels()) {
            String modernChannel = CHANNEL_MODERN_NAMES.get(channel);
            playerChannelBuilder.add(new MessageChannel(channel, modernChannel, handler));
        }
        return playerChannelBuilder.build();
    }

    public static String mapModernName(String chan) {
        String ret = CHANNEL_MODERN_NAMES.get(chan);
        if (ret == null) {
            throw new IllegalStateException("Don't know the modern channel name for: " + chan);
        }
        return ret;
    }
}

