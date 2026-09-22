/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.update;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.lax1dude.eaglercraft.backend.server.adapter.AbortLoadException;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformTask;
import net.lax1dude.eaglercraft.backend.server.api.IUpdateCertificate;
import net.lax1dude.eaglercraft.backend.server.api.SHA1Sum;
import net.lax1dude.eaglercraft.backend.server.base.EaglerPlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServer;
import net.lax1dude.eaglercraft.backend.server.base.config.ConfigDataSettings;
import net.lax1dude.eaglercraft.backend.server.base.update.IUpdateCertificateImpl;
import net.lax1dude.eaglercraft.backend.server.base.update.UpdateCertificate;
import net.lax1dude.eaglercraft.backend.server.base.update.UpdateCertificateMultiset;
import net.lax1dude.eaglercraft.backend.server.base.update.UpdateServiceLoop;
import net.lax1dude.eaglercraft.backend.server.util.Util;

public class UpdateService {
    private final EaglerXServer<?> server;
    private final ConfigDataSettings.ConfigDataUpdateService config;
    private final File eagcertFolder;
    private final boolean loginPacketCerts;
    private final UpdateServiceLoop loop;
    private final UpdateCertificateMultiset certSet;
    private IPlatformTask task;
    private long lastDownload = 0L;
    private Map<String, CachedClientCertificate> certsCache = Collections.emptyMap();

    public UpdateService(EaglerXServer<?> server) {
        this.server = server;
        this.config = server.getConfig().getSettings().getUpdateService();
        boolean bl = this.loginPacketCerts = !this.config.isDiscardLoginPacketCerts();
        if (this.config.isEnableEagcertFolder()) {
            this.eagcertFolder = new File(server.getPlatform().getDataFolder(), "eagcert");
            if (!this.eagcertFolder.isDirectory() && !this.eagcertFolder.mkdirs()) {
                throw new AbortLoadException("Could not create folder: " + this.eagcertFolder.getAbsolutePath());
            }
        } else {
            this.eagcertFolder = null;
        }
        this.certSet = new UpdateCertificateMultiset();
        this.loop = new UpdateServiceLoop(server.getPlatform().getScheduler(), this.config.getCertPacketDataRateLimit());
    }

    public IUpdateCertificateImpl createUpdateCertificate(EaglerPlayerInstance<?> player, byte[] updateCertData) {
        if (!player.isUpdateSystemSupported()) {
            return null;
        }
        if (updateCertData != null && updateCertData.length > 0) {
            IUpdateCertificateImpl newCert = UpdateCertificate.intern(updateCertData);
            this.certSet.dump(cert -> {
                if (cert != newCert) {
                    player.offerUpdateCertificate((IUpdateCertificate)cert);
                }
            });
            if (this.loginPacketCerts && this.certSet.add(newCert)) {
                this.server.forEachEaglerPlayerInternal(player2 -> player2.offerUpdateCertificate(newCert));
            }
            return newCert;
        }
        this.certSet.dump(player::offerUpdateCertificate);
        return null;
    }

    public void removeUpdateCertificate(EaglerPlayerInstance<?> player) {
        IUpdateCertificateImpl cert;
        if (this.loginPacketCerts && (cert = player.getUpdateCertificate()) != null) {
            this.certSet.remove(cert);
        }
    }

    public void start() {
        this.cancelTask();
        if (this.eagcertFolder != null) {
            this.task = this.server.getPlatform().getScheduler().executeAsyncRepeatingTask(this::update, 0L, 10000L);
        }
        this.loop.start();
    }

    private void update() {
        List<IUpdateCertificateImpl> newCerts;
        long now;
        if (this.config.isDownloadLatestCerts() && !this.config.getDownloadCertsFrom().isEmpty() && (now = Util.steadyTime()) - this.lastDownload > (long)this.config.getCheckForUpdateEvery() * 1000L) {
            this.lastDownload = now;
            this.download();
        }
        if ((newCerts = this.enumerate()) != null && !newCerts.isEmpty()) {
            this.server.forEachEaglerPlayerInternal(player -> {
                int l = newCerts.size();
                for (int i = 0; i < l; ++i) {
                    player.offerUpdateCertificate((IUpdateCertificate)newCerts.get(i));
                }
            });
        }
    }

