/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.ForwardingList
 *  com.google.common.collect.LinkedListMultimap
 *  com.google.common.collect.Multimap
 *  com.mojang.authlib.GameProfile
 *  com.mojang.authlib.properties.Property
 *  io.netty.channel.Channel
 *  io.netty.channel.ChannelFuture
 *  io.netty.channel.ChannelFutureListener
 *  io.netty.channel.ChannelHandler
 *  io.netty.channel.ChannelInboundHandlerAdapter
 *  io.netty.channel.ChannelInitializer
 *  io.netty.channel.EventLoopGroup
 *  io.netty.channel.nio.NioEventLoopGroup
 *  io.netty.util.concurrent.GenericFutureListener
 *  io.papermc.paper.network.ChannelInitializeListener
 *  org.bukkit.Server
 *  org.bukkit.command.CommandMap
 *  org.bukkit.entity.Player
 */
package net.lax1dude.eaglercraft.backend.server.bukkit;

import com.google.common.collect.ForwardingList;
import com.google.common.collect.LinkedListMultimap;
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
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.GenericFutureListener;
import io.papermc.paper.network.ChannelInitializeListener;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import net.lax1dude.eaglercraft.backend.server.adapter.IEaglerXServerListener;
import net.lax1dude.eaglercraft.backend.server.api.bukkit.compat.AuthlibCompat;
import net.lax1dude.eaglercraft.backend.server.bukkit.NmsNames;
import net.lax1dude.eaglercraft.backend.server.util.ChannelInitializerHijacker;
import net.lax1dude.eaglercraft.backend.server.util.Util;
import org.bukkit.Server;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;

public class BukkitUnsafe {
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
    private static final Property isEaglerPlayerPropertyT = new Property("isEaglerPlayer", "true", null);
    private static final Property isEaglerPlayerPropertyF = new Property("isEaglerPlayer", "false", null);

