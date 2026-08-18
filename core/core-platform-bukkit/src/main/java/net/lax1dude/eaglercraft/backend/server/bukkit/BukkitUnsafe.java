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

package net.lax1dude.eaglercraft.backend.server.bukkit;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.bukkit.Server;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;

import com.google.common.collect.ForwardingList;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.papermc.paper.network.ChannelInitializeListener;
import net.lax1dude.eaglercraft.backend.server.adapter.IEaglerXServerListener;
import net.lax1dude.eaglercraft.backend.server.util.ChannelInitializerHijacker;
import net.lax1dude.eaglercraft.backend.server.util.Util;

public class BukkitUnsafe {

        private static final VarHandle CLASS_CRAFTPLAYER_HANDLE;
        private static final VarHandle CLASS_NETWORKMANAGER_HANDLE;

        static {
                try {
                        MethodHandles.Lookup lookup = MethodHandles.lookup();
                        CLASS_CRAFTPLAYER_HANDLE = lookup.findStaticVarHandle(BukkitUnsafe.class, "class_CraftPlayer", Class.class);
                        CLASS_NETWORKMANAGER_HANDLE = lookup.findStaticVarHandle(BukkitUnsafe.class, "class_NetworkManager", Class.class);
                } catch(ReflectiveOperationException ex) {
                        throw new ExceptionInInitializerError(ex);
                }
        }

        private static volatile Class<?> class_CraftPlayer = null;
        private static Method method_CraftPlayer_getHandle = null;
        private static Method method_CraftPlayer_addChannel = null;
        private static Class<?> class_EntityPlayer = null;
        private static Field field_EntityPlayer_playerConnection = null;
        private static Method method_EntityPlayer_getProfile = null;
        private static Class<?> class_PlayerConnection = null;
        private static Field field_PlayerConnection_networkManager = null;
        private static volatile Class<?> class_NetworkManager = null;
        private static Field field_NetworkManager_channel = null;
        private static Field field_NetworkManager_address = null;

        private static synchronized void bindCraftPlayer(Player playerObject) {
                if (CLASS_CRAFTPLAYER_HANDLE.getAcquire() != null) {
                        return;
                }
                Class<?> clz = playerObject.getClass();
                try {
                        method_CraftPlayer_getHandle = clz.getMethod("getHandle");
                        // addChannel was removed from CraftPlayer in 1.20+
                        try {
                                method_CraftPlayer_addChannel = clz.getMethod("addChannel", String.class);
                        } catch (NoSuchMethodException ex) {
                                method_CraftPlayer_addChannel = null;
                        }
                        Object entityPlayer = method_CraftPlayer_getHandle.invoke(playerObject);
                        Class<?> clz2 = entityPlayer.getClass();
                        // PlayerConnection field — walk declared fields + superclass chain
                        Class<?> clz2walk = clz2;
                        fpc: do {
                                for (Field f : clz2walk.getDeclaredFields()) {
                                        if (NmsNames.matches(f.getType(), NmsNames.PLAYER_CONNECTION)) {
                                                f.setAccessible(true);
                                                field_EntityPlayer_playerConnection = f;
                                                break fpc;
                                        }
                                }
                        } while ((clz2walk = clz2walk.getSuperclass()) != Object.class);
                        if (field_EntityPlayer_playerConnection == null) {
                                throw new IllegalStateException("Could not locate player connection field of " + clz2.getName());
                        }
                        Class<?> clz3 = field_EntityPlayer_playerConnection.getType();
                        // NetworkManager field — walk superclass chain (on 1.20.2+ lives on ServerCommonPacketListenerImpl)
                        Class<?> clz3walk = clz3;
                        fpcnm: do {
                                for (Field f : clz3walk.getDeclaredFields()) {
                                        if (NmsNames.matches(f.getType(), NmsNames.NETWORK_MANAGER)) {
                                                f.setAccessible(true);
                                                field_PlayerConnection_networkManager = f;
                                                break fpcnm;
                                        }
                                }
                        } while ((clz3walk = clz3walk.getSuperclass()) != Object.class);
                        if (field_PlayerConnection_networkManager == null) {
                                throw new IllegalStateException("Could not locate network manager field of " + clz3.getName());
                        }
                        Class<?> clz4 = field_PlayerConnection_networkManager.getType();
                        // Channel field — walk superclass chain (was using getFields())
                        Class<?> clz4walk = clz4;
                        fpcch: do {
                                for (Field f : clz4walk.getDeclaredFields()) {
                                        if (Channel.class.isAssignableFrom(f.getType())) {
                                                f.setAccessible(true);
                                                field_NetworkManager_channel = f;
                                                break fpcch;
                                        }
                                }
                        } while ((clz4walk = clz4walk.getSuperclass()) != Object.class);
                        // SocketAddress field — walk superclass chain
                        Class<?> clz40 = clz4;
                        e: do {
                                for (Field f : clz40.getDeclaredFields()) {
                                        if (SocketAddress.class.isAssignableFrom(f.getType())) {
                                                f.setAccessible(true);
                                                field_NetworkManager_address = f;
                                                break e;
                                        }
                                }
                        } while ((clz40 = clz40.getSuperclass()) != Object.class);
                        if (field_NetworkManager_channel == null) {
                                throw new IllegalStateException("Could not locate channel field of " + clz4.getName());
                        }
                        if (field_NetworkManager_address == null) {
                                System.err.println("Could not find SocketAddress field in class " + clz4.getName());
                                System.err.println("Use Spigot if you want EaglerXServer to forward player IPs");
                        }
                        method_EntityPlayer_getProfile = findGameProfileGetter(clz2);
                        if (method_EntityPlayer_getProfile == null) {
                                throw new IllegalStateException(
                                                "Could not locate GameProfile getter on " + clz2.getName());
                        }
                        CLASS_NETWORKMANAGER_HANDLE.setRelease(clz4);
                        class_PlayerConnection = clz3;
                        class_EntityPlayer = clz2;
                        CLASS_CRAFTPLAYER_HANDLE.setRelease(clz);
                } catch (Exception ex) {
                        throw Util.propagateReflectThrowable(ex);
                }
        }

