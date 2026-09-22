/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.config;

import java.lang.reflect.InvocationTargetException;

public class ReflectUtil {
    public static RuntimeException propagateReflectThrowable(Exception ex) {
        if (ex instanceof InvocationTargetException) {
            InvocationTargetException exx = (InvocationTargetException)ex;
            Throwable cause = exx.getCause();
            if (cause != null) {
                if (cause instanceof RuntimeException) {
                    RuntimeException cause2 = (RuntimeException)cause;
                    return cause2;
                }
                return new RuntimeException("Encountered an InvocationTargetException while performing reflection", cause);
            }
        } else if (ex instanceof RuntimeException) {
            RuntimeException exx = (RuntimeException)ex;
            return exx;
        }
        return new RuntimeException("Could not perform reflection!", ex);
    }
}

