/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.Multimap
 *  com.mojang.authlib.GameProfile
 *  com.mojang.authlib.properties.Property
 *  io.netty.channel.Channel
 *  net.md_5.bungee.api.chat.BaseComponent
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerChangedWorldEvent
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.player.PlayerLoginEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 */
package net.lax1dude.eaglercraft.backend.server.bukkit;

import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.netty.channel.Channel;
import net.lax1dude.eaglercraft.backend.server.adapter.IPipelineData;
import net.lax1dude.eaglercraft.backend.server.adapter.PipelineAttributes;
import net.lax1dude.eaglercraft.backend.server.api.bukkit.compat.AuthlibCompat;
import net.lax1dude.eaglercraft.backend.server.api.bukkit.event.PlayerLoginInitEvent;
import net.lax1dude.eaglercraft.backend.server.api.bukkit.event.PlayerLoginPostEvent;
import net.lax1dude.eaglercraft.backend.server.bukkit.BukkitUnsafe;
import net.lax1dude.eaglercraft.backend.server.bukkit.PlatformPluginBukkit;
import net.lax1dude.eaglercraft.backend.server.bukkit.async.PlayerPostLoginInjector;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

class BukkitListener
implements Listener {
    private final PlatformPluginBukkit plugin;

    BukkitListener(PlatformPluginBukkit plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onPlayerLoginEvent(PlayerLoginEvent evt) {
        this.plugin.postLoginInjector.handleLoginEvent(evt);
    }

    @EventHandler(priority=EventPriority.LOW)
    public void onPlayerLoginInitEvent(PlayerLoginInitEvent evt) {
        Channel channel = evt.netty().getChannel();
        PlayerPostLoginInjector.LoginEventContext ctx = (PlayerPostLoginInjector.LoginEventContext)channel.attr(PlayerPostLoginInjector.attr).get();
        if (ctx == null) {
            return;
        }
        IPipelineData pipelineData = (IPipelineData)channel.attr(PipelineAttributes.pipelineData()).get();
        if (pipelineData != null && pipelineData.isCompressionDisable()) {
            ctx.markCompressionDisable(true);
        }
    }

    @EventHandler(priority=EventPriority.LOW)
    public void onPlayerPostLoginEvent(PlayerLoginPostEvent evt) {
        Player player = evt.getPlayer();
        this.plugin.forEachChannel(ch -> BukkitUnsafe.addPlayerChannel(player, ch));
        Channel channel = evt.netty().getChannel();
        IPipelineData pipelineData = (IPipelineData)channel.attr(PipelineAttributes.pipelineData()).getAndSet(null);
        evt.registerIntent(this.plugin);
        BukkitListener.awaitPlayState(pipelineData, () -> {
            PlayerPostLoginInjector.setPlayState(evt);
            try {
                this.plugin.initializePlayer(player, channel, pipelineData, b -> {
                    if (b != Boolean.TRUE) {
                        if (b != null) {
                            evt.setKickMessage((BaseComponent)b);
                        }
                        evt.setCancelled(true);
                    }
                    evt.completeIntent(this.plugin);
                });
            }
            catch (Exception ex) {
                try {
                    evt.setCancelled(true);
                    evt.completeIntent(this.plugin);
                }
                catch (IllegalStateException exx) {
                    return;
                }
                if (ex instanceof RuntimeException) {
                    throw (RuntimeException)ex;
                }
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
        this.plugin.confirmPlayer(evt.getPlayer());
    }

    @EventHandler(priority=EventPriority.LOW)
    public void onPlayerChangedWorldEvent(PlayerChangedWorldEvent evt) {
        if (evt.getFrom() != null) {
            this.plugin.worldChange(evt.getPlayer());
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @EventHandler(priority=EventPriority.MONITOR)
    public void onQuitEvent(PlayerQuitEvent evt) {
        block8: {
            this.plugin.dropPlayer(evt.getPlayer());
            try {
                Object handle = BukkitUnsafe.getHandle(evt.getPlayer());
                GameProfile profile = BukkitUnsafe.getGameProfile(handle);
                if (profile == null) break block8;
                GameProfile gameProfile = profile;
                synchronized (gameProfile) {
                    Property[] toRemove;
                    Multimap<String, Property> props = AuthlibCompat.getProperties(profile);
                    for (Property p : toRemove = (Property[])props.values().stream().filter(AuthlibCompat.nameStartsWith("$eaglerMarker_")).toArray(Property[]::new)) {
                        String name = AuthlibCompat.getName(p);
                        AuthlibCompat.remove(props, name, p);
                        try {
                            this.plugin.postLoginInjector.removeMarker(p);
                        }
                        catch (Throwable throwable) {
                            // empty catch block
                        }
                    }
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
    }
}