        /**
         * Locates a GameProfile-returning getter on the EntityPlayer/ServerPlayer class.
         * On 1.17+ the canonical method is getGameProfile(); on 1.21+ getProfile()
         * returns ResolvableProfile (which would ClassCastException), so we explicitly
         * check the return type. Falls back to walking all 0-arg methods returning
         * GameProfile.
         */
        private static Method findGameProfileGetter(Class<?> entityPlayerClass) {
                try {
                        Method m = entityPlayerClass.getMethod("getGameProfile");
                        if (GameProfile.class.isAssignableFrom(m.getReturnType())) {
                                return m;
                        }
                } catch (NoSuchMethodException ex) {
                }
                try {
                        Method m = entityPlayerClass.getMethod("getProfile");
                        if (GameProfile.class.isAssignableFrom(m.getReturnType())) {
                                return m;
                        }
                } catch (NoSuchMethodException ex) {
                }
                for (Method m : entityPlayerClass.getMethods()) {
                        if (m.getParameterCount() == 0 && GameProfile.class.isAssignableFrom(m.getReturnType())) {
                                return m;
                        }
                }
                return null;
        }

        public static Channel getPlayerChannel(Player playerObject) {
                try {
                        if (CLASS_CRAFTPLAYER_HANDLE.getAcquire() == null) {
                                bindCraftPlayer(playerObject);
                        }
                        return (Channel) field_NetworkManager_channel.get(field_PlayerConnection_networkManager
                                        .get(field_EntityPlayer_playerConnection.get(method_CraftPlayer_getHandle.invoke(playerObject))));
                } catch (Throwable e) {
                        // bindCraftPlayer can throw RuntimeException if a future Paper refactor
                        // moves CraftPlayer.getHandle() — let it through cleanly with a clear message.
                        if (e instanceof ReflectiveOperationException) {
                                throw Util.propagateReflectThrowable((ReflectiveOperationException) e);
                        }
                        if (e instanceof RuntimeException) {
                                throw (RuntimeException) e;
                        }
                        throw new RuntimeException("getPlayerChannel failed", e);
                }
        }

        public static String getTexturesProperty(Player player) {
                try {
                        if (CLASS_CRAFTPLAYER_HANDLE.getAcquire() == null) {
                                bindCraftPlayer(player);
                        }
                        // CRITICAL: Use AuthlibCompat to access GameProfile.getProperties() / .properties()
                        // (authlib 9.x made GameProfile a record with properties() returning PropertyMap,
                        // not getProperties() returning Multimap) AND to access Property.getValue() /
                        // .value() (authlib 6.x renamed Property accessors). Both calls go through
                        // reflection-cached MethodHandles so they work on all authlib versions.
                        GameProfile profile = (GameProfile) method_EntityPlayer_getProfile
                                        .invoke(method_CraftPlayer_getHandle.invoke(player));
                        Multimap<String, Property> props = net.lax1dude.eaglercraft.backend.server.api.bukkit.compat.AuthlibCompat
                                        .getProperties(profile);
                        if (props == null) {
                                return null;
                        }
                        Collection<Property> tex = props.get("textures");
                        if (!tex.isEmpty()) {
                                return net.lax1dude.eaglercraft.backend.server.api.bukkit.compat.AuthlibCompat
                                                .getValue(tex.iterator().next());
                        }
                } catch (ReflectiveOperationException e) {
                        throw Util.propagateReflectThrowable(e);
                } catch (Throwable t) {
                        // NoSuchMethodError, ClassCastException, etc. — log and return null so skins
                        // subsystem degrades gracefully instead of kicking the player.
                        System.err.println("[EaglerXServer] getTexturesProperty failed: " + t);
                        return null;
                }
                return null;
        }

        private static final Property isEaglerPlayerPropertyT = new Property("isEaglerPlayer", "true", null);
        private static final Property isEaglerPlayerPropertyF = new Property("isEaglerPlayer", "false", null);

        public static class PropertyInjector {

                private final Multimap<String, Property> props;
                private final Object lock;
                /**
                 * The EntityPlayer/ServerPlayer instance whose GameProfile we're modifying.
                 * Used on authlib 9.x where GameProfile is an immutable record — we need to
                 * construct a new GameProfile with the modified properties and replace the
                 * field on the EntityPlayer via reflection.
                 */
                private final Object entityPlayer;
                /**
                 * The original GameProfile. Used to construct a new GameProfile with modified
                 * properties on authlib 9.x.
                 */
                private final GameProfile originalProfile;

                protected PropertyInjector(Multimap<String, Property> props, Object lock) {
                        this(props, lock, null, null);
                }

                protected PropertyInjector(Multimap<String, Property> props, Object lock, Object entityPlayer,
                                GameProfile originalProfile) {
                        this.props = props;
                        this.lock = lock;
                        this.entityPlayer = entityPlayer;
                        this.originalProfile = originalProfile;
                }

                public void injectTexturesProperty(String texturesPropertyValue, String texturesPropertySignature) {
                        synchronized (lock) {
                                try {
                                        props.removeAll("textures");
                                        props.put("textures",
                                                        new Property("textures", texturesPropertyValue, texturesPropertySignature));
                                } catch (UnsupportedOperationException | IllegalArgumentException e) {
                                        // authlib 9.x: PropertyMap is immutable (ImmutableMultimap.copyOf).
                                        // Replace the entire GameProfile with a new one containing the
                                        // modified properties.
                                        replaceGameProfileOnEntityPlayer("textures", new Property("textures",
                                                        texturesPropertyValue, texturesPropertySignature));
                                }
                        }
                }

