/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.channel.Channel
 *  net.md_5.bungee.api.chat.BaseComponent
 *  net.md_5.bungee.api.chat.TextComponent
 *  org.bukkit.World
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitTask
 */
package net.lax1dude.eaglercraft.backend.server.bukkit;

import io.netty.channel.Channel;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformPlayer;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformServer;
import net.lax1dude.eaglercraft.backend.server.bukkit.BukkitUnsafe;
import net.lax1dude.eaglercraft.backend.server.bukkit.BukkitWorld;
import net.lax1dude.eaglercraft.backend.server.bukkit.PlatformPluginBukkit;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

class BukkitPlayer
implements IPlatformPlayer<Player> {
    private static final AtomicReferenceFieldUpdater<BukkitPlayer, BukkitTask> CONFIRM_TASK_UPDATER;
    private static final boolean PAPER_VIEW_DISTANCE_SUPPORT;
    private static final Method PAPER_SET_VIEW_DISTANCE_SEND;
    private static final Method PAPER_SET_VIEW_DISTANCE;
    private final PlatformPluginBukkit plugin;
    private final Player player;
    private final Channel channel;
    volatile BukkitTask confirmTask;
    Object attachment;
    private String brandString;
    Consumer<Object> closeRedirector;
    boolean closePending;

    BukkitPlayer(PlatformPluginBukkit plugin, Player player, Channel channel) {
        this.plugin = plugin;
        this.player = player;
        this.channel = channel;
        this.brandString = null;
    }

    BukkitTask xchgConfirmTask() {
        return CONFIRM_TASK_UPDATER.getAndSet(this, null);
    }

    @Override
    public Player getPlayerObject() {
        return this.player;
    }

    @Override
    public Channel getChannel() {
        return this.channel;
    }

    @Override
    public IPlatformServer<Player> getServer() {
        World world = this.player.getWorld();
        return world != null ? new BukkitWorld(this.plugin, world) : null;
    }

    @Override
    public String getUsername() {
        return this.player.getName();
    }

    @Override
    public UUID getUniqueId() {
        return this.player.getUniqueId();
    }

    @Override
    public boolean isConnected() {
        if (this.closePending) {
            return false;
        }
        Channel c = BukkitUnsafe.getPlayerChannel(this.player);
        return c != null && c.isActive();
    }

    @Override
    public SocketAddress getSocketAddress() {
        return this.player.getAddress();
    }

    @Override
    public int getMinecraftProtocol() {
        return 47;
    }

    @Override
    public boolean isOnlineMode() {
        return this.plugin.getServer().getOnlineMode();
    }

    @Override
    public String getMinecraftBrand() {
        return this.brandString;
    }

    @Override
    public void sendDataClient(String channel, byte[] message) {
        this.player.sendPluginMessage((Plugin)this.plugin, channel, message);
    }

    @Override
    public void sendDataBackend(String channel, byte[] message) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSetViewDistanceSupportedPaper() {
        return PAPER_VIEW_DISTANCE_SUPPORT;
    }

    @Override
    public void setViewDistancePaper(int distance) {
        if (PAPER_SET_VIEW_DISTANCE_SEND != null) {
            try {
                PAPER_SET_VIEW_DISTANCE_SEND.invoke((Object)this.player, distance);
            }
            catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new RuntimeException("Reflection failed!");
            }
        }
        if (PAPER_SET_VIEW_DISTANCE != null) {
            try {
                PAPER_SET_VIEW_DISTANCE.invoke((Object)this.player, distance);
            }
            catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new RuntimeException("Reflection failed!");
            }
        }
    }

    @Override
    public String getTexturesProperty() {
        return BukkitUnsafe.getTexturesProperty(this.player);
    }

    @Override
    public void sendMessage(String message) {
        this.player.sendMessage((BaseComponent)new TextComponent(message));
    }

    @Override
    public <ComponentObject> void sendMessage(ComponentObject component) {
        this.player.sendMessage((BaseComponent)component);
    }

    @Override
    public void disconnect() {
        this.disconnect("Connection Closed");
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void disconnect(String kickMessage) {
        this.closePending = true;
        BukkitPlayer bukkitPlayer = this;
        synchronized (bukkitPlayer) {
            if (this.closeRedirector != null) {
                this.closeRedirector.accept(new TextComponent(kickMessage));
                return;
            }
        }
        this.plugin.getScheduler().execute(() -> this.player.kickPlayer(kickMessage));
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public <ComponentObject> void disconnect(ComponentObject kickMessage) {
        this.closePending = true;
        BukkitPlayer bukkitPlayer = this;
        synchronized (bukkitPlayer) {
            if (this.closeRedirector != null) {
                this.closeRedirector.accept(kickMessage);
                return;
            }
        }
        String msg = ((BaseComponent)kickMessage).toLegacyText();
        this.plugin.getScheduler().execute(() -> this.player.kickPlayer(msg));
    }

    @Override
    public <T> T getPlayerAttachment() {
        return (T)this.attachment;
    }

    @Override
    public boolean checkPermission(String permission) {
        return this.player.hasPermission(permission);
    }

    @Override
    public boolean isPlayer() {
        return true;
    }

    @Override
    public IPlatformPlayer<Player> asPlayer() {
        return this;
    }

    void handleMCBrandMessage(byte[] data) {
        int len;
        if (data.length > 0 && (len = data[0] & 0xFF) < 128 && len == data.length - 1) {
            this.brandString = new String(data, 1, len, StandardCharsets.UTF_8);
        }
    }

    static {
        boolean support;
        Method viewDistance;
        Method viewDistanceSend;
        CONFIRM_TASK_UPDATER = AtomicReferenceFieldUpdater.newUpdater(BukkitPlayer.class, BukkitTask.class, "confirmTask");
        try {
            viewDistanceSend = Player.class.getMethod("setSendViewDistance", Integer.TYPE);
            viewDistance = null;
            support = true;
        }
        catch (NoSuchMethodException ex) {
            viewDistanceSend = null;
            try {
                viewDistance = Player.class.getMethod("setViewDistance", Integer.TYPE);
                support = true;
            }
            catch (NoSuchMethodException exx) {
                viewDistance = null;
                support = false;
            }
        }
        PAPER_VIEW_DISTANCE_SUPPORT = support;
        PAPER_SET_VIEW_DISTANCE_SEND = viewDistanceSend;
        PAPER_SET_VIEW_DISTANCE = viewDistance;
    }
}

