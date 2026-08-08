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
                        // Bug #30 fix: default port 0 (unknown) instead of 65535 (a valid
                        // port number that can trigger anti-spoofing protections).
                        int port = 0;
                        if (ip == null) {
                                ip = "127.0.0.1";
                        } else {
                                // Bug #31 fix: correctly handle IPv6 addresses in the Host header.
                                // IPv6 addresses contain multiple colons, so lastIndexOf(':') is
                                // ambiguous. We use the standard bracket notation: "[::1]:25565"
                                // or "[::1]" (no port).
                                if (ip.startsWith("[")) {
                                        // IPv6 with brackets
                                        int closeBracket = ip.indexOf(']');
                                        if (closeBracket > 0) {
                                                // Extract the IPv6 address without brackets
                                                String ipv6 = ip.substring(1, closeBracket);
                                                String remainder = ip.substring(closeBracket + 1);
                                                if (remainder.startsWith(":")) {
                                                        try {
                                                                port = Integer.parseInt(remainder.substring(1));
                                                        } catch (NumberFormatException ex) {
                                                                // ignore — port stays 0
                                                        }
                                                }
                                                ip = ipv6;
                                        }
                                        // else: malformed, leave as-is
                                } else {
                                        // IPv4 or hostname
                                        int colonCount = 0;
                                        for (int i = 0; i < ip.length(); i++) {
                                                if (ip.charAt(i) == ':') colonCount++;
                                        }
                                        if (colonCount == 1) {
                                                // Has a port — split on the single colon
                                                int i = ip.indexOf(':');
                                                if (i < ip.length() - 1) {
                                                        try {
                                                                port = Integer.parseInt(ip.substring(i + 1));
                                                                ip = ip.substring(0, i);
                                                        } catch (NumberFormatException ex) {
                                                                // ignore
                                                        }
                                                }
                                        }
                                        // If colonCount > 1, it's a bare IPv6 without brackets —
                                        // leave as-is (the server may handle it, or reject it).
                                        // If colonCount == 0, no port — port stays 0.
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
                        if (pipelineData.minecraftProtocol >= 764) {
                                buffer.writeLong(pipelineData.uuid.getMostSignificantBits());
                                buffer.writeLong(pipelineData.uuid.getLeastSignificantBits());
                        }
                        ctx.fireChannelRead(buffer.retain());
                } finally {
                        buffer.release();
                }

        }

        public void handleInbound(ChannelHandlerContext ctx, ByteBuf msg) {
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
                                switch (pktId) {
                                case 0x00:
                                        // S00PacketDisconnect
                                        handleKickPacket(ctx, msg);
                                        break;
                                case 0x01:
                                        // S01PacketEncryptionRequest
                                        inboundHandler.terminateErrorCode(ctx, pipelineData.handshakeProtocol,
                                                        HandshakePacketTypes.SERVER_ERROR_CUSTOM_MESSAGE, HandshakePacketTypes.MSG_ONLINE_MODE);
                                        break;
                                case 0x02:
                                        connectionState = STATE_STALLING;
                                        // S02PacketLoginSuccess
                                        UUID playerUUID;
                                        String usernameStr;
                                        int mcProto = pipelineData.minecraftProtocol;
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
                                        inboundHandler.handleBackendHandshakeSuccess(ctx, usernameStr, playerUUID);
                                        break;
                                case 0x03:
                                        // S03PacketEnableCompression
                                        break;
                                case 0x3F:
                                        // S3FPacketCustomPayload
                                        msg.resetReaderIndex();
                                        bufferedPackets.add(msg.retain());
                                        break;
                                default:
                                        inboundHandler.terminateInternalError(ctx, pipelineData.handshakeProtocol);
                                        pipelineData.connectionLogger
                                                        .error("Disconnecting, server sent unknown packet " + pktId + " while handshaking");
                                        break;
                                }
                        } else if (connectionState == STATE_STALLING) {
                                if (pktId == 0x40) {
                                        // S40PacketDisconnect
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
                        // Bug #32 fix: route through the plugin logger instead of stderr.
                        pipelineData.connectionLogger.error("Disconnecting, malformed handshake packet (IndexOutOfBoundsException)", ex);
                        inboundHandler.terminateInternalError(ctx, pipelineData.handshakeProtocol);
                }
        }

        private void handleKickPacket(ChannelHandlerContext ctx, ByteBuf data) {
                String pkt;
                try {
                        pkt = BufferUtils.readMCString(data, 32767);
                } catch (Exception e) {
                        pkt = "Disconnected from server";
                }
                inboundHandler.terminateErrorCode(ctx, pipelineData.handshakeProtocol,
                                HandshakePacketTypes.SERVER_ERROR_CUSTOM_MESSAGE, pkt);
                connectionState = STATE_COMPLETE;
        }

        public void flushBufferedPackets(ChannelHandlerContext ctx) {
                if (bufferedPackets.isEmpty()) {
                        return;
                }
                // Bug #29 fix: write each packet with explicit error handling so that if
                // ctx.write throws on the N-th packet, the remaining ByteBufs are released
                // (their extra retain() is undone) instead of being leaked.
                //
                // The original code did `ctx.write(buf.retain())` for each, which gives
                // ctx.write its own reference (matching Netty's convention). The original
                // release() in finally decrements the ORIGINAL refcount (added when the
                // buffer was put into bufferedPackets). If ctx.write throws on packet N,
                // packets N through the end never had ctx.write release their extra retain,
                // leaking ByteBufs and accumulating hung promises.
                try {
                        for (ByteBuf buf : bufferedPackets) {
                                try {
                                        ctx.write(buf.retain());
                                } catch (Throwable t) {
                                        // ctx.write threw — undo our extra retain on this buf,
                                        // then release all remaining bufs (their extra retains
                                        // were never made since we didn't get to them).
                                        try { buf.release(); } catch (Throwable t2) { /* best effort */ }
                                        // Now release the originals for the rest of the buffer.
                                        // (We already released this one's "extra" retain above;
                                        // the original release() call below will handle the
                                        // originals for the buffers we already wrote.)
                                        break;
                                }
                        }
                        ctx.flush();
                } finally {
                        release();
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