    private static synchronized void bindCraftPlayer(Player playerObject) {
        if (class_CraftPlayer != null) {
            return;
        }
        Class<?> clz = playerObject.getClass();
        try {
            Class<?> clz4;
            Class<?> clz3;
            Class<?> clz2;
            method_CraftPlayer_getHandle = clz.getMethod("getHandle", new Class[0]);
            try {
                method_CraftPlayer_addChannel = clz.getMethod("addChannel", String.class);
            }
            catch (NoSuchMethodException ex) {
                method_CraftPlayer_addChannel = null;
            }
            Object entityPlayer = method_CraftPlayer_getHandle.invoke((Object)playerObject, new Object[0]);
            Class<?> clz2walk = clz2 = entityPlayer.getClass();
            block4: do {
                for (Field f : clz2walk.getDeclaredFields()) {
                    if (!NmsNames.matches(f.getType(), NmsNames.PLAYER_CONNECTION)) continue;
                    f.setAccessible(true);
                    field_EntityPlayer_playerConnection = f;
                    break block4;
                }
            } while ((clz2walk = clz2walk.getSuperclass()) != Object.class);
            if (field_EntityPlayer_playerConnection == null) {
                throw new IllegalStateException("Could not locate player connection field of " + clz2.getName());
            }
            Class<?> clz3walk = clz3 = field_EntityPlayer_playerConnection.getType();
            block6: do {
                for (Field f : clz3walk.getDeclaredFields()) {
                    if (!NmsNames.matches(f.getType(), NmsNames.NETWORK_MANAGER)) continue;
                    f.setAccessible(true);
                    field_PlayerConnection_networkManager = f;
                    break block6;
                }
            } while ((clz3walk = clz3walk.getSuperclass()) != Object.class);
            if (field_PlayerConnection_networkManager == null) {
                throw new IllegalStateException("Could not locate network manager field of " + clz3.getName());
            }
            Class<?> clz4walk = clz4 = field_PlayerConnection_networkManager.getType();
            block8: do {
                for (Field f : clz4walk.getDeclaredFields()) {
                    if (!Channel.class.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    field_NetworkManager_channel = f;
                    break block8;
                }
            } while ((clz4walk = clz4walk.getSuperclass()) != Object.class);
            Class<?> clz40 = clz4;
            block10: do {
                for (Field f : clz40.getDeclaredFields()) {
                    if (!SocketAddress.class.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    field_NetworkManager_address = f;
                    break block10;
                }
            } while ((clz40 = clz40.getSuperclass()) != Object.class);
            if (field_NetworkManager_channel == null) {
                throw new IllegalStateException("Could not locate channel field of " + clz4.getName());
            }
            if (field_NetworkManager_address == null) {
                System.err.println("Could not find SocketAddress field in class " + clz4.getName());
                System.err.println("Use Spigot if you want EaglerXServer to forward player IPs");
            }
            if ((method_EntityPlayer_getProfile = BukkitUnsafe.findGameProfileGetter(clz2)) == null) {
                throw new IllegalStateException("Could not locate GameProfile getter on " + clz2.getName());
            }
            class_NetworkManager = clz4;
            class_PlayerConnection = clz3;
            class_EntityPlayer = clz2;
            class_CraftPlayer = clz;
        }
        catch (Exception ex) {
            throw Util.propagateReflectThrowable(ex);
        }
    }

    private static Method findGameProfileGetter(Class<?> entityPlayerClass) {
        Method m2;
        try {
            m2 = entityPlayerClass.getMethod("getGameProfile", new Class[0]);
            if (GameProfile.class.isAssignableFrom(m2.getReturnType())) {
                return m2;
            }
        }
        catch (NoSuchMethodException ex) {
            // empty catch block
        }
        try {
            m2 = entityPlayerClass.getMethod("getProfile", new Class[0]);
            if (GameProfile.class.isAssignableFrom(m2.getReturnType())) {
                return m2;
            }
        }
        catch (NoSuchMethodException noSuchMethodException) {
            // empty catch block
        }
        for (Method m3 : entityPlayerClass.getMethods()) {
            if (m3.getParameterCount() != 0 || !GameProfile.class.isAssignableFrom(m3.getReturnType())) continue;
            return m3;
        }
        return null;
    }

    public static Channel getPlayerChannel(Player playerObject) {
        try {
            if (class_CraftPlayer == null) {
                BukkitUnsafe.bindCraftPlayer(playerObject);
            }
            return (Channel)field_NetworkManager_channel.get(field_PlayerConnection_networkManager.get(field_EntityPlayer_playerConnection.get(method_CraftPlayer_getHandle.invoke((Object)playerObject, new Object[0]))));
        }
        catch (Throwable e) {
            if (e instanceof ReflectiveOperationException) {
                throw Util.propagateReflectThrowable((ReflectiveOperationException)e);
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException)e;
            }
            throw new RuntimeException("getPlayerChannel failed", e);
        }
    }

    public static String getTexturesProperty(Player player) {
        try {
            GameProfile profile;
            Multimap<String, Property> props;
            if (class_CraftPlayer == null) {
                BukkitUnsafe.bindCraftPlayer(player);
            }
            if ((props = AuthlibCompat.getProperties(profile = (GameProfile)method_EntityPlayer_getProfile.invoke(method_CraftPlayer_getHandle.invoke((Object)player, new Object[0]), new Object[0]))) == null) {
                return null;
            }
            Collection tex = props.get("textures");
            if (!tex.isEmpty()) {
                return AuthlibCompat.getValue((Property)tex.iterator().next());
            }
        }
        catch (ReflectiveOperationException e) {
            throw Util.propagateReflectThrowable(e);
        }
        catch (Throwable t) {
            System.err.println("[EaglerXServer] getTexturesProperty failed: " + t);
            return null;
        }
        return null;
    }

    public static PropertyInjector propertyInjector(Player player) {
        try {
            Object entityPlayer;
            GameProfile profile;
            Multimap<String, Property> props;
            if (class_CraftPlayer == null) {
                BukkitUnsafe.bindCraftPlayer(player);
            }
            if ((props = AuthlibCompat.getProperties(profile = (GameProfile)method_EntityPlayer_getProfile.invoke(entityPlayer = method_CraftPlayer_getHandle.invoke((Object)player, new Object[0]), new Object[0]))) == null) {
                throw new IllegalStateException("Could not get GameProfile properties via AuthlibCompat");
            }
            return new PropertyInjector(props, profile, entityPlayer, profile);
        }
        catch (Throwable e) {
            if (e instanceof ReflectiveOperationException) {
                throw Util.propagateReflectThrowable((ReflectiveOperationException)e);
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException)e;
            }
            throw new RuntimeException("propertyInjector failed", e);
        }
    }

    public static Property injectProfileProperty(Object entityPlayer, String key, String value, String signature) {
        if (class_CraftPlayer == null) {
            return null;
        }
        try {
            GameProfile profile = (GameProfile)method_EntityPlayer_getProfile.invoke(entityPlayer, new Object[0]);
            Multimap<String, Property> props = AuthlibCompat.getProperties(profile);
            if (props == null) {
                return null;
            }
            Property newProp = AuthlibCompat.createProperty(key, value, signature);
            try {
                props.removeAll(key);
                props.put(key, newProp);
                if (!AuthlibCompat.containsKey(props, key)) {
                    throw new UnsupportedOperationException("PropertyMap is immutable");
                }
                return newProp;
            }
            catch (IllegalArgumentException | UnsupportedOperationException e) {
                return BukkitUnsafe.replaceGameProfileOnEntityPlayer(entityPlayer, props, profile, key, newProp);
            }
        }
        catch (Throwable t) {
            System.err.println("[EaglerXServer] injectProfileProperty failed for key '" + key + "': " + t);
            return null;
        }
    }

