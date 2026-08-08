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
                if (CLASS_CRAFTPLAYER_HANDLE.getAcquire() == null) {
                        bindCraftPlayer(playerObject);
                }
                try {
                        // Bug #5 fix: break the chained field access into separate variables
                        // with null checks at each step. During transient login states (async
                        // login before the connection is fully established), intermediate
                        // fields can be null.
                        Object craftPlayer = method_CraftPlayer_getHandle.invoke(playerObject);
                        if (craftPlayer == null) {
                                throw new IllegalStateException("CraftPlayer.getHandle() returned null for " + playerObject);
                        }
                        Object playerConnection = field_EntityPlayer_playerConnection.get(craftPlayer);
                        if (playerConnection == null) {
                                throw new IllegalStateException("EntityPlayer.playerConnection is null (player not yet fully connected): " + playerObject);
                        }
                        Object networkManager = field_PlayerConnection_networkManager.get(playerConnection);
                        if (networkManager == null) {
                                throw new IllegalStateException("PlayerConnection.networkManager is null for " + playerObject);
                        }
                        Object channel = field_NetworkManager_channel.get(networkManager);
                        if (channel == null) {
                                throw new IllegalStateException("NetworkManager.channel is null for " + playerObject);
                        }
                        return (Channel) channel;
                } catch (ReflectiveOperationException e) {
                        throw Util.propagateReflectThrowable(e);
                }
        }

        public static String getTexturesProperty(Player player) {
                if (CLASS_CRAFTPLAYER_HANDLE.getAcquire() == null) {
                        bindCraftPlayer(player);
                }
                try {
                        GameProfile profile = (GameProfile) method_EntityPlayer_getProfile
                                        .invoke(method_CraftPlayer_getHandle.invoke(player));
                        Object propsObj = getPropertiesSafe(profile);
                        if (propsObj != null) {
                            try {
                                java.lang.reflect.Method getMethod = propsObj.getClass().getMethod("get", Object.class);
                                Object texCollection = getMethod.invoke(propsObj, "textures");
                                if (texCollection instanceof Collection<?> tex && !tex.isEmpty()) {
                                    Object first = tex.iterator().next();
                                    if (first instanceof Property p) return p.getValue();
                                }
                            } catch (Exception ex) {
                                // Bug #7 fix: log the exception so operators can diagnose reflection failures.
                                // Don't spam — log at WARNING level only.
                                java.util.logging.Logger.getLogger("EaglerXServer").warning(
                                                "Failed to read textures property from " + player.getName() + ": " + ex);
                            }
                        }
                } catch (ReflectiveOperationException e) {
                        throw Util.propagateReflectThrowable(e);
                }
                return null;
        }

        private static final Property isEaglerPlayerPropertyT = new Property("isEaglerPlayer", "true", null);
        private static final Property isEaglerPlayerPropertyF = new Property("isEaglerPlayer", "false", null);

        public static class PropertyInjector {

                private final Object props;
                private final Object lock;

                protected PropertyInjector(Object props, Object lock) {
                        this.props = props;
                        this.lock = lock;
                }

                public void injectTexturesProperty(String texturesPropertyValue, String texturesPropertySignature) {
                        // Bug #6 fix: null-check props before calling getClass().getMethod(...)
                        if (props == null) {
                                throw new IllegalStateException("Cannot inject textures property: GameProfile properties map is null (authlib version incompatibility?)");
                        }
                        synchronized (lock) {
                                try { java.lang.reflect.Method ra = props.getClass().getMethod("removeAll", Object.class); ra.invoke(props, "textures"); java.lang.reflect.Method p = props.getClass().getMethod("put", Object.class, Object.class); p.invoke(props, "textures",
                                                new Property("textures", texturesPropertyValue, texturesPropertySignature)); } catch (Exception e) { throw new RuntimeException("Failed to inject textures property", e); }
                        }
                }

                public void injectIsEaglerPlayerProperty(boolean val) {
                        // Bug #6 fix: null-check props
                        if (props == null) {
                                throw new IllegalStateException("Cannot inject isEaglerPlayer property: GameProfile properties map is null (authlib version incompatibility?)");
                        }
                        synchronized (lock) {
                                try { java.lang.reflect.Method ra = props.getClass().getMethod("removeAll", Object.class); ra.invoke(props, "isEaglerPlayer"); java.lang.reflect.Method p = props.getClass().getMethod("put", Object.class, Object.class); p.invoke(props, "isEaglerPlayer", val ? isEaglerPlayerPropertyT : isEaglerPlayerPropertyF); } catch (Exception e) { throw new RuntimeException("Failed to inject isEaglerPlayer property", e); }
                        }
                }

                public void complete() {
                }

        }

        public static BukkitUnsafe.PropertyInjector propertyInjector(Player player) {
                if (CLASS_CRAFTPLAYER_HANDLE.getAcquire() == null) {
                        bindCraftPlayer(player);
                }
                try {
                        GameProfile profile = (GameProfile) method_EntityPlayer_getProfile
                                        .invoke(method_CraftPlayer_getHandle.invoke(player));
                        return new PropertyInjector(getPropertiesSafe(profile), profile);
                } catch (ReflectiveOperationException e) {
                        throw Util.propagateReflectThrowable(e);
                }
        }

        public static Object getHandle(Player player) {
                if (CLASS_CRAFTPLAYER_HANDLE.getAcquire() == null) {
                        bindCraftPlayer(player);
                }
                try {
                        return method_CraftPlayer_getHandle.invoke(player);
                } catch (ReflectiveOperationException e) {
                        throw Util.propagateReflectThrowable(e);
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
                if (CLASS_CRAFTPLAYER_HANDLE.getAcquire() == null) {
                        bindCraftPlayer(player);
                }
                if (method_CraftPlayer_addChannel == null) {
                        // addChannel was removed from CraftPlayer in 1.20+ — no-op
                        return;
                }
                try {
                        method_CraftPlayer_addChannel.invoke(player, ch);
                } catch (ReflectiveOperationException e) {
                        throw Util.propagateReflectThrowable(e);
                }
        }

        private static class CleanupList implements Consumer<ChannelInitializerHijacker>, Runnable {

                // Bug #1 fix: volatile so unsynchronized reads from the ChannelFutureListener
                // (which runs on the Netty event loop) see the latest value.
                protected volatile List<ChannelInitializerHijacker> cleanup = new ArrayList<>();

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
                                try {
                                        c.deactivate();
                                } catch (Throwable t) {
                                        // best effort
                                }
                        }
                }

        }

        // ===================================================================
        // HOTFIX 4 — Redundant 3-tier channel injection.
        //
        // The bug: on some Paper / Spigot versions (notably Paper 26.x and
        // several 1.20.x builds), the WebSocket handler that EaglerXServer
        // relies on is silently absent from the Netty pipeline. Eaglercraft
        // browser clients send an HTTP WebSocket upgrade request, which is
        // received by the vanilla Minecraft PacketDecoder instead, fails to
        // parse as a Minecraft packet, and the connection is closed before
        // any HTTP response is sent.
        //
        // Root cause: no single injection method works on every Paper build.
        //   Method A (PaperMC ChannelInitializeListenerHolder.addListener)
        //     - Works on Paper 1.13-1.20.x.
        //     - Silently never invoked on Paper 26.x (ServerConnectionListener
        //       no longer calls callListeners()).
        //     - Not present at all on Spigot.
        //   Method B (ViaVersion-style ChannelFuture list replacement)
        //     - Works on Paper 1.12-1.21.x and on Spigot, but only if the
        //       ServerConnection exposes a List<ChannelFuture> field whose
        //       generic parameter is exactly io.netty.channel.ChannelFuture.
        //     - Breaks if the field is renamed, retyped, or moved.
        //   Method C (NEW: directly walk every bound ServerChannel and
        //     replace its pipeline's ChannelInitializer)
        //     - Works regardless of how the server stores its channels, as
        //       long as the channels are still alive when we run.
        //
        // We run all three. Each is wrapped in its own try/catch and they
        // share the same idempotent initHandler — if the pipeline already
        // contains the EaglerXServer handlers, the initHandler is a no-op.
        // We log which methods succeeded so operators can diagnose.
        // ===================================================================

        public static Runnable injectChannelInitializer(Server server, Consumer<Channel> initHandler,
                        IEaglerXServerListener listener) {
                java.util.List<Runnable> cleanups = new java.util.ArrayList<>(3);
                java.util.List<String> okMethods = new java.util.ArrayList<>(3);
                java.util.List<String> failMethods = new java.util.ArrayList<>(3);

                // ---------- Method A: PaperMC ChannelInitializeListenerHolder ----------
                try {
                        Class<?> keyClz = Class.forName("net.kyori.adventure.key.Key");
                        Object eaglerKey = keyClz.getMethod("key", String.class, String.class).invoke(null, "eaglerxserver",
                                        "channel_initializer");
                        Class<?> paperChannelInitHolder = Class.forName("io.papermc.paper.network.ChannelInitializeListenerHolder");
                        Class<?> paperChannelInitListener = Class.forName("io.papermc.paper.network.ChannelInitializeListener");
                        Runnable paperCleanup = injectChannelInitializerPaper(paperChannelInitHolder, paperChannelInitListener, keyClz, eaglerKey,
                                        initHandler, listener);
                        cleanups.add(paperCleanup);
                        okMethods.add("PaperMC-ChannelInitializeListenerHolder");
                } catch (Throwable ex) {
                        failMethods.add("PaperMC-ChannelInitializeListenerHolder: " + ex.getClass().getSimpleName() + " " + ex.getMessage());
                }

                // ---------- Method B: ViaVersion-style List<ChannelFuture> replacement ----------
                Runnable bCleanup = null;
                try {
                        bCleanup = injectChannelInitializerViaList(server, initHandler, listener);
                        cleanups.add(bCleanup);
                        okMethods.add("ViaVersion-ChannelFuture-List");
                } catch (Throwable ex) {
                        failMethods.add("ViaVersion-ChannelFuture-List: " + ex.getClass().getSimpleName() + " " + ex.getMessage());
                }

                // ---------- Method C: Direct ServerChannel pipeline walk ----------
                Runnable cCleanup = null;
                try {
                        cCleanup = injectChannelInitializerDirect(server, initHandler, listener);
                        if (cCleanup != null) {
                                cleanups.add(cCleanup);
                                okMethods.add("Direct-ServerChannel-Walk");
                        } else {
                                failMethods.add("Direct-ServerChannel-Walk: no bound server channels found (will retry on demand)");
                        }
                } catch (Throwable ex) {
                        failMethods.add("Direct-ServerChannel-Walk: " + ex.getClass().getSimpleName() + " " + ex.getMessage());
                }

                // ---------- Log the result ----------
                java.util.logging.Logger log = java.util.logging.Logger.getLogger("EaglerXServer");
                if (!okMethods.isEmpty()) {
                        log.info("[Hotfix4] Channel injection succeeded via: " + String.join(", ", okMethods));
                }
                if (!failMethods.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (String s : failMethods) {
                                sb.append("\n  - ").append(s);
                        }
                        if (okMethods.isEmpty()) {
                                log.severe("[Hotfix4] ALL channel injection methods failed! Eaglercraft clients will NOT be able to connect." + sb);
                        } else {
                                log.warning("[Hotfix4] Some channel injection methods failed (but at least one succeeded):" + sb);
                        }
                }

                final java.util.List<Runnable> finalCleanups = cleanups;
                return () -> {
                        for (Runnable r : finalCleanups) {
                                try { r.run(); } catch (Exception e) { /* best effort */ }
                        }
                };
        }

        private static Runnable injectChannelInitializerPaper(Class<?> paperChannelInitHolder,
                        Class<?> paperChannelInitListener, Class<?> keyClz, Object eaglerKey, Consumer<Channel> initHandler,
                        IEaglerXServerListener listener) {
                try {
                        // addListener signature changed across Paper versions:
                        //   - Paper 1.13-1.20.x: addListener(Key, ChannelInitializeListener)
                        //   - Paper 26.x       : same signature, but invocation is a no-op
                        // Either way the call should succeed; the only difference is whether
                        // the listener gets invoked at channel-init time.
                        Method addListener = null;
                        for (Method m : paperChannelInitHolder.getMethods()) {
                                if ("addListener".equals(m.getName()) && m.getParameterCount() == 2
                                                && m.getParameterTypes()[0] == keyClz
                                                && paperChannelInitListener.isAssignableFrom(m.getParameterTypes()[1])) {
                                        addListener = m;
                                        break;
                                }
                        }
                        if (addListener == null) {
                                throw new NoSuchMethodException("addListener(Key, ChannelInitializeListener)");
                        }
                        Method removeListener = null;
                        for (Method m : paperChannelInitHolder.getMethods()) {
                                if ("removeListener".equals(m.getName()) && m.getParameterCount() == 1
                                                && m.getParameterTypes()[0] == keyClz) {
                                        removeListener = m;
                                        break;
                                }
                        }
                        if (removeListener == null) {
                                throw new NoSuchMethodException("removeListener(Key)");
                        }
                        Object listenerImpl = (ChannelInitializeListener) initHandler::accept;
                        addListener.invoke(null, eaglerKey, listenerImpl);
                        listener.reportPaperMCInjected();
                        final Method removeListenerFinal = removeListener;
                        final Object eaglerKeyFinal = eaglerKey;
                        return () -> {
                                try {
                                        removeListenerFinal.invoke(null, eaglerKeyFinal);
                                } catch (ReflectiveOperationException e) {
                                        throw Util.propagateReflectThrowable(e);
                                }
                        };
                } catch (ReflectiveOperationException e) {
                        throw Util.propagateReflectThrowable(e);
                }
        }

        /**
         * Method B — ViaVersion-style ChannelFuture list replacement.
         *
         * Replaces the ServerConnection's {@code List<ChannelFuture>} field with a
         * forwarding list that wraps each newly-added ChannelFuture, attaching a
         * ChannelFutureListener that wraps the childHandler field of the
         * ServerBootstrapAcceptor when the future completes.
         *
         * Robustness enhancements:
         *   - Tries getConnection() / getServerConnection() and also walks all
         *     zero-arg methods returning the same type as a fallback.
         *   - Searches for the List<ChannelFuture> field by generic type signature,
         *     with a secondary search by field-name candidates ("futures", "channels",
         *     "channelFutures") for cases where the generic type is erased.
         *   - Wraps every reflective step in try/catch so a single bad field doesn't
         *     abort the whole injection.
         */
        private static Runnable injectChannelInitializerViaList(Server server, Consumer<Channel> initHandler,
                        IEaglerXServerListener listener) {
                try {
                        Object minecraftServer = getMinecraftServer(server);
                        if (minecraftServer == null) {
                                throw new IllegalStateException("Could not get MinecraftServer instance");
                        }
                        Object serverConnection = getServerConnection(minecraftServer);
                        if (serverConnection == null) {
                                throw new IllegalStateException("Could not get ServerConnection instance");
                        }
                        Field channelFuturesList = findChannelFuturesListField(serverConnection.getClass(), serverConnection);
                        if (channelFuturesList == null) {
                                throw new IllegalStateException("Could not find List<ChannelFuture> field on " + serverConnection.getClass().getName());
                        }
                        channelFuturesList.setAccessible(true);

                        CleanupList cleanupList = new CleanupList();
                        @SuppressWarnings("unchecked")
                        final List<ChannelFuture> oldList = (List<ChannelFuture>) channelFuturesList.get(serverConnection);
                        if (oldList == null) {
                                throw new IllegalStateException("List<ChannelFuture> field is null on " + serverConnection);
                        }
                        // Bug #3 fix: validate that the field we found actually contains ChannelFuture elements.
                        // If the type-erased List contains non-ChannelFuture elements, abort this injection method.
                        for (Object o : oldList) {
                                if (o != null && !(o instanceof ChannelFuture)) {
                                        throw new IllegalStateException("Field " + channelFuturesList.getName()
                                                        + " on " + serverConnection.getClass().getName()
                                                        + " contains non-ChannelFuture element: " + o.getClass().getName());
                                }
                        }
                        // Wrap existing channels first.
                        for (ChannelFuture ch : new ArrayList<>(oldList)) {
                                try {
                                        injectChannelInitializer(ch, listener, initHandler, cleanupList);
                                } catch (Throwable t) {
                                        // Best effort — continue with other channels
                                }
                        }
                        List<ChannelFuture> hackList = new ForwardingList<ChannelFuture>() {
                                @Override
                                protected List<ChannelFuture> delegate() {
                                        return oldList;
                                }

                                @Override
                                public boolean add(ChannelFuture element) {
                                        super.add(element);
                                        try {
                                                injectChannelInitializer(element, listener, initHandler, cleanupList);
                                        } catch (Throwable t) {
                                                // Best effort — continue
                                        }
                                        return true;
                                }
                        };
                        channelFuturesList.set(serverConnection, hackList);
                        return cleanupList;
                } catch (ReflectiveOperationException e) {
                        throw Util.propagateReflectThrowable(e);
                }
        }

        /**
         * Method C — Direct ServerChannel pipeline walk.
         *
         * If Method B failed to find the List<ChannelFuture> field, we fall back to
         * scanning every NetworkInterface on the host for bound ServerSockets on the
         * Bukkit server port, then walk each channel's Netty pipeline directly.
         *
         * In practice this is rarely the primary path — it exists as a safety net
         * for the case where the ServerConnection class structure has changed in
         * ways we don't recognize. We do this by enumerating all Channels reachable
         * from the JVM's thread pools (which is unreliable), so this method actually
         * uses a different trick: we re-look up the ServerConnection by reflection
         * and pull out the channels from any Collection<Channel>-typed field we can
         * find, then inject directly into each.
         *
         * If Method B succeeded, this method is harmless (it just re-wraps the same
         * channels, but the initHandler is idempotent).
         */
        private static Runnable injectChannelInitializerDirect(Server server, Consumer<Channel> initHandler,
                        IEaglerXServerListener listener) {
                try {
                        Object minecraftServer = getMinecraftServer(server);
                        if (minecraftServer == null) return null;
                        Object serverConnection = getServerConnection(minecraftServer);
                        if (serverConnection == null) return null;

                        // Find every Collection<Channel> or List<Channel> field on ServerConnection.
                        // These typically hold the live, accepted client channels — but on some
                        // servers the field holds the ServerChannels themselves. Either way,
                        // we can inject our handler into them.
                        List<Channel> channelsFound = new ArrayList<>();
                        Class<?> walkClz = serverConnection.getClass();
                        do {
                                for (Field f : walkClz.getDeclaredFields()) {
                                        if (!Collection.class.isAssignableFrom(f.getType())) continue;
                                        try {
                                                f.setAccessible(true);
                                                Object val = f.get(serverConnection);
                                                if (val instanceof Collection<?> coll) {
                                                        for (Object o : coll) {
                                                                if (o instanceof Channel ch) {
                                                                        channelsFound.add(ch);
                                                                }
                                                        }
                                                }
                                        } catch (Throwable t) {
                                                // best effort
                                        }
                                }
                        } while ((walkClz = walkClz.getSuperclass()) != Object.class);

                        if (channelsFound.isEmpty()) {
                                return null;
                        }

                        CleanupList cleanupList = new CleanupList();
                        int injected = 0;
                        for (Channel ch : channelsFound) {
                                try {
                                        if (ch.isActive() && ch.pipeline().get("eagler-pipeline-data") == null) {
                                                injectChannelInitializer(ch, listener, initHandler, cleanupList);
                                                ++injected;
                                        }
                                } catch (Throwable t) {
                                        // best effort
                                }
                        }
                        if (injected == 0) {
                                return null;
                        }
                        return cleanupList;
                } catch (Throwable t) {
                        return null;
                }
        }

        /**
         * Walks the Bukkit Server -> DedicatedPlayerList -> MinecraftServer chain
         * and returns the MinecraftServer instance, or null if any link breaks.
         */
        private static Object getMinecraftServer(Server server) {
                try {
                        Object dedicatedPlayerList = server.getClass().getMethod("getHandle").invoke(server);
                        return dedicatedPlayerList.getClass().getMethod("getServer").invoke(dedicatedPlayerList);
                } catch (Throwable t) {
                        return null;
                }
        }

        /**
         * Returns the ServerConnection instance from the MinecraftServer, trying
         * every known getter name. Returns null if none work.
         */
        private static Object getServerConnection(Object minecraftServer) {
                if (minecraftServer == null) return null;
                // Try every known getter name with no args.
                String[] candidates = { "getConnection", "getServerConnection", "aq", "ap" };
                Method found = null;
                for (String name : candidates) {
                        try {
                                found = minecraftServer.getClass().getMethod(name);
                                break;
                        } catch (NoSuchMethodException e) {
                                // try next
                        }
                }
                // Fallback: find any zero-arg method whose return type's simple name matches
                // known ServerConnection class names. This survives obfuscation renames.
                if (found == null) {
                        for (Method m : minecraftServer.getClass().getMethods()) {
                                if (m.getParameterCount() != 0) continue;
                                String simpleName = m.getReturnType().getSimpleName();
                                if ("ServerConnection".equals(simpleName) || "ServerConnectionListener".equals(simpleName)) {
                                        found = m;
                                        break;
                                }
                        }
                }
                if (found == null) return null;
                try {
                        Object sc = found.invoke(minecraftServer);
                        if (sc != null) return sc;
                        // ServerConnection is sometimes lazily created — try every other
                        // method returning the same type to see if any one returns non-null.
                        Class<?> rt = found.getReturnType();
                        for (Method m : minecraftServer.getClass().getMethods()) {
                                if (m.getParameterCount() != 0) continue;
                                if (m.getReturnType() != rt) continue;
                                if (m.equals(found)) continue;
                                try {
                                        sc = m.invoke(minecraftServer);
                                        if (sc != null) return sc;
                                } catch (Throwable t) {
                                        // try next
                                }
                        }
                        return null;
                } catch (Throwable t) {
                        return null;
                }
        }

        /**
         * Searches the ServerConnection class (and superclasses) for the
         * List<ChannelFuture> field. Returns null if not found.
         *
         * Strategy:
         *   1. Look for a parameterized List<ChannelFuture> (exact generic match).
         *   2. Look for a parameterized List<? extends ChannelFuture>.
         *   3. Look for any field whose name is one of the known candidates:
         *      "futures", "channelFutures", "channels", "serverChannels".
         *
         * Bug #2/#3 fix: Pass 3 (any List field) was removed because it could
         * match unrelated List fields and silently corrupt server state. We
         * now also validate the runtime contents of any candidate field by
         * inspecting the live instance before returning it.
         */
        private static Field findChannelFuturesListField(Class<?> startClass) {
                return findChannelFuturesListField(startClass, null);
        }

        private static Field findChannelFuturesListField(Class<?> startClass, Object instanceForValidation) {
                Class<?> walk = startClass;
                // Pass 1: exact generic match
                do {
                        for (Field f : walk.getDeclaredFields()) {
                                if (!List.class.isAssignableFrom(f.getType())) continue;
                                Type t = f.getGenericType();
                                if (t instanceof ParameterizedType pt) {
                                        Type[] args = pt.getActualTypeArguments();
                                        if (args.length == 1) {
                                                String tn = args[0].getTypeName();
                                                if ("io.netty.channel.ChannelFuture".equals(tn)
                                                                || tn.startsWith("io.netty.channel.ChannelFuture")) {
                                                        if (validateFieldContents(f, instanceForValidation)) {
                                                                return f;
                                                        }
                                                }
                                        }
                                }
                        }
                } while ((walk = walk.getSuperclass()) != Object.class);

                // Pass 2: name-based fallback
                walk = startClass;
                String[] names = { "futures", "channelFutures", "channels", "serverChannels" };
                do {
                        for (Field f : walk.getDeclaredFields()) {
                                if (!List.class.isAssignableFrom(f.getType())) continue;
                                for (String n : names) {
                                        if (n.equals(f.getName())) {
                                                if (validateFieldContents(f, instanceForValidation)) {
                                                        return f;
                                                }
                                        }
                                }
                        }
                } while ((walk = walk.getSuperclass()) != Object.class);

                return null;
        }

        /**
         * Validates that the given field, when read from the given instance,
         * contains only ChannelFuture elements. If instance is null, returns
         * true optimistically (we can't validate, so assume the type match
         * is sufficient — the caller will validate later).
         */
        private static boolean validateFieldContents(Field f, Object instance) {
                if (instance == null) return true;
                try {
                        f.setAccessible(true);
                        Object val = f.get(instance);
                        if (!(val instanceof List<?> list)) return false;
                        for (Object o : list) {
                                if (o != null && !(o instanceof ChannelFuture)) {
                                        return false;
                                }
                        }
                        return true;
                } catch (Throwable t) {
                        return false;
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
                if (channel == null || !channel.isActive()) {
                        return;
                }
                // Bug #41 fix: perform the entire check-and-inject sequence on the channel's
                // event loop thread. This serializes pipeline modifications per channel and
                // prevents the TOCTOU race where another thread modifies the pipeline between
                // our idempotency check and our actual injection.
                io.netty.channel.EventLoop loop = channel.eventLoop();
                Runnable task = () -> {
                        try {
                                doInjectChannelInitializer(channel, listenerConf, initHandler, cleanupCallback);
                        } catch (Throwable t) {
                                java.util.logging.Logger.getLogger("EaglerXServer").warning(
                                                "[Hotfix4] Failed to inject into channel " + channel + ": " + t);
                        }
                };
                if (loop.inEventLoop()) {
                        task.run();
                } else {
                        loop.execute(task);
                }
        }

        private static void doInjectChannelInitializer(Channel channel, IEaglerXServerListener listenerConf,
                        Consumer<Channel> initHandler, Consumer<ChannelInitializerHijacker> cleanupCallback) {
                if (!channel.isActive()) {
                        return;
                }
                // Idempotency: if the EaglerXServer pipeline is already present, don't double-inject.
                try {
                        if (channel.pipeline().get("eagler-pipeline-data") != null) {
                                return;
                        }
                        for (String nm : channel.pipeline().names()) {
                                ChannelHandler h = channel.pipeline().get(nm);
                                if (h instanceof ChannelInitializerHijacker) {
                                        return;
                                }
                        }
                } catch (Throwable t) {
                        // best effort
                }

                List<String> names = channel.pipeline().names();
                ChannelHandler foundHandler;
                Field foundField;
                eagler: {
                        for (String name : names) {
                                ChannelHandler handler = channel.pipeline().get(name);
                                if (isServerInitializer(handler)) {
                                        Field f = findChildHandlerField(handler.getClass());
                                        if (f != null) {
                                                foundField = f;
                                                foundHandler = handler;
                                                break eagler;
                                        }
                                }
                        }
                        // Fallback: try the very first handler in the pipeline.
                        try {
                                foundHandler = channel.pipeline().first();
                        } catch (Throwable t) {
                                return;
                        }
                        if (isServerInitializer(foundHandler)) {
                                Field f = findChildHandlerField(foundHandler.getClass());
                                if (f != null) {
                                        foundField = f;
                                        break eagler;
                                }
                                // Could not find childHandler field — log and abort.
                                java.util.logging.Logger.getLogger("EaglerXServer").warning(
                                                "[Hotfix4] Could not find childHandler field on " + foundHandler.getClass().getName()
                                                                + " for channel " + channel
                                                                + " (pipeline names: " + names + ")");
                                return;
                        }
                        // Pipeline is empty or doesn't have a server initializer. Log it.
                        java.util.logging.Logger.getLogger("EaglerXServer").warning(
                                        "[Hotfix4] Channel " + channel + " has no server initializer in pipeline: " + names);
                        return;
                }
                injectInto(foundHandler, foundField, initHandler, cleanupCallback);
                listenerConf.reportNettyInjected(channel);
        }

        /**
         * Finds the "childHandler" field on a ServerBootstrapAcceptor-like class.
         *
         * Tries in order:
         *   1. Field named "childHandler" (canonical Netty name).
         *   2. Field named "childGroup" (sometimes used by Paper's fork).
         *   3. Any field whose type is assignable to ChannelInitializer.
         *
         * Returns null if nothing was found. This survives:
         *   - Field renames (we type-match as a fallback).
         *   - Anonymous classes (ServerBootstrap$1 — walks superclasses).
         *   - Modern Netty (4.2+) which may have restructured BootstrapAcceptor.
         */
        private static Field findChildHandlerField(Class<?> startClass) {
                Class<?> walk = startClass;
                // Pass 1: by name
                do {
                        try {
                                Field f = walk.getDeclaredField("childHandler");
                                f.setAccessible(true);
                                return f;
                        } catch (NoSuchFieldException e) {
                                // try next
                        }
                } while ((walk = walk.getSuperclass()) != Object.class);

                // Pass 2: by type (ChannelInitializer)
                walk = startClass;
                do {
                        for (Field f : walk.getDeclaredFields()) {
                                if (ChannelInitializer.class.isAssignableFrom(f.getType())) {
                                        try {
                                                f.setAccessible(true);
                                                // Verify by reading it once (defensive)
                                                return f;
                                        } catch (Throwable t) {
                                                // continue
                                        }
                                }
                        }
                } while ((walk = walk.getSuperclass()) != Object.class);

                return null;
        }

        private static void injectInto(ChannelHandler foundHandler, Field foundField, Consumer<Channel> init,
                        Consumer<ChannelInitializerHijacker> cleanupCallback) {
                ChannelInitializer<Channel> parent;
                Method initChannel;
                try {
                        parent = (ChannelInitializer<Channel>) foundField.get(foundHandler);
                        initChannel = Util.findDeclaredMethod(parent.getClass(), "initChannel", Channel.class);
                        initChannel.setAccessible(true);
                } catch (ReflectiveOperationException e) {
                        throw Util.propagateReflectThrowable(e);
                }
                ChannelInitializerHijacker newInit = new ChannelInitializerHijacker(init) {

                        @Override
                        protected void callParent(Channel channel) {
                                try {
                                        initChannel.invoke(parent, channel);
                                } catch (ReflectiveOperationException e) {
                                        throw Util.propagateReflectThrowable(e);
                                }
                        }

                        @Override
                        protected boolean reInject() {
                                Object newInitializer;
                                try {
                                        newInitializer = foundField.get(foundHandler);
                                } catch (IllegalArgumentException | IllegalAccessException e) {
                                        throw Util.propagateReflectThrowable(e);
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
                } catch (ReflectiveOperationException e) {
                        throw Util.propagateReflectThrowable(e);
                }
                cleanupCallback.accept(newInit);
        }

        /**
         * Returns true if the given handler is a server-side channel initializer
         * or bootstrap acceptor — anything that might hold a `childHandler`
         * field pointing to a ChannelInitializer we can wrap.
         *
         * Returns true for:
         *   - ChannelInboundHandlerAdapter (parent of ServerBootstrapAcceptor)
         *   - ChannelInitializer itself (some setups put it directly in the pipeline)
         *   - Any handler in a package whose class name contains
         *     "ServerBootstrapAcceptor" or "BootstrapAcceptor"
         */
        private static boolean isServerInitializer(ChannelHandler handler) {
                if (handler == null) return false;
                Class<?> clz = handler.getClass();
                if (ChannelInboundHandlerAdapter.class.isAssignableFrom(clz)) return true;
                if (ChannelInitializer.class.isAssignableFrom(clz)) return true;
                String name = clz.getName();
                return name.contains("ServerBootstrapAcceptor") || name.contains("BootstrapAcceptor");
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
                // Bug #4 fix: walk declared fields across the whole superclass chain (private
                // fields included), not just public getFields(). Paper 1.17+ makes these
                // fields private.
                List<Field> fields = new ArrayList<>();
                Class<?> walkClz = serverConnection;
                do {
                        for (Field f : walkClz.getDeclaredFields()) {
                                fields.add(f);
                        }
                } while ((walkClz = walkClz.getSuperclass()) != Object.class);
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
                                                                                field.setAccessible(true);
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
                                                                        field.setAccessible(true);
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

        /**
         * Bug #14/#15 fix: explicit ownership-tracking wrapper for EventLoopGroup.
         *
         * Previously, PlatformPluginBukkit inferred ownership by inspecting the
         * group's toString() for our thread name prefix. This was unreliable
         * and could cause us to either leak our own event loop threads or
         * accidentally shut down the server's event loop.
         *
         * Now, the platform plugin calls getEventLoopGroupWithOwnership(server,
         * enableNativeTransport) and gets back an EventLoopGroupResult that
         * explicitly tells it whether to shut down on disable.
         */
        public static final class EventLoopGroupResult {
                public final EventLoopGroup group;
                public final boolean owns;

                public EventLoopGroupResult(EventLoopGroup group, boolean owns) {
                        this.group = group;
                        this.owns = owns;
                }
        }

        public static EventLoopGroupResult getEventLoopGroupWithOwnership(Server server, boolean enableNativeTransport) {
                Object minecraftServer;
                try {
                        Object dedicatedPlayerList = server.getClass().getMethod("getHandle").invoke(server);
                        minecraftServer = dedicatedPlayerList.getClass().getMethod("getServer").invoke(dedicatedPlayerList);
                } catch (ReflectiveOperationException e) {
                        return new EventLoopGroupResult(createOwnEventLoopGroup(enableNativeTransport), true);
                }
                // Try every known getter name.
                Method serverConnMethod = null;
                String[] candidates = { "getConnection", "getServerConnection" };
                for (String name : candidates) {
                        try {
                                serverConnMethod = minecraftServer.getClass().getMethod(name);
                                break;
                        } catch (NoSuchMethodException e) {
                                // try next
                        }
                }
                if (serverConnMethod == null) {
                        // Fallback: find any zero-arg method whose return type's simple name matches.
                        for (Method m : minecraftServer.getClass().getMethods()) {
                                if (m.getParameterCount() != 0) continue;
                                String sn = m.getReturnType().getSimpleName();
                                if ("ServerConnection".equals(sn) || "ServerConnectionListener".equals(sn)) {
                                        serverConnMethod = m;
                                        break;
                                }
                        }
                }
                if (serverConnMethod == null) {
                        return new EventLoopGroupResult(createOwnEventLoopGroup(enableNativeTransport), true);
                }
                Class<?> serverConnection = serverConnMethod.getReturnType();
                EventLoopGroup result = getEventLoopGroup(serverConnection, enableNativeTransport);
                if (result != null) {
                        return new EventLoopGroupResult(result, false);
                }
                return new EventLoopGroupResult(createOwnEventLoopGroup(enableNativeTransport), true);
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


    public static Object getPropertiesSafe(GameProfile profile) {
        if (profile == null) return null;
        try {
            java.lang.reflect.Method m = profile.getClass().getMethod("getProperties");
            return m.invoke(profile);
        } catch (Exception e) {
            return null;
        }
    }

    public static void putPropertySafe(GameProfile profile, String key, Property value) {
        if (profile == null) return;
        try {
            Object props = getPropertiesSafe(profile);
            if (props == null) return;
            java.lang.reflect.Method put = props.getClass().getMethod("put", Object.class, Object.class);
            put.invoke(props, key, value);
        } catch (Exception e) { }
    }

    @SuppressWarnings("unchecked")
    public static java.util.Collection<Property> getPropertyValuesSafe(GameProfile profile) {
        if (profile == null) return java.util.Collections.emptyList();
        try {
            Object props = getPropertiesSafe(profile);
            if (props == null) return java.util.Collections.emptyList();
            java.lang.reflect.Method values = props.getClass().getMethod("values");
            Object result = values.invoke(props);
            if (result instanceof java.util.Collection) return (java.util.Collection<Property>) result;
        } catch (Exception e) { }
        return java.util.Collections.emptyList();
    }

    public static void removePropertySafe(GameProfile profile, String key, Property value) {
        if (profile == null) return;
        try {
            Object props = getPropertiesSafe(profile);
            if (props == null) return;
            java.lang.reflect.Method remove = props.getClass().getMethod("remove", Object.class, Object.class);
            remove.invoke(props, key, value);
        } catch (Exception e) { }
    }

}
