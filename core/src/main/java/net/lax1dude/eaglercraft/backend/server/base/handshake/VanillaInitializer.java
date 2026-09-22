/*
 * Copyright (c) 2025 lax1dude. All Rights Reserved.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */

package net.lax1dude.eaglercraft.backend.server.base.handshake;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServer;
import net.lax1dude.eaglercraft.backend.server.base.NettyPipelineData;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.EaglerCompressionGuardHandler;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.BufferUtils;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.WebSocketEaglerInitialHandler;

public class VanillaInitializer {

        protected final EaglerXServer<?> server;
        protected final NettyPipelineData pipelineData;
        protected final WebSocketEaglerInitialHandler inboundHandler;
        protected final List<ByteBuf> bufferedPackets;

        private static final int STATE_PRE = 0;
        private static final int STATE_SENT_LOGIN = 1;
        private static final int STATE_STALLING = 2;
        private static final int STATE_COMPLETE = 3;
        private int connectionState = STATE_PRE;

        public VanillaInitializer(EaglerXServer<?> server, NettyPipelineData pipelineData,
                        WebSocketEaglerInitialHandler inboundHandler) {
                this.server = server;
                this.pipelineData = pipelineData;
                this.inboundHandler = inboundHandler;
                this.bufferedPackets = new LinkedList<>();
        }

        public void init(ChannelHandlerContext ctx) {
                // C00Handshake
                ByteBuf buffer = ctx.alloc().buffer();
                try {
                        BufferUtils.writeVarInt(buffer, 0x00);
                        BufferUtils.writeVarInt(buffer, pipelineData.minecraftProtocol);
                        String ip = pipelineData.headerHost;
                        int port = 65535;
                        if (ip == null) {
                                ip = "127.0.0.1";
                        } else {
                                int i = ip.lastIndexOf(':');
                                if (i != -1 && i < ip.length() - 1) {
                                        try {
                                                port = Integer.parseInt(ip.substring(i + 1));
                                                ip = ip.substring(0, i);
                                        } catch (NumberFormatException ex) {
                                        }
                                }
                                if (ip.length() > 255) {
                                        ip = ip.substring(0, 255);
                                }
                        }
                        BufferUtils.writeMCString(buffer, ip, 255);
                        buffer.writeShort(port);
                        BufferUtils.writeVarInt(buffer, 2);
                        ctx.fireChannelRead(buffer.retain());
                } finally {
                        buffer.release();
                }

                if (inboundHandler.terminated || !ctx.channel().isActive()) {
                        return;
                }

                connectionState = STATE_SENT_LOGIN;

                // C00PacketLoginStart
                buffer = ctx.alloc().buffer();
                try {
                        BufferUtils.writeVarInt(buffer, 0x00);
                        BufferUtils.writeMCString(buffer, pipelineData.username, 16);
                        // LoginStart ("hello") field history:
                        // < 759 (1.19): name only
                        // 759-760 (1.19-1.19.2): name + hasPublicKey boolean
                        // >= 760 (1.19.1+): name (+bool for 760) + profile UUID
                        if (pipelineData.minecraftProtocol >= 759 && pipelineData.minecraftProtocol <= 760) {
                                buffer.writeBoolean(false);
                        }
                        if (pipelineData.minecraftProtocol >= 760) {
                                buffer.writeLong(pipelineData.uuid.getMostSignificantBits());
                                buffer.writeLong(pipelineData.uuid.getLeastSignificantBits());
                        }
                        ctx.fireChannelRead(buffer.retain());
                } finally {
                        buffer.release();
                }

        }

        /**
         * Returns the MC play-phase Disconnect (ClientboundDisconnectPacket) ID for the
         * given Minecraft protocol version. These IDs are stable within version ranges
         * but changed across major MC versions:
         * <ul>
         * <li>1.8 (proto 47): 0x40</li>
         * <li>1.9 – 1.18.2 (107 – 758): 0x1A</li>
         * <li>1.19 (759): 0x1D</li>
         * <li>1.19.1 – 1.19.3 (760 – 761): 0x17</li>
         * <li>1.19.4 – 1.20.4 (762 – 765): 0x1A</li>
         * <li>1.20.5+ (766+): 0x1D</li>
         * </ul>
         */
        private static int playDisconnectId(int mcProto) {
                if (mcProto <= 47) return 0x40;          // 1.8
                if (mcProto < 393) return 0x1A;          // 1.9 – 1.12.2
                if (mcProto <= 404) return 0x1B;         // 1.13 – 1.13.2
                if (mcProto <= 758) return 0x1A;          // 1.14 – 1.18.2
                if (mcProto <= 759) return 0x1D;          // 1.19
                if (mcProto <= 761) return 0x17;          // 1.19.1 – 1.19.3
                if (mcProto <= 765) return 0x1A;          // 1.19.4 – 1.20.4
                return 0x1D;                              // 1.20.5+
        }

