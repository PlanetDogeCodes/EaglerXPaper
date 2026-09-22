/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.util.ReferenceCounted
 */
package net.lax1dude.eaglercraft.backend.supervisor.protocol.util;

import io.netty.util.ReferenceCounted;
import java.lang.reflect.Method;

final class TouchCompat {
    private static final Method TOUCH_HINT;

    TouchCompat() {
    }

    static void touch(ReferenceCounted refcounted, Object hint) {
        if (TOUCH_HINT == null || refcounted == null) {
            return;
        }
        try {
            TOUCH_HINT.invoke((Object)refcounted, hint);
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            // empty catch block
        }
    }

    static {
        Method m = null;
        try {
            m = ReferenceCounted.class.getMethod("touch", Object.class);
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            // empty catch block
        }
        TOUCH_HINT = m;
    }
}

