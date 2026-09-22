/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.message;

import net.lax1dude.eaglercraft.backend.server.base.EaglerPlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServer;
import net.lax1dude.eaglercraft.backend.server.base.config.ConfigDataSettings;
import net.lax1dude.eaglercraft.backend.server.base.message.InjectedMessageController;
import net.lax1dude.eaglercraft.backend.server.base.message.LegacyMessageController;
import net.lax1dude.eaglercraft.backend.server.base.message.MessageController;
import net.lax1dude.eaglercraft.backend.server.base.message.RewindMessageControllerHandle;
import net.lax1dude.eaglercraft.backend.server.base.message.RewindMessageControllerImpl;
import net.lax1dude.eaglercraft.backend.server.base.message.ServerMessageHandler;
import net.lax1dude.eaglercraft.backend.server.base.message.ServerV3MessageHandler;
import net.lax1dude.eaglercraft.backend.server.base.message.ServerV4MessageHandler;
import net.lax1dude.eaglercraft.backend.server.base.message.ServerV5MessageHandler;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.GamePluginMessageProtocol;

public class MessageControllerFactory {
    public static MessageController initializePlayer(EaglerPlayerInstance<?> instance) {
        boolean modernChannelNames;
        GamePluginMessageProtocol protocol = instance.getEaglerProtocol();
        ServerMessageHandler handler = MessageControllerFactory.createHandler(protocol.ver, instance);
        RewindMessageControllerHandle rewindHandle = instance.getRewindMessageControllerHandle();
        if (rewindHandle != null) {
            return new RewindMessageControllerImpl(rewindHandle, protocol, handler);
        }
        EaglerXServer server = instance.getEaglerXServer();
        ConfigDataSettings settings = server.getConfig().getSettings();
        int sendDelay = settings.getProtocolV4DefragSendDelay();
        int maxPackets = settings.getProtocolV4DefragMaxPackets();
        if (protocol.ver >= 5) {
            return InjectedMessageController.injectEagler(protocol, handler, instance.getPlatformPlayer().getChannel(), sendDelay, maxPackets);
        }
        boolean bl = modernChannelNames = server.getPlatform().isModernPluginChannelNamesOnly() || instance.getMinecraftProtocol() > 340;
        if (protocol.ver == 4 && sendDelay > 0) {
            return new LegacyMessageController(protocol, handler, instance.getPlatformPlayer().getChannel().eventLoop(), sendDelay, maxPackets, modernChannelNames);
        }
        return new LegacyMessageController(protocol, handler, null, 0, maxPackets, modernChannelNames);
    }

    private static ServerMessageHandler createHandler(int ver, EaglerPlayerInstance<?> instance) {
        switch (ver) {
            case 5: {
                return new ServerV5MessageHandler(instance);
            }
            case 4: {
                return new ServerV4MessageHandler(instance);
            }
            case 3: {
                return new ServerV3MessageHandler(instance);
            }
        }
        throw new IllegalStateException();
    }
}

