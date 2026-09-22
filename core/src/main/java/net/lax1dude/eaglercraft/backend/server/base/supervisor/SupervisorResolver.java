/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.cache.CacheBuilder
 *  com.google.common.cache.CacheLoader
 *  com.google.common.cache.LoadingCache
 *  com.google.common.collect.MapMaker
 */
package net.lax1dude.eaglercraft.backend.server.base.supervisor;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.MapMaker;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformLogger;
import net.lax1dude.eaglercraft.backend.server.api.brand.IBrandRegistration;
import net.lax1dude.eaglercraft.backend.server.api.collect.IntProcedure;
import net.lax1dude.eaglercraft.backend.server.api.skins.EnumSkinModel;
import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerCape;
import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerSkin;
import net.lax1dude.eaglercraft.backend.server.base.BrandService;
import net.lax1dude.eaglercraft.backend.server.base.config.ConfigDataSettings;
import net.lax1dude.eaglercraft.backend.server.base.skins.type.MissingCape;
import net.lax1dude.eaglercraft.backend.server.base.skins.type.MissingSkin;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.ForeignCape;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.ForeignSkin;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.ISupervisorExpiring;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.ISupervisorResolverImpl;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.SupervisorConnection;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.SupervisorPlayer;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.SupervisorService;

public class SupervisorResolver
implements ISupervisorResolverImpl {
    public static final long FOREIGN_LOOKUP_TIMEOUT = 15000000000L;
    private final SupervisorService<?> service;
    private final LoadingCache<String, ForeignSkin> foreignSkinCache;
    private final LoadingCache<String, ForeignCape> foreignCapeCache;
    private final ConcurrentMap<UUID, PendingSkinLookup> pendingSkinLookups;
    private final ConcurrentMap<UUID, PendingCapeLookup> pendingCapeLookups;
    private List<IDeferredLoad> deferred = new LinkedList<IDeferredLoad>();

    SupervisorResolver(SupervisorService<?> service) {
        this.service = service;
        ConfigDataSettings.ConfigDataSkinService conf = service.getEaglerXServer().getConfig().getSettings().getSkinService();
        this.foreignSkinCache = CacheBuilder.newBuilder().expireAfterAccess((long)conf.getSkinCacheMemoryKeepSeconds(), TimeUnit.SECONDS).initialCapacity(Math.min(1024, conf.getSkinCacheMemoryMaxObjects())).maximumSize((long)conf.getSkinCacheMemoryMaxObjects()).concurrencyLevel(16).build((CacheLoader)new CacheLoader<String, ForeignSkin>(){

            public ForeignSkin load(String key) throws Exception {
                return new ForeignSkin(SupervisorResolver.this, key);
            }
        });
        this.foreignCapeCache = CacheBuilder.newBuilder().expireAfterAccess((long)conf.getSkinCacheMemoryKeepSeconds(), TimeUnit.SECONDS).initialCapacity(Math.min(1024, conf.getSkinCacheMemoryMaxObjects())).maximumSize((long)conf.getSkinCacheMemoryMaxObjects()).concurrencyLevel(16).build((CacheLoader)new CacheLoader<String, ForeignCape>(){

            public ForeignCape load(String key) throws Exception {
                return new ForeignCape(SupervisorResolver.this, key);
            }
        });
        this.pendingSkinLookups = new MapMaker().initialCapacity(256).concurrencyLevel(16).makeMap();
        this.pendingCapeLookups = new MapMaker().initialCapacity(256).concurrencyLevel(16).makeMap();
    }

    SupervisorConnection getConnection() {
        return this.service.getConnection();
    }

    IPlatformLogger logger() {
        return this.service.logger();
    }

    ForeignSkin getForeignSkin(String url) {
        try {
            return this.foreignSkinCache.get(url);
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException)cause;
            }
            throw new RuntimeException(cause);
        }
    }

    ForeignCape getForeignCape(String url) {
        try {
            return this.foreignCapeCache.get(url);
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException)cause;
            }
            throw new RuntimeException(cause);
        }
    }

    @Override
    public boolean isPlayerKnown(UUID playerUUID) {
        if (playerUUID == null) {
            throw new NullPointerException("playerUUID");
        }
        SupervisorConnection conn = this.service.getConnection();
        if (conn != null) {
            return conn.remotePlayers.containsKey(playerUUID);
        }
        return false;
    }

    @Override
    public int getCachedNodeId(UUID playerUUID) {
        SupervisorPlayer player;
        if (playerUUID == null) {
            throw new NullPointerException("playerUUID");
        }
        SupervisorConnection conn = this.service.getConnection();
        if (conn != null && (player = (SupervisorPlayer)conn.remotePlayers.get(playerUUID)) != null) {
            return player.getNodeId();
        }
        return -1;
    }

    @Override
    public void resolvePlayerNodeId(UUID playerUUID, IntProcedure callback) {
        if (playerUUID == null) {
            throw new NullPointerException("playerUUID");
        }
        if (callback == null) {
            throw new NullPointerException("callback");
        }
        SupervisorConnection conn = this.service.getConnection();
        if (conn != null) {
            SupervisorPlayer player = conn.loadPlayer(playerUUID);
            int node = player.getNodeId();
            if (node != -1) {
                callback.apply(node);
            } else {
                player.loadBrandUUID(null, trash -> {
                    if (trash != null) {
                        callback.apply(player.getNodeId());
                    } else {
                        callback.apply(-1);
                    }
                });
            }
        } else {
            callback.apply(-1);
        }
    }

    @Override
    public void resolvePlayerBrand(UUID playerUUID, Consumer<UUID> callback) {
        if (playerUUID == null) {
            throw new NullPointerException("playerUUID");
        }
        if (callback == null) {
            throw new NullPointerException("callback");
        }
        SupervisorConnection conn = this.service.getConnection();
        if (conn != null) {
            conn.loadPlayer(playerUUID).loadBrandUUID(null, callback);
        } else {
            callback.accept(null);
        }
    }

    @Override
    public void resolvePlayerRegisteredBrand(UUID playerUUID, BiConsumer<UUID, IBrandRegistration> callback) {
        if (playerUUID == null) {
            throw new NullPointerException("playerUUID");
        }
        if (callback == null) {
            throw new NullPointerException("callback");
        }
        SupervisorConnection conn = this.service.getConnection();
        if (conn != null) {
            conn.loadPlayer(playerUUID).loadBrandUUID(null, uuid -> {
                if (uuid != null) {
                    callback.accept((UUID)uuid, ((BrandService)this.service.getEaglerXServer().getBrandService()).lookupRegisteredBrand((UUID)uuid));
                } else {
                    callback.accept(null, null);
                }
            });
        } else {
            callback.accept(null, null);
        }
    }

    @Override
    public boolean isSkinDownloadEnabled() {
        return true;
    }

    @Override
    public IEaglerPlayerSkin getSkinNotFound(UUID playerUUID) {
        return MissingSkin.forPlayerUUID(playerUUID);
    }

    @Override
    public IEaglerPlayerCape getCapeNotFound() {
        return MissingCape.MISSING_CAPE;
    }

    @Override
    public void resolvePlayerSkin(UUID playerUUID, Consumer<IEaglerPlayerSkin> callback) {
        if (playerUUID == null) {
            throw new NullPointerException("playerUUID");
        }
        if (callback == null) {
            throw new NullPointerException("callback");
        }
        SupervisorConnection conn = this.service.getConnection();
        if (conn != null) {
            conn.loadPlayer(playerUUID).loadSkinData(null, callback);
        } else {
            callback.accept(MissingSkin.UNAVAILABLE_SKIN);
        }
    }

    @Override
    public void resolvePlayerCape(UUID playerUUID, Consumer<IEaglerPlayerCape> callback) {
        if (playerUUID == null) {
            throw new NullPointerException("playerUUID");
        }
        if (callback == null) {
            throw new NullPointerException("callback");
        }
        SupervisorConnection conn = this.service.getConnection();
        if (conn != null) {
            conn.loadPlayer(playerUUID).loadCapeData(null, callback);
        } else {
            callback.accept(MissingCape.UNAVAILABLE_CAPE);
        }
    }

    @Override
    public void loadCacheSkinFromURL(String skinURL, EnumSkinModel modelId, Consumer<IEaglerPlayerSkin> callback) {
        this.loadCacheSkinFromURL(skinURL, modelId.getId(), callback);
    }

    @Override
    public void loadCacheSkinFromURL(String skinURL, int modelIdRaw, Consumer<IEaglerPlayerSkin> callback) {
        if (skinURL == null) {
            throw new NullPointerException("skinURL");
        }
        if (callback == null) {
            throw new NullPointerException("callback");
        }
        this.getForeignSkin(skinURL).load(modelIdRaw, null, callback);
    }

    @Override
    public void loadCacheCapeFromURL(String capeURL, Consumer<IEaglerPlayerCape> callback) {
        if (capeURL == null) {
            throw new NullPointerException("capeURL");
        }
        if (callback == null) {
            throw new NullPointerException("callback");
        }
        this.getForeignCape(capeURL).load(null, callback);
    }

    @Override
    public void resolvePlayerSkinKeyed(UUID requester, UUID playerUUID, Consumer<IEaglerPlayerSkin> callback) {
        SupervisorConnection conn = this.service.getConnection();
        if (conn != null) {
            conn.loadPlayer(playerUUID).loadSkinData(requester, callback);
        } else {
            callback.accept(MissingSkin.UNAVAILABLE_SKIN);
        }
    }

    @Override
    public void resolvePlayerCapeKeyed(UUID requester, UUID playerUUID, Consumer<IEaglerPlayerCape> callback) {
        SupervisorConnection conn = this.service.getConnection();
        if (conn != null) {
            conn.loadPlayer(playerUUID).loadCapeData(requester, callback);
        } else {
            callback.accept(MissingCape.UNAVAILABLE_CAPE);
        }
    }

    @Override
    public void resolvePlayerBrandKeyed(UUID requester, UUID playerUUID, Consumer<UUID> callback) {
        SupervisorConnection conn = this.service.getConnection();
        if (conn != null) {
            conn.loadPlayer(playerUUID).loadBrandUUID(requester, callback);
        } else {
            callback.accept(ISupervisorResolverImpl.UNAVAILABLE);
        }
    }

    @Override
    public void resolveForeignSkinKeyed(UUID requester, int modelId, String skinURL, Consumer<IEaglerPlayerSkin> callback) {
        this.getForeignSkin(skinURL).load(modelId, requester, callback);
    }

    @Override
    public void resolveForeignCapeKeyed(UUID requester, String capeURL, Consumer<IEaglerPlayerCape> callback) {
        this.getForeignCape(capeURL).load(requester, callback);
    }

    void addWaitingForeignURLSkinLookup(UUID requestUUID, Consumer<IEaglerPlayerSkin> callback) {
        long now = System.nanoTime();
        PendingSkinLookup lookup = new PendingSkinLookup(requestUUID, now + 15000000000L, callback);
        this.pendingSkinLookups.put(requestUUID, lookup);
        this.service.timeoutLoop().addFuture(now, lookup);
    }

    void addWaitingForeignURLCapeLookup(UUID requestUUID, Consumer<IEaglerPlayerCape> callback) {
        long now = System.nanoTime();
        PendingCapeLookup lookup = new PendingCapeLookup(requestUUID, now + 15000000000L, callback);
        this.pendingCapeLookups.put(requestUUID, lookup);
        this.service.timeoutLoop().addFuture(now, lookup);
    }

    public boolean onForeignSkinReceived(UUID requestUUID, IEaglerPlayerSkin skin) {
        PendingSkinLookup lookup = (PendingSkinLookup)this.pendingSkinLookups.remove(requestUUID);
        if (lookup != null) {
            try {
                lookup.consumer.accept(skin);
            }
            catch (Exception ex) {
                this.service.logger().error("Caught error from lazy load callback", ex);
            }
            return true;
        }
        return false;
    }

    public boolean onForeignCapeReceived(UUID requestUUID, IEaglerPlayerCape cape) {
        PendingCapeLookup lookup = (PendingCapeLookup)this.pendingCapeLookups.remove(requestUUID);
        if (lookup != null) {
            try {
                lookup.consumer.accept(cape);
            }
            catch (Exception ex) {
                this.service.logger().error("Caught error from lazy load callback", ex);
            }
            return true;
        }
        return false;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void addDeferred(IDeferredLoad runnable) {
        block5: {
            block6: {
                SupervisorResolver supervisorResolver = this;
                synchronized (supervisorResolver) {
                    if (this.deferred == null) {
                        break block5;
                    }
                    if (this.getConnection() != null) {
                        break block6;
                    }
                    this.deferred.add(runnable);
                    return;
                }
            }
            runnable.complete(false);
            return;
        }
        runnable.complete(true);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    void flushDeferred() {
        ArrayList<IDeferredLoad> lst;
        SupervisorResolver supervisorResolver = this;
        synchronized (supervisorResolver) {
            if (this.deferred == null) {
                return;
            }
            lst = new ArrayList<IDeferredLoad>(this.deferred);
            this.deferred = null;
        }
        for (IDeferredLoad run : lst) {
            run.complete(false);
        }
    }

    void onConnectionEnd() {
        int i;
        Object[] arr = this.pendingSkinLookups.values().toArray();
        for (i = 0; i < arr.length; ++i) {
            ((ISupervisorExpiring)arr[i]).expire();
        }
        this.pendingSkinLookups.clear();
        arr = this.pendingCapeLookups.values().toArray();
        for (i = 0; i < arr.length; ++i) {
            ((ISupervisorExpiring)arr[i]).expire();
        }
        this.pendingCapeLookups.clear();
    }

    private class PendingSkinLookup
    implements ISupervisorExpiring {
        private final UUID requestUUID;
        private final long expiresAt;
        private final Consumer<IEaglerPlayerSkin> consumer;

        protected PendingSkinLookup(UUID requestUUID, long expiresAt, Consumer<IEaglerPlayerSkin> consumer) {
            this.requestUUID = requestUUID;
            this.expiresAt = expiresAt;
            this.consumer = consumer;
        }

        @Override
        public long expiresAt() {
            return this.expiresAt;
        }

        @Override
        public void expire() {
            if (SupervisorResolver.this.pendingSkinLookups.remove(this.requestUUID) != null) {
                try {
                    this.consumer.accept(MissingSkin.UNAVAILABLE_SKIN);
                }
                catch (Exception ex) {
                    SupervisorResolver.this.service.logger().error("Caught error from lazy load callback", ex);
                }
            }
        }
    }

    private class PendingCapeLookup
    implements ISupervisorExpiring {
        private final UUID requestUUID;
        private final long expiresAt;
        private final Consumer<IEaglerPlayerCape> consumer;

        protected PendingCapeLookup(UUID requestUUID, long expiresAt, Consumer<IEaglerPlayerCape> consumer) {
            this.requestUUID = requestUUID;
            this.expiresAt = expiresAt;
            this.consumer = consumer;
        }

        @Override
        public long expiresAt() {
            return this.expiresAt;
        }

        @Override
        public void expire() {
            if (SupervisorResolver.this.pendingCapeLookups.remove(this.requestUUID) != null) {
                try {
                    this.consumer.accept(MissingCape.UNAVAILABLE_CAPE);
                }
                catch (Exception ex) {
                    SupervisorResolver.this.service.logger().error("Caught error from lazy load callback", ex);
                }
            }
        }
    }

    static interface IDeferredLoad {
        public void complete(boolean var1);
    }
}