        /**
         * Returns the MC play-phase PluginMessage (ClientboundCustomPayloadPacket) ID
         * for the given Minecraft protocol version.
         * <ul>
         * <li>1.8 (proto 47): 0x3F</li>
         * <li>1.9 – 1.19 (107 – 759): 0x18</li>
         * <li>1.19.1 – 1.19.3 (760 – 761): 0x15</li>
         * <li>1.19.4 – 1.20.4 (762 – 765): 0x0A</li>
         * <li>1.20.5+ (766+): 0x18</li>
         * </ul>
         */
        private static int pluginMessagePlayId(int mcProto) {
                if (mcProto <= 47) return 0x3F;          // 1.8
                if (mcProto < 393) return 0x18;          // 1.9 – 1.12.2
                if (mcProto <= 404) return 0x19;         // 1.13 – 1.13.2
                if (mcProto <= 759) return 0x18;          // 1.14 – 1.19
                if (mcProto <= 761) return 0x15;          // 1.19.1 – 1.19.3
                if (mcProto <= 765) return 0x0A;          // 1.19.4 – 1.20.4
                return 0x18;                              // 1.20.5+
        }

        /**
         * 1.20.2+ Configuration-state Disconnect packet ID.
         * 1.20.2-1.20.4 (764-765): PluginMessage=0x00, Disconnect=0x01, FinishConfig=0x02.
         * 1.20.5+ (766+): CookieRequest=0x00, PluginMessage=0x01, Disconnect=0x02 (verified
         * against the Paper 1.21.11 configuration registry order).
         */
        private static int configDisconnectId(int mcProto) {
                if (mcProto >= 764 && mcProto <= 765) return 0x01;
                return 0x02;
        }