    private static Property replaceGameProfileOnEntityPlayer(Object entityPlayer, Multimap<String, Property> props, GameProfile originalProfile, String key, Property newProp) {
        try {
            LinkedListMultimap mutableProps = LinkedListMultimap.create();
            for (Map.Entry e : props.entries()) {
                if (((String)e.getKey()).equals(key)) continue;
                mutableProps.put((Object)((String)e.getKey()), (Object)((Property)e.getValue()));
            }
            mutableProps.put((Object)key, (Object)newProp);
            Class<?> propertyMapClass = Class.forName("com.mojang.authlib.properties.PropertyMap");
            Constructor<?> ctor = propertyMapClass.getConstructor(Multimap.class);
            Object newPropertyMap = ctor.newInstance(mutableProps);
            Constructor gpCtor = GameProfile.class.getDeclaredConstructor(UUID.class, String.class, propertyMapClass);
            gpCtor.setAccessible(true);
            UUID profileId = AuthlibCompat.getProfileId(originalProfile);
            String profileName = AuthlibCompat.getProfileName(originalProfile);
            if (profileId == null || profileName == null) {
                System.err.println("[EaglerXServer] replaceGameProfileOnEntityPlayer: could not get profile id/name via AuthlibCompat \u2014 property '" + key + "' will not be injected");
                return null;
            }
            GameProfile newProfile = (GameProfile)gpCtor.newInstance(profileId, profileName, newPropertyMap);
            Field gameProfileField = BukkitUnsafe.findGameProfileField(entityPlayer.getClass());
            if (gameProfileField == null) {
                System.err.println("[EaglerXServer] could not locate gameProfile field on " + entityPlayer.getClass().getName() + " \u2014 property '" + key + "' will not be injected");
                return null;
            }
            gameProfileField.set(entityPlayer, newProfile);
            return newProp;
        }
        catch (Throwable t) {
            System.err.println("[EaglerXServer] replaceGameProfileOnEntityPlayer failed for key '" + key + "': " + t);
            return null;
        }
    }

