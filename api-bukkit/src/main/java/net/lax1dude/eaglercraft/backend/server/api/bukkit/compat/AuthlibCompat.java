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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

/**
 * Reflection shim that lets EaglerXServer call authlib {@link GameProfile} and
 * {@link Property} accessors across three different authlib generations:
 *
 * <ul>
 * <li><b>authlib 1.x–4.x (MC 1.12–1.20.3)</b>: classic POJO classes. GameProfile has
 *     {@code getProperties()} returning {@code Multimap<String, Property>}. Property has
 *     {@code getName()}, {@code getValue()}, {@code getSignature()} returning {@code String}.</li>
 *
 * <li><b>authlib 6.x (MC 1.20.5–1.21.10)</b>: Property became a record with {@code name()},
 *     {@code value()}, {@code signature()}. GameProfile still has {@code getProperties()}
 *     returning {@code PropertyMap} (which implements {@code Multimap<String, Property>}).</li>
 *
 * <li><b>authlib 9.x (MC 1.21.11+ / Paper 26.x / Leaf)</b>: GameProfile also became a record
 *     with {@code id()}, {@code name()}, {@code properties()}. The legacy
 *     {@code getProperties()} method no longer exists. PropertyMap still implements
 *     {@code Multimap<String, Property>}.</li>
 * </ul>
 *
 * <p>Because EaglerXServer is compiled against the authlib 1.x paper-api 1.12.2 stub,
 * direct bytecode linkage to {@code Property.getName()} or {@code GameProfile.getProperties()}
 * throws {@link NoSuchMethodError} at runtime on Paper 26.x (authlib 9.x). This class
 * resolves the correct accessor once at class-load time via reflection and caches it
 * in {@link MethodHandle}s for fast invocation.
 *
 * <p>This class is intentionally thread-safe (the {@link MethodHandle} lookups happen
 * inside the static initializer) and side-effect-free. Every accessor call falls back
 * to safe defaults (returning {@code null} or empty collection) rather than throwing,
 * so a partial binding failure doesn't crash the entire plugin.
 *
 * <p>On any authlib version, the canonical 3-arg {@code Property(name, value, signature)}
 * constructor is preserved, so {@link #createProperty(String, String, String)} works
 * universally.
 */
public final class AuthlibCompat {

    /** Set to true if Property is the authlib 6.x/9.x record variant (has name() not getName()). */
    public static final boolean AUTHLIB_6_PLUS;

    /** Set to true if GameProfile is the authlib 9.x record variant (has properties() not getProperties()). */
    public static final boolean GAMEPROFILE_IS_RECORD;

    private static final MethodHandle MH_NAME;
    private static final MethodHandle MH_VALUE;
    private static final MethodHandle MH_SIGNATURE;

    /**
     * Binds to either {@code GameProfile.getProperties()} (legacy) or
     * {@code GameProfile.properties()} (record). Returns an object that is
     * always an instance of {@code Multimap<String, Property>} — either directly
     * (legacy authlib) or via {@code PropertyMap extends ForwardingMultimap}
     * (authlib 6.x+). The return type of the MethodHandle is {@code Object} to
     * accommodate both signatures; the wrapper methods below cast to
     * {@code Multimap<String, Property>} at the call site.
     */
    private static final MethodHandle MH_GAMEPROFILE_GET_PROPERTIES;

    /**
     * Binds to either {@code GameProfile.getId()} (legacy) or
     * {@code GameProfile.id()} (authlib 9.x record). Returns the player's UUID.
     */
    private static final MethodHandle MH_GAMEPROFILE_GET_ID;

    /**
     * Binds to either {@code GameProfile.getName()} (legacy) or
     * {@code GameProfile.name()} (authlib 9.x record). Returns the player's name.
     */
    private static final MethodHandle MH_GAMEPROFILE_GET_NAME;

    private static final Method MH_MULTIMAP_REMOVE_ALL;
    private static final Method MH_MULTIMAP_PUT;
    private static final Method MH_MULTIMAP_REMOVE;
    private static final Method MH_MULTIMAP_GET;
    private static final Method MH_MULTIMAP_VALUES;
    private static final Method MH_MULTIMAP_CONTAINS_KEY;

