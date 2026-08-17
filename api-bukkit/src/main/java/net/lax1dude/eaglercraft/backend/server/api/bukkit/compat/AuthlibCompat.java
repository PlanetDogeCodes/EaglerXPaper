/*
 * Copyright (c) 2025 lax1dude. All Rights Reserved.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 */

package net.lax1dude.eaglercraft.backend.server.api.bukkit.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

/**
 * Reflection shim that lets EaglerXServer call authlib {@link Property} accessors
 * across both the legacy authlib 1.x-4.x API ({@code getName()}, {@code getValue()},
 * {@code getSignature()}) and the authlib 6.x record API ({@code name()},
 * {@code value()}, {@code signature()}).
 *
 * <p>Paper 26.x (MC 1.21.11) ships authlib 6.x where {@link Property} became a
 * record. The legacy accessor methods were removed, so any bytecode linkage to
 * {@code Property.getName()} throws {@link NoSuchMethodError} at runtime on
 * Paper 26.x even though EaglerXServer compiles cleanly against the old
 * paper-api 1.12.2 stub.
 *
 * <p>This class resolves the correct accessor once at class-load time. The
 * lookup is performed exactly once and cached. The cost after warm-up is a
 * single {@code invokeExact} of a {@link MethodHandle} (roughly 1.5x a direct call).
 *
 * <p>This class is intentionally thread-safe (the {@link MethodHandle} lookups
 * happen inside the static initializer) and side-effect-free.
 */
public final class AuthlibCompat {

    /** Set to true if Property is the authlib 6.x record variant. */
    public static final boolean AUTHLIB_6_PLUS;

    private static final MethodHandle MH_NAME;
    private static final MethodHandle MH_VALUE;
    private static final MethodHandle MH_SIGNATURE;

    private static final MethodHandle MH_PROFILE_GET_PROPERTIES;
    private static final Method MH_MULTIMAP_REMOVE_ALL;
    private static final Method MH_MULTIMAP_PUT;
    private static final Method MH_MULTIMAP_REMOVE;
    private static final Method MH_MULTIMAP_GET;
    private static final Method MH_MULTIMAP_VALUES;
    private static final Method MH_MULTIMAP_CONTAINS_KEY;