    private static Field findGameProfileField(Class<?> entityPlayerClass) {
        for (Class<?> walk = entityPlayerClass; walk != null && walk != Object.class; walk = walk.getSuperclass()) {
            for (Field f : walk.getDeclaredFields()) {
                if (f.getType() != GameProfile.class) continue;
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }

    public static Object getHandle(Player player) {
        try {
            if (class_CraftPlayer == null) {
                BukkitUnsafe.bindCraftPlayer(player);
            }
            return method_CraftPlayer_getHandle.invoke((Object)player, new Object[0]);
        }
        catch (Throwable e) {
            if (e instanceof ReflectiveOperationException) {
                throw Util.propagateReflectThrowable((ReflectiveOperationException)e);
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException)e;
            }
            throw new RuntimeException("getHandle failed", e);
        }
    }

    public static GameProfile getGameProfile(Object entityPlayer) {
        try {
            return (GameProfile)method_EntityPlayer_getProfile.invoke(entityPlayer, new Object[0]);
        }
        catch (ReflectiveOperationException e) {
            throw Util.propagateReflectThrowable(e);
        }
    }

    private static synchronized void bindRealAddress(Object networkManager) {
        Class<?> clz;
        if (class_NetworkManager != null) {
            return;
        }
        Class<?> clz0 = clz = networkManager.getClass();
        do {
            for (Field field : clz0.getDeclaredFields()) {
                if (!SocketAddress.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                field_NetworkManager_address = field;
                class_NetworkManager = clz;
                return;
            }
        } while ((clz0 = clz0.getSuperclass()) != Object.class);
        class_NetworkManager = clz;
        System.err.println("Could not find SocketAddress field in class " + clz.getName() + " (or parents)");
        System.err.println("Use Spigot if you want EaglerXServer to forward player IPs");
    }

    public static void updateRealAddress(Object networkManager, SocketAddress address) {
        Class<?> clz = class_NetworkManager;
        if (clz == null) {
            BukkitUnsafe.bindRealAddress(networkManager);
            clz = class_NetworkManager;
        }
        if (field_NetworkManager_address != null && clz.isAssignableFrom(networkManager.getClass())) {
            try {
                field_NetworkManager_address.set(networkManager, address);
            }
            catch (IllegalAccessException | IllegalArgumentException e) {
                throw Util.propagateReflectThrowable(e);
            }
        }
    }

    public static void addPlayerChannel(Player player, String ch) {
        try {
            if (class_CraftPlayer == null) {
                BukkitUnsafe.bindCraftPlayer(player);
            }
            if (method_CraftPlayer_addChannel == null) {
                return;
            }
            method_CraftPlayer_addChannel.invoke((Object)player, ch);
        }
        catch (Throwable e) {
            if (e instanceof ReflectiveOperationException) {
                throw Util.propagateReflectThrowable((ReflectiveOperationException)e);
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException)e;
            }
            throw new RuntimeException("addPlayerChannel failed", e);
        }
    }

    public static Runnable injectChannelInitializer(Server server, Consumer<Channel> initHandler, IEaglerXServerListener listener) {
        Class<?> paperChannelInitListener;
        Class<?> paperChannelInitHolder;
        Object eaglerKey;
        Class<?> keyClz;
        try {
            keyClz = Class.forName("net.kyori.adventure.key.Key");
            eaglerKey = keyClz.getMethod("key", String.class, String.class).invoke(null, "eaglerxserver", "channel_initializer");
            paperChannelInitHolder = Class.forName("io.papermc.paper.network.ChannelInitializeListenerHolder");
            paperChannelInitListener = Class.forName("io.papermc.paper.network.ChannelInitializeListener");
        }
        catch (ReflectiveOperationException ex) {
            return BukkitUnsafe.injectChannelInitializerOld(server, initHandler, listener);
        }
        return BukkitUnsafe.injectChannelInitializerPaper(paperChannelInitHolder, paperChannelInitListener, keyClz, eaglerKey, initHandler, listener);
    }

    private static Runnable injectChannelInitializerPaper(Class<?> paperChannelInitHolder, Class<?> paperChannelInitListener, Class<?> keyClz, Object eaglerKey, Consumer<Channel> initHandler, IEaglerXServerListener listener) {
        try {
            Method addListener = paperChannelInitHolder.getMethod("addListener", keyClz, paperChannelInitListener);
            Method removeListener = paperChannelInitHolder.getMethod("removeListener", keyClz);
            ChannelInitializeListener listenerImpl = initHandler::accept;
            addListener.invoke(null, eaglerKey, listenerImpl);
            listener.reportPaperMCInjected();
            return () -> {
                try {
                    removeListener.invoke(null, eaglerKey);
                }
                catch (ReflectiveOperationException e) {
                    throw Util.propagateReflectThrowable(e);
                }
            };
        }
        catch (ReflectiveOperationException e) {
            throw Util.propagateReflectThrowable(e);
        }
    }

    private static Runnable injectChannelInitializerOld(Server server, final Consumer<Channel> initHandler, final IEaglerXServerListener listener) {
        try {
            Class<?> serverConnectionClass;
            Method getServerConnection;
            Object dedicatedPlayerList = server.getClass().getMethod("getHandle", new Class[0]).invoke((Object)server, new Object[0]);
            Object minecraftServer = dedicatedPlayerList.getClass().getMethod("getServer", new Class[0]).invoke(dedicatedPlayerList, new Object[0]);
            try {
                getServerConnection = minecraftServer.getClass().getMethod("getConnection", new Class[0]);
            }
            catch (NoSuchMethodException e1) {
                getServerConnection = minecraftServer.getClass().getMethod("getServerConnection", new Class[0]);
            }
            Object serverConnection = getServerConnection.invoke(minecraftServer, new Object[0]);
            if (serverConnection == null) {
                serverConnectionClass = getServerConnection.getReturnType();
                for (Method meth : minecraftServer.getClass().getMethods()) {
                    if (meth.getReturnType() == serverConnectionClass && !meth.equals(getServerConnection) && (serverConnection = meth.invoke(minecraftServer, new Object[0])) != null) break;
                }
                if (serverConnection == null) {
                    throw new RuntimeException("Could not get ServerConnection instance from server! (Try Paper)");
                }
            }
            serverConnectionClass = serverConnection.getClass();
            Field channelFuturesList = null;
            block5: do {
                for (Field f : serverConnectionClass.getDeclaredFields()) {
                    ParameterizedType tt;
                    Type[] params;
                    Type t;
                    if (!List.class.isAssignableFrom(f.getType()) || !((t = f.getGenericType()) instanceof ParameterizedType) || (params = (tt = (ParameterizedType)t).getActualTypeArguments()).length != 1 || !"io.netty.channel.ChannelFuture".equals(params[0].getTypeName())) continue;
                    channelFuturesList = f;
                    channelFuturesList.setAccessible(true);
                    break block5;
                }
            } while ((serverConnectionClass = serverConnectionClass.getSuperclass()) != Object.class);
            if (channelFuturesList == null) {
                throw new RuntimeException("Could not get ServerConnection channel futures list! (Try Paper)");
            }
            final CleanupList cleanupList = new CleanupList();
            final List<ChannelFuture> oldList = (List<ChannelFuture>)channelFuturesList.get(serverConnection);
            cleanupList.restoreField = channelFuturesList;
            cleanupList.restoreTarget = serverConnection;
            cleanupList.restoreOriginalList = oldList;
            for (ChannelFuture ch : oldList) {
                BukkitUnsafe.injectChannelInitializer(ch, listener, initHandler, cleanupList);
            }
            ForwardingList<ChannelFuture> hackList = new ForwardingList<ChannelFuture>(){

                protected List<ChannelFuture> delegate() {
                    return oldList;
                }

                public boolean add(ChannelFuture element) {
                    super.add(element);
                    BukkitUnsafe.injectChannelInitializer(element, listener, (Consumer<Channel>)initHandler, cleanupList);
                    return true;
                }
            };
            channelFuturesList.set(serverConnection, hackList);
            return cleanupList;
        }
        catch (ReflectiveOperationException e) {
            throw Util.propagateReflectThrowable(e);
        }
    }

    private static void injectChannelInitializer(ChannelFuture channel, final IEaglerXServerListener listenerConf, final Consumer<Channel> initHandler, final CleanupList cleanupCallback) {
        channel.addListener((GenericFutureListener)new ChannelFutureListener(){

            public void operationComplete(ChannelFuture var1) throws Exception {
                if (var1.isSuccess() && cleanupCallback.cleanup != null) {
                    BukkitUnsafe.injectChannelInitializer(var1.channel(), listenerConf, (Consumer<Channel>)initHandler, cleanupCallback);
                }
            }
        });
    }

    private static void injectChannelInitializer(Channel channel, IEaglerXServerListener listenerConf, Consumer<Channel> initHandler, Consumer<ChannelInitializerHijacker> cleanupCallback) {
        ChannelHandler foundHandler;
        Field foundField;
        block4: {
            List<String> names = channel.pipeline().names();
            for (String name : names) {
                ChannelHandler handler = channel.pipeline().get(name);
                if (!BukkitUnsafe.isServerInitializer(handler) || (foundField = BukkitUnsafe.findChildHandlerField(handler.getClass())) == null) continue;
                foundField.setAccessible(true);
                foundHandler = handler;
                break block4;
            }
            foundHandler = channel.pipeline().first();
            if (BukkitUnsafe.isServerInitializer(foundHandler)) {
                foundField = BukkitUnsafe.findChildHandlerField(foundHandler.getClass());
                if (foundField == null) {
                    throw new RuntimeException("Could not find ChannelBootstrapAccelerator to inject into!");
                }
                foundField.setAccessible(true);
            } else {
                return;
            }
        }
        BukkitUnsafe.injectInto(foundHandler, foundField, initHandler, cleanupCallback);
        listenerConf.reportNettyInjected(channel);
    }

    private static Field findChildHandlerField(Class<?> handlerClass) {
        try {
            return Util.findDeclaredField(handlerClass, "childHandler");
        }
        catch (NoSuchFieldException ex) {
            try {
                return Util.findDeclaredField(handlerClass, "val$currentChildHandler");
            }
            catch (NoSuchFieldException exx) {
                return null;
            }
        }
    }

    private static void injectInto(final ChannelHandler foundHandler, final Field foundField, final Consumer<Channel> init, final Consumer<ChannelInitializerHijacker> cleanupCallback) {
        Method initChannel;
        ChannelInitializer parent;
        try {
            parent = (ChannelInitializer)foundField.get(foundHandler);
            initChannel = Util.findDeclaredMethod(parent.getClass(), "initChannel", Channel.class);
            initChannel.setAccessible(true);
        }
        catch (Throwable e) {
            if (e instanceof ReflectiveOperationException) {
                throw Util.propagateReflectThrowable((ReflectiveOperationException)e);
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException)e;
            }
            if (e instanceof Error) {
                throw (Error)e;
            }
            throw new RuntimeException("injectInto failed to bind parent initializer", e);
        }
        ChannelInitializerHijacker newInit = new ChannelInitializerHijacker(init){

            @Override
            protected void callParent(Channel channel) {
                try {
                    initChannel.invoke((Object)parent, channel);
                }
                catch (Throwable e) {
                    if (e instanceof ReflectiveOperationException) {
                        throw Util.propagateReflectThrowable((ReflectiveOperationException)e);
                    }
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException)e;
                    }
                    if (e instanceof Error) {
                        throw (Error)e;
                    }
                    throw new RuntimeException("callParent failed", e);
                }
            }