        public void handleInbound(ChannelHandlerContext ctx, ByteBuf msg) {
                // CRITICAL: Once we've reached the terminal STATE_COMPLETE (after a kick packet
                // was processed), silently drop any further inbound packets — don't re-trigger
                // terminateInternalError on an already-closing channel. The previous code would
                // log misleading "unexpected packet X in unknown state" warnings and call
                // terminateInternalError a second time, which on some pipeline configurations
                // throws because the channel is already inactive.
                if (connectionState == STATE_COMPLETE) {
                        return;
                }
                try {
                        msg.markReaderIndex();
                        int pktId = BufferUtils.readVarInt(msg, 3);
                        if (connectionState == STATE_PRE) {
                                if (pktId == 0x00) {
                                        // S00PacketDisconnect
                                        handleKickPacket(ctx, msg);
                                } else if (pktId == 0x01) {
                                        // S01PacketEncryptionRequest
                                        inboundHandler.terminateErrorCode(ctx, pipelineData.handshakeProtocol,
                                                        HandshakePacketTypes.SERVER_ERROR_CUSTOM_MESSAGE, HandshakePacketTypes.MSG_ONLINE_MODE);
                                } else {
                                        inboundHandler.terminateInternalError(ctx, pipelineData.handshakeProtocol);
                                        pipelineData.connectionLogger.error("Disconnecting, server sent unexpected packet " + pktId);
                                }
                        } else if (connectionState == STATE_SENT_LOGIN) {
                                int mcProto = pipelineData.minecraftProtocol;
                                int pluginMsgId = pluginMessagePlayId(mcProto);
                                if (pktId == 0x00) {
                                        // S00PacketDisconnect
                                        handleKickPacket(ctx, msg);
                                } else if (pktId == 0x01) {
                                        // S01PacketEncryptionRequest
                                        inboundHandler.terminateErrorCode(ctx, pipelineData.handshakeProtocol,
                                                        HandshakePacketTypes.SERVER_ERROR_CUSTOM_MESSAGE, HandshakePacketTypes.MSG_ONLINE_MODE);
                                } else if (pktId == 0x02) {
                                        connectionState = STATE_STALLING;
                                        // S02PacketLoginSuccess
                                        UUID playerUUID;
                                        String usernameStr;
                                        if (mcProto >= 735) {
                                                playerUUID = new UUID(msg.readLong(), msg.readLong());
                                                usernameStr = BufferUtils.readMCString(msg, 16);
                                                if (mcProto >= 759) {
                                                        int propCount = BufferUtils.readVarInt(msg, 5);
                                                        for (int j = 0; j < propCount; ++j) {
                                                                msg.skipBytes(BufferUtils.readVarInt(msg, 5));
                                                                msg.skipBytes(BufferUtils.readVarInt(msg, 5));
                                                                if (msg.readBoolean()) {
                                                                        msg.skipBytes(BufferUtils.readVarInt(msg, 5));
                                                                }
                                                        }
                                                }
                                                if (mcProto >= 766 && msg.isReadable()) {
                                                        msg.readBoolean();
                                                }
                                        } else {
                                                String uuidStr = BufferUtils.readMCString(msg, 36);
                                                try {
                                                        playerUUID = UUID.fromString(uuidStr);
                                                } catch (IllegalArgumentException ex) {
                                                        inboundHandler.terminateInternalError(ctx, pipelineData.handshakeProtocol);
                                                        return;
                                                }
                                                usernameStr = BufferUtils.readMCString(msg, 16);
                                        }
                                        // CRITICAL (MC 1.20.2+): fire ServerboundLoginAcknowledged IMMEDIATELY
                                        // after parsing LoginSuccess, before handleBackendHandshakeSuccess
                                        // round-trips through the Eagler handshake. Without this, the vanilla
                                        // server is stuck in LOGIN→CONFIGURATION transition for the entire
                                        // Eagler handshake round-trip (~50-200 ms), which on slow networks
                                        // can exceed the 30s login timeout.
                                        if (mcProto >= 764) {
                                                ByteBuf ackBuf = ctx.alloc().buffer();
                                                try {
                                                        BufferUtils.writeVarInt(ackBuf, 0x03); // ServerboundLoginAcknowledged
                                                        ctx.fireChannelRead(ackBuf.retain());
                                                } finally {
                                                        ackBuf.release();
                                                }
                                        }
                                        inboundHandler.handleBackendHandshakeSuccess(ctx, usernameStr, playerUUID);
                                } else if (pktId == 0x03) {
                                        EaglerCompressionGuardHandler.scheduleStrip(ctx.channel());
                                } else if (pktId == pluginMsgId) {
                                        // PluginMessage (Custom Payload) — buffer for replay after login
                                        msg.resetReaderIndex();
                                        bufferedPackets.add(msg.retain());
                                } else if (pktId == 0x04) {
                                        // LoginPluginRequest: the vanilla login listener blocks waiting for a
                                        // response that an Eagler client can never send (proxy forwarding data,
                                        // Forge handshake, etc). Terminating with a clear error beats a silent
                                        // 30-second login timeout stall.
                                        connectionState = STATE_COMPLETE;
                                        inboundHandler.terminateErrorCode(ctx, pipelineData.handshakeProtocol,
                                                        HandshakePacketTypes.SERVER_ERROR_CUSTOM_MESSAGE,
                                                        "This server requires a login plugin handshake (proxy forwarding or mod support) that Eaglercraft clients cannot provide");
                                } else {
                                        // Other unknown login-state packets (CookieRequest 0x05, etc) are optional
                                        // vanilla queries; dropping them lets the login continue, and replaying
                                        // them into the post-login stream would corrupt it instead.
                                        pipelineData.connectionLogger.warn(
                                                        "Dropping unknown login-state packet 0x" + Integer.toHexString(pktId)
                                                                        + " (Eagler clients cannot respond to it)");
                                }
                        } else if (connectionState == STATE_STALLING) {
                                int mcProto = pipelineData.minecraftProtocol;
                                int playDisconId = playDisconnectId(mcProto);
                                int configDisconId = configDisconnectId(mcProto);
                                // 1.20.2+ Configuration phase has its own Disconnect ID.
                                boolean isConfigPhase = mcProto >= 764;
                                if (pktId == playDisconId || (isConfigPhase && pktId == configDisconId)) {
                                        // Play-phase or Config-phase Disconnect
                                        handleKickPacket(ctx, msg);
                                } else {
                                        msg.resetReaderIndex();
                                        bufferedPackets.add(msg.retain());
                                }
                        } else {
                                pipelineData.connectionLogger
                                                .error("Disconnecting, server sent unexpected packet " + pktId + " in unknown state");
                                inboundHandler.terminateInternalError(ctx, pipelineData.handshakeProtocol);
                        }
                } catch (IndexOutOfBoundsException ex) {
                        ex.printStackTrace();
                        inboundHandler.terminateInternalError(ctx, pipelineData.handshakeProtocol);
                } catch (Throwable t) {
                        // Defensive: any unexpected Throwable (e.g. DecoderException from BufferUtils
                        // or a NumberFormatException parsing a UUID) should not crash the EventLoop.
                        // Log and terminate cleanly.
                        pipelineData.connectionLogger.error("VanillaInitializer.handleInbound caught unexpected exception", t);
                        inboundHandler.terminateInternalError(ctx, pipelineData.handshakeProtocol);
                }
        }

        private void handleKickPacket(ChannelHandlerContext ctx, ByteBuf data) {
                String pkt = BufferUtils.readMCString(data, 32767);
                // Set STATE_COMPLETE BEFORE calling terminateErrorCode, so any synchronous
                // re-entrancy during terminate (e.g. another handler calling handleInbound)
                // sees the terminal state and short-circuits via the early-return guard
                // at the top of handleInbound.
                connectionState = STATE_COMPLETE;
                inboundHandler.terminateErrorCode(ctx, pipelineData.handshakeProtocol,
                                HandshakePacketTypes.SERVER_ERROR_CUSTOM_MESSAGE, pkt);
        }

        public void flushBufferedPackets(ChannelHandlerContext ctx) {
                if (!bufferedPackets.isEmpty()) {
                        try {
                                for (ByteBuf buf : bufferedPackets) {
                                        ctx.write(buf.retain());
                                }
                                ctx.flush();
                        } finally {
                                release();
                        }
                }
        }

        public void release() {
                if (!bufferedPackets.isEmpty()) {
                        for (ByteBuf buf : bufferedPackets) {
                                buf.release();
                        }
                        bufferedPackets.clear();
                }
        }

}