                public void injectIsEaglerPlayerProperty(boolean val) {
                        synchronized (lock) {
                                try {
                                        props.removeAll("isEaglerPlayer");
                                        props.put("isEaglerPlayer", val ? isEaglerPlayerPropertyT : isEaglerPlayerPropertyF);
                                } catch (UnsupportedOperationException | IllegalArgumentException e) {
                                        // authlib 9.x: immutable PropertyMap — replace GameProfile.
                                        replaceGameProfileOnEntityPlayer("isEaglerPlayer",
                                                        val ? isEaglerPlayerPropertyT : isEaglerPlayerPropertyF);
                                }
                        }
                }

                /**
                 * Called when the existing GameProfile's PropertyMap is immutable (authlib 9.x).
                 * Delegates to the outer class's static helper.
                 */
                private void replaceGameProfileOnEntityPlayer(String key, Property newProp) {
                        if (originalProfile == null || entityPlayer == null) {
                                System.err.println("[EaglerXServer] PropertyInjector: cannot replace GameProfile on authlib 9.x "
                                                + "because originalProfile or entityPlayer is null — property '" + key
                                                + "' will not be injected");
                                return;
                        }
                        BukkitUnsafe.replaceGameProfileOnEntityPlayer(entityPlayer, props, originalProfile, key, newProp);
                }

                public void complete() {
                }

        }

        public static BukkitUnsafe.PropertyInjector propertyInjector(Player player) {
                try {
                        if (CLASS_CRAFTPLAYER_HANDLE.getAcquire() == null) {
                                bindCraftPlayer(player);
                        }
                        Object entityPlayer = method_CraftPlayer_getHandle.invoke(player);
                        GameProfile profile = (GameProfile) method_EntityPlayer_getProfile.invoke(entityPlayer);
                        // CRITICAL: use AuthlibCompat.getProperties() instead of profile.getProperties()
                        // because authlib 9.x made GameProfile a record with properties() returning
                        // PropertyMap (no getProperties() method).
                        Multimap<String, Property> props = net.lax1dude.eaglercraft.backend.server.api.bukkit.compat.AuthlibCompat
                                        .getProperties(profile);
                        if (props == null) {
                                throw new IllegalStateException("Could not get GameProfile properties via AuthlibCompat");
                        }
                        // Pass the entityPlayer and originalProfile so that on authlib 9.x (where
                        // PropertyMap is immutable), the PropertyInjector can construct a new
                        // GameProfile with the modified properties and replace the field on the
                        // EntityPlayer via reflection.
                        return new PropertyInjector(props, profile, entityPlayer, profile);
                } catch (Throwable e) {
                        if (e instanceof ReflectiveOperationException) {
                                throw Util.propagateReflectThrowable((ReflectiveOperationException) e);
                        }
                        if (e instanceof RuntimeException) {
                                throw (RuntimeException) e;
                        }
                        throw new RuntimeException("propertyInjector failed", e);
                }
        }

        /**
         * Injects a (key, property) pair into the EntityPlayer's GameProfile, transparently
         * handling all authlib versions:
         * <ul>
         * <li>authlib 1.x-6.x: the PropertyMap returned by {@code getProperties()} is mutable,
         *     so we just call {@code props.put(key, property)} after {@code props.removeAll(key)}.</li>
         * <li>authlib 9.x: the PropertyMap returned by {@code properties()} is immutable
         *     (ImmutableMultimap.copyOf). We construct a new GameProfile with the modified
         *     properties and replace the {@code gameProfile} field on the EntityPlayer via
         *     reflection.</li>
         * </ul>
         *
         * <p>Returns the Property that was injected (useful for tracking the marker to remove
         * it later), or {@code null} on failure.
         */
        public static Property injectProfileProperty(Object entityPlayer, String key, String value, String signature) {
                if (CLASS_CRAFTPLAYER_HANDLE.getAcquire() == null) {
                        // bindCraftPlayer requires a Player object, not an EntityPlayer; we can't bind here.
                        // Caller should have already bound via getPlayerChannel/propertyInjector/getHandle/etc.
                        // Best-effort: return null.
                        return null;
                }
                try {
                        GameProfile profile = (GameProfile) method_EntityPlayer_getProfile.invoke(entityPlayer);
                        Multimap<String, Property> props = net.lax1dude.eaglercraft.backend.server.api.bukkit.compat.AuthlibCompat
                                        .getProperties(profile);
                        if (props == null) {
                                return null;
                        }
                        Property newProp = net.lax1dude.eaglercraft.backend.server.api.bukkit.compat.AuthlibCompat
                                        .createProperty(key, value, signature);
                        try {
                                // Try mutable path first (authlib 1.x-6.x)
                                props.removeAll(key);
                                props.put(key, newProp);
                                // Verify the put actually succeeded (authlib 9.x silently no-ops)
                                if (!net.lax1dude.eaglercraft.backend.server.api.bukkit.compat.AuthlibCompat
                                                .containsKey(props, key)) {
                                        // Silent no-op — fall back to immutable path.
                                        throw new UnsupportedOperationException("PropertyMap is immutable");
                                }
                                return newProp;
                        } catch (UnsupportedOperationException | IllegalArgumentException e) {
                                // authlib 9.x immutable path — replace GameProfile entirely.
                                return replaceGameProfileOnEntityPlayer(entityPlayer, props, profile, key, newProp);
                        }
                } catch (Throwable t) {
                        System.err.println("[EaglerXServer] injectProfileProperty failed for key '" + key + "': " + t);
                        return null;
                }
        }