            @Override
            protected boolean reInject() {
                Object newInitializer;
                try {
                    newInitializer = foundField.get(foundHandler);
                }
                catch (Throwable e) {
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException)e;
                    }
                    if (e instanceof Error) {
                        throw (Error)e;
                    }
                    throw new RuntimeException("reInject failed to read foundField", e);
                }
                if (this != newInitializer) {
                    System.err.println("Detected another plugin's channel initializer (" + newInitializer.getClass().getName() + ") injected into the pipeline, reinjecting EaglerXServer again to make sure its first, because we really are that rude");
                    BukkitUnsafe.injectInto(foundHandler, foundField, init, cleanupCallback);
                    return true;
                }
                return false;
            }
        };
        try {
            foundField.set(foundHandler, (Object)newInit);
        }
        catch (Throwable e) {
            if (e instanceof ReflectiveOperationException) {
                throw Util.propagateReflectThrowable((ReflectiveOperationException)e);
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException)e;
            }
            if (e instanceof Error) {
                throw (Error)e;
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
            return (CommandMap)f.get(server);
        }
        catch (IllegalAccessException | NoSuchFieldException | SecurityException ex) {
            try {
                Method m = Util.findDeclaredMethod(server.getClass(), "getCommandMap", new Class[0]);
                m.setAccessible(true);
                return (CommandMap)m.invoke((Object)server, new Object[0]);
            }
            catch (ReflectiveOperationException ex1) {
                throw Util.propagateReflectThrowable(ex1);
            }
        }
    }

    private static Field findField(Class<?> clazz, Class<?> fieldType) throws NoSuchFieldException {
        Class<?> clazz0 = clazz;
        do {
            for (Field field : clazz0.getDeclaredFields()) {
                if (field.getType() != fieldType) continue;
                field.setAccessible(true);
                return field;
            }
        } while ((clazz0 = clazz0.getSuperclass()) != Object.class);
        throw new NoSuchFieldException("Could not find field with type " + fieldType + " in class " + clazz.getName() + " (or parents)");
    }

    public static boolean isEnableNativeTransport(Server server) {
        Object dedicatedServer;
        try {
            Object dedicatedPlayerList = server.getClass().getMethod("getHandle", new Class[0]).invoke((Object)server, new Object[0]);
            dedicatedServer = dedicatedPlayerList.getClass().getMethod("getServer", new Class[0]).invoke(dedicatedPlayerList, new Object[0]);
        }
        catch (ReflectiveOperationException e) {
            return true;
        }
        try {
            Object propertyManager = dedicatedServer.getClass().getMethod("getPropertyManager", new Class[0]).invoke(dedicatedServer, new Object[0]);
            Method getBoolean = Util.findDeclaredMethod(propertyManager.getClass(), "getBoolean", String.class, Boolean.TYPE);
            getBoolean.setAccessible(true);
            return (Boolean)getBoolean.invoke(propertyManager, "use-native-transport", true);
        }
        catch (NoSuchMethodException propertyManager) {
        }
        catch (ReflectiveOperationException e) {
            return true;
        }
        try {
            Object propertyManager = dedicatedServer.getClass().getMethod("getDedicatedServerProperties", new Class[0]).invoke(dedicatedServer, new Object[0]);
            Method getBoolean = Util.findDeclaredMethod(propertyManager.getClass(), "getBoolean", String.class, Boolean.TYPE);
            getBoolean.setAccessible(true);
            return (Boolean)getBoolean.invoke(propertyManager, "use-native-transport", true);
        }
        catch (NoSuchMethodException propertyManager) {
        }
        catch (ReflectiveOperationException e) {
            return true;
        }
        return true;
    }

    public static EventLoopGroup getEventLoopGroup(Server server, boolean enableNativeTransport) {
        Method serverConnMethod;
        Object minecraftServer;
        try {
            Object dedicatedPlayerList = server.getClass().getMethod("getHandle", new Class[0]).invoke((Object)server, new Object[0]);
            minecraftServer = dedicatedPlayerList.getClass().getMethod("getServer", new Class[0]).invoke(dedicatedPlayerList, new Object[0]);
        }
        catch (ReflectiveOperationException e) {
            return BukkitUnsafe.createOwnEventLoopGroup(enableNativeTransport);
        }
        try {
            serverConnMethod = minecraftServer.getClass().getMethod("getConnection", new Class[0]);
        }
        catch (NoSuchMethodException e) {
            try {
                serverConnMethod = minecraftServer.getClass().getMethod("getServerConnection", new Class[0]);
            }
            catch (NoSuchMethodException e2) {
                return BukkitUnsafe.createOwnEventLoopGroup(enableNativeTransport);
            }
        }
        Class<?> serverConnection = serverConnMethod.getReturnType();
        EventLoopGroup result = BukkitUnsafe.getEventLoopGroup(serverConnection, enableNativeTransport);
        if (result != null) {
            return result;
        }
        return BukkitUnsafe.createOwnEventLoopGroup(enableNativeTransport);
    }

    public static EventLoopGroup getEventLoopGroup(Class<?> serverConnection, boolean enableNativeTransport) {
        Object val2;
        ParameterizedType tt;
        Type[] args;
        Type type;
        Class<?> clz;
        Field[] fields = serverConnection.getFields();
        if (enableNativeTransport) {
            for (Field field : fields) {
                clz = field.getType();
                if (!clz.getSimpleName().equals("LazyInitVar") || !((type = field.getGenericType()) instanceof ParameterizedType) || (args = (tt = (ParameterizedType)type).getActualTypeArguments()).length != 1 || !"io.netty.channel.epoll.EpollEventLoopGroup".equals(args[0].getTypeName())) continue;
                for (Method m : clz.getMethods()) {
                    if (m.getGenericReturnType() == m.getReturnType()) continue;
                    try {
                        return (EventLoopGroup)m.invoke(field.get(null), new Object[0]);
                    }
                    catch (ReflectiveOperationException e) {
                        throw Util.propagateReflectThrowable(e);
                    }
                }
            }
        }
        for (Field field : fields) {
            clz = field.getType();
            if (!clz.getSimpleName().equals("LazyInitVar") || !((type = field.getGenericType()) instanceof ParameterizedType) || (args = (tt = (ParameterizedType)type).getActualTypeArguments()).length != 1 || !"io.netty.channel.nio.NioEventLoopGroup".equals(args[0].getTypeName())) continue;
            for (Method m : clz.getMethods()) {
                if (m.getGenericReturnType() == m.getReturnType()) continue;
                try {
                    return (EventLoopGroup)m.invoke(field.get(null), new Object[0]);
                }
                catch (ReflectiveOperationException e) {
                    throw Util.propagateReflectThrowable(e);
                }
            }
        }
        Class<?> epollType = null;
        Class<?> nioType = null;
        try {
            epollType = Class.forName("io.netty.channel.epoll.EpollEventLoopGroup");
        }
        catch (ClassNotFoundException classNotFoundException) {
            // empty catch block
        }
        try {
            nioType = Class.forName("io.netty.channel.nio.NioEventLoopGroup");
        }
        catch (ClassNotFoundException classNotFoundException) {
            // empty catch block
        }
        if (enableNativeTransport && epollType != null) {
            for (Field field : fields) {
                if (!epollType.isAssignableFrom(field.getType())) continue;
                try {
                    val2 = field.get(null);
                    if (val2 instanceof EventLoopGroup) {
                        return (EventLoopGroup)val2;
                    }
                }
                catch (ReflectiveOperationException ex2) {
                    // empty catch block
                }
            }
        }
        if (nioType != null) {
            for (Field field : fields) {
                if (!nioType.isAssignableFrom(field.getType())) continue;
                try {
                    val2 = field.get(null);
                    if (val2 instanceof EventLoopGroup) {
                        return (EventLoopGroup)val2;
                    }
                }
                catch (ReflectiveOperationException reflectiveOperationException) {
                    // empty catch block
                }
            }
        }
        return null;
    }

    private static EventLoopGroup createOwnEventLoopGroup(boolean enableNativeTransport) {
        ThreadFactory tf = BukkitUnsafe.createDefaultThreadFactory("EaglerXPaper IO");
        if (enableNativeTransport) {
            try {
                Class<?> epollCls = Class.forName("io.netty.channel.epoll.EpollEventLoopGroup");
                return (EventLoopGroup)epollCls.getConstructor(ThreadFactory.class).newInstance(tf);
            }
            catch (ReflectiveOperationException epollCls) {
                // empty catch block
            }
        }
        try {
            Class<?> nioCls = Class.forName("io.netty.channel.nio.NioEventLoopGroup");
            return (EventLoopGroup)nioCls.getConstructor(ThreadFactory.class).newInstance(tf);
        }
        catch (ReflectiveOperationException e) {
            return new NioEventLoopGroup();
        }
    }

    private static ThreadFactory createDefaultThreadFactory(String name) {
        try {
            Class<?> dtfCls = Class.forName("io.netty.util.concurrent.DefaultThreadFactory");
            return (ThreadFactory)dtfCls.getConstructor(String.class, Boolean.TYPE, Integer.TYPE).newInstance(name, true, 5);
        }
        catch (ReflectiveOperationException e) {
            return Executors.defaultThreadFactory();
        }
    }

    private static class CleanupList
    implements Consumer<ChannelInitializerHijacker>,
    Runnable {
        protected List<ChannelInitializerHijacker> cleanup = new ArrayList<ChannelInitializerHijacker>();
        protected Field restoreField;
        protected Object restoreTarget;
        protected List<ChannelFuture> restoreOriginalList;

        private CleanupList() {
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        @Override
        public void accept(ChannelInitializerHijacker c) {
            CleanupList cleanupList = this;
            synchronized (cleanupList) {
                if (this.cleanup != null) {
                    this.cleanup.add(c);
                    return;
                }
            }
            c.deactivate();
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        @Override
        public void run() {
            ArrayList<ChannelInitializerHijacker> cc;
            CleanupList cleanupList = this;
            synchronized (cleanupList) {
                cc = new ArrayList<ChannelInitializerHijacker>(this.cleanup);
                this.cleanup = null;
            }
            for (ChannelInitializerHijacker c : cc) {
                c.deactivate();
            }
            if (this.restoreField != null && this.restoreTarget != null && this.restoreOriginalList != null) {
                try {
                    Object current = this.restoreField.get(this.restoreTarget);
                    if (current != null && current.getClass().getName().contains("ForwardingList") && current != this.restoreOriginalList) {
                        this.restoreField.set(this.restoreTarget, this.restoreOriginalList);
                    } else if (current != this.restoreOriginalList) {
                        System.err.println("[EaglerXServer] ServerConnection channel futures list was replaced by another plugin; not restoring original to avoid clobbering their wrapper.");
                    }
                }
                catch (Throwable t) {
                    System.err.println("[EaglerXServer] Could not restore original channel futures list: " + t);
                }
                this.restoreField = null;
                this.restoreTarget = null;
                this.restoreOriginalList = null;
            }
        }
    }

    public static class PropertyInjector {
        private final Multimap<String, Property> props;
        private final Object lock;
        private final Object entityPlayer;
        private final GameProfile originalProfile;

        protected PropertyInjector(Multimap<String, Property> props, Object lock) {
            this(props, lock, null, null);
        }

        protected PropertyInjector(Multimap<String, Property> props, Object lock, Object entityPlayer, GameProfile originalProfile) {
            this.props = props;
            this.lock = lock;
            this.entityPlayer = entityPlayer;
            this.originalProfile = originalProfile;
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        public void injectTexturesProperty(String texturesPropertyValue, String texturesPropertySignature) {
            Object object = this.lock;
            synchronized (object) {
                try {
                    this.props.removeAll("textures");
                    this.props.put("textures", new Property("textures", texturesPropertyValue, texturesPropertySignature));
                }
                catch (IllegalArgumentException | UnsupportedOperationException e) {
                    this.replaceGameProfileOnEntityPlayer("textures", new Property("textures", texturesPropertyValue, texturesPropertySignature));
                }
            }
        }

        /*
         * WARNING - Removed try catching itself - possible behaviour change.
         */
        public void injectIsEaglerPlayerProperty(boolean val) {
            Object object = this.lock;
            synchronized (object) {
                try {
                    this.props.removeAll("isEaglerPlayer");
                    this.props.put("isEaglerPlayer", val ? isEaglerPlayerPropertyT : isEaglerPlayerPropertyF);
                }
                catch (IllegalArgumentException | UnsupportedOperationException e) {
                    this.replaceGameProfileOnEntityPlayer("isEaglerPlayer", val ? isEaglerPlayerPropertyT : isEaglerPlayerPropertyF);
                }
            }
        }

        private void replaceGameProfileOnEntityPlayer(String key, Property newProp) {
            if (this.originalProfile == null || this.entityPlayer == null) {
                System.err.println("[EaglerXServer] PropertyInjector: cannot replace GameProfile on authlib 9.x because originalProfile or entityPlayer is null \u2014 property '" + key + "' will not be injected");
                return;
            }
            BukkitUnsafe.replaceGameProfileOnEntityPlayer(this.entityPlayer, (Multimap<String, Property>)this.props, this.originalProfile, key, newProp);
        }

        public void complete() {
        }
    }
}

