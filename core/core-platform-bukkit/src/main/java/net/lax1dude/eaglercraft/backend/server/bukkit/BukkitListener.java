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

package net.lax1dude.eaglercraft.backend.server.bukkit;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import io.netty.channel.Channel;
import net.lax1dude.eaglercraft.backend.server.adapter.IPipelineData;
import net.lax1dude.eaglercraft.backend.server.adapter.PipelineAttributes;
import net.lax1dude.eaglercraft.backend.server.api.bukkit.event.PlayerLoginInitEvent;
import net.lax1dude.eaglercraft.backend.server.api.bukkit.event.PlayerLoginPostEvent;
import net.lax1dude.eaglercraft.backend.server.bukkit.async.PlayerPostLoginInjector;
import net.md_5.bungee.api.chat.BaseComponent;

class BukkitListener implements Listener {

        private final PlatformPluginBukkit plugin;

        BukkitListener(PlatformPluginBukkit plugin) {
                this.plugin = plugin;
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onPlayerLoginEvent(PlayerLoginEvent evt) {
                plugin.postLoginInjector.handleLoginEvent(evt);
        }

        @EventHandler(priority = EventPriority.LOW)
        public void onPlayerLoginInitEvent(PlayerLoginInitEvent evt) {
                Channel channel = evt.netty().getChannel();
                // CRITICAL: ctx may be null if the channel was not wrapped by EaglerXServer
                // (e.g. a Bedrock-via-Geyser connection that bypassed our wrapNetworkManager,
                // or a vanilla player whose connection didn't go through the PaperMC listener).
                // Don't NPE — bail out cleanly.
                PlayerPostLoginInjector.LoginEventContext ctx = channel.attr(PlayerPostLoginInjector.attr).get();
                if (ctx == null) {
                        return;
                }
                IPipelineData pipelineData = channel.attr(PipelineAttributes.<IPipelineData>pipelineData()).get();
                if (pipelineData != null && pipelineData.isCompressionDisable()) {
                        ctx.markCompressionDisable(true);
                }
        }

        @EventHandler(priority = EventPriority.LOW)
        public void onPlayerPostLoginEvent(PlayerLoginPostEvent evt) {
                Player player = evt.getPlayer();
                plugin.forEachChannel((ch) -> {
                        BukkitUnsafe.addPlayerChannel(player, ch);
                });
                Channel channel = evt.netty().getChannel();
                IPipelineData pipelineData = channel.attr(PipelineAttributes.<IPipelineData>pipelineData()).getAndSet(null);
                evt.registerIntent(plugin);
                awaitPlayState(pipelineData, () -> {
                        PlayerPostLoginInjector.setPlayState(evt);
                        try {
                                plugin.initializePlayer(player, channel, pipelineData, (b) -> {
                                        if (b != Boolean.TRUE) {
                                                if (b != null) {
                                                        evt.setKickMessage((BaseComponent) b);
                                                }
                                                evt.setCancelled(true);
                                        }
                                        evt.completeIntent(plugin);
                                });
                        } catch (Exception ex) {
                                try {
                                        evt.setCancelled(true);
                                        evt.completeIntent(plugin);
                                } catch (IllegalStateException exx) {
                                        return;
                                }
                                if (ex instanceof RuntimeException exx)
                                        throw exx;
                                throw new RuntimeException("Uncaught exception", ex);
                        }
                });
        }

        private static void awaitPlayState(IPipelineData conn, Runnable cont) {
                if (conn != null) {
                        conn.awaitPlayState(cont);
                } else {
                        cont.run();
                }
        }

        @EventHandler
        public void onPlayerJoinEvent(PlayerJoinEvent evt) {
                plugin.confirmPlayer(evt.getPlayer());
        }

        @EventHandler(priority = EventPriority.LOW)
        public void onPlayerChangedWorldEvent(PlayerChangedWorldEvent evt) {
                if (evt.getFrom() != null) {
                        plugin.worldChange(evt.getPlayer());
                }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onQuitEvent(PlayerQuitEvent evt) {
                plugin.dropPlayer(evt.getPlayer());
                // Clean up any orphaned eaglerMarker properties from the player's GameProfile.
                // These are inserted by PlayerPostLoginInjector.handleLoginEvent and are
                // normally removed when PacketLoginOutSuccess is sent. But if login fails
                // before that (kick, timeout, disconnect), the marker stays forever.
                //
                // CRITICAL: must use AuthlibCompat (not Property.getName() directly) because
                // authlib 6.x (Paper 26.x / MC 1.21.11) renamed Property.getName() to name().
                // A direct call throws NoSuchMethodError which is an Error, NOT an Exception,
                // so the old `catch (Exception e)` didn't catch it.
                try {
                        Object handle = BukkitUnsafe.getHandle(evt.getPlayer());
                        com.mojang.authlib.GameProfile profile = BukkitUnsafe.getGameProfile(handle);
                        if (profile != null) {
                                synchronized (profile) {
                                        com.google.common.collect.Multimap<String, com.mojang.authlib.properties.Property> props = net.lax1dude.eaglercraft.backend.server.api.bukkit.compat.AuthlibCompat
                                                        .getProperties(profile);
                                        com.mojang.authlib.properties.Property[] toRemove = props.values().stream()
                                                        .filter(net.lax1dude.eaglercraft.backend.server.api.bukkit.compat.AuthlibCompat
                                                                        .nameStartsWith("$eaglerMarker_"))
                                                        .toArray(com.mojang.authlib.properties.Property[]::new);
                                        for (com.mojang.authlib.properties.Property p : toRemove) {
                                                String name = net.lax1dude.eaglercraft.backend.server.api.bukkit.compat.AuthlibCompat
                                                                .getName(p);
                                                net.lax1dude.eaglercraft.backend.server.api.bukkit.compat.AuthlibCompat
                                                                .remove(props, name, p);
                                                // CRITICAL: also remove the (Property, Player) entry from the
                                                // PlayerPostLoginInjector.entityPlayers weak map. Without this,
                                                // the entry stays alive until GC reclaims both the Property and
                                                // the Player — which can take seconds to minutes. During that
                                                // window a fast reconnect could match a stale marker.
                                                try {
                                                        plugin.postLoginInjector.removeMarker(p);
                                                } catch (Throwable ignored) {
                                                }
                                        }
                                }
                        }
                } catch (Throwable e) {
                        // Widened from Exception to Throwable: NoSuchMethodError, NoClassDefFoundError,
                        // and other Errors must not propagate to Bukkit's event dispatcher.
                        // Best effort — don't crash on quit.
                }
        }

}
