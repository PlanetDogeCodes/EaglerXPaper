/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.ImmutableList
 *  com.google.common.collect.ImmutableMap
 *  com.google.common.collect.MapMaker
 *  eu.hexagonmc.spigot.annotation.meta.DependencyType
 *  eu.hexagonmc.spigot.annotation.plugin.Dependency
 *  eu.hexagonmc.spigot.annotation.plugin.Plugin
 *  eu.hexagonmc.spigot.annotation.plugin.Plugin$Spigot
 *  io.netty.bootstrap.Bootstrap
 *  io.netty.bootstrap.ServerBootstrap
 *  io.netty.channel.Channel
 *  io.netty.channel.ChannelHandler
 *  io.netty.channel.ChannelHandlerContext
 *  io.netty.channel.ChannelInboundHandlerAdapter
 *  io.netty.channel.ChannelPipeline
 *  io.netty.channel.EventLoopGroup
 *  io.netty.channel.ServerChannel
 *  io.netty.channel.epoll.Epoll
 *  io.netty.channel.epoll.EpollEventLoopGroup
 *  io.netty.channel.epoll.EpollServerSocketChannel
 *  io.netty.channel.epoll.EpollSocketChannel
 *  io.netty.channel.socket.nio.NioServerSocketChannel
 *  io.netty.channel.socket.nio.NioSocketChannel
 *  net.md_5.bungee.api.chat.BaseComponent
 *  net.md_5.bungee.api.chat.TextComponent
 *  org.bukkit.Server
 *  org.bukkit.World
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandMap
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.event.Listener
 *  org.bukkit.permissions.Permission
 *  org.bukkit.permissions.PermissionDefault
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 *  org.bukkit.plugin.messaging.Messenger
 *  org.bukkit.plugin.messaging.PluginMessageListener
 *  org.bukkit.scheduler.BukkitTask
 */
package net.lax1dude.eaglercraft.backend.server.bukkit;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MapMaker;
import eu.hexagonmc.spigot.annotation.meta.DependencyType;
import eu.hexagonmc.spigot.annotation.plugin.Dependency;
import eu.hexagonmc.spigot.annotation.plugin.Plugin;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.lax1dude.eaglercraft.backend.server.adapter.AbortLoadException;
import net.lax1dude.eaglercraft.backend.server.adapter.EnumAdapterPlatformType;
import net.lax1dude.eaglercraft.backend.server.adapter.IEaglerXServerCommandType;
import net.lax1dude.eaglercraft.backend.server.adapter.IEaglerXServerJoinListener;
import net.lax1dude.eaglercraft.backend.server.adapter.IEaglerXServerListener;
import net.lax1dude.eaglercraft.backend.server.adapter.IEaglerXServerLoginInitializer;
import net.lax1dude.eaglercraft.backend.server.adapter.IEaglerXServerMessageChannel;
import net.lax1dude.eaglercraft.backend.server.adapter.IEaglerXServerMessageHandler;
import net.lax1dude.eaglercraft.backend.server.adapter.IEaglerXServerNettyPipelineInitializer;
import net.lax1dude.eaglercraft.backend.server.adapter.IEaglerXServerPlayerCountHandler;
import net.lax1dude.eaglercraft.backend.server.adapter.IEaglerXServerPlayerInitializer;
import net.lax1dude.eaglercraft.backend.server.adapter.IPipelineComponent;
import net.lax1dude.eaglercraft.backend.server.adapter.IPipelineData;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatform;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformCommandSender;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformComponentHelper;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformLogger;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformNettyPipelineInitializer;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformPlayer;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformPlayerInitializer;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformScheduler;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformServer;
import net.lax1dude.eaglercraft.backend.server.adapter.JavaLogger;
import net.lax1dude.eaglercraft.backend.server.adapter.PipelineAttributes;
import net.lax1dude.eaglercraft.backend.server.adapter.event.IEventDispatchAdapter;
import net.lax1dude.eaglercraft.backend.server.api.EnumPipelineEvent;
import net.lax1dude.eaglercraft.backend.server.api.bukkit.compat.AuthlibCompat;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServer;
import net.lax1dude.eaglercraft.backend.server.base.NettyPipelineData;
import net.lax1dude.eaglercraft.backend.server.bukkit.BukkitCommand;
import net.lax1dude.eaglercraft.backend.server.bukkit.BukkitConsole;
import net.lax1dude.eaglercraft.backend.server.bukkit.BukkitListener;
import net.lax1dude.eaglercraft.backend.server.bukkit.BukkitLoginData;
import net.lax1dude.eaglercraft.backend.server.bukkit.BukkitPlayer;
import net.lax1dude.eaglercraft.backend.server.bukkit.BukkitScheduler;
import net.lax1dude.eaglercraft.backend.server.bukkit.BukkitUnsafe;
import net.lax1dude.eaglercraft.backend.server.bukkit.BukkitWorld;
import net.lax1dude.eaglercraft.backend.server.bukkit.async.PlayerPostLoginInjector;
import net.lax1dude.eaglercraft.backend.server.bukkit.event.BukkitEventDispatchAdapter;
import net.lax1dude.eaglercraft.backend.server.bungee.chat.BungeeComponentHelper;
import net.lax1dude.eaglercraft.backend.server.config.EnumConfigFormat;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

