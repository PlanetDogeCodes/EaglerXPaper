/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 */
package net.lax1dude.eaglercraft.backend.server.base.skins;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformLogger;
import net.lax1dude.eaglercraft.backend.skin_cache.ISkinCacheService;

public class SkinCachePrewarmer {
    private final ISkinCacheService skinCacheService;
    private final IPlatformLogger logger;
    private final File usercacheFile;
    private final int maxPlayers;
    private final int threadCount;
    private static final int MAX_CONCURRENT_FETCHES = 2;
    private static final long FETCH_DELAY_MS = 500L;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile boolean running = false;
    private volatile ExecutorService executor;
    private final Semaphore fetchSemaphore = new Semaphore(2);
    private final Object fetchDelayLock = new Object();
    private volatile long lastFetchTime = 0L;

    public SkinCachePrewarmer(ISkinCacheService skinCacheService, IPlatformLogger logger, File usercacheFile, int maxPlayers, int threadCount) {
        this.skinCacheService = skinCacheService;
        this.logger = logger;
        this.usercacheFile = usercacheFile;
        this.maxPlayers = Math.max(1, maxPlayers);
        this.threadCount = Math.max(1, Math.min(threadCount, 4));
    }

    public synchronized void startAsync() {
        ExecutorService ex;
        if (this.started.get()) {
            return;
        }
        if (this.usercacheFile == null || !this.usercacheFile.exists() || !this.usercacheFile.isFile()) {
            this.logger.info("[Skin Prewarm] usercache.json not found, skipping skin cache pre-warming");
            return;
        }
        this.executor = ex = Executors.newFixedThreadPool(this.threadCount, r -> {
            Thread t = new Thread(r, "eaglerxpaper-skin-prewarm");
            t.setDaemon(true);
            t.setPriority(1);
            return t;
        });
        this.started.set(true);
        this.running = true;
        Thread driver = new Thread(() -> {
            Throwable failure = null;
            try {
                this.prewarm();
            }
            catch (Throwable x) {
                failure = x;
            }
            this.running = false;
            if (this.executor == ex) {
                this.shutdown();
            }
            if (failure != null) {
                this.logger.warn("[Skin Prewarm] Skin cache pre-warming failed: " + failure.getMessage());
            }
        }, "eaglerxpaper-skin-prewarm-driver");
        driver.setDaemon(true);
        driver.setPriority(1);
        driver.start();
    }

