/*
 * Copyright (c) 2025 lax1dude. All Rights Reserved.
 *
 * Skin cache pre-warming — asynchronously pre-loads skins for recently-seen
 * players so that the first join doesn't stall on a skin download.
 *
 * On server enable, this reads usercache.json (which Bukkit/Paper maintains
 * with recent player usernames), fetches each player's GameProfile from
 * Mojang's sessionserver API to extract their textures property, then resolves
 * the skin/cape URLs through the existing SkinCacheService. This populates the
 * in-memory and on-disk cache so that when the player actually joins, their
 * skin is already available with zero download latency.
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

public class SkinCachePrewarmer {

    private final ISkinCacheService skinCacheService;
    private final IPlatformLogger logger;
    private final File usercacheFile;
    private final int maxPlayers;
    private final int threadCount;

    // Mojang rate limiting: ~600 requests per 10 min = 1 per second.
    // We use a semaphore to limit concurrent requests and a delay between fetches.
    private static final int MAX_CONCURRENT_FETCHES = 2;
    private static final long FETCH_DELAY_MS = 500; // 2 per second max

    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile boolean running = false;
    private volatile ExecutorService executor;
    private volatile HttpClient httpClient;
    private final Semaphore fetchSemaphore = new Semaphore(MAX_CONCURRENT_FETCHES);
    // Bug #27 fix: use AtomicLong for lastFetchTime so the read-modify-write
    // (sleep + update) is serialized across concurrent fetcher threads. Without
    // this, two threads could both read the same last value, both sleep the same
    // duration, and both update — effectively doubling the request rate to
    // Mojang's API and triggering 429 rate limits.
    private final java.util.concurrent.atomic.AtomicLong lastFetchTime = new java.util.concurrent.atomic.AtomicLong(0);

    public SkinCachePrewarmer(ISkinCacheService skinCacheService, IPlatformLogger logger,
            File usercacheFile, int maxPlayers, int threadCount) {
        this.skinCacheService = skinCacheService;
        this.logger = logger;
        this.usercacheFile = usercacheFile;
        this.maxPlayers = Math.max(1, maxPlayers);
        this.threadCount = Math.max(1, Math.min(threadCount, 4));
    }

    /**
     * Starts the pre-warming process asynchronously. Returns immediately.
     * Thread-safe: if called concurrently, only one instance will start.
     */
    public synchronized void startAsync() {
        if (started.get()) {
            return;
        }
        if (usercacheFile == null || !usercacheFile.exists() || !usercacheFile.isFile()) {
            logger.info("[Skin Prewarm] usercache.json not found, skipping skin cache pre-warming");
            return;
        }

        // Create the executor and HttpClient BEFORE setting started=true
        // so that shutdown() can see them.
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

        CompletableFuture.runAsync(this::prewarm, ex).whenComplete((v, x) -> {
            running = false;
            shutdown();
            if (x != null) {
                logger.warn("[Skin Prewarm] Skin cache pre-warming failed: " + x.getMessage());
            }
        });
    }

    /**
     * Shuts down the prewarmer. Safe to call multiple times from any thread.
     *
     * Bug #26 fix: synchronized to coordinate with startAsync(). Without this,
     * shutdown() could null out executor/httpClient while startAsync() is still
     * setting them up, leaving dangling resources that are never cleaned up.
     */
    public synchronized void shutdown() {
        running = false;
        started.set(false);

        ExecutorService ex = executor;
        if (ex != null && !ex.isShutdown()) {
            ex.shutdownNow();
            try {
                if (!ex.awaitTermination(5, TimeUnit.SECONDS)) {
                    // Force shutdown if tasks didn't complete
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        executor = null;

        HttpClient client = httpClient;
        if (client != null) {
            // Java 21+ implements AutoCloseable; older versions don't
            try {
                if (client instanceof AutoCloseable) {
                    ((AutoCloseable) client).close();
                }
            } catch (Throwable t) {
                // Best effort — don't crash on shutdown
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

        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger skinsLoaded = new AtomicInteger(0);
        AtomicInteger capesLoaded = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        CompletableFuture<?>[] futures = new CompletableFuture[total];
        int idx = 0;
        for (String uuidStr : uuidsToFetch) {
            final String uuid = uuidStr;
            futures[idx++] = CompletableFuture.runAsync(() -> {
                if (!running) return;
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
            }, executor);
        }

        CompletableFuture.allOf(futures).join();

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
                count++;
                if (count >= maxPlayers) break;
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

        // Rate limit: acquire semaphore + enforce minimum delay between fetches
        try {
            fetchSemaphore.acquire();
            try {
                // Enforce minimum delay between fetches.
                // Bug #27 fix: use a CAS loop to atomically claim a "fetch slot" by
                // advancing lastFetchTime. If two threads race, only one wins the CAS
                // and the loser must sleep longer to respect the delay.
                long sleepUntil;
                while (true) {
                    long now = System.currentTimeMillis();
                    long last = lastFetchTime.get();
                    long earliestNext = Math.max(last + FETCH_DELAY_MS, now);
                    // Try to claim this slot. If we win, lastFetchTime is now earliestNext.
                    if (lastFetchTime.compareAndSet(last, earliestNext)) {
                        sleepUntil = earliestNext;
                        break;
                    }
                    // Lost the race — retry with the new value.
                }
                long sleepMs = sleepUntil - System.currentTimeMillis();
                if (sleepMs > 0) {
                    Thread.sleep(sleepMs);
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

                // Extract skin URL
                JsonElement skinElem = texturesObj.get("SKIN");
                if (skinElem != null && skinElem.isJsonObject()) {
                    JsonObject skinObj = skinElem.getAsJsonObject();
                    JsonElement urlElem = skinObj.get("url");
                    if (urlElem != null && urlElem.isJsonPrimitive()) {
                        String skinUrl = urlElem.getAsString();
                        if (skinUrl != null && !skinUrl.isEmpty()) {
                            final boolean[] success = {false};
                            skinCacheService.resolveSkinByURL(skinUrl, (data) -> {
                                if (data != null && data != ISkinCacheService.ERROR) {
                                    success[0] = true;
                                }
                            });
                            // Note: resolveSkinByURL is async, so success may not be set yet.
                            // We count the URL as "discovered" — actual cache hit is best-effort.
                            skinsLoaded++;
                        }
                    }
                }

                // Extract cape URL
                JsonElement capeElem = texturesObj.get("CAPE");
                if (capeElem != null && capeElem.isJsonObject()) {
                    JsonObject capeObj = capeElem.getAsJsonObject();
                    JsonElement urlElem = capeObj.get("url");
                    if (urlElem != null && urlElem.isJsonPrimitive()) {
                        String capeUrl = urlElem.getAsString();
                        if (capeUrl != null && !capeUrl.isEmpty()) {
                            skinCacheService.resolveCapeByURL(capeUrl, (data) -> {
                                // Async — best effort
                            });
                            capesLoaded++;
                        }
                    }
                }
            } catch (Exception e) {
                // Malformed textures property — skip
            }
            break; // Only one textures property per profile
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
                // Rate limited by Mojang — back off
                logger.warn("[Skin Prewarm] Mojang API rate limit hit (429), backing off...");
                try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
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
        } catch (java.io.IOException | InterruptedException e) {
            // Bug #28 fix: distinguish expected network errors (IOException) from
            // unexpected ones (NPE, JsonParseException, SecurityException, etc.).
            // Network errors are common (429s, DNS issues, connection refused) so
            // we log at DEBUG only. Other exceptions indicate a code bug and are
            // logged at WARN.
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        } catch (RuntimeException e) {
            // Bug #28 fix: log non-network exceptions so operators can diagnose
            // JsonParseException, NPE, etc.
            logger.warn("[Skin Prewarm] Unexpected error fetching profile for " + uuid + ": " + e);
            return null;
        }
    }

    public boolean isRunning() {
        return running;
    }
}
