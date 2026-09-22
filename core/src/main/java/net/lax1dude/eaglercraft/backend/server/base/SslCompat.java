/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.handler.ssl.SslContext
 */
package net.lax1dude.eaglercraft.backend.server.base;

import io.netty.handler.ssl.SslContext;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLException;

public final class SslCompat {
    private SslCompat() {
    }

    public static boolean isSupported() {
        try {
            Class.forName("io.netty.handler.ssl.SslContextBuilder");
            return true;
        }
        catch (ClassNotFoundException ex) {
            return false;
        }
    }

    public static SslContext forServer(File certChain, File key, String keyPassword) throws SSLException {
        return SslCompat.invoke("forServer", new Class[]{File.class, File.class, String.class}, certChain, key, keyPassword);
    }

    public static SslContext forServer(InputStream certChain, InputStream key, String keyPassword) throws SSLException {
        return SslCompat.invoke("forServer", new Class[]{InputStream.class, InputStream.class, String.class}, certChain, key, keyPassword);
    }

    public static SslContext forServer(PrivateKey key, String keyPassword, X509Certificate[] certChain) throws SSLException {
        return SslCompat.invoke("forServer", new Class[]{PrivateKey.class, String.class, X509Certificate[].class}, key, keyPassword, certChain);
    }

    public static SslContext forClient() throws SSLException {
        return SslCompat.invoke("forClient", new Class[0], new Object[0]);
    }

    private static SslContext invoke(String factory, Class<?>[] paramTypes, Object ... args) throws SSLException {
        try {
            Class<?> builderClass = Class.forName("io.netty.handler.ssl.SslContextBuilder");
            Object builder = builderClass.getMethod(factory, paramTypes).invoke(null, args);
            return (SslContext)builderClass.getMethod("build", new Class[0]).invoke(builder, new Object[0]);
        }
        catch (ClassNotFoundException ex) {
            throw new SSLException("Built-in TLS is not available on this server's Netty version (4.1+ required). Configure a reverse proxy (Caddy, nginx, playit.gg, etc.) to provide wss:// TLS instead.");
        }
        catch (IllegalAccessException ex) {
            throw new SSLException("Failed to access SslContextBuilder", ex);
        }
        catch (NoSuchMethodException ex) {
            throw new SSLException("SslContextBuilder is missing the required method", ex);
        }
        catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof SSLException) {
                throw (SSLException)cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException)cause;
            }
            throw new SSLException("Failed to build SSL context", cause);
        }
    }
}