        /**
         * Helper for {@link #injectProfileProperty} — constructs a new GameProfile with the
         * modified properties and replaces the {@code gameProfile} field on the EntityPlayer.
         * Used on authlib 9.x where PropertyMap is immutable.
         */
        private static Property replaceGameProfileOnEntityPlayer(Object entityPlayer, Multimap<String, Property> props,
                        GameProfile originalProfile, String key, Property newProp) {
                try {
                        // Build a new mutable PropertyMap with the original properties plus the new one.
                        com.google.common.collect.LinkedListMultimap<String, Property> mutableProps =
                                        com.google.common.collect.LinkedListMultimap.create();
                        for (java.util.Map.Entry<String, Property> e : props.entries()) {
                                if (!e.getKey().equals(key)) {
                                        mutableProps.put(e.getKey(), e.getValue());
                                }
                        }
                        mutableProps.put(key, newProp);
                        // Construct a new PropertyMap with the mutable copy
                        Class<?> propertyMapClass = Class.forName("com.mojang.authlib.properties.PropertyMap");
                        java.lang.reflect.Constructor<?> ctor = propertyMapClass.getConstructor(
                                        com.google.common.collect.Multimap.class);
                        Object newPropertyMap = ctor.newInstance(mutableProps);
                        // Construct a new GameProfile with the same id, name, and new PropertyMap
                        java.lang.reflect.Constructor<?> gpCtor = GameProfile.class.getDeclaredConstructor(
                                        java.util.UUID.class, String.class, propertyMapClass);
                        gpCtor.setAccessible(true);
                        // CRITICAL: use AuthlibCompat.getProfileId() / getProfileName() instead of
                        // originalProfile.getId() / .getName() because authlib 9.x made GameProfile
                        // a record and removed the getId()/getName() methods (replaced with id() /
                        // name() record accessors). A direct bytecode call throws NoSuchMethodError.
                        java.util.UUID profileId = net.lax1dude.eaglercraft.backend.server.api.bukkit.compat.AuthlibCompat
                                        .getProfileId(originalProfile);
                        String profileName = net.lax1dude.eaglercraft.backend.server.api.bukkit.compat.AuthlibCompat
                                        .getProfileName(originalProfile);
                        if (profileId == null || profileName == null) {
                                System.err.println("[EaglerXServer] replaceGameProfileOnEntityPlayer: could not get profile id/name "
                                                + "via AuthlibCompat — property '" + key + "' will not be injected");
                                return null;
                        }
                        GameProfile newProfile = (GameProfile) gpCtor.newInstance(profileId, profileName, newPropertyMap);
                        // Find the gameProfile field on the EntityPlayer (or its superclass Player)
                        Field gameProfileField = findGameProfileField(entityPlayer.getClass());
                        if (gameProfileField == null) {
                                System.err.println("[EaglerXServer] could not locate gameProfile field on "
                                                + entityPlayer.getClass().getName() + " — property '" + key
                                                + "' will not be injected");
                                return null;
                        }
                        gameProfileField.set(entityPlayer, newProfile);
                        return newProp;
                } catch (Throwable t) {
                        System.err.println("[EaglerXServer] replaceGameProfileOnEntityPlayer failed for key '"
                                        + key + "': " + t);
                        return null;
                }
        }

        private static Field findGameProfileField(Class<?> entityPlayerClass) {
                Class<?> walk = entityPlayerClass;
                while (walk != null && walk != Object.class) {
                        for (Field f : walk.getDeclaredFields()) {
                                if (f.getType() == GameProfile.class) {
                                        f.setAccessible(true);
                                        return f;
                                }
                        }
                        walk = walk.getSuperclass();
                }
                return null;
        }

        public static Object getHandle(Player player) {
                try {
                        if (CLASS_CRAFTPLAYER_HANDLE.getAcquire() == null) {
                                bindCraftPlayer(player);
                        }
                        return method_CraftPlayer_getHandle.invoke(player);
                } catch (Throwable e) {
                        if (e instanceof ReflectiveOperationException) {
                                throw Util.propagateReflectThrowable((ReflectiveOperationException) e);
                        }
                        if (e instanceof RuntimeException) {
                                throw (RuntimeException) e;
                        }
                        throw new RuntimeException("getHandle failed", e);
                }
        }

        public static GameProfile getGameProfile(Object entityPlayer) {
                try {
                        return (GameProfile) method_EntityPlayer_getProfile.invoke(entityPlayer);
                } catch (ReflectiveOperationException e) {
                        throw Util.propagateReflectThrowable(e);
                }
        }

        private static synchronized void bindRealAddress(Object networkManager) {
                if (CLASS_NETWORKMANAGER_HANDLE.getAcquire() != null) {
                        return;
                }
                Class<?> clz = networkManager.getClass();
                Class<?> clz0 = clz;
                do {
                        for (Field field : clz0.getDeclaredFields()) {
                                if (SocketAddress.class.isAssignableFrom(field.getType())) {
                                        field.setAccessible(true);
                                        field_NetworkManager_address = field;
                                        CLASS_NETWORKMANAGER_HANDLE.setRelease(clz);
                                        return;
                                }
                        }
                } while ((clz0 = clz0.getSuperclass()) != Object.class);
                CLASS_NETWORKMANAGER_HANDLE.setRelease(clz);
                System.err.println("Could not find SocketAddress field in class " + clz.getName() + " (or parents)");
                System.err.println("Use Spigot if you want EaglerXServer to forward player IPs");
        }

        public static void updateRealAddress(Object networkManager, SocketAddress address) {
                Class<?> clz;
                if ((clz = (Class<?>) CLASS_NETWORKMANAGER_HANDLE.getAcquire()) == null) {
                        bindRealAddress(networkManager);
                        clz = (Class<?>) CLASS_NETWORKMANAGER_HANDLE.getAcquire();
                }
                if (field_NetworkManager_address != null && clz.isAssignableFrom(networkManager.getClass())) {
                        try {
                                field_NetworkManager_address.set(networkManager, address);
                        } catch (IllegalArgumentException | IllegalAccessException e) {
                                throw Util.propagateReflectThrowable(e);
                        }
                }
        }

