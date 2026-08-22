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

package net.lax1dude.eaglercraft.backend.server.util;

import java.util.concurrent.atomic.AtomicBoolean;

import java.util.function.Consumer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.util.AttributeKey;

public abstract class ChannelInitializerHijacker extends ChannelInitializer<Channel> {

        /**
         * Per-channel attribute that records whether EaglerXServer has already injected
         * its pipeline handlers into this channel. Prevents double-init when another
         * plugin (ViaVersion, ProtocolLib, PacketEvents) re-injects the channel
         * initializer and our hijacker gets called a second time for the same channel.
         */
        public static final AttributeKey<AtomicBoolean> EAGLER_INITIALIZED =
                        AttributeKey.valueOf("eaglerx-channel-initialized");

        private class ImplInitial implements Consumer<Channel> {

                @Override
                public void accept(Channel channel) {
                        Consumer<Channel> run;
                        eagler: {
                                synchronized (ChannelInitializerHijacker.this) {
                                        run = impl;
                                        if (run != this) {
                                                break eagler;
                                        }
                                        if (reInject()) {
                                                impl = ChannelInitializerHijacker.this::callParent;
                                                run = (c) -> c.close(channel.voidPromise());
                                                break eagler;
                                        }
                                        run = impl = ChannelInitializerHijacker.this::callParentAndInit;
                                }
                                run.accept(channel);
                                return;
                        }
                        if (run != null) {
                                run.accept(channel);
                        }
                }

        }

        protected final Consumer<Channel> initServerChild;

        public ChannelInitializerHijacker(Consumer<Channel> initServerChild) {
                this.initServerChild = initServerChild;
        }

        /**
         * CRITICAL: must be volatile. This field is written by deactivate() (called
         * from the Bukkit main thread on plugin disable) and read by initChannel
         * (called on the Netty EventLoop thread). Without volatile, the EventLoop
         * may observe the stale ImplInitial reference and try to init a channel after
         * the plugin has been disabled, causing NPE in server.logger() etc.
         */
        protected volatile Consumer<Channel> impl = new ImplInitial();

        protected abstract void callParent(Channel channel);

        protected void callParentAndInit(Channel channel) {
                // Idempotency guard: if EaglerXServer has already initialized this channel,
                // don't re-run initServerChild — that would re-wrap packet_handler with
                // a double-proxy and break login.
                AtomicBoolean initialized = channel.attr(EAGLER_INITIALIZED).get();
                if (initialized == null) {
                        initialized = new AtomicBoolean(false);
                        AtomicBoolean raced = channel.attr(EAGLER_INITIALIZED).setIfAbsent(initialized);
                        if (raced != null) {
                                initialized = raced;
                        }
                }
                if (initialized.getAndSet(true)) {
                        // Already initialized — just call parent (which is a no-op on already-initialized
                        // channels because Netty's ChannelInitializer removes itself after initChannel).
                        callParent(channel);
                        return;
                }
                callParent(channel);
                try {
                        initServerChild.accept(channel);
                } catch (Throwable t) {
                        // If Eagler's init throws (e.g. pipeline already contains a handler with our name),
                        // log and continue — don't kill the channel. The vanilla path will still work.
                        System.err.println("[EaglerXServer] initServerChild failed for channel " + channel + ": " + t);
                }
        }

        protected abstract boolean reInject();

        @Override
        protected void initChannel(Channel var1) throws Exception {
                impl.accept(var1);
        }

        public void deactivate() {
                impl = ChannelInitializerHijacker.this::callParent;
        }

}
