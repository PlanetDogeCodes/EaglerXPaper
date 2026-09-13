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

package net.lax1dude.eaglercraft.backend.server.base.skins;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.lax1dude.eaglercraft.backend.skin_cache.ISkinCacheService;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformLogger;

/**
 * Async skin cache pre-warming on server enable. Reads Bukkit's usercache.json,
 * fetches each player's textures property from the Mojang session server, and
 * resolves the skin/cape URLs through the skin cache service so the first join
 * of each player doesn't stall on a download.
 */
public class SkinCachePrewarmer {

        private final ISkinCacheService skinCacheService;
        private final IPlatformLogger logger;
        private final File usercacheFile;
        private final int maxPlayers;
        private final int threadCount;

        // Mojang allows ~600 requests per 10 minutes; stay well under it
        private static final int MAX_CONCURRENT_FETCHES = 2;
        private static final long FETCH_DELAY_MS = 500;

        private final AtomicBoolean started = new AtomicBoolean(false);
        private volatile boolean running = false;
        private volatile ExecutorService executor;
        private volatile HttpClient httpClient;
        private final Semaphore fetchSemaphore = new Semaphore(MAX_CONCURRENT_FETCHES);
        private final Object fetchDelayLock = new Object();
        private volatile long lastFetchTime = 0;

        public SkinCachePrewarmer(ISkinCacheService skinCacheService, IPlatformLogger logger,
                        File usercacheFile, int maxPlayers, int threadCount) {
                this.skinCacheService = skinCacheService;
                this.logger = logger;
                this.usercacheFile = usercacheFile;
                this.maxPlayers = Math.max(1, maxPlayers);
                this.threadCount = Math.max(1, Math.min(threadCount, 4));
        }

        /**
         * Starts pre-warming asynchronously. Returns immediately. Thread-safe.
         */
        public synchronized void startAsync() {
                if (started.get()) {
                        return;
                }
                if (usercacheFile == null || !usercacheFile.exists() || !usercacheFile.isFile()) {
                        logger.info("[Skin Prewarm] usercache.json not found, skipping skin cache pre-warming");
                        return;
                }

                ExecutorService ex = Executors.newFixedThreadPool(threadCount, r -> {
                        Thread t = new Thread(r, "eaglerxpaper-skin-prewarm");
                        t.setDaemon(true);
                        t.setPriority(Thread.MIN_PRIORITY);
                        return t;
                });
                executor = ex;

                try {
                        httpClient = HttpClient.newBuilder()
                                        .connectTimeout(Duration.ofSeconds(10))
                                        .executor(ex)
                                        .build();
                } catch (Exception e) {
                        logger.warn("[Skin Prewarm] Could not create HTTP client: " + e.getMessage());
                        ex.shutdownNow();
                        executor = null;
                        return;
                }

                started.set(true);
                running = true;

                // The driver runs on its own thread, NOT on the fetch pool: with
                // threadCount == 1 (1-3 vCPU servers) a pool-thread driver would
                // occupy the only worker while blocking on the fetch tasks it
                // queued to that same pool, deadlocking the prewarm forever.
                Thread driver = new Thread(() -> {
                        Throwable failure = null;
                        try {
                                prewarm();
                        } catch (Throwable x) {
                                failure = x;
                        }
                        running = false;
                        // only shut down if this run still owns the current executor
                        if (executor == ex) {
                                shutdown();
                        }
                        if (failure != null) {
                                logger.warn("[Skin Prewarm] Skin cache pre-warming failed: " + failure.getMessage());
                        }
                }, "eaglerxpaper-skin-prewarm-driver");
                driver.setDaemon(true);
                driver.setPriority(Thread.MIN_PRIORITY);
                driver.start();
        }