        public static void addPlayerChannel(Player player, String ch) {
                try {
                        if (CLASS_CRAFTPLAYER_HANDLE.getAcquire() == null) {
                                bindCraftPlayer(player);
                        }
                        if (method_CraftPlayer_addChannel == null) {
                                // addChannel was removed from CraftPlayer in 1.20+ — no-op
                                return;
                        }
                        method_CraftPlayer_addChannel.invoke(player, ch);
                } catch (Throwable e) {
                        if (e instanceof ReflectiveOperationException) {
                                throw Util.propagateReflectThrowable((ReflectiveOperationException) e);
                        }
                        if (e instanceof RuntimeException) {
                                throw (RuntimeException) e;
                        }
                        throw new RuntimeException("addPlayerChannel failed", e);
                }
        }

        private static class CleanupList implements Consumer<ChannelInitializerHijacker>, Runnable {

                protected List<ChannelInitializerHijacker> cleanup = new ArrayList<>();

                /**
                 * Captured on construction so that {@code run()} can restore the original
                 * {@code List<ChannelFuture>} back into the ServerConnection field when
                 * the plugin is disabled. Without this, every PlugMan {@code /reload}
                 * stacks a new ForwardingList on top of the previous one, and the field
                 * never gets back its original list. After N reloads the field holds N
                 * nested ForwardingLists and {@code add()} becomes O(N).
                 */
                protected Field restoreField;
                protected Object restoreTarget;
                protected List<ChannelFuture> restoreOriginalList;

                @Override
                public void accept(ChannelInitializerHijacker c) {
                        synchronized (this) {
                                if (cleanup != null) {
                                        cleanup.add(c);
                                        return;
                                }
                        }
                        c.deactivate();
                }

                @Override
                public void run() {
                        List<ChannelInitializerHijacker> cc;
                        synchronized (this) {
                                cc = new ArrayList<>(cleanup);
                                cleanup = null;
                        }
                        for (ChannelInitializerHijacker c : cc) {
                                c.deactivate();
                        }
                        // Restore the original List<ChannelFuture> back into the ServerConnection
                        // field. This unwinds the ForwardingList wrapper we installed, so a
                        // subsequent reload starts from a clean state instead of stacking another
                        // wrapper. If restore fails (e.g. field was already mutated by another
                        // plugin), we silently leave it — better than throwing on disable.
                        //
                        // CRITICAL: Only restore if the field still contains OUR wrapper. If another
                        // plugin (Geyser, Floodgate) wrapped our ForwardingList with their own, blindly
                        // restoring would clobber their wrapper and break their channel init flow.
                        if (restoreField != null && restoreTarget != null && restoreOriginalList != null) {
                                try {
                                        Object current = restoreField.get(restoreTarget);
                                        // Only restore if the current value is the ForwardingList we installed.
                                        // We can't compare by reference because the ForwardingList is an
                                        // anonymous class created in injectChannelInitializerOld; compare by
                                        // class name + delegate identity instead. If anything looks different,
                                        // leave the field alone — the new plugin's wrapper stays in place.
                                        if (current != null && current.getClass().getName().contains("ForwardingList")
                                                        && current != restoreOriginalList) {
                                                // Looks like our wrapper — safe to restore.
                                                restoreField.set(restoreTarget, restoreOriginalList);
                                        } else if (current == restoreOriginalList) {
                                                // Already restored — nothing to do.
                                        } else {
                                                // Some other plugin replaced the list — leave it alone.
                                                System.err.println(
                                                                "[EaglerXServer] ServerConnection channel futures list was replaced by another plugin; not restoring original to avoid clobbering their wrapper.");
                                        }
                                } catch (Throwable t) {
                                        System.err.println("[EaglerXServer] Could not restore original channel futures list: " + t);
                                }
                                restoreField = null;
                                restoreTarget = null;
                                restoreOriginalList = null;
                        }
                }

        }

        public static Runnable injectChannelInitializer(Server server, Consumer<Channel> initHandler,
                        IEaglerXServerListener listener) {
                Class<?> keyClz;
                Object eaglerKey;
                Class<?> paperChannelInitHolder;
                Class<?> paperChannelInitListener;
                try {
                        keyClz = Class.forName("net.kyori.adventure.key.Key");
                        eaglerKey = keyClz.getMethod("key", String.class, String.class).invoke(null, "eaglerxserver",
                                        "channel_initializer");
                        paperChannelInitHolder = Class.forName("io.papermc.paper.network.ChannelInitializeListenerHolder");
                        paperChannelInitListener = Class.forName("io.papermc.paper.network.ChannelInitializeListener");
                } catch (ReflectiveOperationException ex) {
                        return injectChannelInitializerOld(server, initHandler, listener);
                }
                return injectChannelInitializerPaper(paperChannelInitHolder, paperChannelInitListener, keyClz, eaglerKey,
                                initHandler, listener);
        }

        private static Runnable injectChannelInitializerPaper(Class<?> paperChannelInitHolder,
                        Class<?> paperChannelInitListener, Class<?> keyClz, Object eaglerKey, Consumer<Channel> initHandler,
                        IEaglerXServerListener listener) {
                try {
                        Method addListener = paperChannelInitHolder.getMethod("addListener", keyClz, paperChannelInitListener);
                        Method removeListener = paperChannelInitHolder.getMethod("removeListener", keyClz);
                        Object listenerImpl = (ChannelInitializeListener) initHandler::accept;
                        addListener.invoke(null, eaglerKey, listenerImpl);
                        listener.reportPaperMCInjected();
                        return () -> {
                                try {
                                        removeListener.invoke(null, eaglerKey);
                                } catch (ReflectiveOperationException e) {
                                        throw Util.propagateReflectThrowable(e);
                                }
                        };
                } catch (ReflectiveOperationException e) {
                        throw Util.propagateReflectThrowable(e);
                }
        }