@Plugin(name="EaglercraftXServer", version="1.1.1 Stable 1", description="Official EaglercraftX plugin for Spigot and Paper servers", spigot=@Plugin.Spigot(authors={"lax1dude"}, website="https://lax1dude.net/eaglerxserver", prefix="EaglerXServer"), dependencies={@Dependency(name="SkinsRestorer", type=DependencyType.SOFTDEPEND)})
public class PlatformPluginBukkit
extends JavaPlugin
implements IPlatform<Player> {
    public static final String PLUGIN_NAME = "EaglercraftXServer";
    public static final String PLUGIN_AUTHOR = "lax1dude";
    public static final String PLUGIN_VERSION = "1.1.1 Stable 1";
    private IPlatformLogger loggerImpl;
    private IEventDispatchAdapter<Player, BaseComponent> eventDispatcherImpl;
    protected boolean aborted = false;
    protected Runnable cleanupListeners;
    protected Runnable onServerEnable;
    protected Runnable onServerDisable;
    protected IEaglerXServerNettyPipelineInitializer<IPipelineData> pipelineInitializer;
    protected IEaglerXServerLoginInitializer<IPipelineData> loginInitializer;
    protected IEaglerXServerPlayerInitializer<IPipelineData, Object, Player> playerInitializer;
    protected IEaglerXServerJoinListener<Player> serverJoinListener;
    protected Collection<IEaglerXServerCommandType<Player>> commandsList;
    protected IEaglerXServerListener listenerConf;
    protected Collection<IEaglerXServerMessageChannel<Player>> playerChannelsList;
    protected boolean post_v1_13;
    protected IPlatformScheduler schedulerImpl;
    protected IPlatformComponentHelper componentHelperImpl;
    protected CommandSender cacheConsoleCommandSenderInstance;
    protected IPlatformCommandSender<Player> cacheConsoleCommandSenderHandle;
    protected boolean enableNativeTransport;
    protected EventLoopGroup eventLoopGroup;
    protected boolean ownsEventLoopGroup;
    protected PlayerPostLoginInjector postLoginInjector;
    private final ConcurrentMap<Player, BukkitPlayer> playerInstanceMap = new MapMaker().initialCapacity(512).concurrencyLevel(16).makeMap();
    private final Class<? extends Channel> channelClassNIO = NioSocketChannel.class;
    private final Class<? extends Channel> channelClassEpoll = EpollSocketChannel.class;
    private final Class<? extends ServerChannel> serverChannelClassNIO = NioServerSocketChannel.class;
    private final Class<? extends ServerChannel> serverChannelClassEpoll = EpollServerSocketChannel.class;
    private static final ImmutableMap<String, IPipelineComponent.EnumPipelineComponent> PIPELINE_COMPONENTS_MAP = ImmutableMap.<String, IPipelineComponent.EnumPipelineComponent>builder().put("splitter", IPipelineComponent.EnumPipelineComponent.FRAME_DECODER).put("prepender", IPipelineComponent.EnumPipelineComponent.FRAME_ENCODER).put("encoder", IPipelineComponent.EnumPipelineComponent.MINECRAFT_ENCODER).put("decoder", IPipelineComponent.EnumPipelineComponent.MINECRAFT_DECODER).put("via-encoder", IPipelineComponent.EnumPipelineComponent.VIA_ENCODER).put("via-decoder", IPipelineComponent.EnumPipelineComponent.VIA_DECODER).put("protocol_lib_inbound_interceptor", IPipelineComponent.EnumPipelineComponent.PROTOCOLLIB_INBOUND_INTERCEPTOR).put("protocol_lib_inbound_protocol_getter", IPipelineComponent.EnumPipelineComponent.PROTOCOLLIB_PROTOCOL_GETTER_NAME).put("protocol_lib_wire_packet_encoder", IPipelineComponent.EnumPipelineComponent.PROTOCOLLIB_WIRE_PACKET_ENCODER).put("timeout", IPipelineComponent.EnumPipelineComponent.READ_TIMEOUT_HANDLER).put("legacy_query", IPipelineComponent.EnumPipelineComponent.BUKKIT_LEGACY_HANDLER).put("packet_handler", IPipelineComponent.EnumPipelineComponent.INBOUND_PACKET_HANDLER).put("pe-decoder-packetevents", IPipelineComponent.EnumPipelineComponent.PACKETEVENTS_DECODER).put("pe-encoder-packetevents", IPipelineComponent.EnumPipelineComponent.PACKETEVENTS_ENCODER).put("pe-timeout-handler-packetevents", IPipelineComponent.EnumPipelineComponent.PACKETEVENTS_TIMEOUT_HANDLER).build();

    public void onLoad() {
        this.aborted = true;
        this.post_v1_13 = this.isPost_v1_13();
        final Server server = this.getServer();
        this.loggerImpl = new JavaLogger(this.getLogger());
        this.eventDispatcherImpl = new BukkitEventDispatchAdapter((org.bukkit.plugin.Plugin)this, server, server.getPluginManager(), server.getScheduler());
        this.schedulerImpl = new BukkitScheduler(this, server.getScheduler());
        this.componentHelperImpl = new BungeeComponentHelper(new TextComponent("Username is already connected to this server!"));
        this.cacheConsoleCommandSenderInstance = server.getConsoleSender();
        this.cacheConsoleCommandSenderHandle = new BukkitConsole(this.cacheConsoleCommandSenderInstance);
        this.enableNativeTransport = Epoll.isAvailable() && BukkitUnsafe.isEnableNativeTransport(server);
        this.eventLoopGroup = BukkitUnsafe.getEventLoopGroup(server, this.enableNativeTransport);
        this.ownsEventLoopGroup = PlatformPluginBukkit.isOwnEventLoopGroup(this.eventLoopGroup);
        this.postLoginInjector = new PlayerPostLoginInjector(this);
        if (this.enableNativeTransport && !(this.eventLoopGroup instanceof EpollEventLoopGroup)) {
            this.enableNativeTransport = false;
        }
        IPlatform.InitNonProxying<Player> init = new IPlatform.InitNonProxying<Player>(){

            @Override
            public void setOnServerEnable(Runnable enable) {
                PlatformPluginBukkit.this.onServerEnable = enable;
            }

            @Override
            public void setOnServerDisable(Runnable disable) {
                PlatformPluginBukkit.this.onServerDisable = disable;
            }

            @Override
            public void setPipelineInitializer(IEaglerXServerNettyPipelineInitializer<? extends IPipelineData> initializer) {
                PlatformPluginBukkit.this.pipelineInitializer = (IEaglerXServerNettyPipelineInitializer<IPipelineData>)initializer;
            }

            @Override
            public void setConnectionInitializer(IEaglerXServerLoginInitializer<? extends IPipelineData> initializer) {
                PlatformPluginBukkit.this.loginInitializer = (IEaglerXServerLoginInitializer<IPipelineData>)initializer;
            }

            @Override
            public void setPlayerInitializer(IEaglerXServerPlayerInitializer<? extends IPipelineData, ?, Player> initializer) {
                PlatformPluginBukkit.this.playerInitializer = (IEaglerXServerPlayerInitializer<IPipelineData, Object, Player>)initializer;
            }

            @Override
            public void setServerJoinListener(IEaglerXServerJoinListener<Player> listener) {
                PlatformPluginBukkit.this.serverJoinListener = listener;
            }

            @Override
            public void setEaglerPlayerChannels(Collection<IEaglerXServerMessageChannel<Player>> channels) {
                PlatformPluginBukkit.this.playerChannelsList = channels;
            }

            @Override
            public IPlatform<Player> getPlatform() {
                return PlatformPluginBukkit.this;
            }

            @Override
            public void setCommandRegistry(Collection<IEaglerXServerCommandType<Player>> commands) {
                PlatformPluginBukkit.this.commandsList = commands;
            }

            @Override
            public void setEaglerListener(IEaglerXServerListener listener) {
                PlatformPluginBukkit.this.listenerConf = listener;
            }

            @Override
            public SocketAddress getListenerAddress() {
                return new InetSocketAddress(server.getIp(), server.getPort());
            }
        };
        try {
            new EaglerXServer<Player>().load(init);
        }
        catch (AbortLoadException ex) {
            this.logger().error("Server startup aborted: " + ex.getMessage());
            Throwable t = ex.getCause();
            if (t != null) {
                this.logger().error("Caused by: ", t);
            }
            throw new IllegalStateException("Startup aborted");
        }
        this.aborted = false;
    }

    public void onEnable() {
        if (this.aborted) {
            return;
        }
        this.aborted = true;
        try {
            String authlibErr = AuthlibCompat.smokeTest();
            if (authlibErr != null) {
                this.loggerImpl.error("***********************************************");
                this.loggerImpl.error("* AUTHLIB COMPAT WARNING:");
                this.loggerImpl.error("* " + authlibErr);
                this.loggerImpl.error("* EaglerXServer will continue to load, but Eagler");
                this.loggerImpl.error("* player detection, skin injection, and the post-");
                this.loggerImpl.error("* login swap flow may fail at runtime.");
                this.loggerImpl.error("***********************************************");
            } else {
                String variant = AuthlibCompat.GAMEPROFILE_IS_RECORD ? "9.x record API (GameProfile record)" : (AuthlibCompat.AUTHLIB_6_PLUS ? "6.x record API (Property record)" : "1.x-4.x legacy API");
                this.loggerImpl.info("AuthlibCompat smoke test passed (authlib " + variant + " detected).");
            }
        }
        catch (Throwable t) {
            this.loggerImpl.error("***********************************************");
            this.loggerImpl.error("* AUTHLIB COMPAT INITIALIZATION FAILED:");
            this.loggerImpl.error("* " + t.getClass().getSimpleName() + ": " + t.getMessage());
            this.loggerImpl.error("* EaglerXServer will continue to load, but authlib-based");
            this.loggerImpl.error("* features (isEaglerPlayer, skin textures lookup) will");
            this.loggerImpl.error("* return false/null. Please report this to the developer.");
            this.loggerImpl.error("***********************************************");
        }
        Server server = this.getServer();
        server.getPluginManager().registerEvents((Listener)new BukkitListener(this), (org.bukkit.plugin.Plugin)this);
        CommandMap cmdMap = BukkitUnsafe.getCommandMap(server);
        for (IEaglerXServerCommandType<Player> cmd : this.commandsList) {
            server.getPluginManager().addPermission(new Permission(cmd.getPermission(), PermissionDefault.OP));
            cmdMap.register("eagler", (Command)new BukkitCommand(this, cmd));
        }
        Messenger msgr = server.getMessenger();
        PluginMessageListener ls = (ch, player, data) -> {
            BukkitPlayer playerInstance = (BukkitPlayer)this.playerInstanceMap.get(player);
            if (playerInstance != null) {
                playerInstance.handleMCBrandMessage(data);
            }
        };
        if (!this.post_v1_13) {
            msgr.registerIncomingPluginChannel((org.bukkit.plugin.Plugin)this, "MC|Brand", ls);
        }
        msgr.registerIncomingPluginChannel((org.bukkit.plugin.Plugin)this, "minecraft:brand", ls);
        for (IEaglerXServerMessageChannel<Player> channel2 : this.playerChannelsList) {
            IEaglerXServerMessageHandler<Player> handler = channel2.getHandler();
            msgr.registerOutgoingPluginChannel((org.bukkit.plugin.Plugin)this, channel2.getModernName());
            if (!this.post_v1_13) {
                msgr.registerOutgoingPluginChannel((org.bukkit.plugin.Plugin)this, channel2.getLegacyName());
            }
            if (handler == null) continue;
            ls = (ch, player, data) -> {
                BukkitPlayer playerInstance = (BukkitPlayer)this.playerInstanceMap.get(player);
                if (playerInstance != null) {
                    handler.handle(channel2, playerInstance, data);
                }
            };
            msgr.registerIncomingPluginChannel((org.bukkit.plugin.Plugin)this, channel2.getModernName(), ls);
            if (this.post_v1_13) continue;
            msgr.registerIncomingPluginChannel((org.bukkit.plugin.Plugin)this, channel2.getLegacyName(), ls);
        }
        this.cleanupListeners = BukkitUnsafe.injectChannelInitializer(this.getServer(), channel -> {
            if (!channel.isActive()) {
                return;
            }
            final List<IPipelineComponent> pipelineList = new ArrayList<IPipelineComponent>();
            ChannelPipeline pipeline = channel.pipeline();
            ChannelHandler networkManager = pipeline.get("packet_handler");
            if (networkManager != null) {
                // Version detection must NOT depend on bind() state: bind() only runs on the
                // first Eagler connection, so the first 1.20.2+ connection would otherwise be
                // mis-routed through the legacy NetworkManager-wrap path, skip the UUID
                // registrar and lose its login context ("no login context found").
                if (this.postLoginInjector.isModernLoginFlow(networkManager)) {
                    this.postLoginInjector.storeContext(networkManager, (Channel)channel);
                    // IMPORTANT: must be BEFORE packet_handler, not addLast. On MC 1.20.5+/26.x
                    // the vanilla Connection handler does not forward userEventTriggered down the
                    // pipeline, so a tail-positioned registrar would never observe
                    // EAGLER_ENTERED_PLAY_STATE and every 1.20.2+ Eagler login would log
                    // "no login context found" and lose post-login features.
                    pipeline.addBefore("packet_handler", "eagler-login-uuid-registrar", (ChannelHandler)new PlayStateUUIDRegistrar());
                } else {
                    pipeline.replace("packet_handler", "packet_handler", (ChannelHandler)this.postLoginInjector.wrapNetworkManager(networkManager, (Channel)channel));
                }
            }
            for (final String str : pipeline.names()) {
                final ChannelHandler handler = pipeline.get(str);
                if (handler == null) continue;
                pipelineList.add(new IPipelineComponent(){
                    private IPipelineComponent.EnumPipelineComponent type = null;

                    @Override
                    public IPipelineComponent.EnumPipelineComponent getIdentifiedType() {
                        if (this.type == null) {
                            this.type = PIPELINE_COMPONENTS_MAP.getOrDefault(str, IPipelineComponent.EnumPipelineComponent.UNIDENTIFIED);
                        }
                        return this.type;
                    }

                    @Override
                    public String getName() {
                        return str;
                    }

                    @Override
                    public ChannelHandler getHandle() {
                        return handler;
                    }
                });
            }
            this.pipelineInitializer.initialize(new IPlatformNettyPipelineInitializer<IPipelineData>(){

                @Override
                public void setAttachment(IPipelineData object) {
                    channel.attr(PipelineAttributes.pipelineData()).set((Object)object);
                }

                @Override
                public List<IPipelineComponent> getPipeline() {
                    return pipelineList;
                }

                @Override
                public IEaglerXServerListener getListener() {
                    return PlatformPluginBukkit.this.listenerConf;
                }

                @Override
                public Consumer<SocketAddress> realAddressHandle() {
                    return addr -> {
                        try {
                            PlayerPostLoginInjector.LoginEventContext ctx = (PlayerPostLoginInjector.LoginEventContext)channel.attr(PlayerPostLoginInjector.attr).get();
                            if (ctx != null) {
                                BukkitUnsafe.updateRealAddress(ctx.originalNetworkManager(), addr);
                            } else {
                                ChannelHandler o = channel.pipeline().get("packet_handler");
                                if (o != null) {
                                    BukkitUnsafe.updateRealAddress(o, addr);
                                }
                            }
                        }
                        catch (Throwable t) {
                            Logger.getLogger("EaglerXServer").log(Level.WARNING, "Failed to update real address", t);
                        }
                    };
                }

                @Override
                public Channel getChannel() {
                    return channel;
                }
            });
        }, this.listenerConf);
        if (this.onServerEnable != null) {
            this.onServerEnable.run();
        }
        this.aborted = false;
    }

    public void onDisable() {
        if (this.aborted) {
            return;
        }
        if (this.cleanupListeners != null) {
            this.cleanupListeners.run();
            this.cleanupListeners = null;
        }
        if (this.onServerDisable != null) {
            this.onServerDisable.run();
        }
        Server server = this.getServer();
        for (IEaglerXServerCommandType<Player> cmd : this.commandsList) {
            server.getPluginManager().removePermission(cmd.getPermission());
        }
        Messenger msgr = server.getMessenger();
        if (!this.post_v1_13) {
            msgr.unregisterIncomingPluginChannel((org.bukkit.plugin.Plugin)this, "MC|Brand");
        }
        msgr.unregisterIncomingPluginChannel((org.bukkit.plugin.Plugin)this, "minecraft:brand");
        for (IEaglerXServerMessageChannel<Player> channel : this.playerChannelsList) {
            IEaglerXServerMessageHandler<Player> handler = channel.getHandler();
            msgr.unregisterOutgoingPluginChannel((org.bukkit.plugin.Plugin)this, channel.getModernName());
            if (!this.post_v1_13) {
                msgr.unregisterOutgoingPluginChannel((org.bukkit.plugin.Plugin)this, channel.getLegacyName());
            }
            if (handler == null) continue;
            msgr.unregisterIncomingPluginChannel((org.bukkit.plugin.Plugin)this, channel.getModernName());
            if (this.post_v1_13) continue;
            msgr.unregisterIncomingPluginChannel((org.bukkit.plugin.Plugin)this, channel.getLegacyName());
        }
        if (this.ownsEventLoopGroup && this.eventLoopGroup != null) {
            try {
                this.eventLoopGroup.shutdownGracefully(0L, 5L, TimeUnit.SECONDS);
            }
            catch (Exception exception) {
                // empty catch block
            }
            this.eventLoopGroup = null;
            this.ownsEventLoopGroup = false;
        }
    }

    private static boolean isOwnEventLoopGroup(EventLoopGroup group) {
        if (group == null) {
            return false;
        }
        try {
            String str = group.toString();
            if (str != null && str.contains("EaglerXPaper IO")) {
                return true;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return false;
    }

    @Override
    public EnumAdapterPlatformType getType() {
        return EnumAdapterPlatformType.BUKKIT;
    }

    @Override
    public String getVersion() {
        return this.getServer().getVersion();
    }

    @Override
    public String getPluginId() {
        return this.getDescription().getName();
    }

    @Override
    public IPlatformLogger logger() {
        return this.loggerImpl;
    }

    @Override
    public IPlatformCommandSender<Player> getConsole() {
        return this.cacheConsoleCommandSenderHandle;
    }

    IPlatformCommandSender<Player> getCommandSender(CommandSender obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Player) {
            return this.getPlayer((Player)obj);
        }
        if (obj == this.cacheConsoleCommandSenderInstance) {
            return this.cacheConsoleCommandSenderHandle;
        }
        return new BukkitConsole(obj);
    }

    @Override
    public void forEachPlayer(Consumer<IPlatformPlayer<Player>> playerCallback) {
        this.playerInstanceMap.values().forEach(playerCallback);
    }

    @Override
    public Collection<IPlatformPlayer<Player>> getAllPlayers() {
        return ImmutableList.copyOf(this.playerInstanceMap.values());
    }

    @Override
    public IPlatformPlayer<Player> getPlayer(Player playerObj) {
        return (IPlatformPlayer)this.playerInstanceMap.get(playerObj);
    }

    @Override
    public IPlatformPlayer<Player> getPlayer(String username) {
        Player player = this.getServer().getPlayer(username);
        if (player != null) {
            return (IPlatformPlayer)this.playerInstanceMap.get(player);
        }
        return null;
    }

    @Override
    public IPlatformPlayer<Player> getPlayer(UUID uuid) {
        Player player = this.getServer().getPlayer(uuid);
        if (player != null) {
            return (IPlatformPlayer)this.playerInstanceMap.get(player);
        }
        return null;
    }

    @Override
    public Map<String, IPlatformServer<Player>> getRegisteredServers() {
        return Collections.emptyMap();
    }

    @Override
    public IPlatformServer<Player> getServer(String serverName) {
        World world = this.getServer().getWorld(serverName);
        return world != null ? new BukkitWorld(this, world) : null;
    }

    @Override
    public IEventDispatchAdapter<Player, ?> eventDispatcher() {
        return this.eventDispatcherImpl;
    }

    @Override
    public Class<Player> getPlayerClass() {
        return Player.class;
    }

    @Override
    public IPlatformScheduler getScheduler() {
        return this.schedulerImpl;
    }

    @Override
    public Set<EnumConfigFormat> getConfigFormats() {
        return EnumConfigFormat.getSupported();
    }

    @Override
    public IPlatformComponentHelper getComponentHelper() {
        return this.componentHelperImpl;
    }

    @Override
    public boolean isOnlineMode() {
        return this.getServer().getOnlineMode();
    }

    @Override
    public boolean isModernPluginChannelNamesOnly() {
        return this.post_v1_13;
    }

    @Override
    public int getPlayerTotal() {
        return this.getServer().getOnlinePlayers().size();
    }

    @Override
    public int getPlayerMax() {
        return this.getServer().getMaxPlayers();
    }

    @Override
    public void setPlayerCountHandler(IEaglerXServerPlayerCountHandler playerCountHandler) {
    }

    @Override
    public Bootstrap setChannelFactory(Bootstrap bootstrap, SocketAddress address) {
        if (PlatformPluginBukkit.isUnixDomainAddress(address)) {
            throw new UnsupportedOperationException("Unix sockets not supported by this platform!");
        }
        if (this.enableNativeTransport) {
            return (Bootstrap)bootstrap.channel(this.channelClassEpoll);
        }
        return (Bootstrap)bootstrap.channel(this.channelClassNIO);
    }

    @Override
    public ServerBootstrap setServerChannelFactory(ServerBootstrap bootstrap, SocketAddress address) {
        if (PlatformPluginBukkit.isUnixDomainAddress(address)) {
            throw new UnsupportedOperationException("Unix sockets not supported by this platform!");
        }
        if (this.enableNativeTransport) {
            return (ServerBootstrap)bootstrap.channel(this.serverChannelClassEpoll);
        }
        return (ServerBootstrap)bootstrap.channel(this.serverChannelClassNIO);
    }

    @Override
    public EventLoopGroup getBossEventLoopGroup() {
        return null;
    }

    @Override
    public EventLoopGroup getWorkerEventLoopGroup() {
        return this.eventLoopGroup;
    }

    public void initializePlayer(final Player player, Channel channel, final IPipelineData pipelineData, final Consumer<Object> onComplete) {
        BukkitLoginData loginData = new BukkitLoginData(pipelineData);
        this.loginInitializer.initializeLogin(loginData);
        if (loginData.eaglerPlayerProperty != 0 || loginData.texturesPropertyValue != null) {
            BukkitUnsafe.PropertyInjector injector = BukkitUnsafe.propertyInjector(player);
            if (loginData.texturesPropertyValue != null) {
                injector.injectTexturesProperty(loginData.texturesPropertyValue, loginData.texturesPropertySignature);
                loginData.texturesPropertyValue = null;
                loginData.texturesPropertySignature = null;
            }
            if (loginData.eaglerPlayerProperty != 0) {
                injector.injectIsEaglerPlayerProperty(loginData.eaglerPlayerProperty == 2);
            }
            injector.complete();
        }
        final BukkitPlayer p = new BukkitPlayer(this, player, channel);
        p.closeRedirector = new CloseRedirector();
        this.playerInitializer.initializePlayer(new IPlatformPlayerInitializer<IPipelineData, Object, Player>(){

            @Override
            public void setPlayerAttachment(Object attachment) {
                p.attachment = attachment;
            }

            @Override
            public IPipelineData getPipelineAttachment() {
                return pipelineData;
            }

            @Override
            public IPlatformPlayer<Player> getPlayer() {
                return p;
            }

            /*
             * WARNING - Removed try catching itself - possible behaviour change.
             */
            @Override
            public void complete() {
                Object obj = null;
                BukkitPlayer bukkitPlayer = p;
                synchronized (bukkitPlayer) {
                    if (p.closeRedirector != null) {
                        obj = ((CloseRedirector)p.closeRedirector).val;
                        p.closeRedirector = null;
                    }
                }
                if (obj != null) {
                    onComplete.accept(obj);
                    return;
                }
                PlatformPluginBukkit.this.playerInstanceMap.put(player, p);
                final BukkitPlayer p2 = p;
                p.confirmTask = PlatformPluginBukkit.this.getServer().getScheduler().runTaskLaterAsynchronously((org.bukkit.plugin.Plugin)PlatformPluginBukkit.this, () -> {
                    p2.confirmTask = null;
                    PlatformPluginBukkit.this.getLogger().warning("Player " + p.getUsername() + " was initialized, but never fired PlayerJoinEvent, dropping...");
                    PlatformPluginBukkit.this.dropPlayer(player);
                }, 100L);
                IEaglerXServerJoinListener<Player> listener = PlatformPluginBukkit.this.serverJoinListener;
                if (listener != null) {
                    listener.handlePreConnect(p);
                }
                onComplete.accept(true);
            }

            /*
             * WARNING - Removed try catching itself - possible behaviour change.
             */
            @Override
            public void cancel() {
                Object obj = null;
                BukkitPlayer bukkitPlayer = p;
                synchronized (bukkitPlayer) {
                    if (p.closeRedirector != null) {
                        obj = ((CloseRedirector)p.closeRedirector).val;
                        p.closeRedirector = null;
                    }
                }
                if (obj != null) {
                    onComplete.accept(obj);
                    return;
                }
                onComplete.accept(null);
            }
        });
    }

    public void confirmPlayer(Player player) {
        BukkitPlayer p = (BukkitPlayer)this.playerInstanceMap.get(player);
        if (p != null) {
            IEaglerXServerJoinListener<Player> listener;
            BukkitTask conf = p.xchgConfirmTask();
            if (conf != null) {
                conf.cancel();
            }
            if ((listener = this.serverJoinListener) != null) {
                listener.handlePostConnect(p, p.getServer());
            }
        }
    }

    public void dropPlayer(Player player) {
        BukkitPlayer p = (BukkitPlayer)this.playerInstanceMap.remove(player);
        if (p != null) {
            BukkitTask conf = p.xchgConfirmTask();
            if (conf != null) {
                conf.cancel();
            }
            this.playerInitializer.destroyPlayer(p);
        }
    }

    private static boolean isUnixDomainAddress(SocketAddress address) {
        if (address == null) {
            return false;
        }
        for (Class<?> clz = address.getClass(); clz != null; clz = clz.getSuperclass()) {
            if (!"io.netty.channel.unix.DomainSocketAddress".equals(clz.getName())) continue;
            return true;
        }
        return false;
    }

    public void worldChange(Player player) {
        IEaglerXServerJoinListener<Player> listener;
        BukkitPlayer p = (BukkitPlayer)this.playerInstanceMap.get(player);
        if (p != null && (listener = this.serverJoinListener) != null) {
            listener.handlePreConnect(p);
            listener.handlePostConnect(p, p.getServer());
        }
    }

    public void forEachChannel(Consumer<String> cb) {
        for (IEaglerXServerMessageChannel<Player> ch : this.playerChannelsList) {
            cb.accept(ch.getModernName());
            if (this.post_v1_13) continue;
            cb.accept(ch.getLegacyName());
        }
    }

    private boolean isPost_v1_13() {
        String[] ver = this.getServer().getBukkitVersion().split("[\\.\\-]");
        if (ver.length >= 2) {
            try {
                int i = Integer.parseInt(ver[0]);
                int j = Integer.parseInt(ver[1]);
                return i > 1 || i == 1 && j >= 13;
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
        }
        return false;
    }

    private static class CloseRedirector
    implements Consumer<Object> {
        protected Object val;

        private CloseRedirector() {
        }

        @Override
        public void accept(Object val) {
            this.val = val;
        }
    }

    private final class PlayStateUUIDRegistrar
    extends ChannelInboundHandlerAdapter {
        private PlayStateUUIDRegistrar() {
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
            if (evt == EnumPipelineEvent.EAGLER_BACKEND_LOGIN_START || evt == EnumPipelineEvent.EAGLER_ENTERED_PLAY_STATE) {
                try {
                    NettyPipelineData pipelineData = (NettyPipelineData)ctx.channel().attr(PipelineAttributes.pipelineData()).get();
                    PlayerPostLoginInjector.LoginEventContext loginCtx = (PlayerPostLoginInjector.LoginEventContext)ctx.channel().attr(PlayerPostLoginInjector.attr).get();
                    if (pipelineData != null && loginCtx != null) {
                        loginCtx.markCompressionDisable(true);
                        // Registering on BOTH events is idempotent (map put). EAGLER_BACKEND_LOGIN_START
                        // fires before the vanilla login listener processes LoginStart, guaranteeing the
                        // context is present when PlayerLoginEvent fires early on 1.20.2+.
                        PlatformPluginBukkit.this.postLoginInjector.registerCtxByUUID(pipelineData.uuid, loginCtx);
                        // An auth/nickname-selection event can change the username AFTER pipelineData.uuid
                        // was derived from the original name; the vanilla server (pre-1.19.1 protocols that
                        // ignore the client UUID) derives the player's offline UUID from the FINAL name in
                        // the synthesized LoginStart. Register under that key as well so the lookup still
                        // matches after a rename.
                        if (pipelineData.username != null) {
                            byte[] nameBytes = ("OfflinePlayer:" + pipelineData.username).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                            PlatformPluginBukkit.this.postLoginInjector.registerCtxByUUID(java.util.UUID.nameUUIDFromBytes(nameBytes), loginCtx);
                        }
                    }
                    else if (evt == EnumPipelineEvent.EAGLER_BACKEND_LOGIN_START) {
                        // Only the early event requires the attributes; by EAGLER_ENTERED_PLAY_STATE the
                        // PlayerLoginPostEvent may already have consumed them (normal flow).
                        PlatformPluginBukkit.this.loggerImpl.warn("EaglerXServer: could not resolve pipeline attributes to register login context by UUID");
                    }
                    if (evt == EnumPipelineEvent.EAGLER_ENTERED_PLAY_STATE) {
                        Channel channel = ctx.channel();
                        PlatformPluginBukkit.this.getServer().getScheduler().runTaskAsynchronously((org.bukkit.plugin.Plugin)PlatformPluginBukkit.this, () -> {
                            try {
                                PlatformPluginBukkit.this.postLoginInjector.fireEventLoginInit(channel);
                            }
                            catch (Throwable t) {
                                PlatformPluginBukkit.this.loggerImpl.warn("EaglerXServer: PlayerLoginInitEvent dispatch failed", t);
                            }
                        });
                    }
                }
                catch (Throwable t) {
                    PlatformPluginBukkit.this.loggerImpl.warn("EaglerXServer: failed to register login context by UUID", t);
                }
                if (evt == EnumPipelineEvent.EAGLER_ENTERED_PLAY_STATE) {
                    ctx.pipeline().remove((ChannelHandler)this);
                }
            }
            super.userEventTriggered(ctx, evt);
        }
    }
}

