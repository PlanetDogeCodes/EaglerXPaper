/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.buffer.ByteBuf
 */
package net.lax1dude.eaglercraft.backend.server.util;

import io.netty.buffer.ByteBuf;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class Util {
    public static final byte[] ZERO_BYTES = new byte[0];
    private static final String hex = "0123456789abcdef";

    public static byte[] newByteArray(int len) {
        return len > 0 ? new byte[len] : ZERO_BYTES;
    }

    public static long steadyTime() {
        return System.nanoTime() / 1000000L;
    }

    public static RuntimeException propagateReflectThrowable(Exception ex) {
        if (ex instanceof InvocationTargetException) {
            InvocationTargetException exx = (InvocationTargetException)ex;
            Throwable cause = exx.getCause();
            if (cause != null) {
                if (cause instanceof RuntimeException) {
                    return (RuntimeException)cause;
                }
                return new RuntimeException("Encountered an InvocationTargetException while performing reflection", cause);
            }
        } else if (ex instanceof RuntimeException) {
            return (RuntimeException)ex;
        }
        return new RuntimeException("Could not perform reflection!", ex);
    }

    public static RuntimeException propagateInvokeThrowable(Throwable ex) {
        if (ex instanceof RuntimeException) {
            return (RuntimeException)ex;
        }
        if (ex instanceof Error) {
            throw (Error)ex;
        }
        return new RuntimeException("Could not invoke method handle!", ex);
    }

    public static Field findDeclaredField(Class<?> clz, String name) throws NoSuchFieldException {
        Class<?> clz0 = clz;
        while (clz0 != Object.class) {
            try {
                return clz0.getDeclaredField(name);
            }
            catch (NoSuchFieldException noSuchFieldException) {
                clz0 = clz0.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field \"" + name + "\" not found in " + clz.getName() + " (or parents)");
    }

    public static Method findDeclaredMethod(Class<?> clz, String name, Class<?> ... params) throws NoSuchMethodException {
        Class<?> clz0 = clz;
        do {
            try {
                Method meth = clz0.getDeclaredMethod(name, params);
                if ((meth.getModifiers() & 0x400) == 0) {
                    return meth;
                }
            }
            catch (NoSuchMethodException noSuchMethodException) {
            }
        } while ((clz0 = clz0.getSuperclass()) != Object.class);
        throw new NoSuchMethodException("Method \"" + name + "\" not found in " + clz.getName() + " (or parents)");
    }

    public static byte[] sha1(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(input);
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 is not supported on this JRE!", e);
        }
    }

    public static MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 is not supported on this JRE!", e);
        }
    }

    public static String hash2string(byte[] b) {
        char[] ret = new char[b.length * 2];
        for (int i = 0; i < b.length; ++i) {
            int bb = b[i] & 0xFF;
            ret[i * 2] = hex.charAt(bb >> 4 & 0xF);
            ret[i * 2 + 1] = hex.charAt(bb & 0xF);
        }
        return new String(ret);
    }

    public static String sanitizeJDBCURIForLogs(String uri) {
        if (uri == null) {
            return null;
        }
        int i = uri.indexOf("password=", 0);
        if (i == -1) {
            i = uri.indexOf("pwd=", 0);
        }
        if (i != -1) {
            int j = uri.indexOf(59, i);
            if (j == -1) {
                j = uri.length();
            }
            return uri.substring(0, i) + uri.substring(i, i + 9) + "***" + uri.substring(j);
        }
        return uri;
    }

    public static UUID createUUIDFromUndashed(String str) {
        if (str.length() != 32) {
            throw new IllegalArgumentException("Invalid UUID string length: " + str.length() + " != 32");
        }
        return new UUID(Long.parseUnsignedLong(str.substring(0, 16), 16), Long.parseUnsignedLong(str.substring(16), 16));
    }

    public static CharSequence toUUIDStringUndashed(UUID uuid) {
        String str = uuid.toString();
        StringBuilder builder = new StringBuilder(32);
        builder.append(str, 0, 8);
        builder.append(str, 9, 13);
        builder.append(str, 14, 18);
        builder.append(str, 19, 23);
        builder.append(str, 24, 36);
        return builder;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void dumpByteBuf(ByteBuf buf, int maxLen) {
        buf.markReaderIndex();
        try {
            StringBuilder builderA = new StringBuilder();
            StringBuilder builderB = new StringBuilder();
            int i = 0;
            while (i < maxLen && buf.isReadable()) {
                short val = buf.readUnsignedByte();
                builderA.append(hex.charAt(val >>> 4));
                builderA.append(hex.charAt(val & 0xF));
                builderA.append(' ');
                if (val == 0) {
                    builderB.append("\\0 ");
                } else if (val == 10) {
                    builderB.append("\\n ");
                } else if (val == 13) {
                    builderB.append("\\r ");
                } else if (val == 9) {
                    builderB.append("\\t ");
                } else {
                    builderB.append((char)val);
                    builderB.append(' ');
                }
                if (++i % 8 != 0) continue;
                System.out.println(builderA + "  " + builderB);
                builderA = new StringBuilder();
                builderB = new StringBuilder();
            }
            if (builderA.length() > 0) {
                System.out.println(builderA + "  " + builderB);
            }
        }
        finally {
            buf.resetReaderIndex();
        }
    }

    public static boolean classExists(String string) {
        try {
            Class.forName(string);
            return true;
        }
        catch (ClassNotFoundException ex) {
            return false;
        }
    }
}

