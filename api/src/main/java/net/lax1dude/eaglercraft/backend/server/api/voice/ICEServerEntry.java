/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nonnull
 *  javax.annotation.Nullable
 */
package net.lax1dude.eaglercraft.backend.server.api.voice;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ICEServerEntry {
    private final String uri;
    private final boolean auth;
    private final String username;
    private final String password;

    @Nonnull
    public static ICEServerEntry create(@Nonnull String uri) {
        return new ICEServerEntry(ICEServerEntry.validate(uri, "uri"), false, null, null);
    }

    @Nonnull
    public static ICEServerEntry create(@Nonnull String uri, @Nonnull String username, @Nonnull String password) {
        return new ICEServerEntry(ICEServerEntry.validate(uri, "uri"), true, ICEServerEntry.validate(username, "username"), ICEServerEntry.validate(password, "password"));
    }

    private static String validate(String str, String name) {
        if (str == null) {
            throw new NullPointerException(name + " cannot be null");
        }
        if (str.indexOf(59) != -1) {
            throw new IllegalArgumentException("Illegal semicolon in " + name);
        }
        return str;
    }

    private ICEServerEntry(String uri, boolean auth, String username, String password) {
        this.uri = uri;
        this.auth = auth;
        this.username = username;
        this.password = password;
    }

    @Nonnull
    public String getURI() {
        return this.uri;
    }

    public boolean isAuthenticated() {
        return this.auth;
    }

    @Nullable
    public String getUsername() {
        return this.username;
    }

    @Nullable
    public String getPassword() {
        return this.password;
    }

    @Nonnull
    public String toString() {
        return this.auth ? this.uri + ';' + this.username + ';' + this.password : this.uri;
    }

    public int hashCode() {
        int result = 1;
        result = 31 * result + (this.auth ? 1231 : 1237);
        result = 31 * result + this.uri.hashCode();
        result = 31 * result + (this.auth ? this.password.hashCode() : 0);
        result = 31 * result + (this.auth ? this.username.hashCode() : 0);
        return result;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ICEServerEntry)) {
            return false;
        }
        ICEServerEntry other = (ICEServerEntry)obj;
        if (this.auth != other.auth) {
            return false;
        }
        if (!this.uri.equals(other.uri)) {
            return false;
        }
        if (this.auth) {
            if (!this.password.equals(other.password)) {
                return false;
            }
            if (!this.username.equals(other.username)) {
                return false;
            }
        }
        return true;
    }
}

