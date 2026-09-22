/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.buffer.ByteBuf
 *  io.netty.buffer.ByteBufOutputStream
 *  io.netty.buffer.Unpooled
 */
package net.lax1dude.eaglercraft.backend.server.base;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.function.Consumer;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformScheduler;
import net.lax1dude.eaglercraft.backend.skin_cache.IHTTPClient;

public class LegacyInternalHTTPClient
implements IHTTPClient {
    private final IPlatformScheduler scheduler;
    private final String userAgent;

    public LegacyInternalHTTPClient(IPlatformScheduler scheduler, String userAgent) {
        this.scheduler = scheduler;
        this.userAgent = userAgent;
    }

    @Override
    public void asyncRequest(String method, URI uri, Consumer<IHTTPClient.Response> responseCallback) {
        URL url;
        String scheme = uri.getScheme();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            responseCallback.accept(new IHTTPClient.Response(new UnsupportedOperationException("Unsupported scheme: " + scheme)));
            return;
        }
        try {
            url = uri.toURL();
        }
        catch (MalformedURLException ex) {
            responseCallback.accept(new IHTTPClient.Response(ex));
            return;
        }
        this.scheduler.executeAsync(() -> {
            IHTTPClient.Response res;
            try {
                HttpURLConnection conn = (HttpURLConnection)url.openConnection();
                conn.addRequestProperty("user-agent", this.userAgent);
                conn.connect();
                ByteBuf buf = Unpooled.buffer((int)1024);
                try {
                    try (InputStream is = conn.getInputStream();){
                        int read;
                        ByteBufOutputStream os = new ByteBufOutputStream(buf);
                        byte[] buffer = new byte[8192];
                        while ((read = is.read(buffer)) != -1) {
                            os.write(buffer, 0, read);
                        }
                    }
                    int responseCode = conn.getResponseCode();
                    res = new IHTTPClient.Response(responseCode, false, buf.retain());
                }
                finally {
                    buf.release();
                    conn.disconnect();
                }
            }
            catch (IOException ex) {
                responseCallback.accept(new IHTTPClient.Response(ex));
                return;
            }
            responseCallback.accept(res);
        });
    }
}

