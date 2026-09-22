/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.Collections2
 *  com.google.common.collect.ImmutableList
 *  com.google.common.collect.ImmutableList$Builder
 *  com.google.common.collect.ImmutableMap
 *  com.google.common.collect.ImmutableMap$Builder
 *  com.google.common.collect.Interner
 *  com.google.common.collect.Interners
 *  com.google.common.collect.MapMaker
 *  com.google.gson.Gson
 *  com.google.gson.GsonBuilder
 *  io.netty.bootstrap.Bootstrap
 *  io.netty.bootstrap.ServerBootstrap
 *  io.netty.channel.EventLoopGroup
 */
package net.lax1dude.eaglercraft.backend.server.base;

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.collect.MapMaker;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.net.ssl.SSLException;
import net.lax1dude.eaglercraft.backend.server.adapter.AbortLoadException;
import net.lax1dude.eaglercraft.backend.server.adapter.IEaglerXServerImpl;
import net.lax1dude.eaglercraft.backend.server.adapter.IEaglerXServerListener;
import net.lax1dude.eaglercraft.backend.server.api.rewind.IEaglerXRewindProtocol;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatform;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformComponentBuilder;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformComponentHelper;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformLogger;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformPlayer;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformServer;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformTask;
import net.lax1dude.eaglercraft.backend.server.adapter.event.IEventDispatchAdapter;
import net.lax1dude.eaglercraft.backend.server.api.EnumPlatformType;
import net.lax1dude.eaglercraft.backend.server.api.ExtendedCapabilitySpec;
import net.lax1dude.eaglercraft.backend.server.api.IBasePlayer;
import net.lax1dude.eaglercraft.backend.server.api.IBinaryHTTPClient;
import net.lax1dude.eaglercraft.backend.server.api.IComponentHelper;
import net.lax1dude.eaglercraft.backend.server.api.IComponentSerializer;
import net.lax1dude.eaglercraft.backend.server.api.IEaglerListenerInfo;
import net.lax1dude.eaglercraft.backend.server.api.IEaglerPlayer;
import net.lax1dude.eaglercraft.backend.server.api.IEaglerXServerAPI;
import net.lax1dude.eaglercraft.backend.server.api.IPacketImageLoader;
import net.lax1dude.eaglercraft.backend.server.api.IScheduler;
import net.lax1dude.eaglercraft.backend.server.api.IServerIconLoader;
import net.lax1dude.eaglercraft.backend.server.api.IUpdateCertificate;
import net.lax1dude.eaglercraft.backend.server.api.attribute.IAttributeKey;
import net.lax1dude.eaglercraft.backend.server.api.attribute.IAttributeManager;
import net.lax1dude.eaglercraft.backend.server.api.collect.HPPC;
import net.lax1dude.eaglercraft.backend.server.api.internal.factory.IEaglerAPIFactory;
import net.lax1dude.eaglercraft.backend.server.api.nbt.INBTHelper;
import net.lax1dude.eaglercraft.backend.server.api.skins.TexturesProperty;
import net.lax1dude.eaglercraft.backend.server.base.APIFactoryImpl;
import net.lax1dude.eaglercraft.backend.server.base.BasePlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.BinaryHTTPClient;
import net.lax1dude.eaglercraft.backend.server.base.BrandService;
import net.lax1dude.eaglercraft.backend.server.base.ClientStateFlagUUIDs;
import net.lax1dude.eaglercraft.backend.server.base.ComponentHelper;
import net.lax1dude.eaglercraft.backend.server.base.DeferredStartSkinCache;
import net.lax1dude.eaglercraft.backend.server.base.EaglerAttributeManager;
import net.lax1dude.eaglercraft.backend.server.base.EaglerListener;
import net.lax1dude.eaglercraft.backend.server.base.EaglerPlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServerJoinListener;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServerLoginInitializer;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServerNettyPipelineInitializer;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServerPlayerInitializer;
import net.lax1dude.eaglercraft.backend.server.base.ExtCapabilityMap;
import net.lax1dude.eaglercraft.backend.server.base.LegacyInternalHTTPClient;
import net.lax1dude.eaglercraft.backend.server.base.NettyPipelineData;
import net.lax1dude.eaglercraft.backend.server.base.PacketImageLoader;
import net.lax1dude.eaglercraft.backend.server.base.PlayerRateLimits;
import net.lax1dude.eaglercraft.backend.server.base.RewindService;
import net.lax1dude.eaglercraft.backend.server.base.SSLCertificateManager;
import net.lax1dude.eaglercraft.backend.server.base.ServerIconLoader;
import net.lax1dude.eaglercraft.backend.server.base.UpdateChecker;
import net.lax1dude.eaglercraft.backend.server.base.collect.HPPCFactory;
import net.lax1dude.eaglercraft.backend.server.base.command.CommandBrand;
import net.lax1dude.eaglercraft.backend.server.base.command.CommandConfirmCode;
import net.lax1dude.eaglercraft.backend.server.base.command.CommandDomain;
import net.lax1dude.eaglercraft.backend.server.base.command.CommandProtocol;
import net.lax1dude.eaglercraft.backend.server.base.command.CommandUserAgent;
import net.lax1dude.eaglercraft.backend.server.base.command.CommandVersion;
import net.lax1dude.eaglercraft.backend.server.base.config.ConfigDataListener;
import net.lax1dude.eaglercraft.backend.server.base.config.ConfigDataPauseMenu;
import net.lax1dude.eaglercraft.backend.server.base.config.ConfigDataRoot;
import net.lax1dude.eaglercraft.backend.server.base.config.ConfigDataSettings;
import net.lax1dude.eaglercraft.backend.server.base.config.ConfigDataSupervisor;
import net.lax1dude.eaglercraft.backend.server.base.config.EaglerConfigLoader;
import net.lax1dude.eaglercraft.backend.server.base.config.EaglerXPaperConfig;
import net.lax1dude.eaglercraft.backend.server.base.message.MessageControllerFactory;
import net.lax1dude.eaglercraft.backend.server.base.message.PlayerChannelHelper;
import net.lax1dude.eaglercraft.backend.server.base.nbt.NBTHelper;
import net.lax1dude.eaglercraft.backend.server.base.notifications.NotificationService;
import net.lax1dude.eaglercraft.backend.server.base.pause_menu.PauseMenuService;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.PipelineTransformer;
import net.lax1dude.eaglercraft.backend.server.base.query.QueryServer;
import net.lax1dude.eaglercraft.backend.server.base.rpc.BackendChannelHelper;
import net.lax1dude.eaglercraft.backend.server.base.rpc.BackendRPCService;
import net.lax1dude.eaglercraft.backend.server.base.skins.ProfileResolver;
import net.lax1dude.eaglercraft.backend.server.base.skins.SimpleProfileCache;
import net.lax1dude.eaglercraft.backend.server.base.skins.SkinCachePrewarmer;
import net.lax1dude.eaglercraft.backend.server.base.skins.SkinManagerEagler;
import net.lax1dude.eaglercraft.backend.server.base.skins.SkinService;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.ISupervisorServiceImpl;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.SupervisorService;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.SupervisorServiceDisabled;
import net.lax1dude.eaglercraft.backend.server.base.update.IUpdateCertificateImpl;
import net.lax1dude.eaglercraft.backend.server.base.update.UpdateCertificate;
import net.lax1dude.eaglercraft.backend.server.base.update.UpdateService;
import net.lax1dude.eaglercraft.backend.server.base.voice.IVoiceServiceImpl;
import net.lax1dude.eaglercraft.backend.server.base.voice.VoiceServiceDisabled;
import net.lax1dude.eaglercraft.backend.server.base.voice.VoiceServiceLocal;
import net.lax1dude.eaglercraft.backend.server.base.voice.VoiceServiceRemote;
import net.lax1dude.eaglercraft.backend.server.base.webserver.WebServer;
import net.lax1dude.eaglercraft.backend.server.base.webview.WebViewService;
import net.lax1dude.eaglercraft.backend.server.util.GsonLenient;
import net.lax1dude.eaglercraft.backend.server.util.Util;
import net.lax1dude.eaglercraft.backend.skin_cache.HTTPClient;
import net.lax1dude.eaglercraft.backend.skin_cache.IHTTPClient;
import net.lax1dude.eaglercraft.backend.skin_cache.ISkinCacheService;
import net.lax1dude.eaglercraft.backend.skin_cache.SkinCacheDatastore;
import net.lax1dude.eaglercraft.backend.skin_cache.SkinCacheDownloader;
import net.lax1dude.eaglercraft.backend.skin_cache.SkinCacheService;
import net.lax1dude.eaglercraft.backend.util.EaglerDrivers;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.GamePluginMessageProtocol;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketClientStateFlagV5EAG;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketOtherPlayerClientUUIDV4EAG;

