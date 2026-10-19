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
 * WHETHER IN CONTRACT, STRICT LIABILITY, TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 */

package net.lax1dude.eaglercraft.backend.server.base;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import net.lax1dude.eaglercraft.backend.server.base.handshake.HandshakePacketTypes;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.BufferUtils;

class RateLimitMessage {

        /*
         * v1.1.1 Stable 2 fix: these messages used to be shared ByteBufs handed out
         * through Unpooled.wrappedBuffer(ByteBuf), which returns an UN-retained view.
         * When the WebSocket pipeline released the first kick frame it freed the
         * shared buffer, so every rate-limit kick after the first (per type) threw
         * IllegalReferenceCountException inside the write path and the client was
         * never shown the "Too many logins!" message. The payloads are now stored
         * as immutable byte arrays and each frame wraps a private copy, so frames
         * can be released any number of times without affecting each other.
         */
        private static final byte[] blockedLogin = generateLoginKick(HandshakePacketTypes.SERVER_ERROR_RATELIMIT_BLOCKED,
                        "Too many logins!");
        private static final byte[] lockedLogin = generateLoginKick(HandshakePacketTypes.SERVER_ERROR_RATELIMIT_LOCKED,
                        "Too many logins!");

        private static byte[] generateLoginKick(int code, String msg) {
                ByteBuf buf = Unpooled.buffer();
                try {
                        buf.writeByte(HandshakePacketTypes.PROTOCOL_SERVER_ERROR);
                        buf.writeByte(code);
                        buf.writeByte(msg.length());
                        BufferUtils.writeCharSequence(buf, msg, StandardCharsets.US_ASCII);
                        byte[] ret = new byte[buf.readableBytes()];
                        buf.readBytes(ret);
                        return ret;
                } finally {
                        buf.release();
                }
        }

        static BinaryWebSocketFrame getBlockedLoginMessage() {
                return new BinaryWebSocketFrame(Unpooled.wrappedBuffer(blockedLogin.clone()));
        }

        static BinaryWebSocketFrame getLockedLoginMessage() {
                return new BinaryWebSocketFrame(Unpooled.wrappedBuffer(lockedLogin.clone()));
        }

        static TextWebSocketFrame getBlockedQueryMessage() {
                return new TextWebSocketFrame("{\"type\":\"blocked\"}");
        }

        static TextWebSocketFrame getLockedQueryMessage() {
                return new TextWebSocketFrame("{\"type\":\"locked\"}");
        }

}