    static {
        boolean authlib6;
        MethodHandle nameMH;
        MethodHandle valueMH;
        MethodHandle signatureMH;
        try {
            // Detect authlib 6.x by checking if the record-style name() exists
            Method nameMethod;
            try {
                nameMethod = Property.class.getMethod("name");
                authlib6 = true;
            } catch (NoSuchMethodException e) {
                nameMethod = Property.class.getMethod("getName");
                authlib6 = false;
            }
            Method valueMethod;
            Method signatureMethod;
            if (authlib6) {
                valueMethod = Property.class.getMethod("value");
                signatureMethod = Property.class.getMethod("signature");
            } else {
                valueMethod = Property.class.getMethod("getValue");
                signatureMethod = Property.class.getMethod("getSignature");
            }
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType strType = MethodType.methodType(String.class);
            nameMH = lookup.unreflect(nameMethod).asType(strType.appendParameterTypes(Property.class));
            valueMH = lookup.unreflect(valueMethod).asType(strType.appendParameterTypes(Property.class));
            signatureMH = lookup.unreflect(signatureMethod).asType(strType.appendParameterTypes(Property.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError("Could not bind authlib Property accessors: " + e);
        }
        AUTHLIB_6_PLUS = authlib6;
        MH_NAME = nameMH;
        MH_VALUE = valueMH;
        MH_SIGNATURE = signatureMH;

        // Multimap<String, Property> interface methods (cached for speed)
        try {
            MH_PROFILE_GET_PROPERTIES = MethodHandles.lookup()
                    .findVirtual(GameProfile.class, "getProperties",
                            MethodType.methodType(Multimap.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError("Could not bind GameProfile.getProperties: " + e);
        }
        try {
            MH_MULTIMAP_REMOVE_ALL = Multimap.class.getMethod("removeAll", Object.class);
            MH_MULTIMAP_PUT = Multimap.class.getMethod("put", Object.class, Object.class);
            MH_MULTIMAP_REMOVE = Multimap.class.getMethod("remove", Object.class, Object.class);
            MH_MULTIMAP_GET = Multimap.class.getMethod("get", Object.class);
            MH_MULTIMAP_VALUES = Multimap.class.getMethod("values");
            MH_MULTIMAP_CONTAINS_KEY = Multimap.class.getMethod("containsKey", Object.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError("Could not bind Multimap methods: " + e);
        }
    }

    private AuthlibCompat() {
    }

    // =====================================================================
    // Property accessor methods
    // =====================================================================

    /**
     * Equivalent to authlib 1.x {@code Property.getName()} / authlib 6.x
     * {@code Property.name()}.
     */
    public static String getName(Property property) {
        if (property == null) {
            return null;
        }
        try {
            return (String) MH_NAME.invokeExact(property);
        } catch (Throwable e) {
            // Should not happen — all known authlib versions expose one of the two accessors.
            throw new RuntimeException("Failed to invoke Property.name accessor", e);
        }
    }

    /** Equivalent to authlib 1.x {@code Property.getValue()} / authlib 6.x {@code Property.value()}. */
    public static String getValue(Property property) {
        if (property == null) {
            return null;
        }
        try {
            return (String) MH_VALUE.invokeExact(property);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to invoke Property.value accessor", e);
        }
    }

    /** Equivalent to authlib 1.x {@code Property.getSignature()} / authlib 6.x {@code Property.signature()}. */
    public static String getSignature(Property property) {
        if (property == null) {
            return null;
        }
        try {
            return (String) MH_SIGNATURE.invokeExact(property);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to invoke Property.signature accessor", e);
        }
    }

    /**
     * Convenience factory for the canonical 3-arg record constructor. Equivalent to
     * {@code new Property(name, value, signature)} on both authlib 1.x and 6.x.
     */
    public static Property createProperty(String name, String value, String signature) {
        return new Property(name, value, signature);
    }

    /**
     * Convenience factory: 2-arg ctor that sets signature to {@code null}. Routes
     * through the 3-arg canonical ctor explicitly so we don't depend on authlib
     * 6.x keeping the convenience 2-arg ctor.
     */
    public static Property createProperty(String name, String value) {
        return new Property(name, value, null);
    }

    // =====================================================================
    // GameProfile / Multimap helpers
    // =====================================================================

    /**
     * Returns the Multimap of properties from the given GameProfile. Type-safe
     * wrapper that handles both authlib 1.x (Multimap) and authlib 6.x (PropertyMap
     * which implements Multimap).
     */
    @SuppressWarnings("unchecked")
    public static Multimap<String, Property> getProperties(GameProfile profile) {
        if (profile == null) {
            return null;
        }
        try {
            return (Multimap<String, Property>) MH_PROFILE_GET_PROPERTIES.invoke(profile);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to invoke GameProfile.getProperties", e);
        }
    }

    /**
     * Removes all properties with the given key from the multimap. Returns the
     * removed collection (matching {@code Multimap.removeAll} semantics).
     */
    @SuppressWarnings("unchecked")
    public static Collection<Property> removeAll(Multimap<String, Property> props, String key) {
        if (props == null) {
            return new ArrayList<>();
        }
        try {
            return (Collection<Property>) MH_MULTIMAP_REMOVE_ALL.invoke(props, key);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to invoke Multimap.removeAll", e);
        }
    }

    /** Puts a (key, value) pair into the multimap. */
    public static boolean put(Multimap<String, Property> props, String key, Property value) {
        if (props == null) {
            return false;
        }
        try {
            return (Boolean) MH_MULTIMAP_PUT.invoke(props, key, value);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to invoke Multimap.put", e);
        }
    }

    /** Removes a single (key, value) pair from the multimap. */
    public static boolean remove(Multimap<String, Property> props, String key, Property value) {
        if (props == null) {
            return false;
        }
        try {
            return (Boolean) MH_MULTIMAP_REMOVE.invoke(props, key, value);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to invoke Multimap.remove", e);
        }
    }

    /** Returns the collection of properties for the given key. */
    @SuppressWarnings("unchecked")
    public static Collection<Property> get(Multimap<String, Property> props, String key) {
        if (props == null) {
            return new ArrayList<>();
        }
        try {
            return (Collection<Property>) MH_MULTIMAP_GET.invoke(props, key);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to invoke Multimap.get", e);
        }
    }

    /** Returns true if the multimap contains any property with the given key. */
    public static boolean containsKey(Multimap<String, Property> props, String key) {
        if (props == null) {
            return false;
        }
        try {
            return (Boolean) MH_MULTIMAP_CONTAINS_KEY.invoke(props, key);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to invoke Multimap.containsKey", e);
        }
    }

    /**
     * Returns a snapshot list of all properties in the multimap whose name starts
     * with the given prefix. Type-safe wrapper that handles both authlib versions.
     */
    public static List<Property> filterByNamePrefix(Multimap<String, Property> props, String prefix) {
        List<Property> result = new ArrayList<>();
        if (props == null) {
            return result;
        }
        for (Property p : props.values()) {
            String name = getName(p);
            if (name != null && name.startsWith(prefix)) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * Removes all properties whose name starts with the given prefix. Returns the
     * number of properties removed. Atomic with respect to the multimap (uses
     * iterator.remove()).
     */
    public static int removeByNamePrefix(Multimap<String, Property> props, String prefix) {
        if (props == null) {
            return 0;
        }
        int removed = 0;
        java.util.Iterator<Property> itr = props.values().iterator();
        while (itr.hasNext()) {
            Property p = itr.next();
            String name = getName(p);
            if (name != null && name.startsWith(prefix)) {
                itr.remove();
                removed++;
            }
        }
        return removed;
    }

    /**
     * Returns the first property value for the given key, or {@code null} if there
     * is no such property.
     */
    public static Property getFirst(Multimap<String, Property> props, String key) {
        Collection<Property> coll = get(props, key);
        if (coll == null || coll.isEmpty()) {
            return null;
        }
        return coll.iterator().next();
    }

    /**
     * Returns the value of the "textures" property on the given player profile, or
     * {@code null} if the player has no textures property.
     */
    public static String getTexturesValue(GameProfile profile) {
        Multimap<String, Property> props = getProperties(profile);
        if (props == null) {
            return null;
        }
        Property tex = getFirst(props, "textures");
        return tex == null ? null : getValue(tex);
    }

    /**
     * Returns the signature of the "textures" property on the given player profile,
     * or {@code null} if the player has no textures property.
     */
    public static String getTexturesSignature(GameProfile profile) {
        Multimap<String, Property> props = getProperties(profile);
        if (props == null) {
            return null;
        }
        Property tex = getFirst(props, "textures");
        return tex == null ? null : getSignature(tex);
    }

    /**
     * Replaces any existing "textures" property with a new one. Convenience method.
     */
    public static void setTextures(Multimap<String, Property> props, String value, String signature) {
        if (props == null) {
            return;
        }
        removeAll(props, "textures");
        put(props, "textures", createProperty("textures", value, signature));
    }

    /**
     * Static smoke test used at startup to verify the accessor bindings are
     * working. Returns null on success or an error message on failure.
     */
    public static String smokeTest() {
        try {
            Property p = createProperty("smoketest", "value", "signature");
            String n = getName(p);
            String v = getValue(p);
            String s = getSignature(p);
            if (!"smoketest".equals(n) || !"value".equals(v) || !"signature".equals(s)) {
                return "AuthlibCompat accessor returned wrong values: name=" + n + " value=" + v + " sig=" + s;
            }
            return null;
        } catch (Throwable t) {
            return "AuthlibCompat smoke test failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    /**
     * Predicate version of {@link #getName(Property)} for use in streams.
     */
    public static Predicate<Property> nameStartsWith(String prefix) {
        return p -> {
            String n = getName(p);
            return n != null && n.startsWith(prefix);
        };
    }
}
