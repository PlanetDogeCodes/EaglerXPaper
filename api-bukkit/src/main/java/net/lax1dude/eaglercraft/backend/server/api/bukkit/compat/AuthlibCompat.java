/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.Multimap
 *  com.mojang.authlib.GameProfile
 *  com.mojang.authlib.properties.Property
 */
package net.lax1dude.eaglercraft.backend.server.api.bukkit.compat;

import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

public final class AuthlibCompat {
    public static final boolean AUTHLIB_6_PLUS;
    public static final boolean GAMEPROFILE_IS_RECORD;
    private static final MethodHandle MH_NAME;
    private static final MethodHandle MH_VALUE;
    private static final MethodHandle MH_SIGNATURE;
    private static final MethodHandle MH_GAMEPROFILE_GET_PROPERTIES;
    private static final MethodHandle MH_GAMEPROFILE_GET_ID;
    private static final MethodHandle MH_GAMEPROFILE_GET_NAME;

    private AuthlibCompat() {
    }

    public static String getName(Property property) {
        if (property == null) {
            return null;
        }
        try {
            return (String)MH_NAME.invokeExact(property);
        }
        catch (Throwable e) {
            return null;
        }
    }

    public static String getValue(Property property) {
        if (property == null) {
            return null;
        }
        try {
            return (String)MH_VALUE.invokeExact(property);
        }
        catch (Throwable e) {
            return null;
        }
    }

    public static String getSignature(Property property) {
        if (property == null) {
            return null;
        }
        try {
            return (String)MH_SIGNATURE.invokeExact(property);
        }
        catch (Throwable e) {
            return null;
        }
    }

    public static Property createProperty(String name, String value, String signature) {
        return new Property(name, value, signature);
    }

    public static Property createProperty(String name, String value) {
        return new Property(name, value, null);
    }

    public static Multimap<String, Property> getProperties(GameProfile profile) {
        if (profile == null || MH_GAMEPROFILE_GET_PROPERTIES == null) {
            return null;
        }
        try {
            Object result = MH_GAMEPROFILE_GET_PROPERTIES.invoke(profile);
            if (result instanceof Multimap) {
                return (Multimap)result;
            }
            return null;
        }
        catch (Throwable e) {
            return null;
        }
    }

    public static UUID getProfileId(GameProfile profile) {
        if (profile == null || MH_GAMEPROFILE_GET_ID == null) {
            return null;
        }
        try {
            return (UUID)MH_GAMEPROFILE_GET_ID.invoke(profile);
        }
        catch (Throwable e) {
            return null;
        }
    }

    public static String getProfileName(GameProfile profile) {
        if (profile == null || MH_GAMEPROFILE_GET_NAME == null) {
            return null;
        }
        try {
            return (String)MH_GAMEPROFILE_GET_NAME.invoke(profile);
        }
        catch (Throwable e) {
            return null;
        }
    }

    public static Collection<Property> removeAll(Multimap<String, Property> props, String key) {
        if (props == null) {
            return new ArrayList<Property>();
        }
        try {
            return props.removeAll(key);
        }
        catch (Throwable e) {
            return new ArrayList<Property>();
        }
    }

    public static boolean put(Multimap<String, Property> props, String key, Property value) {
        if (props == null) {
            return false;
        }
        try {
            return props.put(key, value);
        }
        catch (Throwable e) {
            return false;
        }
    }

    public static boolean remove(Multimap<String, Property> props, String key, Property value) {
        if (props == null) {
            return false;
        }
        try {
            return props.remove(key, value);
        }
        catch (Throwable e) {
            return false;
        }
    }

    public static Collection<Property> get(Multimap<String, Property> props, String key) {
        if (props == null) {
            return new ArrayList<Property>();
        }
        try {
            return props.get(key);
        }
        catch (Throwable e) {
            return new ArrayList<Property>();
        }
    }

    public static boolean containsKey(Multimap<String, Property> props, String key) {
        if (props == null) {
            return false;
        }
        try {
            return props.containsKey(key);
        }
        catch (Throwable e) {
            return false;
        }
    }

    public static List<Property> filterByNamePrefix(Multimap<String, Property> props, String prefix) {
        ArrayList<Property> result = new ArrayList<Property>();
        if (props == null) {
            return result;
        }
        for (Property p : props.values()) {
            String name = AuthlibCompat.getName(p);
            if (name == null || !name.startsWith(prefix)) continue;
            result.add(p);
        }
        return result;
    }

