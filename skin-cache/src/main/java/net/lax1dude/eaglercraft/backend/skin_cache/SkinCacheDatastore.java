/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.skin_cache;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import net.lax1dude.eaglercraft.backend.skin_cache.ISkinCacheDatastore;
import net.lax1dude.eaglercraft.backend.skin_cache.SkinCacheTable;
import net.lax1dude.eaglercraft.backend.util.ILoggerAdapter;
import net.lax1dude.eaglercraft.backend.util.SteadyTime;

public class SkinCacheDatastore
implements ISkinCacheDatastore {
    public static final int SKIN_LENGTH = 12288;
    public static final int CAPE_LENGTH = 1173;
    protected final ILoggerAdapter logger;
    protected final SkinCacheDatastoreThreadEnv[] threads;
    protected final BlockingQueue<SkinCacheDatastoreRunnable> databaseQueue = new LinkedBlockingQueue<SkinCacheDatastoreRunnable>();
    protected final CountDownLatch disposeLatch;
    protected long lastCleanup = 0L;
    protected int keepObjectsDays;
    protected int maxObjects;
    protected final boolean sqliteCompatible;
    protected final SkinCacheTable skin;
    protected final SkinCacheTable cape;
    protected final Object dbWriteLock = new Object();
    private static final SkinCacheDatastoreRunnable TERMINATE = env -> {};

    public SkinCacheDatastore(Connection[] conn, int threadCount, int keepObjectsDays, int maxObjects, int compressionLevel, boolean sqliteCompatible, ILoggerAdapter logger) throws SQLException {
        this.keepObjectsDays = keepObjectsDays;
        this.maxObjects = maxObjects;
        this.sqliteCompatible = sqliteCompatible;
        this.logger = logger;
        this.skin = new SkinCacheTable("eagler_skins", conn[0], sqliteCompatible, logger, this.dbWriteLock);
        this.cape = new SkinCacheTable("eagler_capes", conn[0], sqliteCompatible, logger, this.dbWriteLock);
        this.disposeLatch = new CountDownLatch(threadCount);
        this.threads = new SkinCacheDatastoreThreadEnv[threadCount];
        for (int i = 0; i < threadCount; ++i) {
            this.threads[i] = new SkinCacheDatastoreThreadEnv(i, compressionLevel, conn[conn.length > 1 ? i : 0]);
        }
    }

    private void execute(SkinCacheDatastoreRunnable runnable) {
        this.databaseQueue.add(runnable);
    }

    @Override
    public void loadSkin(String skinURL, Consumer<byte[]> callback) {
        this.execute(env -> {
            byte[] result;
            try {
                result = this.skin.loadSkin(env.skinEnv, skinURL);
            }
            catch (SQLException ex) {
                this.logger.error("Could not load skin \"" + skinURL + "\" from database!");
                callback.accept(null);
                return;
            }
            if (result != null) {
                byte[] res;
                try {
                    res = this.decompressSkin(env, result, 12288);
                }
                catch (DataFormatException ex) {
                    this.logger.warn("Skin \"" + skinURL + "\" could not be decompressed!");
                    callback.accept(null);
                    return;
                }
                callback.accept(res);
            } else {
                callback.accept(null);
            }
        });
    }

    @Override
    public void loadCape(String capeURL, Consumer<byte[]> callback) {
        this.execute(env -> {
            byte[] result;
            try {
                result = this.cape.loadSkin(env.capeEnv, capeURL);
            }
            catch (SQLException ex) {
                this.logger.error("Could not load cape \"" + capeURL + "\" from database!");
                callback.accept(null);
                return;
            }
            if (result != null) {
                byte[] res;
                try {
                    res = this.decompressSkin(env, result, 1173);
                }
                catch (DataFormatException ex) {
                    this.logger.warn("Cape \"" + capeURL + "\" could not be decompressed!");
                    callback.accept(null);
                    return;
                }
                callback.accept(res);
            } else {
                callback.accept(null);
            }
        });
    }

    private byte[] decompressSkin(SkinCacheDatastoreThreadEnv env, byte[] input, int len) throws DataFormatException {
        if (env.inflater == null && input.length == len) {
            return input;
        }
        byte[] ret = new byte[len];
        env.inflater.reset();
        env.inflater.setInput(input, 0, input.length);
        if (env.inflater.inflate(ret, 0, len) != len) {
            throw new DataFormatException();
        }
        return ret;
    }

    @Override
    public void storeSkin(String skinURL, byte[] data) {
        if (data.length != 12288) {
            throw new IllegalArgumentException("Skin length is not 12288 bytes!");
        }
        this.execute(env -> {
            try {
                this.skin.storeSkin(env.skinEnv, skinURL, this.sha1Digest(env, data), this.compressSkin(env, data));
            }
            catch (IllegalStateException | SQLException e) {
                this.logger.error("Skin \"" + skinURL + "\" could not be stored in the database!", e);
            }
        });
    }

    @Override
    public void storeCape(String capeURL, byte[] data) {
        if (data.length != 1173) {
            throw new IllegalArgumentException("Cape length is not 1173 bytes!");
        }
        this.execute(env -> {
            try {
                this.cape.storeSkin(env.capeEnv, capeURL, this.sha1Digest(env, data), this.compressSkin(env, data));
            }
            catch (IllegalStateException | SQLException e) {
                this.logger.error("Cape \"" + capeURL + "\" could not be stored in the database!", e);
            }
        });
    }

    private byte[] compressSkin(SkinCacheDatastoreThreadEnv env, byte[] data) {
        if (env.deflater == null) {
            return data;
        }
        env.deflater.reset();
        env.deflater.setInput(data, 0, data.length);
        env.deflater.finish();
        int i = env.deflater.deflate(env.compressionTmp, 0, env.compressionTmp.length);
        if (i <= 0) {
            throw new IllegalStateException();
        }
        return Arrays.copyOf(env.compressionTmp, i);
    }

    private byte[] sha1Digest(SkinCacheDatastoreThreadEnv env, byte[] data) {
        env.sha1Digest.update(data);
        return env.sha1Digest.digest();
    }

    @Override
    public void tick() {
        long millisSteady = SteadyTime.millis();
        if (millisSteady - this.lastCleanup > 600000L) {
            this.lastCleanup = millisSteady;
            try {
                this.runCleanup();
            }
            catch (SQLException ex) {
                this.logger.error("Could not clean up skin cache!", ex);
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private synchronized void runCleanup() throws SQLException {
        long millis = System.currentTimeMillis();
        long expiry = millis - (long)this.keepObjectsDays * 86400000L;
        Object object = this.dbWriteLock;
        synchronized (object) {
            this.skin.runCleanup(this.maxObjects, expiry);
            this.cape.runCleanup(this.maxObjects, expiry);
        }
    }

    @Override
    public void dispose() {
        for (int i = 0; i < this.threads.length; ++i) {
            this.databaseQueue.add(TERMINATE);
        }
        try {
            if (!this.disposeLatch.await(10L, TimeUnit.SECONDS)) {
                this.logger.warn("Skin cache datastore workers did not terminate in time");
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.skin.dispose();
        this.cape.dispose();
    }

    static void disposeStmt(PreparedStatement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            }
            catch (SQLException sQLException) {
                // empty catch block
            }
        }
    }

    @Override
    public synchronized int getTotalStoredSkins() {
        return this.skin.countSkins();
    }

    @Override
    public synchronized int getTotalStoredCapes() {
        return this.cape.countSkins();
    }

    private static interface SkinCacheDatastoreRunnable {
        public void run(SkinCacheDatastoreThreadEnv var1);
    }

    private class SkinCacheDatastoreThreadEnv {
        protected final Thread thread;
        protected final SkinCacheTable.SkinCacheTableThreadEnv skinEnv;
        protected final SkinCacheTable.SkinCacheTableThreadEnv capeEnv;
        protected final byte[] compressionTmp;
        protected final Deflater deflater;
        protected final Inflater inflater;
        protected final MessageDigest sha1Digest;

        protected SkinCacheDatastoreThreadEnv(int i, int compressionLevel, Connection conn) throws SQLException {
            this.skinEnv = SkinCacheDatastore.this.skin.createThreadEnv(conn);
            this.capeEnv = SkinCacheDatastore.this.cape.createThreadEnv(conn);
            this.compressionTmp = new byte[65535];
            this.deflater = compressionLevel > 0 ? new Deflater(compressionLevel) : null;
            this.inflater = compressionLevel > 0 ? new Inflater() : null;
            try {
                this.sha1Digest = MessageDigest.getInstance("SHA-1");
            }
            catch (NoSuchAlgorithmException ex) {
                throw new RuntimeException("This JRE does not support SHA-1!", ex);
            }
            this.thread = new Thread(() -> {
                while (true) {
                    try {
                        SkinCacheDatastoreRunnable runnable;
                        while ((runnable = SkinCacheDatastore.this.databaseQueue.take()) != TERMINATE) {
                            runnable.run(this);
                        }
                    }
                    catch (Throwable ex) {
                        if (ex instanceof ThreadDeath) {
                            ThreadDeath exx = (ThreadDeath)ex;
                            throw exx;
                        }
                        SkinCacheDatastore.this.logger.error("Caught exception in worker thread #" + (i + 1), ex);
                        continue;
                    }
                    break;
                }
                this.dispose();
                SkinCacheDatastore.this.disposeLatch.countDown();
            }, "SkinCacheDatastore Thread #" + (i + 1));
            this.thread.setDaemon(true);
            this.thread.start();
        }

        public void dispose() {
            this.skinEnv.dispose();
            this.capeEnv.dispose();
            if (this.deflater != null) {
                this.deflater.end();
            }
            if (this.inflater != null) {
                this.inflater.end();
            }
        }
    }
}