    static {
        // ---- Resolve Property accessors (name/value/signature) ----
        boolean authlib6;
        MethodHandle nameMH;
        MethodHandle valueMH;
        MethodHandle signatureMH;
        try {
            Method nameMethod;
            Method valueMethod;
            Method signatureMethod;
            // authlib 6.x+: record accessors
            try {
                nameMethod = Property.class.getMethod("name");
                valueMethod = Property.class.getMethod("value");
                signatureMethod = Property.class.getMethod("signature");
                authlib6 = true;
            } catch (NoSuchMethodException e) {
                // authlib 1.x-4.x: legacy getXxx accessors
                nameMethod = Property.class.getMethod("getName");
                valueMethod = Property.class.getMethod("getValue");
                signatureMethod = Property.class.getMethod("getSignature");
                authlib6 = false;
            }
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType strType = MethodType.methodType(String.class);
            // Shape: (Property)String
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

        // ---- Resolve GameProfile.getProperties() / .properties() ----
        // authlib 9.x: GameProfile is a record with `properties()` returning PropertyMap.
        // authlib 1.x-6.x: GameProfile is a class with `getProperties()` returning Multimap.
        // We try the legacy name first (because MethodHandle.findVirtual requires the
        // EXACT return type at the call site, and on authlib 9.x the legacy method doesn't
        // exist so the lookup throws NoSuchMethodException).
        boolean gameProfileIsRecord;
        MethodHandle getPropsMH;
        MethodHandles.Lookup lookup;
        try {
            lookup = MethodHandles.lookup();
        } catch (Exception e) {
            throw new ExceptionInInitializerError("Could not create MethodHandles.lookup: " + e);
        }
        // Try legacy getProperties() returning Multimap (authlib 1.x-6.x)
        MethodHandle legacyMH = null;
        try {
            legacyMH = lookup.findVirtual(GameProfile.class, "getProperties",
                            MethodType.methodType(Multimap.class));
        } catch (NoSuchMethodException e) {
            // not present on authlib 9.x
        } catch (ReflectiveOperationException e) {
            // unexpected
        }
        if (legacyMH != null) {
            getPropsMH = legacyMH;
            gameProfileIsRecord = false;
        } else {
            // Fall back to record-style properties() returning PropertyMap (authlib 9.x).
            // We can't use PropertyMap.class directly because the compile-time stub may
            // not have it — instead use Object as the return type and let the wrapper
            // methods cast to Multimap at the call site.
            MethodHandle recordMH = null;
            try {
                // The return type of properties() is com.mojang.authlib.properties.PropertyMap,
                // which we can't reference from the compile-time paper-api 1.12.2 stub.
                // Use Class.forName to look it up reflectively.
                Class<?> propertyMapClass;
                try {
                    propertyMapClass = Class.forName("com.mojang.authlib.properties.PropertyMap");
                } catch (ClassNotFoundException cnfe) {
                    propertyMapClass = Object.class;
                }
                recordMH = lookup.findVirtual(GameProfile.class, "properties",
                                MethodType.methodType(propertyMapClass));
            } catch (NoSuchMethodException e) {
                // Neither legacy nor record accessor exists — extremely unusual.
                recordMH = null;
            } catch (ReflectiveOperationException e) {
                recordMH = null;
            }
            if (recordMH != null) {
                // Widen the return type to Object so callers don't need to know about PropertyMap.
                getPropsMH = recordMH.asType(MethodType.methodType(Object.class, GameProfile.class));
                gameProfileIsRecord = true;
            } else {
                // Total binding failure — return null at the call site, which the wrapper
                // methods will treat as an empty Multimap. Don't crash the whole plugin
                // because we couldn't bind one accessor.
                getPropsMH = null;
                gameProfileIsRecord = false;
            }
        }
        GAMEPROFILE_IS_RECORD = gameProfileIsRecord;
        MH_GAMEPROFILE_GET_PROPERTIES = getPropsMH;

        // ---- Resolve GameProfile.getId() / .id() and getName() / .name() ----
        // authlib 9.x removed getId()/getName() and replaced them with record accessors
        // id()/name(). We need these to construct a new GameProfile when modifying
        // properties on authlib 9.x (where the original GameProfile is immutable).
        MethodHandle getIdMH = null;
        MethodHandle getNameMH = null;
        // Try legacy getId() returning UUID first (authlib 1.x-6.x)
        try {
            getIdMH = lookup.findVirtual(GameProfile.class, "getId",
                            MethodType.methodType(java.util.UUID.class));
        } catch (NoSuchMethodException e) {
            // Fall back to record-style id() (authlib 9.x)
            try {
                getIdMH = lookup.findVirtual(GameProfile.class, "id",
                                MethodType.methodType(java.util.UUID.class));
            } catch (ReflectiveOperationException e2) {
                // neither — leave null
            }
        } catch (ReflectiveOperationException e) {
            // unexpected
        }
        // Try legacy getName() returning String first
        try {
            getNameMH = lookup.findVirtual(GameProfile.class, "getName",
                            MethodType.methodType(String.class));
        } catch (NoSuchMethodException e) {
            // Fall back to record-style name()
            try {
                getNameMH = lookup.findVirtual(GameProfile.class, "name",
                                MethodType.methodType(String.class));
            } catch (ReflectiveOperationException e2) {
                // neither — leave null
            }
        } catch (ReflectiveOperationException e) {
            // unexpected
        }
        MH_GAMEPROFILE_GET_ID = getIdMH;
        MH_GAMEPROFILE_GET_NAME = getNameMH;

        // ---- Resolve Multimap interface methods (cached for speed) ----
        // These are on the com.google.common.collect.Multimap interface, which is
        // preserved across all authlib versions because PropertyMap implements
        // ForwardingMultimap<String, Property>.
        Method removeAllM = null, putM = null, removeM = null, getM = null, valuesM = null, containsKeyM = null;
        try {
            removeAllM = Multimap.class.getMethod("removeAll", Object.class);
            putM = Multimap.class.getMethod("put", Object.class, Object.class);
            removeM = Multimap.class.getMethod("remove", Object.class, Object.class);
            getM = Multimap.class.getMethod("get", Object.class);
            valuesM = Multimap.class.getMethod("values");
            containsKeyM = Multimap.class.getMethod("containsKey", Object.class);
        } catch (ReflectiveOperationException e) {
            // Multimap interface changed shape — extremely unlikely, but degrade gracefully.
            System.err.println("[EaglerXServer] AuthlibCompat: could not bind Multimap methods: " + e);
        }
        MH_MULTIMAP_REMOVE_ALL = removeAllM;
        MH_MULTIMAP_PUT = putM;
        MH_MULTIMAP_REMOVE = removeM;
        MH_MULTIMAP_GET = getM;
        MH_MULTIMAP_VALUES = valuesM;
        MH_MULTIMAP_CONTAINS_KEY = containsKeyM;
    }

    private AuthlibCompat() {
    }

    // =====================================================================
    // Property accessor methods
    // =====================================================================

    /**
     * Equivalent to authlib 1.x {@code Property.getName()} / authlib 6.x+
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
            return null;
        }
    }

    /** Equivalent to authlib 1.x {@code Property.getValue()} / authlib 6.x+ {@code Property.value()}. */
    public static String getValue(Property property) {
        if (property == null) {
            return null;
        }
        try {
            return (String) MH_VALUE.invokeExact(property);
        } catch (Throwable e) {
            return null;
        }
    }

    /** Equivalent to authlib 1.x {@code Property.getSignature()} / authlib 6.x+ {@code Property.signature()}. */
    public static String getSignature(Property property) {
        if (property == null) {
            return null;
        }
        try {
            return (String) MH_SIGNATURE.invokeExact(property);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Convenience factory for the canonical 3-arg record constructor. Equivalent to
     * {@code new Property(name, value, signature)} on both authlib 1.x and 6.x+.
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
     * wrapper that handles all authlib versions:
     * <ul>
     * <li>authlib 1.x-4.x: calls {@code GameProfile.getProperties()} returning {@code Multimap<String, Property>} directly.</li>
     * <li>authlib 6.x: calls {@code GameProfile.getProperties()} returning {@code PropertyMap}, which is-a Multimap.</li>
     * <li>authlib 9.x: calls {@code GameProfile.properties()} returning {@code PropertyMap}, which is-a Multimap.</li>
     * </ul>
     * Returns {@code null} if profile is null or if accessor binding failed at class init.
     */
    @SuppressWarnings("unchecked")
    public static Multimap<String, Property> getProperties(GameProfile profile) {
        if (profile == null || MH_GAMEPROFILE_GET_PROPERTIES == null) {
            return null;
        }
        try {
            Object result = MH_GAMEPROFILE_GET_PROPERTIES.invoke(profile);
            if (result instanceof Multimap) {
                return (Multimap<String, Property>) result;
            }
            // Defensive: result might be a PropertyMap that doesn't directly implement
            // Multimap (it always does in current authlib, but if a future version changes
            // the inheritance chain we'd land here). Try to coerce via reflection.
            if (result != null && result instanceof com.google.common.collect.Multimap) {
                return (Multimap<String, Property>) result;
            }
            return null;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Returns the player's UUID from the given GameProfile. Type-safe wrapper that
     * handles both {@code getId()} (authlib 1.x-6.x) and {@code id()} (authlib 9.x
     * record). Returns {@code null} if profile is null or binding failed.
     */
    public static java.util.UUID getProfileId(GameProfile profile) {
        if (profile == null || MH_GAMEPROFILE_GET_ID == null) {
            return null;
        }
        try {
            return (java.util.UUID) MH_GAMEPROFILE_GET_ID.invoke(profile);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Returns the player's name from the given GameProfile. Type-safe wrapper that
     * handles both {@code getName()} (authlib 1.x-6.x) and {@code name()} (authlib
     * 9.x record). Returns {@code null} if profile is null or binding failed.
     */
    public static String getProfileName(GameProfile profile) {
        if (profile == null || MH_GAMEPROFILE_GET_NAME == null) {
            return null;
        }
        try {
            return (String) MH_GAMEPROFILE_GET_NAME.invoke(profile);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Removes all properties with the given key from the multimap. Returns the
     * removed collection (matching {@code Multimap.removeAll} semantics).
     */
    @SuppressWarnings("unchecked")
    public static Collection<Property> removeAll(Multimap<String, Property> props, String key) {
        if (props == null || MH_MULTIMAP_REMOVE_ALL == null) {
            return new ArrayList<>();
        }
        try {
            return (Collection<Property>) MH_MULTIMAP_REMOVE_ALL.invoke(props, key);
        } catch (Throwable e) {
            return new ArrayList<>();
        }
    }

    /** Puts a (key, value) pair into the multimap. */
    public static boolean put(Multimap<String, Property> props, String key, Property value) {
        if (props == null || MH_MULTIMAP_PUT == null) {
            return false;
        }
        try {
            return (Boolean) MH_MULTIMAP_PUT.invoke(props, key, value);
        } catch (Throwable e) {
            return false;
        }
    }

    /** Removes a single (key, value) pair from the multimap. */
    public static boolean remove(Multimap<String, Property> props, String key, Property value) {
        if (props == null || MH_MULTIMAP_REMOVE == null) {
            return false;
        }
        try {
            return (Boolean) MH_MULTIMAP_REMOVE.invoke(props, key, value);
        } catch (Throwable e) {
            return false;
        }
    }

    /** Returns the collection of properties for the given key. */
    @SuppressWarnings("unchecked")
    public static Collection<Property> get(Multimap<String, Property> props, String key) {
        if (props == null || MH_MULTIMAP_GET == null) {
            return new ArrayList<>();
        }
        try {
            return (Collection<Property>) MH_MULTIMAP_GET.invoke(props, key);
        } catch (Throwable e) {
            return new ArrayList<>();
        }
    }

    /** Returns true if the multimap contains any property with the given key. */
    public static boolean containsKey(Multimap<String, Property> props, String key) {
        if (props == null || MH_MULTIMAP_CONTAINS_KEY == null) {
            return false;
        }
        try {
            return (Boolean) MH_MULTIMAP_CONTAINS_KEY.invoke(props, key);
        } catch (Throwable e) {
            return false;
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
            // Verify getProperties works against a real GameProfile.
            // NOTE: on authlib 9.x, the PropertyMap returned by GameProfile.properties() is
            // IMMUTABLE (ImmutableMultimap.copyOf). We don't try to put/remove on it here —
            // the caller (BukkitUnsafe.injectProfileProperty) handles immutability by
            // replacing the entire GameProfile. We just verify that getProperties returns
            // a non-null Multimap and containsKey works.
            try {
                java.util.UUID testId = java.util.UUID.randomUUID();
                GameProfile gp = new GameProfile(testId, "smoketest");
                Multimap<String, Property> props = getProperties(gp);
                if (props == null) {
                    return "AuthlibCompat.getProperties(GameProfile) returned null — GameProfile accessor binding failed";
                }
                // containsKey on an empty Multimap — should return false, not throw.
                if (containsKey(props, "nonexistent_key")) {
                    return "AuthlibCompat.containsKey returned true for nonexistent key (empty GameProfile)";
                }
                // Verify getProfileId / getProfileName work (authlib 9.x removed getId/getName)
                java.util.UUID profileId = getProfileId(gp);
                String profileName = getProfileName(gp);
                if (!testId.equals(profileId)) {
                    return "AuthlibCompat.getProfileId returned wrong value: expected=" + testId + " got=" + profileId;
                }
                if (!"smoketest".equals(profileName)) {
                    return "AuthlibCompat.getProfileName returned wrong value: expected=smoketest got=" + profileName;
                }
            } catch (Throwable t) {
                return "AuthlibCompat GameProfile smoke test failed: " + t.getClass().getSimpleName() + ": " + t.getMessage();
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
