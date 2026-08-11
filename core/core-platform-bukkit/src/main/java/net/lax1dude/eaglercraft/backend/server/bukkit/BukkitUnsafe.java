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
import io.netty.channel.ChannelPipeline;
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
                        Object craftPlayer = method_CraftPlayer_getHandle.invoke(playerObject);
                        if (craftPlayer == null) return null;
                        Object playerConnection = field_EntityPlayer_playerConnection.get(craftPlayer);
                        if (playerConnection == null) return null;
                        Object networkManager = field_PlayerConnection_networkManager.get(playerConnection);
                        if (networkManager == null) return null;
                        Object channel = field_NetworkManager_channel.get(networkManager);
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
                        Object props = getPropertiesSafe(profile);
                        if (props == null) return null;
                        Object texCollection = multimapGet(props, "textures");
                        if (texCollection instanceof Collection<?> tex && !tex.isEmpty()) {
                                Object first = tex.iterator().next();
                                if (first instanceof Property p) return getPropertyValue(p);
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
                        synchronized (lock) {
                                multimapRemoveAll(props, "textures");
                                multimapPut(props, "textures",
                                                new Property("textures", texturesPropertyValue, texturesPropertySignature));
                        }
                }

                public void injectIsEaglerPlayerProperty(boolean val) {
                        synchronized (lock) {
                                multimapRemoveAll(props, "isEaglerPlayer");
                                multimapPut(props, "isEaglerPlayer", val ? isEaglerPlayerPropertyT : isEaglerPlayerPropertyF);
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

                protected List<ChannelInitializerHijacker> cleanup = new ArrayList<>();

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
                                if (cleanup == null) return;
                                cc = new ArrayList<>(cleanup);
                                cleanup = null;
                        }
                        for (ChannelInitializerHijacker c : cc) {
                                try { c.deactivate(); } catch (Throwable t) {}
                        }
                }

        }

        public static Runnable injectChannelInitializer(Server server, Consumer<Channel> initHandler,
                        IEaglerXServerListener listener) {
                Class<?> keyClz;
                Object eaglerKey;
                Class<?> paperChannelInitHolder;
                Class<?> paperChannelInitListener;
                Runnable primaryCleanup;
                try {
                        keyClz = Class.forName("net.kyori.adventure.key.Key");
                        eaglerKey = keyClz.getMethod("key", String.class, String.class).invoke(null, "eaglerxserver",
                                        "channel_initializer");
                        paperChannelInitHolder = Class.forName("io.papermc.paper.network.ChannelInitializeListenerHolder");
                        paperChannelInitListener = Class.forName("io.papermc.paper.network.ChannelInitializeListener");
                        primaryCleanup = injectChannelInitializerPaper(paperChannelInitHolder, paperChannelInitListener, keyClz, eaglerKey,
                                        initHandler, listener);
                } catch (ReflectiveOperationException ex) {
                        primaryCleanup = injectChannelInitializerOld(server, initHandler, listener);
                }
                // Hotfix 7: ALSO install the backup injection method for redundancy.
                // Both methods are idempotent, so having both active is harmless.
                Runnable backupCleanup = injectChannelInitializerBackup(server, initHandler, listener);
                final Runnable pc = primaryCleanup;
                final Runnable bc = backupCleanup;
                return () -> {
                        try { if (pc != null) pc.run(); } catch (Throwable t) {}
                        try { if (bc != null) bc.run(); } catch (Throwable t) {}
                };
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
                List<Field> fields = new ArrayList<>();
                Class<?> walkClz = serverConnection;
                do {
                        for (Field f : walkClz.getDeclaredFields()) fields.add(f);
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
                                                field.setAccessible(true);
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
                                                field.setAccessible(true);
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

        // ===================================================================
        // HOTFIX 7 — authlib 6.x full compatibility helpers + redundant
        // channel injection + pipeline safety.
        // ===================================================================

        private static volatile Method propertyGetNameMethod = null;
        private static volatile Method propertyGetValueMethod = null;
        private static volatile Method getPropertiesMethod = null;
        private static volatile boolean propertyMethodsInit = false;

        private static synchronized void initPropertyMethods() {
                if (propertyMethodsInit) return;
                try { propertyGetNameMethod = Property.class.getMethod("getName"); }
                catch (NoSuchMethodException e) { try { propertyGetNameMethod = Property.class.getMethod("name"); } catch (NoSuchMethodException e2) { propertyGetNameMethod = null; } }
                try { propertyGetValueMethod = Property.class.getMethod("getValue"); }
                catch (NoSuchMethodException e) { try { propertyGetValueMethod = Property.class.getMethod("value"); } catch (NoSuchMethodException e2) { propertyGetValueMethod = null; } }
                try { getPropertiesMethod = GameProfile.class.getMethod("getProperties"); }
                catch (NoSuchMethodException e) { getPropertiesMethod = null; }
                propertyMethodsInit = true;
        }

        public static String getPropertyName(Property prop) {
                if (prop == null) return null;
                if (!propertyMethodsInit) initPropertyMethods();
                if (propertyGetNameMethod == null) return null;
                try { return (String) propertyGetNameMethod.invoke(prop); } catch (Exception e) { return null; }
        }

        public static String getPropertyValue(Property prop) {
                if (prop == null) return null;
                if (!propertyMethodsInit) initPropertyMethods();
                if (propertyGetValueMethod == null) return null;
                try { return (String) propertyGetValueMethod.invoke(prop); } catch (Exception e) { return null; }
        }

        public static Object getPropertiesSafe(GameProfile profile) {
                if (profile == null) return null;
                if (!propertyMethodsInit) initPropertyMethods();
                if (getPropertiesMethod == null) return null;
                try { return getPropertiesMethod.invoke(profile); } catch (Exception e) { return null; }
        }

        public static Object multimapGet(Object mm, String key) {
                if (mm == null) return null;
                try { return mm.getClass().getMethod("get", Object.class).invoke(mm, key); } catch (Exception e) { return null; }
        }

        public static void multimapPut(Object mm, String key, Object value) {
                if (mm == null || key == null) return;
                try { mm.getClass().getMethod("put", Object.class, Object.class).invoke(mm, key, value); } catch (Exception e) { }
        }

        public static void multimapRemove(Object mm, String key, Object value) {
                if (mm == null || key == null) return;
                try { mm.getClass().getMethod("remove", Object.class, Object.class).invoke(mm, key, value); } catch (Exception e) { }
        }

        public static void multimapRemoveAll(Object mm, String key) {
                if (mm == null || key == null) return;
                try { mm.getClass().getMethod("removeAll", Object.class).invoke(mm, key); } catch (Exception e) { }
        }

        @SuppressWarnings("unchecked")
        public static java.util.Collection<Property> getPropertyValuesSafe(GameProfile profile) {
                if (profile == null) return java.util.Collections.emptyList();
                Object props = getPropertiesSafe(profile);
                if (props == null) return java.util.Collections.emptyList();
                try {
                        Object result = props.getClass().getMethod("values").invoke(props);
                        if (result instanceof java.util.Collection<?> coll) {
                                java.util.List<Property> list = new java.util.ArrayList<>();
                                for (Object o : coll) if (o instanceof Property p) list.add(p);
                                return list;
                        }
                } catch (Exception e) { }
                return java.util.Collections.emptyList();
        }

        public static void putProfileProperty(GameProfile profile, Property prop) {
                if (profile == null || prop == null) return;
                Object props = getPropertiesSafe(profile);
                if (props == null) return;
                multimapPut(props, getPropertyName(prop), prop);
        }

        public static void removeProfileProperty(GameProfile profile, Property prop) {
                if (profile == null || prop == null) return;
                Object props = getPropertiesSafe(profile);
                if (props == null) return;
                multimapRemove(props, getPropertyName(prop), prop);
        }

        // ===================================================================
        // HOTFIX 7 FEATURE 1: Redundant Channel Injection.
        //
        // The primary injection uses PaperMC's ChannelInitializeListenerHolder.
        // This adds a SECOND injection method (ViaVersion-style List<ChannelFuture>
        // replacement) as redundancy. If the PaperMC API fails (Paper 26.x,
        // another plugin overrides it, or the API changes), the backup method
        // still works.
        //
        // Both methods are idempotent — if the pipeline already has EaglerXServer
        // handlers, the second method is a no-op.
        // ===================================================================

        public static Runnable injectChannelInitializerBackup(Server server, Consumer<Channel> initHandler,
                        IEaglerXServerListener listener) {
                try {
                        Object dedicatedPlayerList = server.getClass().getMethod("getHandle").invoke(server);
                        Object minecraftServer = dedicatedPlayerList.getClass().getMethod("getServer").invoke(dedicatedPlayerList);
                        Method getServerConnection = null;
                        try { getServerConnection = minecraftServer.getClass().getMethod("getConnection"); }
                        catch (NoSuchMethodException e1) { getServerConnection = minecraftServer.getClass().getMethod("getServerConnection"); }
                        Object serverConnection = getServerConnection.invoke(minecraftServer);
                        if (serverConnection == null) return () -> {};
                        Class<?> serverConnectionClass = serverConnection.getClass();
                        Field channelFuturesList = null;
                        Class<?> walkClz = serverConnectionClass;
                        do {
                                for (Field f : walkClz.getDeclaredFields()) {
                                        if (!List.class.isAssignableFrom(f.getType())) continue;
                                        java.lang.reflect.Type t = f.getGenericType();
                                        if (t instanceof ParameterizedType pt) {
                                                java.lang.reflect.Type[] params = pt.getActualTypeArguments();
                                                if (params.length == 1 && "io.netty.channel.ChannelFuture".equals(params[0].getTypeName())) {
                                                        channelFuturesList = f;
                                                        channelFuturesList.setAccessible(true);
                                                        break;
                                                }
                                        }
                                }
                        } while (channelFuturesList == null && (walkClz = walkClz.getSuperclass()) != Object.class);
                        if (channelFuturesList == null) return () -> {};
                        CleanupList cleanupList = new CleanupList();
                        @SuppressWarnings("unchecked")
                        final List<ChannelFuture> oldList = (List<ChannelFuture>) channelFuturesList.get(serverConnection);
                        if (oldList == null) return () -> {};
                        for (ChannelFuture ch : new ArrayList<>(oldList)) {
                                try {
                                        ch.addListener(new ChannelFutureListener() {
                                                @Override
                                                public void operationComplete(ChannelFuture var1) throws Exception {
                                                        if (var1.isSuccess()) {
                                                                initHandler.accept(var1.channel());
                                                        }
                                                }
                                        });
                                } catch (Throwable t) { }
                        }
                        List<ChannelFuture> hackList = new com.google.common.collect.ForwardingList<ChannelFuture>() {
                                @Override
                                protected List<ChannelFuture> delegate() { return oldList; }
                                @Override
                                public boolean add(ChannelFuture element) {
                                        super.add(element);
                                        try {
                                                element.addListener(new ChannelFutureListener() {
                                                        @Override
                                                        public void operationComplete(ChannelFuture var1) throws Exception {
                                                                if (var1.isSuccess()) {
                                                                        initHandler.accept(var1.channel());
                                                                }
                                                        }
                                                });
                                        } catch (Throwable t) { }
                                        return true;
                                }
                        };
                        channelFuturesList.set(serverConnection, hackList);
                        listener.reportNettyInjected(null);
                        return cleanupList;
                } catch (Throwable t) {
                        java.util.logging.Logger.getLogger("EaglerXServer").warning(
                                        "[Hotfix7] Backup channel injection failed: " + t);
                        return () -> {};
                }
        }

        // ===================================================================
        // HOTFIX 7 FEATURE 2: Pipeline Handler Existence Check.
        //
        // Before calling pipeline.addAfter(name, ...) or addBefore(name, ...),
        // check if the handler exists. If it doesn't, log and skip instead of
        // throwing NoSuchElementException.
        // ===================================================================

        public static boolean safeAddAfter(ChannelPipeline pipeline, String baseHandler, String name, ChannelHandler handler) {
                if (pipeline.get(baseHandler) != null) {
                        try {
                                pipeline.addAfter(baseHandler, name, handler);
                                return true;
                        } catch (Throwable t) {
                                java.util.logging.Logger.getLogger("EaglerXServer").warning(
                                                "[Hotfix7] Failed to addAfter " + name + " after " + baseHandler + ": " + t);
                        }
                } else {
                        java.util.logging.Logger.getLogger("EaglerXServer").fine(
                                        "[Hotfix7] Handler '" + baseHandler + "' not found, skipping addAfter " + name);
                }
                return false;
        }

        public static boolean safeAddBefore(ChannelPipeline pipeline, String baseHandler, String name, ChannelHandler handler) {
                if (pipeline.get(baseHandler) != null) {
                        try {
                                pipeline.addBefore(baseHandler, name, handler);
                                return true;
                        } catch (Throwable t) {
                                java.util.logging.Logger.getLogger("EaglerXServer").warning(
                                                "[Hotfix7] Failed to addBefore " + name + " before " + baseHandler + ": " + t);
                        }
                } else {
                        java.util.logging.Logger.getLogger("EaglerXServer").fine(
                                        "[Hotfix7] Handler '" + baseHandler + "' not found, skipping addBefore " + name);
                }
                return false;
        }

}
