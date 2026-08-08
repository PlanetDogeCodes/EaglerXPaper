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
import net.md_5.bungee.api.chat.TextComponent;

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
                PlayerPostLoginInjector.LoginEventContext ctx = channel.attr(PlayerPostLoginInjector.attr).get();
                IPipelineData pipelineData = channel.attr(PipelineAttributes.<IPipelineData>pipelineData()).get();
                // Bug #20 fix: ctx can be null for non-Eaglercraft (vanilla) connections.
                // The EaglerXServer post-login hack attribute is only set for Eaglercraft
                // connections. Vanilla MC clients connecting through the same port don't
                // have it, so we must null-check before calling ctx.markCompressionDisable.
                if (ctx != null && pipelineData != null && pipelineData.isCompressionDisable()) {
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
                                                        // Bug #21 fix: b may not be a BaseComponent — it could be a
                                                        // String, an Exception, or anything else from CloseRedirector.
                                                        if (b instanceof BaseComponent bc) {
                                                                evt.setKickMessage(bc);
                                                        } else if (b instanceof String s) {
                                                                evt.setKickMessage(new TextComponent(s));
                                                        } else {
                                                                evt.setKickMessage(new TextComponent("Connection Closed"));
                                                        }
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
                try {
                        Object handle = BukkitUnsafe.getHandle(evt.getPlayer());
                        com.mojang.authlib.GameProfile profile = BukkitUnsafe.getGameProfile(handle);
                        if (profile != null) {
                                synchronized (profile) {
                                        // Bug #22 fix: collect the markers to remove FIRST, then remove them
                                        // in a separate pass. Iterating and modifying the live values
                                        // collection simultaneously throws ConcurrentModificationException.
                                        java.util.List<com.mojang.authlib.properties.Property> toRemove = new java.util.ArrayList<>();
                                        for (com.mojang.authlib.properties.Property p : BukkitUnsafe.getPropertyValuesSafe(profile)) {
                                                if (p.getName().startsWith("$eaglerMarker_")) {
                                                        toRemove.add(p);
                                                }
                                        }
                                        for (com.mojang.authlib.properties.Property p : toRemove) {
                                                BukkitUnsafe.removePropertySafe(profile, p.getName(), p);
                                        }
                                }
                        }
                } catch (Exception e) {
                        // Best effort — don't crash on quit
                }
        }

}