    public static int removeByNamePrefix(Multimap<String, Property> props, String prefix) {
        if (props == null) {
            return 0;
        }
        int removed = 0;
        Iterator itr = props.values().iterator();
        while (itr.hasNext()) {
            Property p = (Property)itr.next();
            String name = AuthlibCompat.getName(p);
            if (name == null || !name.startsWith(prefix)) continue;
            itr.remove();
            ++removed;
        }
        return removed;
    }

    public static Property getFirst(Multimap<String, Property> props, String key) {
        Collection<Property> coll = AuthlibCompat.get(props, key);
        if (coll == null || coll.isEmpty()) {
            return null;
        }
        return coll.iterator().next();
    }

    public static String getTexturesValue(GameProfile profile) {
        Multimap<String, Property> props = AuthlibCompat.getProperties(profile);
        if (props == null) {
            return null;
        }
        Property tex = AuthlibCompat.getFirst(props, "textures");
        return tex == null ? null : AuthlibCompat.getValue(tex);
    }

    public static String getTexturesSignature(GameProfile profile) {
        Multimap<String, Property> props = AuthlibCompat.getProperties(profile);
        if (props == null) {
            return null;
        }
        Property tex = AuthlibCompat.getFirst(props, "textures");
        return tex == null ? null : AuthlibCompat.getSignature(tex);
    }

    public static void setTextures(Multimap<String, Property> props, String value, String signature) {
        if (props == null) {
            return;
        }
        AuthlibCompat.removeAll(props, "textures");
        AuthlibCompat.put(props, "textures", AuthlibCompat.createProperty("textures", value, signature));
    }