        /**
         * Shuts down the prewarmer. Safe to call multiple times from any thread.
         */
        public void shutdown() {
                running = false;
                started.set(false);

                ExecutorService ex = executor;
                if (ex != null && !ex.isShutdown()) {
                        ex.shutdownNow();
                        try {
                                ex.awaitTermination(5, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                        }
                }
                executor = null;

                HttpClient client = httpClient;
                if (client != null) {
                        try {
                                // HttpClient is AutoCloseable on Java 21+
                                if (client instanceof AutoCloseable) {
                                        ((AutoCloseable) client).close();
                                }
                        } catch (Throwable t) {
                        }
                        httpClient = null;
                }
        }

        private void prewarm() {
                logger.info("[Skin Prewarm] Reading usercache.json...");
                ConcurrentLinkedQueue<String> uuidsToFetch = readUsercacheUUIDs();
                if (uuidsToFetch.isEmpty()) {
                        logger.info("[Skin Prewarm] No players found in usercache.json, skipping");
                        return;
                }

                int total = uuidsToFetch.size();
                logger.info("[Skin Prewarm] Pre-warming skins for " + total + " players (using "
                                + threadCount + " threads, max " + MAX_CONCURRENT_FETCHES + " concurrent)...");

                ExecutorService ex = executor;
                if (ex == null || !running) {
                        return;
                }

                AtomicInteger completed = new AtomicInteger(0);
                AtomicInteger skinsLoaded = new AtomicInteger(0);
                AtomicInteger capesLoaded = new AtomicInteger(0);
                AtomicInteger errors = new AtomicInteger(0);

                CompletableFuture<?>[] futures = new CompletableFuture[total];
                int idx = 0;
                for (String uuidStr : uuidsToFetch) {
                        if (!running) {
                                break;
                        }
                        final String uuid = uuidStr;
                        try {
                                futures[idx++] = CompletableFuture.runAsync(() -> {
                                        try {
                                                int[] counts = fetchAndCacheProfile(uuid);
                                                skinsLoaded.addAndGet(counts[0]);
                                                capesLoaded.addAndGet(counts[1]);
                                        } catch (Exception e) {
                                                errors.incrementAndGet();
                                        } finally {
                                                int done = completed.incrementAndGet();
                                                if (done % 10 == 0 || done == total) {
                                                        logger.info("[Skin Prewarm] Progress: " + done + "/" + total
                                                                        + " (skins: " + skinsLoaded.get()
                                                                        + ", capes: " + capesLoaded.get()
                                                                        + (errors.get() > 0 ? ", errors: " + errors.get() : "")
                                                                        + ")");
                                                }
                                        }
                                }, ex);
                        } catch (Exception re) {
                                // executor was rejected mid-submission (shutdown)
                                break;
                        }
                }

                // Bounded wait: a shutdownNow() during prewarm drains queued-but-
                // unstarted fetch tasks, whose futures then never complete, and a
                // plain join() would park this thread forever (join is not
                // interruptible). One fetch needs ~500ms of spacing plus network
                // time, so allow a generous per-player margin on top of a minute.
                long waitMs = 60_000l + (long) idx * 1500l;
                try {
                        CompletableFuture.allOf(idx == total ? futures : Arrays.copyOf(futures, idx))
                                        .get(waitMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                } catch (Exception e) {
                        logger.warn("[Skin Prewarm] Timed out or aborted waiting for fetch tasks ("
                                        + completed.get() + "/" + total + " done)");
                }

                logger.info("[Skin Prewarm] Done! Pre-warmed " + skinsLoaded.get() + " skins and "
                                + capesLoaded.get() + " capes for " + total + " players"
                                + (errors.get() > 0 ? " (" + errors.get() + " errors)" : ""));
        }

        private ConcurrentLinkedQueue<String> readUsercacheUUIDs() {
                ConcurrentLinkedQueue<String> uuids = new ConcurrentLinkedQueue<>();
                try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(new FileInputStream(usercacheFile), StandardCharsets.UTF_8))) {
                        JsonElement root = new JsonParser().parse(reader);
                        if (root == null || !root.isJsonArray()) {
                                return uuids;
                        }
                        JsonArray arr = root.getAsJsonArray();
                        int count = 0;
                        for (JsonElement entry : arr) {
                                if (entry == null || !entry.isJsonObject()) continue;
                                JsonObject obj = entry.getAsJsonObject();
                                JsonElement uuidElem = obj.get("uuid");
                                if (uuidElem == null || !uuidElem.isJsonPrimitive()) continue;
                                String uuidStr = uuidElem.getAsString();
                                if (uuidStr == null || uuidStr.isEmpty()) continue;
                                try {
                                        UUID.fromString(uuidStr);
                                } catch (IllegalArgumentException e) {
                                        continue;
                                }
                                uuids.add(uuidStr);
                                if (++count >= maxPlayers) break;
                        }
                } catch (IOException e) {
                        logger.warn("[Skin Prewarm] Could not read usercache.json: " + e.getMessage());
                } catch (Exception e) {
                        logger.warn("[Skin Prewarm] Error parsing usercache.json: " + e.getMessage());
                }
                return uuids;
        }