        private static Runnable injectChannelInitializerOld(Server server, Consumer<Channel> initHandler,
                        IEaglerXServerListener listener) {
                try {
                        Object dedicatedPlayerList = server.getClass().getMethod("getHandle").invoke(server);
                        Object minecraftServer = dedicatedPlayerList.getClass().getMethod("getServer").invoke(dedicatedPlayerList);
                        // getConnection() is the modern name (1.17+); getServerConnection() is legacy (1.12-1.16)
                        Method getServerConnection;
                        try {
                                getServerConnection = minecraftServer.getClass().getMethod("getConnection");
                        } catch (NoSuchMethodException e1) {
                                getServerConnection = minecraftServer.getClass().getMethod("getServerConnection");
                        }
                        Object serverConnection = getServerConnection.invoke(minecraftServer);
                        Class<?> serverConnectionClass;
                        if (serverConnection == null) {
                                serverConnectionClass = getServerConnection.getReturnType();
                                for (Method meth : minecraftServer.getClass().getMethods()) {
                                        if (meth.getReturnType() == serverConnectionClass && !meth.equals(getServerConnection)) {
                                                serverConnection = meth.invoke(minecraftServer);
                                                if (serverConnection != null) {
                                                        break;
                                                }
                                        }
                                }
                                if (serverConnection == null) {
                                        throw new RuntimeException("Could not get ServerConnection instance from server! (Try Paper)");
                                }
                        }
                        serverConnectionClass = serverConnection.getClass();
                        Field channelFuturesList = null;
                        e: do {
                                for (Field f : serverConnectionClass.getDeclaredFields()) {
                                        if (List.class.isAssignableFrom(f.getType())) {
                                                Type t = f.getGenericType();
                                                if (t instanceof ParameterizedType tt) {
                                                        Type[] params = tt.getActualTypeArguments();
                                                        if (params.length == 1 && "io.netty.channel.ChannelFuture".equals(params[0].getTypeName())) {
                                                                channelFuturesList = f;
                                                                channelFuturesList.setAccessible(true);
                                                                break e;
                                                        }
                                                }
                                        }
                                }
                        } while ((serverConnectionClass = serverConnectionClass.getSuperclass()) != Object.class);
                        if (channelFuturesList == null) {
                                throw new RuntimeException("Could not get ServerConnection channel futures list! (Try Paper)");
                        }
                        CleanupList cleanupList = new CleanupList();
                        final List<ChannelFuture> oldList = (List<ChannelFuture>) channelFuturesList.get(serverConnection);
                        // Capture the field+target+originalList so the cleanup Runnable can restore
                        // the original (non-wrapped) list on disable. Without this, PlugMan reload
                        // stacks a new ForwardingList on every plugin reload.
                        cleanupList.restoreField = channelFuturesList;
                        cleanupList.restoreTarget = serverConnection;
                        cleanupList.restoreOriginalList = oldList;
                        for (ChannelFuture ch : oldList) {
                                injectChannelInitializer(ch, listener, initHandler, cleanupList);
                        }
                        List<ChannelFuture> hackList = new ForwardingList<ChannelFuture>() {
                                @Override
                                protected List<ChannelFuture> delegate() {
                                        return oldList;
                                }

                                @Override
                                public boolean add(ChannelFuture element) {
                                        super.add(element);
                                        injectChannelInitializer(element, listener, initHandler, cleanupList);
                                        return true;
                                }
                        };
                        channelFuturesList.set(serverConnection, hackList);
                        return cleanupList;
                } catch (ReflectiveOperationException e) {
                        throw Util.propagateReflectThrowable(e);
                }
        }