    private void download() {
        List<URI> lst = this.config.getDownloadCertsFrom();
        DownloadListener listener = new DownloadListener(lst.size());
        for (URI uri : lst) {
            this.server.getInternalHTTPClient().asyncRequest("GET", uri, res -> {
                try {
                    if (res.exception == null) {
                        if (res.code >= 200 && res.code < 300) {
                            if (res.data != null && res.data.readableBytes() > 0) {
                                this.server.logger().info("Refreshed update certificate: " + uri);
                                byte[] data = new byte[res.data.readableBytes()];
                                res.data.readBytes(data);
                                listener.accept(data);
                            } else {
                                this.server.logger().warn("Received empty response from: " + uri);
                                listener.accept(null);
                            }
                        } else {
                            this.server.logger().error("Received response code " + res.code + " from: " + uri);
                            listener.accept(null);
                        }
                    } else {
                        this.server.logger().error("Could not send request to: " + uri, res.exception);
                        listener.accept(null);
                    }
                }
                finally {
                    if (res.data != null) {
                        res.data.release();
                    }
                }
            });
        }
    }

    private synchronized List<IUpdateCertificateImpl> completeDownload(List<byte[]> results) {
        long millis = System.currentTimeMillis();
        HashSet<String> managedNames = new HashSet<String>();
        ArrayList<IUpdateCertificateImpl> broadcastList = null;
        for (byte[] arr : results) {
            SHA1Sum sum = SHA1Sum.ofData(arr);
            String name = "$dl." + sum.toString() + ".cert";
            managedNames.add(name);
            File file = new File(this.eagcertFolder, name);
            if (file.isFile()) continue;
            try (FileOutputStream os = new FileOutputStream(file);){
                ((OutputStream)os).write(arr);
            }
            catch (IOException ex) {
                this.server.logger().error("Could not write update certificate file: " + file.getAbsolutePath(), ex);
            }
            if (this.certsCache.containsKey(name)) continue;
            IUpdateCertificateImpl ch = UpdateCertificate.internUnsafe(sum, arr);
            long l = file.lastModified();
            if (l == 0L) {
                l = millis;
            }
            if (this.certsCache == Collections.EMPTY_MAP) {
                this.certsCache = new HashMap<String, CachedClientCertificate>();
            }
            this.certsCache.put(name, new CachedClientCertificate(ch, l));
            if (!this.certSet.add(ch)) continue;
            if (broadcastList == null) {
                broadcastList = new ArrayList<IUpdateCertificateImpl>();
            }
            broadcastList.add(ch);
        }
        File[] dirList = this.eagcertFolder.listFiles();
        if (dirList == null) {
            this.server.logger().error("Could not enumerate directory: " + this.eagcertFolder.getAbsolutePath());
            return broadcastList;
        }
        for (int i = 0; i < dirList.length; ++i) {
            File f = dirList[i];
            String n = f.getName();
            if (!n.startsWith("$dl.") || managedNames.contains(n) || millis - f.lastModified() <= 86400000L) continue;
            this.server.logger().warn("Deleting stale certificate: " + n);
            if (f.delete()) continue;
            this.server.logger().error("Failed to delete: " + n);
        }
        return broadcastList;
    }