        /**
         * Fetches a player's profile and caches their skin/cape.
         * Returns int[2]: { skinsLoaded, capesLoaded }
         */
        private int[] fetchAndCacheProfile(String uuidStr) {
                if (!running) return new int[]{0, 0};

                UUID uuid;
                try {
                        uuid = UUID.fromString(uuidStr);
                } catch (IllegalArgumentException e) {
                        return new int[]{0, 0};
                }

                try {
                        fetchSemaphore.acquire();
                        try {
                                // enforce spacing on the Mojang API; sleeping inside the
                                // lock serializes the start times of consecutive requests
                                synchronized (fetchDelayLock) {
                                        long now = System.currentTimeMillis();
                                        long elapsed = now - lastFetchTime;
                                        if (elapsed < FETCH_DELAY_MS) {
                                                Thread.sleep(FETCH_DELAY_MS - elapsed);
                                        }
                                        lastFetchTime = System.currentTimeMillis();
                                }

                                if (!running) return new int[]{0, 0};

                                JsonObject profile = fetchProfileJSON(uuid);
                                if (profile == null) return new int[]{0, 0};

                                return extractAndCacheTextures(profile);
                        } finally {
                                fetchSemaphore.release();
                        }
                } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return new int[]{0, 0};
                }
        }

        private int[] extractAndCacheTextures(JsonObject profile) {
                int skinsLoaded = 0;
                int capesLoaded = 0;

                JsonElement propsElem = profile.get("properties");
                if (propsElem == null || !propsElem.isJsonArray()) return new int[]{0, 0};
                JsonArray props = propsElem.getAsJsonArray();

                for (JsonElement propElem : props) {
                        if (propElem == null || !propElem.isJsonObject()) continue;
                        JsonObject prop = propElem.getAsJsonObject();

                        JsonElement nameElem = prop.get("name");
                        if (nameElem == null || !nameElem.isJsonPrimitive()) continue;
                        if (!"textures".equals(nameElem.getAsString())) continue;

                        JsonElement valueElem = prop.get("value");
                        if (valueElem == null || !valueElem.isJsonPrimitive()) continue;

                        String texturesProperty = valueElem.getAsString();
                        try {
                                String jsonStr = new String(Base64.getDecoder().decode(texturesProperty), StandardCharsets.UTF_8);
                                JsonElement texturesRootElem = new JsonParser().parse(jsonStr);
                                if (texturesRootElem == null || !texturesRootElem.isJsonObject()) break;
                                JsonObject root = texturesRootElem.getAsJsonObject();

                                JsonElement texturesElem = root.get("textures");
                                if (texturesElem == null || !texturesElem.isJsonObject()) break;
                                JsonObject texturesObj = texturesElem.getAsJsonObject();

                                // resolveSkinByURL/resolveCapeByURL populate the async cache
                                JsonElement skinElem = texturesObj.get("SKIN");
                                if (skinElem != null && skinElem.isJsonObject()) {
                                        JsonObject skinObj = skinElem.getAsJsonObject();
                                        JsonElement urlElem = skinObj.get("url");
                                        if (urlElem != null && urlElem.isJsonPrimitive()) {
                                                String skinUrl = urlElem.getAsString();
                                                if (skinUrl != null && !skinUrl.isEmpty()) {
                                                        skinCacheService.resolveSkinByURL(skinUrl, (data) -> {});
                                                        skinsLoaded++;
                                                }
                                        }
                                }

                                JsonElement capeElem = texturesObj.get("CAPE");
                                if (capeElem != null && capeElem.isJsonObject()) {
                                        JsonObject capeObj = capeElem.getAsJsonObject();
                                        JsonElement urlElem = capeObj.get("url");
                                        if (urlElem != null && urlElem.isJsonPrimitive()) {
                                                String capeUrl = urlElem.getAsString();
                                                if (capeUrl != null && !capeUrl.isEmpty()) {
                                                        skinCacheService.resolveCapeByURL(capeUrl, (data) -> {});
                                                        capesLoaded++;
                                                }
                                        }
                                }
                        } catch (Exception e) {
                                // malformed textures property, skip
                        }
                        break; // only one textures property per profile
                }

                return new int[]{skinsLoaded, capesLoaded};
        }

        private JsonObject fetchProfileJSON(UUID uuid) {
                HttpClient client = httpClient;
                if (client == null) return null;

                try {
                        URI uri = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/"
                                        + uuid.toString().replace("-", ""));
                        HttpRequest request = HttpRequest.newBuilder()
                                        .uri(uri)
                                        .header("Accept", "application/json")
                                        .timeout(Duration.ofSeconds(15))
                                        .GET()
                                        .build();
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        int status = response.statusCode();
                        if (status == 429) {
                                logger.warn("[Skin Prewarm] Mojang API rate limit hit (429), backing off...");
                                try {
                                        Thread.sleep(5000);
                                } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                }
                                return null;
                        }
                        if (status != 200) {
                                return null;
                        }
                        String body = response.body();
                        if (body == null || body.isEmpty()) return null;
                        JsonElement root = new JsonParser().parse(body);
                        if (root == null || !root.isJsonObject()) return null;
                        return root.getAsJsonObject();
                } catch (Exception e) {
                        // network error, timeout, etc. — skip this player
                        return null;
                }
        }

        public boolean isRunning() {
                return running;
        }
}