    public void shutdown() {
        this.running = false;
        this.started.set(false);
        ExecutorService ex = this.executor;
        if (ex != null && !ex.isShutdown()) {
            ex.shutdownNow();
            try {
                ex.awaitTermination(5L, TimeUnit.SECONDS);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        this.executor = null;
    }

    private void prewarm() {
        this.logger.info("[Skin Prewarm] Reading usercache.json...");
        ConcurrentLinkedQueue<String> uuidsToFetch = this.readUsercacheUUIDs();
        if (uuidsToFetch.isEmpty()) {
            this.logger.info("[Skin Prewarm] No players found in usercache.json, skipping");
            return;
        }
        int total = uuidsToFetch.size();
        this.logger.info("[Skin Prewarm] Pre-warming skins for " + total + " players (using " + this.threadCount + " threads, max " + 2 + " concurrent)...");
        ExecutorService ex = this.executor;
        if (ex == null || !this.running) {
            return;
        }
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger skinsLoaded = new AtomicInteger(0);
        AtomicInteger capesLoaded = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);
        CompletableFuture[] futures = new CompletableFuture[total];
        int idx = 0;
        for (String uuidStr : uuidsToFetch) {
            if (!this.running) break;
            String uuid = uuidStr;
            try {
                futures[idx++] = CompletableFuture.runAsync(() -> {
                    try {
                        int[] counts = this.fetchAndCacheProfile(uuid);
                        skinsLoaded.addAndGet(counts[0]);
                        capesLoaded.addAndGet(counts[1]);
                        int done = completed.incrementAndGet();
                        if (done % 10 == 0 || done == total) {
                            this.logger.info("[Skin Prewarm] Progress: " + done + "/" + total + " (skins: " + skinsLoaded.get() + ", capes: " + capesLoaded.get() + (errors.get() > 0 ? ", errors: " + errors.get() : "") + ")");
                        }
                    }
                    catch (Exception e) {
                        try {
                            errors.incrementAndGet();
                            int done = completed.incrementAndGet();
                            if (done % 10 == 0 || done == total) {
                                this.logger.info("[Skin Prewarm] Progress: " + done + "/" + total + " (skins: " + skinsLoaded.get() + ", capes: " + capesLoaded.get() + (errors.get() > 0 ? ", errors: " + errors.get() : "") + ")");
                            }
                        }
                        catch (Throwable throwable) {
                            int done = completed.incrementAndGet();
                            if (done % 10 == 0 || done == total) {
                                this.logger.info("[Skin Prewarm] Progress: " + done + "/" + total + " (skins: " + skinsLoaded.get() + ", capes: " + capesLoaded.get() + (errors.get() > 0 ? ", errors: " + errors.get() : "") + ")");
                            }
                            throw throwable;
                        }
                    }
                }, ex);
            }
            catch (Exception re) {
                break;
            }
        }
        long waitMs = 60000L + (long)idx * 1500L;
        try {
            CompletableFuture.allOf(idx == total ? futures : Arrays.copyOf(futures, idx)).get(waitMs, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        catch (Exception e) {
            this.logger.warn("[Skin Prewarm] Timed out or aborted waiting for fetch tasks (" + completed.get() + "/" + total + " done)");
        }
        this.logger.info("[Skin Prewarm] Done! Pre-warmed " + skinsLoaded.get() + " skins and " + capesLoaded.get() + " capes for " + total + " players" + (errors.get() > 0 ? " (" + errors.get() + " errors)" : ""));
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private ConcurrentLinkedQueue<String> readUsercacheUUIDs() {
        ConcurrentLinkedQueue<String> uuids = new ConcurrentLinkedQueue<String>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader((InputStream)new FileInputStream(this.usercacheFile), StandardCharsets.UTF_8));){
            JsonElement root = new JsonParser().parse((Reader)reader);
            if (root == null || !root.isJsonArray()) {
                ConcurrentLinkedQueue<String> concurrentLinkedQueue = uuids;
                return concurrentLinkedQueue;
            }
            JsonArray arr = root.getAsJsonArray();
            int count = 0;
            Iterator iterator = arr.iterator();
            while (iterator.hasNext()) {
                String uuidStr;
                JsonObject obj;
                JsonElement uuidElem;
                JsonElement entry = (JsonElement)iterator.next();
                if (entry == null || !entry.isJsonObject() || (uuidElem = (obj = entry.getAsJsonObject()).get("uuid")) == null || !uuidElem.isJsonPrimitive() || (uuidStr = uuidElem.getAsString()) == null || uuidStr.isEmpty()) continue;
                try {
                    UUID.fromString(uuidStr);
                }
                catch (IllegalArgumentException e) {
                    continue;
                }
                uuids.add(uuidStr);
                if (++count >= this.maxPlayers) return uuids;
            }
            return uuids;
        }
        catch (IOException e) {
            this.logger.warn("[Skin Prewarm] Could not read usercache.json: " + e.getMessage());
            return uuids;
        }
        catch (Exception e) {
            this.logger.warn("[Skin Prewarm] Error parsing usercache.json: " + e.getMessage());
        }
        return uuids;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled aggressive exception aggregation
     */
    private int[] fetchAndCacheProfile(String uuidStr) {
        UUID uuid;
        if (!this.running) {
            return new int[]{0, 0};
        }
        try {
            uuid = UUID.fromString(uuidStr);
        }
        catch (IllegalArgumentException e) {
            return new int[]{0, 0};
        }
        try {
            this.fetchSemaphore.acquire();
            try {
                Object lock = this.fetchDelayLock;
                synchronized (lock) {
                    long now = System.currentTimeMillis();
                    long elapsed = now - this.lastFetchTime;
                    if (elapsed < 500L) {
                        Thread.sleep(500L - elapsed);
                    }
                    this.lastFetchTime = System.currentTimeMillis();
                }
                if (!this.running) {
                    return new int[]{0, 0};
                }
                JsonObject profile = this.fetchProfileJSON(uuid);
                if (profile == null) {
                    int[] nArray = new int[]{0, 0};
                    return nArray;
                }
                int[] nArray = this.extractAndCacheTextures(profile);
                return nArray;
            }
            finally {
                this.fetchSemaphore.release();
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new int[]{0, 0};
        }
    }

    private int[] extractAndCacheTextures(JsonObject profile) {
        int skinsLoaded = 0;
        int capesLoaded = 0;
        JsonElement propsElem = profile.get("properties");
        if (propsElem == null || !propsElem.isJsonArray()) {
            return new int[]{0, 0};
        }
        JsonArray props = propsElem.getAsJsonArray();
        for (JsonElement propElem : props) {
            JsonElement valueElem;
            JsonObject prop;
            JsonElement nameElem;
            if (propElem == null || !propElem.isJsonObject() || (nameElem = (prop = propElem.getAsJsonObject()).get("name")) == null || !nameElem.isJsonPrimitive() || !"textures".equals(nameElem.getAsString()) || (valueElem = prop.get("value")) == null || !valueElem.isJsonPrimitive()) continue;
            String texturesProperty = valueElem.getAsString();
            try {
                String capeUrl;
                JsonObject capeObj;
                JsonElement urlElem;
                JsonElement capeElem;
                String skinUrl;
                JsonObject skinObj;
                JsonElement urlElem2;
                JsonObject root;
                JsonElement texturesElem;
                String jsonStr = new String(Base64.getDecoder().decode(texturesProperty), StandardCharsets.UTF_8);
                JsonElement texturesRootElem = new JsonParser().parse(jsonStr);
                if (texturesRootElem == null || !texturesRootElem.isJsonObject() || (texturesElem = (root = texturesRootElem.getAsJsonObject()).get("textures")) == null || !texturesElem.isJsonObject()) break;
                JsonObject texturesObj = texturesElem.getAsJsonObject();
                JsonElement skinElem = texturesObj.get("SKIN");
                if (skinElem != null && skinElem.isJsonObject() && (urlElem2 = (skinObj = skinElem.getAsJsonObject()).get("url")) != null && urlElem2.isJsonPrimitive() && (skinUrl = urlElem2.getAsString()) != null && !skinUrl.isEmpty()) {
                    this.skinCacheService.resolveSkinByURL(skinUrl, data -> {});
                    ++skinsLoaded;
                }
                if ((capeElem = texturesObj.get("CAPE")) == null || !capeElem.isJsonObject() || (urlElem = (capeObj = capeElem.getAsJsonObject()).get("url")) == null || !urlElem.isJsonPrimitive() || (capeUrl = urlElem.getAsString()) == null || capeUrl.isEmpty()) break;
                this.skinCacheService.resolveCapeByURL(capeUrl, data -> {});
                break;
            }
            catch (Exception exception) {
                // empty catch block
                break;
            }
        }
        return new int[]{skinsLoaded, ++capesLoaded};
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private JsonObject fetchProfileJSON(UUID uuid) {
        if (!this.running) {
            return null;
        }
        HttpURLConnection conn = null;
        try {
            JsonObject jsonObject;
            String body;
            StringBuilder sb;
            URI uri = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", ""));
            conn = (HttpURLConnection)uri.toURL().openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            int status = conn.getResponseCode();
            if (status == 429) {
                this.logger.warn("[Skin Prewarm] Mojang API rate limit hit (429), backing off...");
                try {
                    Thread.sleep(5000L);
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                JsonObject e = null;
                return e;
            }
            if (status != 200) {
                JsonObject e = null;
                return e;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));){
                int n;
                sb = new StringBuilder();
                char[] buf = new char[2048];
                while ((n = reader.read(buf)) != -1) {
                    sb.append(buf, 0, n);
                }
                body = sb.toString();
            }
            if (body.isEmpty()) {
                return null;
            }
            JsonElement root = new JsonParser().parse(body);
            if (root == null || !root.isJsonObject()) {
                jsonObject = null;
                return jsonObject;
            }
            jsonObject = root.getAsJsonObject();
            return jsonObject;
        }
        catch (Exception e) {
            JsonObject jsonObject = null;
            return jsonObject;
        }
        finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public boolean isRunning() {
        return this.running;
    }
}