    public static String smokeTest() {
        try {
            Property p = AuthlibCompat.createProperty("smoketest", "value", "signature");
            String n = AuthlibCompat.getName(p);
            String v = AuthlibCompat.getValue(p);
            String s = AuthlibCompat.getSignature(p);
            if (!("smoketest".equals(n) && "value".equals(v) && "signature".equals(s))) {
                return "AuthlibCompat accessor returned wrong values: name=" + n + " value=" + v + " sig=" + s;
            }
            try {
                UUID testId = UUID.randomUUID();
                GameProfile gp = AuthlibCompat.createGameProfileForTest(testId);
                Multimap<String, Property> props = AuthlibCompat.getProperties(gp);
                if (props == null) {
                    return "AuthlibCompat.getProperties(GameProfile) returned null \u2014 GameProfile accessor binding failed";
                }
                if (AuthlibCompat.containsKey(props, "nonexistent_key")) {
                    return "AuthlibCompat.containsKey returned true for nonexistent key (empty GameProfile)";
                }
                UUID profileId = AuthlibCompat.getProfileId(gp);
                String profileName = AuthlibCompat.getProfileName(gp);
                if (!testId.equals(profileId)) {
                    return "AuthlibCompat.getProfileId returned wrong value: expected=" + testId + " got=" + profileId;
                }
                if (!"smoketest".equals(profileName)) {
                    return "AuthlibCompat.getProfileName returned wrong value: expected=smoketest got=" + profileName;
                }
            }
            catch (Throwable t) {
                return "AuthlibCompat GameProfile smoke test failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            }
            return null;
        }
        catch (Throwable t) {
            return "AuthlibCompat smoke test failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private static GameProfile createGameProfileForTest(UUID testId) throws Exception {
        try {
            return new GameProfile(testId, "smoketest");
        }
        catch (Throwable throwable) {
            try {
                Object emptyMap;
                Class<?> propertyMapClass = Class.forName("com.mojang.authlib.properties.PropertyMap");
                try {
                    emptyMap = propertyMapClass.getConstructor(new Class[0]).newInstance(new Object[0]);
                }
                catch (ReflectiveOperationException ex) {
                    emptyMap = null;
                }
                if (emptyMap instanceof Multimap) {
                    return (GameProfile)GameProfile.class.getConstructor(UUID.class, String.class, propertyMapClass).newInstance(testId, "smoketest", emptyMap);
                }
            }
            catch (Throwable throwable2) {
                // empty catch block
            }
            throw new IllegalStateException("No usable GameProfile constructor");
        }
    }

    public static Predicate<Property> nameStartsWith(String prefix) {
        return p -> {
            String n = AuthlibCompat.getName(p);
            return n != null && n.startsWith(prefix);
        };
    }

    static {
        boolean gameProfileIsRecord;
        MethodHandle getPropsMH;
        MethodHandles.Lookup lookup;
        MethodHandle signatureMH;
        MethodHandle valueMH;
        MethodHandle nameMH;
        boolean authlib6;
        try {
            Method signatureMethod;
            Method valueMethod;
            Method nameMethod;
            try {
                nameMethod = Property.class.getMethod("name", new Class[0]);
                valueMethod = Property.class.getMethod("value", new Class[0]);
                signatureMethod = Property.class.getMethod("signature", new Class[0]);
                authlib6 = true;
            }
            catch (NoSuchMethodException e) {
                nameMethod = Property.class.getMethod("getName", new Class[0]);
                valueMethod = Property.class.getMethod("getValue", new Class[0]);
                signatureMethod = Property.class.getMethod("getSignature", new Class[0]);
                authlib6 = false;
            }
            MethodHandles.Lookup lookup2 = MethodHandles.lookup();
            MethodType strType = MethodType.methodType(String.class);
            nameMH = lookup2.unreflect(nameMethod).asType(strType.appendParameterTypes(Property.class));
            valueMH = lookup2.unreflect(valueMethod).asType(strType.appendParameterTypes(Property.class));
            signatureMH = lookup2.unreflect(signatureMethod).asType(strType.appendParameterTypes(Property.class));
        }
        catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError("Could not bind authlib Property accessors: " + e);
        }
        AUTHLIB_6_PLUS = authlib6;
        MH_NAME = nameMH;
        MH_VALUE = valueMH;
        MH_SIGNATURE = signatureMH;
        try {
            lookup = MethodHandles.lookup();
        }
        catch (Exception e) {
            throw new ExceptionInInitializerError("Could not create MethodHandles.lookup: " + e);
        }
        MethodHandle legacyMH = null;
        try {
            legacyMH = lookup.unreflect(GameProfile.class.getMethod("getProperties", new Class[0])).asType(MethodType.methodType(Multimap.class, GameProfile.class));
        }
        catch (NoSuchMethodException strType) {
        }
        catch (ReflectiveOperationException strType) {
        }
        catch (IllegalArgumentException strType) {
            // empty catch block
        }
        if (legacyMH != null) {
            getPropsMH = legacyMH;
            gameProfileIsRecord = false;
        } else {
            MethodHandle recordMH = null;
            try {
                Class<?> propertyMapClass;
                try {
                    propertyMapClass = Class.forName("com.mojang.authlib.properties.PropertyMap");
                }
                catch (ClassNotFoundException cnfe) {
                    propertyMapClass = Object.class;
                }
                recordMH = lookup.findVirtual(GameProfile.class, "properties", MethodType.methodType(propertyMapClass));
            }
            catch (NoSuchMethodException e) {
                recordMH = null;
            }
            catch (ReflectiveOperationException e) {
                recordMH = null;
            }
            if (recordMH != null) {
                getPropsMH = recordMH.asType(MethodType.methodType(Object.class, GameProfile.class));
                gameProfileIsRecord = true;
            } else {
                getPropsMH = null;
                gameProfileIsRecord = false;
            }
        }
        GAMEPROFILE_IS_RECORD = gameProfileIsRecord;
        MH_GAMEPROFILE_GET_PROPERTIES = getPropsMH;
        MethodHandle getIdMH = null;
        MethodHandle getNameMH = null;
        try {
            getIdMH = lookup.findVirtual(GameProfile.class, "getId", MethodType.methodType(UUID.class));
        }
        catch (NoSuchMethodException e) {
            try {
                getIdMH = lookup.findVirtual(GameProfile.class, "id", MethodType.methodType(UUID.class));
            }
            catch (ReflectiveOperationException reflectiveOperationException) {}
        }
        catch (ReflectiveOperationException e) {
            // empty catch block
        }
        try {
            getNameMH = lookup.findVirtual(GameProfile.class, "getName", MethodType.methodType(String.class));
        }
        catch (NoSuchMethodException e) {
            try {
                getNameMH = lookup.findVirtual(GameProfile.class, "name", MethodType.methodType(String.class));
            }
            catch (ReflectiveOperationException reflectiveOperationException) {}
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            // empty catch block
        }
        MH_GAMEPROFILE_GET_ID = getIdMH;
        MH_GAMEPROFILE_GET_NAME = getNameMH;
    }
}

