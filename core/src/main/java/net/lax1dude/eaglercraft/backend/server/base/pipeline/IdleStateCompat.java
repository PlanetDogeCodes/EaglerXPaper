/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.handler.timeout.IdleStateHandler
 */
package net.lax1dude.eaglercraft.backend.server.base.pipeline;

import io.netty.handler.timeout.IdleStateHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class IdleStateCompat {
    private static final Method RESET_READ_TIMEOUT;
    private static final Method RESET_WRITE_TIMEOUT;

    private IdleStateCompat() {
    }

    public static void resetReadTimeout(IdleStateHandler handler) {
        IdleStateCompat.reset(handler, RESET_READ_TIMEOUT);
    }

    public static void resetWriteTimeout(IdleStateHandler handler) {
        IdleStateCompat.reset(handler, RESET_WRITE_TIMEOUT);
    }

    private static void reset(IdleStateHandler handler, Method method) {
        if (handler == null || method == null) {
            return;
        }
        try {
            method.invoke((Object)handler, new Object[0]);
        }
        catch (IllegalAccessException illegalAccessException) {
        }
        catch (InvocationTargetException invocationTargetException) {
            // empty catch block
        }
    }

    static {
        Method read = null;
        Method write = null;
        try {
            read = IdleStateHandler.class.getMethod("resetReadTimeout", new Class[0]);
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            // empty catch block
        }
        try {
            write = IdleStateHandler.class.getMethod("resetWriteTimeout", new Class[0]);
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            // empty catch block
        }
        RESET_READ_TIMEOUT = read;
        RESET_WRITE_TIMEOUT = write;
    }
}