public class EaglerXServer<PlayerObject>
implements IEaglerXServerImpl<PlayerObject>,
IEaglerAPIFactory,
IEaglerXServerAPI<PlayerObject>,
IEaglerXServerAPI.NettyUnsafe {
    public static final Gson GSON_PRETTY = GsonLenient.setLenient(new GsonBuilder()).setPrettyPrinting().create();
    public static final Interner<UUID> uuidInterner = Interners.newWeakInterner();
    private final EaglerAttributeManager attributeManager = APIFactoryImpl.INSTANCE.getEaglerAttribManager();
    private final EaglerAttributeManager.EaglerAttributeHolder attributeHolder = this.attributeManager.createEaglerHolder();
    private boolean hasStartedLoading = false;
    private IPlatform<PlayerObject> platform;
    private EnumPlatformType platformType;
    private Class<PlayerObject> playerClazz;
    private Set<Class<?>> playerClassSet;
    private ConfigDataRoot config;
    private IEventDispatchAdapter<PlayerObject, ?> eventDispatcher;
    private Set<EaglerPlayerInstance<PlayerObject>> eaglerPlayers;
    private BrandService<PlayerObject> brandRegistry;
    private Map<String, EaglerListener> listeners;
    private Map<SocketAddress, EaglerListener> listenersByAddress;
    private QueryServer queryServer;
    private WebServer webServer;
    private RewindService<PlayerObject> rewindService;
    private PipelineTransformer pipelineTransformer;
    private ExtCapabilityMap extCapabilityMap;
    private SSLCertificateManager certificateManager;
    private IPlatformTask certificateRefreshTask;
    private volatile String serverListConfirmCode;
    private Class<?> componentType;
    private Set<Class<?>> componentTypeSet;
    private ComponentHelper<?> componentHelper;
    private IHTTPClient httpClient;
    private BinaryHTTPClient httpClientAPI;
    private ProfileResolver profileResolver;
    private volatile TexturesProperty eaglerPlayersVanillaSkin;
    private boolean isEaglerPlayerProperyEnabled;
    private SkinService<PlayerObject> skinService;
    private DeferredStartSkinCache skinCacheService;
    private SkinCachePrewarmer skinCachePrewarmer;
    private Connection[] skinCacheJDBCHandle;
    private IVoiceServiceImpl<PlayerObject> voiceService;
    private NotificationService<PlayerObject> notificationService;
    private WebViewService<PlayerObject> webViewService;
    private PauseMenuService<PlayerObject> pauseMenuService;
    private UpdateService updateService;
    private UpdateChecker updateChecker;
    private BackendRPCService<PlayerObject> backendRPCService;
    private ISupervisorServiceImpl<PlayerObject> supervisorService;
    private PlayerRateLimits.RateLimitParams ratelimitParams;

    @Override
    public void load(IPlatform.Init<PlayerObject> init) {
        if (this.hasStartedLoading) {
            throw new IllegalStateException();
        }
        this.hasStartedLoading = true;
        this.eaglerPlayers = Collections.newSetFromMap(new MapMaker().initialCapacity(512).concurrencyLevel(16).makeMap());
        this.platform = init.getPlatform();
        this.playerClazz = this.platform.getPlayerClass();
        this.playerClassSet = Collections.singleton(this.playerClazz);
        switch (this.platform.getType()) {
            case BUNGEE: {
                this.platformType = EnumPlatformType.BUNGEECORD;
                break;
            }
            case BUKKIT: {
                this.platformType = EnumPlatformType.BUKKIT;
                break;
            }
            case VELOCITY: {
                this.platformType = EnumPlatformType.VELOCITY;
                break;
            }
            default: {
                this.platformType = EnumPlatformType.STANDALONE;
            }
        }
        if (this.platformType != EnumPlatformType.BUKKIT) {
            this.logger().info("Loading " + this.getServerBrand() + " " + this.getServerVersion() + "...");
        }
        this.logger().info("(Platform: " + this.platformType.getName() + ")");
        if (this.platformType == EnumPlatformType.BUKKIT) {
            this.logger().warn("Note: Its highly recommended to install EaglerXServer on BungeeCord or Velocity instead, you will have a much better experience");
        }
        this.eventDispatcher = this.platform.eventDispatcher();
        try {
            this.config = EaglerConfigLoader.loadConfig(this.platform);
        }
        catch (IOException e) {
            throw new AbortLoadException("Could not read one or more config files!", e);
        }
        this.logger().info("Server Name: \"" + this.config.getSettings().getServerName() + "\"");
        this.brandRegistry = new BrandService(this);
        this.queryServer = new QueryServer(this);
        this.webServer = new WebServer(this);
        this.rewindService = new RewindService(this);
        this.pipelineTransformer = new PipelineTransformer(this, this.rewindService);
        this.extCapabilityMap = new ExtCapabilityMap();
        this.certificateManager = new SSLCertificateManager(this.logger());
        this.componentType = this.componentHelper().getComponentType();
        this.componentTypeSet = Collections.singleton(this.componentType);
        this.componentHelper = new ComponentHelper(this.componentHelper());
        if (Util.classExists("io.netty.handler.ssl.SslContextBuilder") && Util.classExists("io.netty.handler.codec.http.HttpHeaderNames")) {
            this.httpClient = new HTTPClient(() -> this.bootstrapClient(null), "Mozilla/5.0 " + this.getServerVersionString());
        } else {
            this.logger().warn("Using legacy JDK-based HTTP client because Netty is too outdated");
            this.httpClient = new LegacyInternalHTTPClient(this.platform.getScheduler(), "Mozilla/5.0 " + this.getServerVersionString());
        }
        this.httpClientAPI = new BinaryHTTPClient(this.httpClient);
        this.profileResolver = new ProfileResolver(this, this.httpClient);
        ConfigDataSettings.ConfigDataSkinService skinSvcConf = this.config.getSettings().getSkinService();
        ConfigDataSupervisor supervisorConf = this.config.getSupervisor();
        if (supervisorConf != null && supervisorConf.isEnableSupervisor()) {
            this.supervisorService = new SupervisorService(this);
            this.skinService = new SkinService(this, null, skinSvcConf.getFNAWSkinsPredicate(), skinSvcConf.isDownloadVanillaSkinsToClients());
        } else {
            this.supervisorService = new SupervisorServiceDisabled(this);
            if (skinSvcConf.isDownloadVanillaSkinsToClients()) {
                this.skinCacheService = new DeferredStartSkinCache();
                this.skinService = new SkinService(this, this.skinCacheService, skinSvcConf.getFNAWSkinsPredicate(), true);
            } else {
                this.skinService = new SkinService(this, null, skinSvcConf.getFNAWSkinsPredicate(), false);
            }
        }
        this.isEaglerPlayerProperyEnabled = this.config.getSettings().isEnableIsEaglerPlayerProperty();
        this.eaglerPlayersVanillaSkin = null;
        File vanillaSkinCache = new File("eagler_vanilla_skin_cache.json");
        String vanillaSkin = this.config.getSettings().getEaglerPlayersVanillaSkin();
        if (vanillaSkin != null) {
            SimpleProfileCache.loadProfile(this, vanillaSkinCache, vanillaSkin, 604800000L, res -> {
                if (res != null) {
                    this.logger().info("Loaded vanilla profile: \"" + vanillaSkin + "\"");
                    this.eaglerPlayersVanillaSkin = res;
                }
            });
        } else {
            vanillaSkinCache.delete();
        }
        ConfigDataSettings.ConfigDataVoiceService voiceConfig = this.config.getSettings().getVoiceService();
        if (voiceConfig.isEnableVoiceService()) {
            this.voiceService = voiceConfig.isVoiceBackendRelayMode() ? new VoiceServiceRemote(this) : new VoiceServiceLocal(this, voiceConfig);
            this.voiceService.setICEServers(this.config.getICEServers());
        } else {
            this.voiceService = new VoiceServiceDisabled(this);
        }
        this.notificationService = new NotificationService(this);
        this.webViewService = new WebViewService(this);
        this.webViewService.setTemplateGlobal("server_name", this.getServerName());
        this.webViewService.setTemplateGlobal("plugin_name", this.getServerBrand());
        this.webViewService.setTemplateGlobal("plugin_version", this.getServerVersion());
        this.webViewService.setTemplateGlobal("plugin_authors", "lax1dude");
        this.config.getPauseMenu().getServerInfoButtonEmbedTemplateGlobals().forEach(this.webViewService::setTemplateGlobal);
        this.pauseMenuService = new PauseMenuService(this);
        ConfigDataPauseMenu pauseMenuConf = this.config.getPauseMenu();
        if (pauseMenuConf.isEnableCustomPauseMenu()) {
            try {
                this.pauseMenuService.reloadDefaultPauseMenu(this.platform.getDataFolder(), pauseMenuConf);
            }
            catch (IOException e) {
                this.logger().error("Could not load custom pause menu!", e);
                this.pauseMenuService.setDefaultPauseMenu(this.pauseMenuService.getVanillaPauseMenu());
            }
        }
        if (this.config.getSettings().getUpdateService().isEnableUpdateSystem()) {
            this.updateService = new UpdateService(this);
        }
        this.updateChecker = new UpdateChecker(this, this.config.getSettings().getUpdateChecker());
        if (this.config.getSettings().isEnableBackendRPCAPI() && this.platform.getType().proxy) {
            this.backendRPCService = new BackendRPCService(this);
        }
        this.ratelimitParams = new PlayerRateLimits.RateLimitParams(skinSvcConf.getSkinLookupRatelimit(), skinSvcConf.getCapeLookupRatelimit(), voiceConfig.getVoiceConnectRatelimit(), voiceConfig.getVoiceRequestRatelimit(), voiceConfig.getVoiceICERatelimit(), this.config.getSettings().getBrandLookupRatelimit(), this.config.getSettings().getWebviewDownloadRatelimit(), this.config.getSettings().getWebviewMessageRatelimit(), skinSvcConf.getSkinCacheAntagonistsRatelimit(), supervisorConf != null ? supervisorConf.getSupervisorSkinAntagonistsRatelimit() : 0, supervisorConf != null ? supervisorConf.getSupervisorBrandAntagonistsRatelimit() : 0);
        init.setOnServerEnable(this::enableHandler);
        init.setOnServerDisable(this::disableHandler);
        init.setPipelineInitializer(new EaglerXServerNettyPipelineInitializer(this));
        init.setConnectionInitializer(new EaglerXServerLoginInitializer(this));
        init.setPlayerInitializer(new EaglerXServerPlayerInitializer(this));
        init.setServerJoinListener(new EaglerXServerJoinListener(this));
        init.setCommandRegistry(Arrays.asList(new CommandVersion(this), new CommandBrand(this), new CommandProtocol(this), new CommandDomain(this), new CommandUserAgent(this), new CommandConfirmCode(this)));
        if (this.platform.getType().proxy) {
            this.loadProxying((IPlatform.InitProxying)init);
        } else {
            this.loadNonProxying((IPlatform.InitNonProxying)init);
        }
        this.eventDispatcher.setAPI(this);
        APIFactoryImpl.INSTANCE.initialize(this.playerClazz, this);
    }

    private void loadProxying(IPlatform.InitProxying<PlayerObject> init) {
        ImmutableMap.Builder listenersBuilder = ImmutableMap.builder();
        ImmutableMap.Builder listenersByAddressBuilder = ImmutableMap.builder();
        ImmutableList.Builder listenersImpl = ImmutableList.builder();
        for (ConfigDataListener listener : this.config.getListeners().values()) {
            EaglerListener eagListener;
            try {
                eagListener = new EaglerListener(this, listener);
            }
            catch (SSLException ex) {
                throw new AbortLoadException("TLS configuration is invalid!", ex);
            }
            catch (IOException ex) {
                throw new AbortLoadException("Could not load server icon!", ex);
            }
            listenersBuilder.put((Object)listener.getListenerName(), (Object)eagListener);
            listenersByAddressBuilder.put((Object)listener.getInjectAddress(), (Object)eagListener);
            listenersImpl.add((Object)eagListener);
        }
        this.listeners = listenersBuilder.build();
        this.listenersByAddress = listenersByAddressBuilder.build();
        init.setEaglerListeners((Collection<IEaglerXServerListener>)listenersImpl.build());
        init.setEaglerPlayerChannels(PlayerChannelHelper.getPlayerChannels(this));
        init.setEaglerBackendChannels(BackendChannelHelper.getBackendChannels(this));
    }

    private void loadNonProxying(IPlatform.InitNonProxying<PlayerObject> init) {
        EaglerListener eagListener;
        try {
            eagListener = new EaglerListener(this, init.getListenerAddress(), this.config.getListeners().values().iterator().next());
        }
        catch (SSLException ex) {
            throw new AbortLoadException("TLS configuration is invalid!", ex);
        }
        catch (IOException ex) {
            throw new AbortLoadException("Could not load server icon!", ex);
        }
        this.listeners = ImmutableMap.of("default", eagListener);
        this.listenersByAddress = ImmutableMap.of(init.getListenerAddress(), eagListener);
        init.setEaglerListener(eagListener);
        init.setEaglerPlayerChannels(PlayerChannelHelper.getPlayerChannels(this));
    }

    public ConfigDataRoot getConfig() {
        return this.config;
    }

    public IPlatform<PlayerObject> getPlatform() {
        return this.platform;
    }

    public PipelineTransformer getPipelineTransformer() {
        return this.pipelineTransformer;
    }

    public SSLCertificateManager getCertificateManager() {
        return this.certificateManager;
    }

    private void enableHandler() {
        if (this.platformType != EnumPlatformType.BUKKIT) {
            this.logger().info("Enabling " + this.getServerBrand() + " " + this.getServerVersion() + "...");
        }
        this.webServer.refreshBuiltinPages();
        if (this.certificateManager.hasRefreshableFiles()) {
            long refreshRate = (long)Math.max(this.config.getSettings().getTLSCertRefreshRate(), 1) * 1000L;
            this.certificateRefreshTask = this.platform.getScheduler().executeAsyncRepeatingTask(this.certificateManager::update, refreshRate, refreshRate);
        }
        if (this.skinCacheService != null) {
            SkinCacheDatastore datastore;
            ConfigDataSettings.ConfigDataSkinService skinConf = this.config.getSettings().getSkinService();
            this.logger().info("Connecting to skin cache database \"" + Util.sanitizeJDBCURIForLogs(skinConf.getSkinCacheDBURI()) + "\"...");
            int threadCount = skinConf.getSkinCacheThreadCount();
            if (threadCount <= 0) {
                threadCount = Runtime.getRuntime().availableProcessors();
            }
            int connectionCount = 1;
            if (!skinConf.isSkinCacheSQLiteCompatible() || skinConf.isSkinCacheForceConnectionPool()) {
                connectionCount = threadCount;
            }
            try {
                this.skinCacheJDBCHandle = EaglerDrivers.connectToDatabase(skinConf.getSkinCacheDBURI(), skinConf.getSkinCacheDriverClass(), skinConf.getSkinCacheDriverPath(), new Properties(), this.platform.getDataFolder(), this.logger(), connectionCount);
                datastore = new SkinCacheDatastore(this.skinCacheJDBCHandle, threadCount, skinConf.getSkinCacheDiskKeepObjectsDays(), skinConf.getSkinCacheDiskMaxObjects(), Math.min(skinConf.getSkinCacheCompressionLevel(), 9), skinConf.isSkinCacheSQLiteCompatible(), this.logger());
                this.logger().info("Connected to skin cache database successfully!");
            }
            catch (SQLException e) {
                this.logger().error("Caught an exception while initializing the skin cache database", e);
                if (this.skinCacheJDBCHandle != null) {
                    for (int i = 0; i < this.skinCacheJDBCHandle.length; ++i) {
                        try {
                            this.skinCacheJDBCHandle[i].close();
                            continue;
                        }
                        catch (SQLException sQLException) {
                            // empty catch block
                        }
                    }
                    this.skinCacheJDBCHandle = null;
                }
                return;
            }
            this.skinCacheService.setDelegate(new SkinCacheService(new SkinCacheDownloader(this.httpClient, skinConf.getValidSkinDownloadURLs()), datastore, skinConf.getSkinCacheMemoryKeepSeconds(), skinConf.getSkinCacheMemoryMaxObjects(), this.logger()));
            if (EaglerXPaperConfig.enableSkinPrewarm) {
                try {
                    this.prewarmSkinCache(skinConf);
                }
                catch (Exception e) {
                    this.logger().warn("Could not start skin cache pre-warming: " + e.getMessage());
                }
            }
        }
        this.skinService.handleEnabled();
        if (this.updateService != null) {
            this.updateService.start();
        }
        this.updateChecker.handleEnable();
        this.supervisorService.handleEnable();
        this.platform.getScheduler().executeDelayed(this.pipelineTransformer::nagAgain, 10000L);
    }

    private void prewarmSkinCache(ConfigDataSettings.ConfigDataSkinService skinConf) {
        File usercache = new File("usercache.json");
        int maxPlayers = EaglerXPaperConfig.prewarmMaxPlayers;
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        this.skinCachePrewarmer = new SkinCachePrewarmer(this.skinCacheService, this.logger(), usercache, maxPlayers, threads);
        this.skinCachePrewarmer.startAsync();
    }

    private void disableHandler() {
        if (this.platformType != EnumPlatformType.BUKKIT) {
            this.logger().info("Disabling " + this.getServerBrand() + " " + this.getServerVersion() + "...");
        }
        this.webServer.releaseBuiltinPages();
        if (this.certificateRefreshTask != null) {
            this.certificateRefreshTask.cancel();
            this.certificateRefreshTask = null;
        }
        this.skinService.handleDisabled();
        if (this.skinCachePrewarmer != null) {
            this.skinCachePrewarmer.shutdown();
            this.skinCachePrewarmer = null;
        }
        if (this.skinCacheService != null) {
            ISkinCacheService delegate = this.skinCacheService.getDelegate();
            if (delegate instanceof SkinCacheService) {
                try {
                    ((SkinCacheService)delegate).dispose();
                }
                catch (Throwable t) {
                    this.logger().error("Failed to dispose skin cache service", t);
                }
            }
            if (this.skinCacheJDBCHandle != null) {
                this.logger().info("Disconnecting from skin cache database \"" + Util.sanitizeJDBCURIForLogs(this.config.getSettings().getSkinService().getSkinCacheDBURI()) + "\"...");
                boolean errored = false;
                for (int i = 0; i < this.skinCacheJDBCHandle.length; ++i) {
                    try {
                        this.skinCacheJDBCHandle[i].close();
                        continue;
                    }
                    catch (SQLException ee) {
                        this.logger().error("Failed to disconnect from skin cache database!", ee);
                        errored = true;
                    }
                }
                this.skinCacheJDBCHandle = null;
                if (!errored) {
                    this.logger().info("Disconnected from skin cache database successfully!");
                }
            }
            this.skinCacheService.setDelegate(null);
        }
        if (this.updateService != null) {
            this.updateService.stop();
        }
        this.updateChecker.handleDisable();
        this.supervisorService.handleDisable();
    }

    public void registerPlayer(BasePlayerInstance<PlayerObject> playerInstance) {
        if (this.backendRPCService != null) {
            playerInstance.backendRPCManager = this.backendRPCService.createVanillaPlayerRPCManager(playerInstance);
        }
        playerInstance.skinManager = this.skinService.createVanillaSkinManager(playerInstance);
    }

    public void registerEaglerPlayer(EaglerPlayerInstance<PlayerObject> playerInstance, NettyPipelineData.ProfileDataHolder profileData, Runnable onComplete) {
        if (!this.eaglerPlayers.add(playerInstance)) {
            throw new RegistrationStateException();
        }
        playerInstance.messageController = MessageControllerFactory.initializePlayer(playerInstance);
        if (this.updateService != null) {
            playerInstance.updateCertificate = this.updateService.createUpdateCertificate(playerInstance, profileData.updateCertInit);
        }
        playerInstance.voiceManager = this.voiceService.createVoiceManager(playerInstance);
        playerInstance.notifManager = this.notificationService.createPlayerManager(playerInstance);
        playerInstance.webViewManager = this.webViewService.createWebViewManager(playerInstance);
        playerInstance.pauseMenuManager = this.pauseMenuService.createPauseMenuManager(playerInstance);
        if (this.backendRPCService != null) {
            playerInstance.backendRPCManager = this.backendRPCService.createEaglerPlayerRPCManager(playerInstance);
        }
        int ver = playerInstance.getEaglerProtocol().ver;
        if (this.config.getSettings().isEnableIsEaglerPlayerProperty()) {
            if (ver >= 5) {
                playerInstance.sendEaglerMessage(new SPacketClientStateFlagV5EAG(ClientStateFlagUUIDs.EAGLER_PLAYER_FLAG_PRESENT.getMostSignificantBits(), ClientStateFlagUUIDs.EAGLER_PLAYER_FLAG_PRESENT.getLeastSignificantBits(), this.supervisorService.isSupervisorEnabled() ? 3 : 1));
            } else if (ver >= 4 && this.supervisorService.isSupervisorEnabled()) {
                playerInstance.sendEaglerMessage(new SPacketOtherPlayerClientUUIDV4EAG(-1, ClientStateFlagUUIDs.LEGACY_EAGLER_PLAYER_FLAG_PRESENT.getMostSignificantBits(), ClientStateFlagUUIDs.LEGACY_EAGLER_PLAYER_FLAG_PRESENT.getLeastSignificantBits()));
            }
        }
        if (!this.skinService.isSkinDownloadEnabled() && ver >= 5) {
            playerInstance.sendEaglerMessage(new SPacketClientStateFlagV5EAG(ClientStateFlagUUIDs.DISABLE_SKIN_URL_LOOKUP.getMostSignificantBits(), ClientStateFlagUUIDs.DISABLE_SKIN_URL_LOOKUP.getLeastSignificantBits(), 1));
        }
        if (ver >= 5) {
            playerInstance.sendEaglerMessage(new SPacketClientStateFlagV5EAG(ClientStateFlagUUIDs.SET_MAX_MULTI_PACKET.getMostSignificantBits(), ClientStateFlagUUIDs.SET_MAX_MULTI_PACKET.getLeastSignificantBits(), this.config.getSettings().getProtocolV4DefragMaxPackets()));
        }
        this.skinService.createEaglerSkinManager(playerInstance, profileData, mgr -> {
            int distance;
            playerInstance.skinManager = mgr;
            try {
                if (playerInstance.isEaglerXRewindPlayer()) {
                    ((IEaglerXRewindProtocol<PlayerObject, Object>)playerInstance.getRewindProtocol()).handleCreatePlayer(playerInstance.getRewindAttachment(), playerInstance);
                }
            }
            catch (Exception ex) {
                this.logger().error("Uncaught exception initializing rewind player", ex);
                onComplete.run();
                return;
            }
            IPlatformPlayer platformPlayer = playerInstance.getPlatformPlayer();
            if (platformPlayer.isSetViewDistanceSupportedPaper() && (distance = this.config.getSettings().getEaglerPlayersViewDistance()) > 0) {
                platformPlayer.setViewDistancePaper(Math.max(distance, 3));
            }
            this.updateChecker.sendUpdateMessage(platformPlayer);
            onComplete.run();
        });
    }

    public void unregisterPlayer(BasePlayerInstance<PlayerObject> playerInstance) {
    }

    public void unregisterEaglerPlayer(EaglerPlayerInstance<PlayerObject> playerInstance) {
        if (!this.eaglerPlayers.remove(playerInstance)) {
            throw new RegistrationStateException();
        }
        if (this.updateService != null) {
            this.updateService.removeUpdateCertificate(playerInstance);
            playerInstance.updateCertificate = null;
        }
        if (playerInstance.voiceManager != null) {
            playerInstance.voiceManager.destroyVoiceManager();
        }
        if (playerInstance.isEaglerXRewindPlayer()) {
            ((IEaglerXRewindProtocol<PlayerObject, Object>)playerInstance.getRewindProtocol()).handleDestroyPlayer(playerInstance.getRewindAttachment());
        }
        if (playerInstance.messageController != null) {
            try {
                playerInstance.messageController.dispose();
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            playerInstance.messageController = null;
        }
    }

    void handleServerPreConnect(BasePlayerInstance<PlayerObject> player) {
        if (player.backendRPCManager != null) {
            player.backendRPCManager.handleServerPreConnect();
        }
        if (player.isEaglerPlayer()) {
            IEaglerPlayer eaglerPlayer = player.asEaglerPlayer();
            if (((EaglerPlayerInstance)eaglerPlayer).voiceManager != null) {
                ((EaglerPlayerInstance)eaglerPlayer).voiceManager.handleServerPreConnect();
            }
        }
    }

    void handleServerPostConnect(BasePlayerInstance<PlayerObject> player, IPlatformServer<PlayerObject> server) {
        String serverName = server.getServerConfName();
        if (player.backendRPCManager != null) {
            player.backendRPCManager.handleServerPostConnect();
        }
        if (player.isEaglerPlayer()) {
            IEaglerPlayer eaglerPlayer = player.asEaglerPlayer();
            ((SkinManagerEagler)((EaglerPlayerInstance)eaglerPlayer).getSkinManager()).handleServerPostConnect(serverName);
            if (((EaglerPlayerInstance)eaglerPlayer).voiceManager != null) {
                ((EaglerPlayerInstance)eaglerPlayer).voiceManager.handleServerPostConnect(serverName);
            }
        }
    }

    @Override
    public Set<Class<?>> getPlayerTypes() {
        return this.playerClassSet;
    }

    @Override
    public IAttributeManager getGlobalAttributeManager() {
        return this.attributeManager;
    }

    public EaglerAttributeManager getEaglerAttribManager() {
        return this.attributeManager;
    }

    @Override
    public <T> IEaglerXServerAPI<T> getAPI(Class<T> playerClass) {
        if (!playerClass.isAssignableFrom(this.playerClazz)) {
            throw new ClassCastException("Class " + this.playerClazz.getName() + " cannot be cast to " + playerClass.getName());
        }
        return (IEaglerXServerAPI<T>)this;
    }

    @Override
    public IEaglerXServerAPI<?> getDefaultAPI() {
        return this;
    }

    @Override
    public <T> T get(IAttributeKey<T> key) {
        return this.attributeHolder.get(key);
    }

    @Override
    public <T> void set(IAttributeKey<T> key, T value) {
        this.attributeHolder.set(key, value);
    }

    @Override
    public IEaglerAPIFactory getFactory() {
        return this;
    }

    @Override
    public EnumPlatformType getPlatformType() {
        return this.platformType;
    }

    @Override
    public Class<PlayerObject> getPlayerClass() {
        return this.playerClazz;
    }

    @Override
    public String getServerBrand() {
        return "EaglercraftXServer";
    }

    @Override
    public String getServerVersion() {
        return "1.1.1 Stable 1";
    }

    public String getServerVersionString() {
        return "EaglercraftXServer/1.1.1 Stable 1";
    }

    @Override
    public String getServerName() {
        return this.config.getSettings().getServerName();
    }

    @Override
    public UUID getServerUUID() {
        return this.config.getSettings().getServerUUID();
    }

    public String getServerUUIDString() {
        return this.config.getSettings().getServerUUIDString();
    }

    @Override
    public boolean isAuthenticationEventsEnabled() {
        return this.config.getSettings().isEnableAuthenticationEvents();
    }

    @Override
    public boolean isEaglerHandshakeSupported(int vers) {
        return this.config.getSettings().getProtocols().isEaglerHandshakeSupported(vers);
    }

    @Override
    public boolean isEaglerProtocolSupported(GamePluginMessageProtocol vers) {
        return this.config.getSettings().getProtocols().isEaglerProtocolSupported(vers.ver);
    }

    @Override
    public boolean isMinecraftProtocolSupported(int vers) {
        return this.config.getSettings().getProtocols().isMinecraftProtocolSupported(vers);
    }

    @Override
    public boolean isMinecraftProtocolSupportedV5(int vers) {
        return this.config.getSettings().getProtocols().isMinecraftProtocolSupportedV5(vers);
    }

    @Override
    public BasePlayerInstance<PlayerObject> getPlayer(PlayerObject player) {
        if (player == null) {
            throw new NullPointerException("player");
        }
        IPlatformPlayer<PlayerObject> platformPlayer = this.platform.getPlayer(player);
        return platformPlayer != null ? (BasePlayerInstance)platformPlayer.getPlayerAttachment() : null;
    }

    @Override
    public BasePlayerInstance<PlayerObject> getPlayerByName(String playerName) {
        if (playerName == null) {
            throw new NullPointerException("playerName");
        }
        IPlatformPlayer<PlayerObject> platformPlayer = this.platform.getPlayer(playerName);
        return platformPlayer != null ? (BasePlayerInstance)platformPlayer.getPlayerAttachment() : null;
    }

    @Override
    public BasePlayerInstance<PlayerObject> getPlayerByUUID(UUID playerUUID) {
        if (playerUUID == null) {
            throw new NullPointerException("playerUUID");
        }
        IPlatformPlayer<PlayerObject> platformPlayer = this.platform.getPlayer(playerUUID);
        return platformPlayer != null ? (BasePlayerInstance)platformPlayer.getPlayerAttachment() : null;
    }

    @Override
    public EaglerPlayerInstance<PlayerObject> getEaglerPlayer(PlayerObject player) {
        if (player == null) {
            throw new NullPointerException("player");
        }
        IPlatformPlayer<PlayerObject> platformPlayer = this.platform.getPlayer(player);
        if (platformPlayer != null) {
            return ((BasePlayerInstance)platformPlayer.getPlayerAttachment()).asEaglerPlayer();
        }
        return null;
    }

    @Override
    public EaglerPlayerInstance<PlayerObject> getEaglerPlayerByName(String playerName) {
        if (playerName == null) {
            throw new NullPointerException("playerName");
        }
        IPlatformPlayer<PlayerObject> platformPlayer = this.platform.getPlayer(playerName);
        if (platformPlayer != null) {
            return ((BasePlayerInstance)platformPlayer.getPlayerAttachment()).asEaglerPlayer();
        }
        return null;
    }

    @Override
    public EaglerPlayerInstance<PlayerObject> getEaglerPlayerByUUID(UUID playerUUID) {
        if (playerUUID == null) {
            throw new NullPointerException("playerUUID");
        }
        IPlatformPlayer<PlayerObject> platformPlayer = this.platform.getPlayer(playerUUID);
        if (platformPlayer != null) {
            return ((BasePlayerInstance)platformPlayer.getPlayerAttachment()).asEaglerPlayer();
        }
        return null;
    }

    @Override
    public boolean isPlayer(PlayerObject player) {
        if (player == null) {
            throw new NullPointerException("player");
        }
        return this.platform.getPlayer(player) != null;
    }

    @Override
    public boolean isPlayerByName(String playerName) {
        if (playerName == null) {
            throw new NullPointerException("playerName");
        }
        return this.platform.getPlayer(playerName) != null;
    }

    @Override
    public boolean isPlayerByUUID(UUID playerUUID) {
        if (playerUUID == null) {
            throw new NullPointerException("playerUUID");
        }
        return this.platform.getPlayer(playerUUID) != null;
    }

    @Override
    public boolean isEaglerPlayer(PlayerObject player) {
        if (player == null) {
            throw new NullPointerException("player");
        }
        IPlatformPlayer<PlayerObject> platformPlayer = this.platform.getPlayer(player);
        return platformPlayer != null && ((BasePlayerInstance)platformPlayer.getPlayerAttachment()).isEaglerPlayer();
    }

    @Override
    public boolean isEaglerPlayerByName(String playerName) {
        if (playerName == null) {
            throw new NullPointerException("playerName");
        }
        IPlatformPlayer<PlayerObject> platformPlayer = this.platform.getPlayer(playerName);
        return platformPlayer != null && ((BasePlayerInstance)platformPlayer.getPlayerAttachment()).isEaglerPlayer();
    }

    @Override
    public boolean isEaglerPlayerByUUID(UUID playerUUID) {
        if (playerUUID == null) {
            throw new NullPointerException("playerUUID");
        }
        IPlatformPlayer<PlayerObject> platformPlayer = this.platform.getPlayer(playerUUID);
        return platformPlayer != null && ((BasePlayerInstance)platformPlayer.getPlayerAttachment()).isEaglerPlayer();
    }

    @Override
    public void forEachPlayer(Consumer<IBasePlayer<PlayerObject>> callback) {
        if (callback == null) {
            throw new NullPointerException("callback");
        }
        this.platform.forEachPlayer((IPlatformPlayer<PlayerObject> player) -> callback.accept((IBasePlayer)player.getPlayerAttachment()));
    }

    @Override
    public void forEachEaglerPlayer(Consumer<IEaglerPlayer<PlayerObject>> callback) {
        if (callback == null) {
            throw new NullPointerException("callback");
        }
        this.eaglerPlayers.forEach(callback);
    }

    public void forEachEaglerPlayerInternal(Consumer<EaglerPlayerInstance<PlayerObject>> callback) {
        this.eaglerPlayers.forEach(callback);
    }

    @Override
    public Collection<IBasePlayer<PlayerObject>> getAllPlayers() {
        return Collections2.transform(this.platform.getAllPlayers(), IPlatformPlayer::getPlayerAttachment);
    }

    public Collection<BasePlayerInstance<PlayerObject>> getAllPlayersInternal() {
        return Collections2.transform(this.platform.getAllPlayers(), IPlatformPlayer::getPlayerAttachment);
    }

    @Override
    public Collection<IEaglerPlayer<PlayerObject>> getAllEaglerPlayers() {
        return ImmutableList.copyOf(this.eaglerPlayers);
    }

    public Collection<EaglerPlayerInstance<PlayerObject>> getAllEaglerPlayersInternal() {
        return ImmutableList.copyOf(this.eaglerPlayers);
    }

    @Override
    public int getEaglerPlayerCount() {
        return this.eaglerPlayers.size();
    }

    @Override
    public Collection<IUpdateCertificate> getUpdateCertificates() {
        if (this.updateService != null) {
            return this.updateService.dumpAllCerts();
        }
        return Collections.emptyList();
    }

    @Override
    public IUpdateCertificate createUpdateCertificate(byte[] data, int offset, int length) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        byte[] copy = new byte[length];
        System.arraycopy(data, offset, copy, 0, length);
        return UpdateCertificate.intern(copy);
    }

    @Override
    public void addUpdateCertificate(IUpdateCertificate cert) {
        if (!(cert instanceof IUpdateCertificateImpl)) {
            throw new UnsupportedOperationException("Unknown certificate: " + cert);
        }
        if (this.updateService != null) {
            this.forEachEaglerPlayer(player -> player.offerUpdateCertificate(cert));
        }
    }

    public UpdateService getUpdateService() {
        return this.updateService;
    }

    public BackendRPCService<PlayerObject> getBackendRPCService() {
        return this.backendRPCService;
    }

    @Override
    public Collection<IEaglerListenerInfo> getAllEaglerListeners() {
        return ImmutableList.copyOf(this.listeners.values());
    }

    @Override
    public IEaglerListenerInfo getListenerByName(String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        return this.listeners.get(name);
    }

    @Override
    public IEaglerListenerInfo getListenerByAddress(SocketAddress address) {
        if (address == null) {
            throw new NullPointerException("name");
        }
        return this.listenersByAddress.get(address);
    }

    @Override
    public ProfileResolver getProfileResolver() {
        return this.profileResolver;
    }

    @Override
    public TexturesProperty getEaglerPlayersVanillaSkin() {
        return this.eaglerPlayersVanillaSkin;
    }

    @Override
    public void setEaglerPlayersVanillaSkin(TexturesProperty property) {
        this.eaglerPlayersVanillaSkin = property;
    }

    @Override
    public boolean isEaglerPlayerPropertyEnabled() {
        return this.isEaglerPlayerProperyEnabled;
    }

    @Override
    public void setEaglerPlayerProperyEnabled(boolean enable) {
        this.isEaglerPlayerProperyEnabled = enable;
    }

    @Override
    public void registerExtendedCapability(Object plugin, ExtendedCapabilitySpec capability) {
        if (plugin == null) {
            throw new NullPointerException("plugin");
        }
        if (capability == null) {
            throw new NullPointerException("capability");
        }
        this.extCapabilityMap.registerCapability(plugin, capability);
    }

    @Override
    public void unregisterExtendedCapability(Object plugin, ExtendedCapabilitySpec capability) {
        if (plugin == null) {
            throw new NullPointerException("plugin");
        }
        if (capability == null) {
            throw new NullPointerException("capability");
        }
        this.extCapabilityMap.unregisterCapability(plugin, capability);
    }

    @Override
    public boolean isExtendedCapabilityRegistered(UUID capabilityUUID, int version) {
        if (capabilityUUID == null) {
            throw new NullPointerException("capabilityUUID");
        }
        return this.extCapabilityMap.isCapabilityRegistered(capabilityUUID, version);
    }

    public ExtCapabilityMap getExtCapabilityMap() {
        return this.extCapabilityMap;
    }

    @Override
    public SkinService<PlayerObject> getSkinService() {
        return this.skinService;
    }

    @Override
    public IVoiceServiceImpl<PlayerObject> getVoiceService() {
        return this.voiceService;
    }

    @Override
    public BrandService<PlayerObject> getBrandService() {
        return this.brandRegistry;
    }

    @Override
    public NotificationService<PlayerObject> getNotificationService() {
        return this.notificationService;
    }

    @Override
    public PauseMenuService<PlayerObject> getPauseMenuService() {
        return this.pauseMenuService;
    }

    @Override
    public WebViewService<PlayerObject> getWebViewService() {
        return this.webViewService;
    }

    @Override
    public ISupervisorServiceImpl<PlayerObject> getSupervisorService() {
        return this.supervisorService;
    }

    @Override
    public RewindService<PlayerObject> getEaglerXRewindService() {
        return this.rewindService;
    }

    @Override
    public IPacketImageLoader getPacketImageLoader() {
        return PacketImageLoader.INSTANCE;
    }

    @Override
    public QueryServer getQueryServer() {
        return this.queryServer;
    }

    @Override
    public IServerIconLoader getServerIconLoader() {
        return ServerIconLoader.INSTANCE;
    }

    @Override
    public WebServer getWebServer() {
        return this.webServer;
    }

    @Override
    public IScheduler getScheduler() {
        return this.platform.getScheduler();
    }

    @Override
    public Set<Class<?>> getComponentTypes() {
        return this.componentTypeSet;
    }

    @Override
    public <ComponentObject> IComponentSerializer<ComponentObject> getComponentSerializer(Class<ComponentObject> componentType) {
        if (componentType != this.componentType) {
            throw new ClassCastException("Component class " + componentType.getName() + " is not supported on this platform!");
        }
        return (IComponentSerializer<ComponentObject>)this.componentHelper;
    }

    @Override
    public IComponentHelper getComponentHelper() {
        return this.componentHelper;
    }

    @Override
    public INBTHelper getNBTHelper() {
        return NBTHelper.INSTANCE;
    }

    public IHTTPClient getInternalHTTPClient() {
        return this.httpClient;
    }

    @Override
    public IBinaryHTTPClient getBinaryHTTPClient() {
        return this.httpClientAPI;
    }

    @Override
    public UUID intern(UUID uuid) {
        if (uuid == null) {
            throw new NullPointerException("uuid");
        }
        return uuidInterner.intern(uuid);
    }

    @Override
    public IAttributeManager getAttributeManager() {
        return this.attributeManager;
    }

    @Override
    public HPPC getHPPC() {
        return HPPCFactory.INSTANCE;
    }

    @Override
    public boolean isNettyPlatform() {
        return true;
    }

    @Override
    public IEaglerXServerAPI.NettyUnsafe netty() {
        return this;
    }

    @Override
    public Bootstrap bootstrapClient(SocketAddress remoteAddress) {
        Bootstrap bootstrap = (Bootstrap)new Bootstrap().group(this.getWorkerEventLoopGroup());
        if (remoteAddress != null) {
            bootstrap.remoteAddress(remoteAddress);
        }
        return this.setChannelFactory(bootstrap, remoteAddress);
    }

    @Override
    public ServerBootstrap bootstrapServer(SocketAddress localAddress) {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        EventLoopGroup bossGroup = this.getBossEventLoopGroup();
        if (bossGroup != null) {
            serverBootstrap.group(bossGroup, this.getWorkerEventLoopGroup());
        } else {
            serverBootstrap.group(this.getWorkerEventLoopGroup());
        }
        if (localAddress != null) {
            serverBootstrap.localAddress(localAddress);
        }
        return this.setServerChannelFactory(serverBootstrap, localAddress);
    }

    @Override
    public Bootstrap setChannelFactory(Bootstrap boostrap, SocketAddress address) {
        return this.platform.setChannelFactory(boostrap, address);
    }

    @Override
    public ServerBootstrap setServerChannelFactory(ServerBootstrap boostrap, SocketAddress address) {
        return this.platform.setServerChannelFactory(boostrap, address);
    }

    @Override
    public EventLoopGroup getBossEventLoopGroup() {
        return this.platform.getBossEventLoopGroup();
    }

    @Override
    public EventLoopGroup getWorkerEventLoopGroup() {
        return this.platform.getWorkerEventLoopGroup();
    }

    public IPlatformLogger logger() {
        return this.platform.logger();
    }

    public IEventDispatchAdapter<PlayerObject, ?> eventDispatcher() {
        return this.platform.eventDispatcher();
    }

    public IPlatformComponentHelper componentHelper() {
        return this.platform.getComponentHelper();
    }

    public IPlatformComponentBuilder componentBuilder() {
        return this.platform.getComponentHelper().builder();
    }

    public PlayerRateLimits.RateLimitParams rateLimitParams() {
        return this.ratelimitParams;
    }

    public void setServerListConfirmCode(String code) {
        this.serverListConfirmCode = code;
    }

    public boolean testServerListConfirmCode(String code) {
        if (this.serverListConfirmCode != null && code.equals(this.serverListConfirmCode)) {
            this.serverListConfirmCode = null;
            return true;
        }
        return false;
    }

    public static class RegistrationStateException
    extends IllegalStateException {
    }
}