        private static void injectChannelInitializer(ChannelFuture channel, IEaglerXServerListener listenerConf,
                        Consumer<Channel> initHandler, CleanupList cleanupCallback) {
                channel.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture var1) throws Exception {
                                if (var1.isSuccess() && cleanupCallback.cleanup != null) {
                                        injectChannelInitializer(var1.channel(), listenerConf, initHandler, cleanupCallback);
                                }
                        }
                });
        }

        // Inspired by ViaVersion
        private static void injectChannelInitializer(Channel channel, IEaglerXServerListener listenerConf,
                        Consumer<Channel> initHandler, Consumer<ChannelInitializerHijacker> cleanupCallback) {
                List<String> names = channel.pipeline().names();
                ChannelHandler foundHandler;
                Field foundField;
                eagler: {
                        for (String name : names) {
                                ChannelHandler handler = channel.pipeline().get(name);
                                if (isServerInitializer(handler)) {
                                        try {
                                                foundField = Util.findDeclaredField(handler.getClass(), "childHandler");
                                                foundField.setAccessible(true);
                                                foundHandler = handler;
                                                break eagler;
                                        } catch (IllegalArgumentException | ReflectiveOperationException ex) {
                                        }
                                }
                        }
                        foundHandler = channel.pipeline().first();
                        if (isServerInitializer(foundHandler)) {
                                try {
                                        foundField = Util.findDeclaredField(foundHandler.getClass(), "childHandler");
                                        foundField.setAccessible(true);
                                        break eagler;
                                } catch (ReflectiveOperationException ex) {
                                        throw new RuntimeException("Could not find ChannelBootstrapAccelerator to inject into!");
                                }
                        }
                        return;
                }
                injectInto(foundHandler, foundField, initHandler, cleanupCallback);
                listenerConf.reportNettyInjected(channel);
        }

        private static void injectInto(ChannelHandler foundHandler, Field foundField, Consumer<Channel> init,
                        Consumer<ChannelInitializerHijacker> cleanupCallback) {
                ChannelInitializer<Channel> parent;
                Method initChannel;
                try {
                        parent = (ChannelInitializer<Channel>) foundField.get(foundHandler);
                        initChannel = Util.findDeclaredMethod(parent.getClass(), "initChannel", Channel.class);
                        initChannel.setAccessible(true);
                } catch (Throwable e) {
                        // Widened from ReflectiveOperationException: a ClassCastException if Paper
                        // refactored the field type, an NPE if foundField is null, or any other
                        // Throwable must not abort plugin startup with a misleading message.
                        if (e instanceof ReflectiveOperationException) {
                                throw Util.propagateReflectThrowable((ReflectiveOperationException) e);
                        }
                        if (e instanceof RuntimeException) {
                                throw (RuntimeException) e;
                        }
                        if (e instanceof Error) {
                                throw (Error) e;
                        }
                        throw new RuntimeException("injectInto failed to bind parent initializer", e);
                }
                ChannelInitializerHijacker newInit = new ChannelInitializerHijacker(init) {

                        @Override
                        protected void callParent(Channel channel) {
                                try {
                                        initChannel.invoke(parent, channel);
                                } catch (Throwable e) {
                                        if (e instanceof ReflectiveOperationException) {
                                                throw Util.propagateReflectThrowable((ReflectiveOperationException) e);
                                        }
                                        if (e instanceof RuntimeException) {
                                                throw (RuntimeException) e;
                                        }
                                        if (e instanceof Error) {
                                                throw (Error) e;
                                        }
                                        throw new RuntimeException("callParent failed", e);
                                }
                        }

                        @Override
                        protected boolean reInject() {
                                Object newInitializer;
                                try {
                                        newInitializer = foundField.get(foundHandler);
                                } catch (Throwable e) {
                                        if (e instanceof RuntimeException) {
                                                throw (RuntimeException) e;
                                        }
                                        if (e instanceof Error) {
                                                throw (Error) e;
                                        }
                                        throw new RuntimeException("reInject failed to read foundField", e);
                                }
                                if (this != newInitializer) {
                                        System.err.println("Detected another plugin's channel initializer ("
                                                        + newInitializer.getClass().getName() + ") injected into the pipeline, "
                                                        + "reinjecting EaglerXServer again to make sure its first, because we "
                                                        + "really are that rude");
                                        injectInto(foundHandler, foundField, init, cleanupCallback);
                                        return true;
                                } else {
                                        return false;
                                }
                        }

                };
                try {
                        foundField.set(foundHandler, newInit);
                } catch (Throwable e) {
                        if (e instanceof ReflectiveOperationException) {
                                throw Util.propagateReflectThrowable((ReflectiveOperationException) e);
                        }
                        if (e instanceof RuntimeException) {
                                throw (RuntimeException) e;
                        }
                        if (e instanceof Error) {
                                throw (Error) e;
                        }
                        throw new RuntimeException("injectInto failed to set newInit", e);
                }
                cleanupCallback.accept(newInit);
        }

        private static boolean isServerInitializer(ChannelHandler handler) {
                return handler != null && ChannelInboundHandlerAdapter.class.isAssignableFrom(handler.getClass());
        }

        public static CommandMap getCommandMap(Server server) {
                try {
                        Field f = Util.findDeclaredField(server.getClass(), "commandMap");
                        f.setAccessible(true);
                        return (CommandMap) f.get(server);
                } catch (IllegalAccessException | NoSuchFieldException | SecurityException ex) {
                        try {
                                Method m = Util.findDeclaredMethod(server.getClass(), "getCommandMap");
                                m.setAccessible(true);
                                return (CommandMap) m.invoke(server);
                        } catch (ReflectiveOperationException ex1) {
                                throw Util.propagateReflectThrowable(ex1);
                        }
                }
        }

        private static Field findField(Class<?> clazz, Class<?> fieldType) throws NoSuchFieldException {
                Class<?> clazz0 = clazz;
                do {
                        for (Field field : clazz0.getDeclaredFields()) {
                                if (field.getType() == fieldType) {
                                        field.setAccessible(true);
                                        return field;
                                }
                        }
                } while ((clazz0 = clazz0.getSuperclass()) != Object.class);
                throw new NoSuchFieldException(
                                "Could not find field with type " + fieldType + " in class " + clazz.getName() + " (or parents)");
        }

        public static boolean isEnableNativeTransport(Server server) {
                Object dedicatedPlayerList;
                Object dedicatedServer;
                try {
                        dedicatedPlayerList = server.getClass().getMethod("getHandle").invoke(server);
                        dedicatedServer = dedicatedPlayerList.getClass().getMethod("getServer").invoke(dedicatedPlayerList);
                } catch (ReflectiveOperationException e) {
                        return true;
                }
                // Try getPropertyManager first (1.12-1.16)
                try {
                        Object propertyManager = dedicatedServer.getClass().getMethod("getPropertyManager").invoke(dedicatedServer);
                        Method getBoolean = Util.findDeclaredMethod(propertyManager.getClass(), "getBoolean", String.class, boolean.class);
                        getBoolean.setAccessible(true);
                        return (Boolean) getBoolean.invoke(propertyManager, "use-native-transport", true);
                } catch (NoSuchMethodException e) {
                        // fall through to getDedicatedServerProperties
                } catch (ReflectiveOperationException e) {
                        return true;
                }
                // Try getDedicatedServerProperties (1.17+)
                try {
                        Object propertyManager = dedicatedServer.getClass().getMethod("getDedicatedServerProperties").invoke(dedicatedServer);
                        Method getBoolean = Util.findDeclaredMethod(propertyManager.getClass(), "getBoolean", String.class, boolean.class);
                        getBoolean.setAccessible(true);
                        return (Boolean) getBoolean.invoke(propertyManager, "use-native-transport", true);
                } catch (NoSuchMethodException e) {
                        // fall through to default
                } catch (ReflectiveOperationException e) {
                        return true;
                }
                return true;
        }

        public static EventLoopGroup getEventLoopGroup(Server server, boolean enableNativeTransport) {
                Object minecraftServer;
                try {
                        Object dedicatedPlayerList = server.getClass().getMethod("getHandle").invoke(server);
                        minecraftServer = dedicatedPlayerList.getClass().getMethod("getServer").invoke(dedicatedPlayerList);
                } catch (ReflectiveOperationException e) {
                        return createOwnEventLoopGroup(enableNativeTransport);
                }
                // getConnection() is the modern (1.17+) name; getServerConnection() is legacy (1.12-1.16)
                Method serverConnMethod;
                try {
                        serverConnMethod = minecraftServer.getClass().getMethod("getConnection");
                } catch (NoSuchMethodException e) {
                        try {
                                serverConnMethod = minecraftServer.getClass().getMethod("getServerConnection");
                        } catch (NoSuchMethodException e2) {
                                return createOwnEventLoopGroup(enableNativeTransport);
                        }
                }
                Class<?> serverConnection = serverConnMethod.getReturnType();
                EventLoopGroup result = getEventLoopGroup(serverConnection, enableNativeTransport);
                if (result != null) {
                        return result;
                }
                return createOwnEventLoopGroup(enableNativeTransport);
        }

        public static EventLoopGroup getEventLoopGroup(Class<?> serverConnection, boolean enableNativeTransport) {
                Field[] fields = serverConnection.getFields();
                if (enableNativeTransport) {
                        for (Field field : fields) {
                                Class<?> clz = field.getType();
                                if (clz.getSimpleName().equals("LazyInitVar")) {
                                        Type type = field.getGenericType();
                                        if (type instanceof ParameterizedType tt) {
                                                Type[] args = tt.getActualTypeArguments();
                                                if (args.length == 1
                                                                && "io.netty.channel.epoll.EpollEventLoopGroup".equals(args[0].getTypeName())) {
                                                        for (Method m : clz.getMethods()) {
                                                                if (m.getGenericReturnType() != m.getReturnType()) {
                                                                        try {
                                                                                return (EventLoopGroup) m.invoke(field.get(null));
                                                                        } catch (ReflectiveOperationException e) {
                                                                                throw Util.propagateReflectThrowable(e);
                                                                        }
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }
                }
                for (Field field : fields) {
                        Class<?> clz = field.getType();
                        if (clz.getSimpleName().equals("LazyInitVar")) {
                                Type type = field.getGenericType();
                                if (type instanceof ParameterizedType tt) {
                                        Type[] args = tt.getActualTypeArguments();
                                        if (args.length == 1 && "io.netty.channel.nio.NioEventLoopGroup".equals(args[0].getTypeName())) {
                                                for (Method m : clz.getMethods()) {
                                                        if (m.getGenericReturnType() != m.getReturnType()) {
                                                                try {
                                                                        return (EventLoopGroup) m.invoke(field.get(null));
                                                                } catch (ReflectiveOperationException e) {
                                                                        throw Util.propagateReflectThrowable(e);
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }
                }
                // Modern direct-field lookup (1.17+): ServerConnection has static
                // EpollEventLoopGroup/NioEventLoopGroup fields instead of LazyInitVar wrappers.
                Class<?> epollType = null;
                Class<?> nioType = null;
                try {
                        epollType = Class.forName("io.netty.channel.epoll.EpollEventLoopGroup");
                } catch (ClassNotFoundException e) {
                }
                try {
                        nioType = Class.forName("io.netty.channel.nio.NioEventLoopGroup");
                } catch (ClassNotFoundException e) {
                }
                if (enableNativeTransport && epollType != null) {
                        for (Field field : fields) {
                                if (epollType.isAssignableFrom(field.getType())) {
                                        try {
                                                Object val = field.get(null);
                                                if (val instanceof EventLoopGroup) {
                                                        return (EventLoopGroup) val;
                                                }
                                        } catch (ReflectiveOperationException e) {
                                                // ignore
                                        }
                                }
                        }
                }
                if (nioType != null) {
                        for (Field field : fields) {
                                if (nioType.isAssignableFrom(field.getType())) {
                                        try {
                                                Object val = field.get(null);
                                                if (val instanceof EventLoopGroup) {
                                                        return (EventLoopGroup) val;
                                                }
                                        } catch (ReflectiveOperationException e) {
                                                // ignore
                                        }
                                }
                        }
                }
                return null;
        }

        private static EventLoopGroup createOwnEventLoopGroup(boolean enableNativeTransport) {
                java.util.concurrent.ThreadFactory tf = createDefaultThreadFactory("Netty Server IO");
                if (enableNativeTransport) {
                        try {
                                Class<?> epollCls = Class.forName("io.netty.channel.epoll.EpollEventLoopGroup");
                                return (EventLoopGroup) epollCls.getConstructor(java.util.concurrent.ThreadFactory.class)
                                                .newInstance(tf);
                        } catch (ReflectiveOperationException e) {
                                // fall through to Nio
                        }
                }
                try {
                        Class<?> nioCls = Class.forName("io.netty.channel.nio.NioEventLoopGroup");
                        return (EventLoopGroup) nioCls.getConstructor(java.util.concurrent.ThreadFactory.class)
                                        .newInstance(tf);
                } catch (ReflectiveOperationException e) {
                        return new io.netty.channel.nio.NioEventLoopGroup();
                }
        }

        private static java.util.concurrent.ThreadFactory createDefaultThreadFactory(String name) {
                try {
                        Class<?> dtfCls = Class.forName("io.netty.util.concurrent.DefaultThreadFactory");
                        return (java.util.concurrent.ThreadFactory) dtfCls
                                        .getConstructor(String.class, boolean.class, int.class)
                                        .newInstance(name, true, Thread.NORM_PRIORITY);
                } catch (ReflectiveOperationException e) {
                        return java.util.concurrent.Executors.defaultThreadFactory();
                }
        }

}
