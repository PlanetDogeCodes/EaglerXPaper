/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base;

public interface IIdentifiedConnection {
    public Object getIdentityToken();

    public static abstract class Base
    implements IIdentifiedConnection {
        public int hashCode() {
            return System.identityHashCode(this.getIdentityToken());
        }

        public boolean equals(Object o) {
            return this == o || o instanceof IIdentifiedConnection && this.getIdentityToken() == ((IIdentifiedConnection)o).getIdentityToken();
        }
    }
}