    private synchronized List<IUpdateCertificateImpl> enumerate() {
        File[] dirList = this.eagcertFolder.listFiles();
        if (dirList == null) {
            this.server.logger().error("Could not enumerate directory: " + this.eagcertFolder.getAbsolutePath());
            return null;
        }
        boolean dirty = false;
        HashMap<String, CachedClientCertificate> certs = new HashMap<String, CachedClientCertificate>();
        HashMap<String, CachedClientCertificate> oldCerts = new HashMap<String, CachedClientCertificate>(this.certsCache);
        for (int i = 0; i < dirList.length; ++i) {
            File f = dirList[i];
            String string = f.getName();
            long lastModified = f.lastModified();
            CachedClientCertificate cc = (CachedClientCertificate)oldCerts.remove(string);
            if (cc != null) {
                if (cc.lastModified != lastModified) {
                    dirty = true;
                    this.loadCert(certs, f, lastModified);
                    continue;
                }
                certs.put(string, cc);
                continue;
            }
            dirty = true;
            this.loadCert(certs, f, lastModified);
        }
        if (!dirty && oldCerts.isEmpty()) {
            return null;
        }
        ArrayList<IUpdateCertificateImpl> broadcastList = null;
        for (Map.Entry<String, CachedClientCertificate> entry : this.certsCache.entrySet()) {
            CachedClientCertificate oldCert = entry.getValue();
            CachedClientCertificate newCert = (CachedClientCertificate)certs.get(entry.getKey());
            if (newCert == null) {
                this.server.logger().warn("Update certificate was deleted: " + entry.getKey());
                this.certSet.remove(oldCert.certificate);
                continue;
            }
            if (newCert.certificate == oldCert.certificate) continue;
            this.server.logger().warn("Update certificate was modified: " + entry.getKey());
            this.certSet.remove(oldCert.certificate);
            if (!this.certSet.add(newCert.certificate)) continue;
            if (broadcastList == null) {
                broadcastList = new ArrayList();
            }
            broadcastList.add(newCert.certificate);
        }
        for (Map.Entry<String, CachedClientCertificate> entry : certs.entrySet()) {
            if (this.certsCache.containsKey(entry.getKey())) continue;
            this.server.logger().warn("Update certificate was loaded: " + entry.getKey());
            CachedClientCertificate newCert = entry.getValue();
            if (!this.certSet.add(newCert.certificate)) continue;
            if (broadcastList == null) {
                broadcastList = new ArrayList<IUpdateCertificateImpl>();
            }
            broadcastList.add(newCert.certificate);
        }
        this.certsCache = !certs.isEmpty() ? certs : Collections.emptyMap();
        return broadcastList;
    }

    private void loadCert(Map<String, CachedClientCertificate> certs, File file, long lastModified) {
        try {
            byte[] fileData;
            String n = file.getName();
            if (file.length() > 32750L) {
                throw new IOException("File is too long! Max: 32750 bytes");
            }
            try (FileInputStream fis = new FileInputStream(file);){
                int read;
                ByteArrayOutputStream bao = new ByteArrayOutputStream((int)file.length());
                byte[] buffer = new byte[8192];
                while ((read = fis.read(buffer)) != -1) {
                    bao.write(buffer, 0, read);
                }
                fileData = bao.toByteArray();
            }
            if (fileData.length > 32750) {
                throw new IOException("File is too long! Max: 32750 bytes");
            }
            IUpdateCertificateImpl ch = UpdateCertificate.intern(fileData);
            certs.put(n, new CachedClientCertificate(ch, lastModified));
            this.server.logger().info("Reloaded certificate: " + file.getAbsolutePath());
        }
        catch (IOException ex) {
            this.server.logger().error("Failed to read: " + file.getAbsolutePath());
            this.server.logger().error("Reason: " + ex);
        }
    }

    public void stop() {
        this.cancelTask();
        this.loop.stop();
    }

    private void cancelTask() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    public Collection<IUpdateCertificate> dumpAllCerts() {
        return (Collection<IUpdateCertificate>)(Object)this.certSet.dump();
    }

    public UpdateServiceLoop getLoop() {
        return this.loop;
    }

    private class DownloadListener {
        private int cnt;
        private List<byte[]> results = new ArrayList<byte[]>();

        private DownloadListener(int cnt) {
            this.cnt = cnt;
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        private void accept(byte[] data) {
            List<byte[]> res;
            DownloadListener downloadListener = this;
            synchronized (downloadListener) {
                if (this.results == null) {
                    return;
                }
                if (data != null) {
                    this.results.add(data);
                }
                if (--this.cnt != 0) {
                    return;
                }
                res = this.results;
                this.results = null;
            }
            if (res != null && !res.isEmpty()) {
                UpdateService.this.server.getPlatform().getScheduler().executeAsync(() -> {
                    List lst = UpdateService.this.completeDownload(res);
                    if (lst != null && !lst.isEmpty()) {
                        UpdateService.this.server.forEachEaglerPlayerInternal(player -> {
                            int l = lst.size();
                            for (int i = 0; i < l; ++i) {
                                player.offerUpdateCertificate((IUpdateCertificate)lst.get(i));
                            }
                        });
                    }
                });
            }
        }
    }

    private static class CachedClientCertificate {
        protected final IUpdateCertificateImpl certificate;
        protected final long lastModified;

        protected CachedClientCertificate(IUpdateCertificateImpl certificate, long lastModified) {
            this.certificate = certificate;
            this.lastModified = lastModified;
        }
    }
}

