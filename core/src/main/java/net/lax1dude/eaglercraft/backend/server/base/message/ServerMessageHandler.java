package net.lax1dude.eaglercraft.backend.server.base.message;

import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformComponentBuilder.EnumChatColor;
import net.lax1dude.eaglercraft.backend.server.base.EaglerPlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServer;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.WrongPacketException;

public abstract class ServerMessageHandler implements MessageController.IMessageHandler {

        protected final EaglerPlayerInstance<?> eaglerHandle;

        public ServerMessageHandler(EaglerPlayerInstance<?> eaglerHandle) {
                this.eaglerHandle = eaglerHandle;
        }

        public EaglerXServer<?> getServer() {
                return eaglerHandle.getEaglerXServer();
        }

        @Override
        public void handleException(Exception ex) {
                EaglerXServer<?> server = getServer();
                if (ex instanceof WrongPacketException || ex instanceof NotCapableException) {
                        server.logger().error("Protocol error, disconnecting", ex);
                        eaglerHandle.disconnect(server.componentBuilder().buildTextComponent().beginStyle().color(EnumChatColor.RED).end().text("Eaglercraft Protocol Error").end());
                } else {
                        server.logger().warn("Transient packet error (player NOT disconnected): " + ex.getMessage());
                }
        }

        protected RuntimeException wrongPacket() {
                return new WrongPacketException();
        }

        protected RuntimeException notCapable() {
                return new NotCapableException();
        }

}
