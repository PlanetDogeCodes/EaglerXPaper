/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.MapMaker
 *  com.mojang.authlib.GameProfile
 *  com.mojang.authlib.properties.Property
 *  io.netty.channel.Channel
 *  io.netty.channel.ChannelHandler
 *  io.netty.channel.ChannelHandlerAdapter
 *  io.netty.util.AttributeKey
 *  io.netty.util.concurrent.GenericFutureListener
 *  net.md_5.bungee.api.chat.BaseComponent
 *  net.md_5.bungee.api.chat.TextComponent
 *  org.bukkit.entity.Player
 *  org.bukkit.event.Event
 *  org.bukkit.event.player.PlayerLoginEvent
 */
package net.lax1dude.eaglercraft.backend.server.bukkit.async;

import com.google.common.collect.MapMaker;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GenericFutureListener;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import net.lax1dude.eaglercraft.backend.server.api.bukkit.compat.AuthlibCompat;
import net.lax1dude.eaglercraft.backend.server.api.bukkit.event.PlayerLoginPostEvent;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.NOPDummyHandler;
import net.lax1dude.eaglercraft.backend.server.bukkit.BukkitUnsafe;
import net.lax1dude.eaglercraft.backend.server.bukkit.NmsNames;
import net.lax1dude.eaglercraft.backend.server.bukkit.PlatformPluginBukkit;
import net.lax1dude.eaglercraft.backend.server.bukkit.async.PlayerLoginInitEventImpl;
import net.lax1dude.eaglercraft.backend.server.bukkit.async.PlayerLoginPostEventImpl;
import net.lax1dude.eaglercraft.backend.server.util.ClassProxy;
import net.lax1dude.eaglercraft.backend.server.util.Util;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerLoginEvent;

public class PlayerPostLoginInjector {
    public static final AttributeKey<LoginEventContext> attr = AttributeKey.valueOf((String)"eagler-postlogin-hack");
    protected final PlatformPluginBukkit plugin;
    protected volatile Class<Object> netManagerClass;
    protected volatile Boolean modernLoginFlow;
    protected Constructor<Object> netManagerCtor;
    protected ClassProxy<Object> netManagerProxy;
    protected Field netManagerDir;
    protected Field netManagerChannel;
    protected Method sendPacketMethod1;
    protected Method sendPacketMethod2;
    protected Method sendPacketMethod3;
    protected Method getHandlerMethod;
    protected Class<Object> handshakeListenerClass;
    protected Field handshakeListenerNetManager;
    protected Field handlerAdded;
    protected volatile Class<Object> loginListenerClass;
    protected Constructor<Object> loginListenerCtor;
    protected int loginListenerCtorArgCount = 2;
    protected ClassProxy<Object> loginListenerProxy;
    protected Field loginListenerServer;
    protected Field loginListenerNetManager;
    protected Class<Object> enumProtocolState;
    protected Object protocolStateOnResume;
    protected Field loginListenerState;
    protected Method loginListenerTick;
    protected Method loginListenerDisconnect;
    protected Field loginListenerPlayer;
    protected Method setHandlerMethod;
    public Method setupInboundMethod;
    protected Field loginListenerTransferred;
    protected Field loginListenerGameProfile;
    protected volatile Class<Object> packetLoginSuccessClass;
    protected Field packetLoginSuccessGameProfile;
    protected volatile Class<?> packetPlayDisconnect;
    protected Constructor<?> packetPlayDisconnectCtor;
    protected Field packetLoginDisconnectMsg;
    protected final ConcurrentMap<Property, Player> entityPlayers;
    protected final ConcurrentMap<UUID, LoginEventContext> ctxByUUID;
    private static final String[] KNOWN_PLAY_DISCONNECT_FQNS = new String[]{"net.minecraft.network.protocol.common.ClientboundDisconnectPacket", "net.minecraft.network.protocol.game.ClientboundDisconnectPacket", "net.minecraft.server.v1_12_R1.PacketPlayOutKickDisconnect"};
    private static final String[] LEGACY_COMPRESSION_METHOD_NAMES = new String[]{"setCompressionLevel", "a"};

    public PlayerPostLoginInjector(PlatformPluginBukkit plugin) {
        this.plugin = plugin;
        this.entityPlayers = new MapMaker().concurrencyLevel(8).weakKeys().weakValues().makeMap();
        this.ctxByUUID = new ConcurrentHashMap<UUID, LoginEventContext>(16, 0.75f, 8);
    }

    public boolean isModernLoginFlow() {
        Boolean cached = this.modernLoginFlow;
        if (cached != null) {
            return cached;
        }
        return this.setupInboundMethod != null;
    }

    public boolean isModernLoginFlow(Object netManager) {
        Boolean cached = this.modernLoginFlow;
        if (cached != null) {
            return cached;
        }
        boolean found = false;
        try {
            Class<?> clz = netManager.getClass();
            while (clz != null && clz != Object.class) {
                block16: for (Method m : clz.getMethods()) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 2 && params[0].getSimpleName().equals("ProtocolInfo")
                                    && params[1].getSimpleName().equals("PacketListener")) {
                        found = true;
                        break block16;
                    }
                }
                if (found) break;
                clz = clz.getSuperclass();
            }
        }
        catch (Throwable t) {
            found = this.setupInboundMethod != null;
        }
        this.modernLoginFlow = found;
        return found;
    }

    public void storeContext(Object netManager, Channel channel) {
        try {
            LoginEventContext ctx = new LoginEventContext(netManager, channel);
            channel.attr(attr).set(ctx);
        }
        catch (Throwable t) {
            this.plugin.logger().warn("EaglerXServer: storeContext failed", t);
        }
    }

    public void registerCtxByUUID(UUID uuid, LoginEventContext ctx) {
        if (uuid != null && ctx != null) {
            this.ctxByUUID.put(uuid, ctx);
            ctx.channel.closeFuture().addListener(f -> this.ctxByUUID.values().remove(ctx));
        }
    }

    public void removeMarker(Property marker) {
        if (marker == null) {
            return;
        }
        try {
            this.entityPlayers.remove(marker);
        }
        catch (Throwable t8) {
            // empty catch block
        }
    }

    private synchronized void bind(Object netManager) {
        if (this.netManagerClass != null) {
            return;
        }
        try {
            Class<?> netManagerClass = netManager.getClass();
            Class<?> protocolDirType = null;
            Field protocolDirField = null;
            Field channelField = null;
            Class<?> nmWalk = netManagerClass;
            block2: do {
                for (Field f : nmWalk.getDeclaredFields()) {
                    Class<?> clz = f.getType();
                    if (!NmsNames.PROTOCOL_DIRECTION.contains(clz.getSimpleName())) continue;
                    f.setAccessible(true);
                    protocolDirType = f.getType();
                    protocolDirField = f;
                    break block2;
                }
            } while ((nmWalk = nmWalk.getSuperclass()) != Object.class);
            Class<?> nmWalk2 = netManagerClass;
            block4: do {
                for (Field f : nmWalk2.getDeclaredFields()) {
                    Class<?> clz = f.getType();
                    if (!Channel.class.isAssignableFrom(clz)) continue;
                    f.setAccessible(true);
                    channelField = f;
                    break block4;
                }
            } while ((nmWalk2 = nmWalk2.getSuperclass()) != Object.class);
            if (protocolDirField == null) {
                throw new IllegalStateException("Could not locate direction field of " + netManagerClass.getName());
            }
            if (channelField == null) {
                throw new IllegalStateException("Could not locate channel field of " + netManagerClass.getName());
            }
            Method setHandlerMethod = null;
            Method setupInboundMethod = null;
            Method sendPacketMethod1 = null;
            Method sendPacketMethod2 = null;
            Method sendPacketMethod3 = null;
            Method getHandlerMethod = null;
            Class<?> futureListenerArr = Array.newInstance(GenericFutureListener.class, 0).getClass();
            for (Method m : netManagerClass.getMethods()) {
                Class<?>[] params = m.getParameterTypes();
                if (setHandlerMethod == null && params.length == 1 && params[0].getSimpleName().equals("PacketListener")) {
                    setHandlerMethod = m;
                } else if (setupInboundMethod == null && params.length == 2 && params[0].getSimpleName().equals("ProtocolInfo") && params[1].getSimpleName().equals("PacketListener")) {
                    setupInboundMethod = m;
                } else if (sendPacketMethod1 == null && params.length == 1 && params[0].getSimpleName().equals("Packet")) {
                    sendPacketMethod1 = m;
                } else if (sendPacketMethod3 == null && params.length == 3 && params[0].getSimpleName().equals("Packet") && GenericFutureListener.class.isAssignableFrom(params[1]) && params[2].equals(futureListenerArr)) {
                    sendPacketMethod3 = m;
                    sendPacketMethod2 = null;
                } else if (sendPacketMethod3 == null && sendPacketMethod2 == null && params.length == 2 && params[0].getSimpleName().equals("Packet") && GenericFutureListener.class.isAssignableFrom(params[1])) {
                    sendPacketMethod2 = m;
                } else if (getHandlerMethod == null && params.length == 0 && m.getReturnType().getSimpleName().equals("PacketListener")) {
                    getHandlerMethod = m;
                }
                if (setHandlerMethod != null && sendPacketMethod1 != null && sendPacketMethod3 != null && getHandlerMethod != null) break;
            }
            if (setHandlerMethod == null && setupInboundMethod == null) {
                throw new IllegalStateException("Could not locate set handler function of " + netManagerClass.getName() + " \u2014 neither setListener(PacketListener) nor setupInboundProtocol(ProtocolInfo, PacketListener) found");
            }
            if (sendPacketMethod1 == null) {
                throw new IllegalStateException("Could not locate send packet (1 param) function of " + netManagerClass.getName());
            }
            if (sendPacketMethod2 == null && sendPacketMethod3 == null) {
                throw new IllegalStateException("Could not locate send packet (2 or 3 param) function of " + netManagerClass.getName());
            }
            if (getHandlerMethod == null) {
                throw new IllegalStateException("Could not locate get handler function of " + netManagerClass.getName());
            }
            Object handshakeListener = getHandlerMethod.invoke(netManager, new Object[0]);
            Class<?> handshakeListenerClass = handshakeListener.getClass();
            Field handshakeListenerNetManager = null;
            Class<?> hslWalk = handshakeListenerClass;
            block7: do {
                for (Field f : hslWalk.getDeclaredFields()) {
                    if (f.getType() != netManagerClass) continue;
                    f.setAccessible(true);
                    handshakeListenerNetManager = f;
                    break block7;
                }
            } while ((hslWalk = hslWalk.getSuperclass()) != Object.class);
            if (handshakeListenerNetManager == null) {
                throw new IllegalStateException("Could not locate network manager field of " + handshakeListenerClass.getName());
            }
            this.netManagerCtor = (Constructor<Object>)netManagerClass.getDeclaredConstructor(protocolDirType);
            this.netManagerCtor.setAccessible(true);
            this.netManagerProxy = (ClassProxy<Object>)ClassProxy.bindProxy(PlayerPostLoginInjector.class.getClassLoader(), netManagerClass);
            this.netManagerDir = protocolDirField;
            this.netManagerChannel = channelField;
            this.setHandlerMethod = setHandlerMethod;
            this.setupInboundMethod = setupInboundMethod;
            this.sendPacketMethod1 = sendPacketMethod1;
            this.sendPacketMethod2 = sendPacketMethod2;
            this.sendPacketMethod3 = sendPacketMethod3;
            this.getHandlerMethod = getHandlerMethod;
            this.handshakeListenerClass = (Class<Object>)handshakeListenerClass;
            this.handshakeListenerNetManager = handshakeListenerNetManager;
            this.handlerAdded = ChannelHandlerAdapter.class.getDeclaredField("added");
            this.handlerAdded.setAccessible(true);
            this.netManagerClass = (Class<Object>)netManagerClass;
            if (setupInboundMethod != null) {
                this.plugin.logger().info("EaglerXServer: detected MC 1.20.2+ setupInboundProtocol(ProtocolInfo, PacketListener) \u2014 using 2-arg listener install path");
            } else {
                this.plugin.logger().info("EaglerXServer: detected MC 1.12-1.20.1 setListener(PacketListener) \u2014 using 1-arg listener install path");
            }
        }
        catch (ReflectiveOperationException e) {
            throw Util.propagateReflectThrowable(e);
        }
    }

    public Object wrapNetworkManager(Object netManager, Channel channel) {
        Class<Object> netManagerClass = this.netManagerClass;
        if (netManagerClass == null) {
            this.bind(netManager);
            netManagerClass = this.netManagerClass;
        }
        if (!netManagerClass.isAssignableFrom(netManager.getClass())) {
            throw new IllegalStateException("Unknown NetworkManager type: " + netManager.getClass().getName());
        }
        try {
            Object ret;
            LoginEventContext ctx = new LoginEventContext(netManager, channel);
            ctx.proxiedNetworkManager = ret = this.netManagerProxy.createProxy(this.netManagerCtor, new Object[]{this.netManagerDir.get(netManager)}, (obj, meth, args) -> {
                if (this.setupInboundMethod == null) {
                    if (this.setHandlerMethod != null && this.setHandlerMethod.equals(meth) && args != null && args.length >= 1 && args[0] != null && NmsNames.matches(args[0], NmsNames.LOGIN_LISTENER)) {
                        meth.invoke(netManager, args);
                        this.fireEventLoginInit(channel);
                        args[0] = this.wrapLoginListener(this.getHandlerMethod.invoke(netManager, new Object[0]), ctx);
                        meth.invoke(netManager, args);
                        return null;
                    }
                } else if (this.setupInboundMethod.equals(meth) && args != null && args.length >= 2 && args[1] != null && NmsNames.matches(args[1], NmsNames.LOGIN_LISTENER)) {
                    this.fireEventLoginInit(channel);
                    if (this.loginListenerGameProfile != null) {
                        try {
                            UUID profileUUID;
                            GameProfile llProfile = (GameProfile)this.loginListenerGameProfile.get(args[1]);
                            if (llProfile != null && (profileUUID = AuthlibCompat.getProfileId(llProfile)) != null) {
                                this.registerCtxByUUID(profileUUID, ctx);
                            }
                        }
                        catch (Throwable llProfile) {
                            // empty catch block
                        }
                    }
                }
                if (this.sendPacketMethod1 != null && this.sendPacketMethod1.equals(meth)) {
                    String nm = args[0].getClass().getSimpleName();
                    if (NmsNames.PACKET_LOGIN_DISCONNECT.contains(nm) && ctx.clientPlayState) {
                        Class<?> clz2 = this.packetPlayDisconnect;
                        if (clz2 == null) {
                            this.bindPacketPlayDisconnect(args[0].getClass());
                            clz2 = this.packetPlayDisconnect;
                        }
                        if (clz2 != Void.TYPE) {
                            args[0] = this.packetPlayDisconnectCtor.newInstance(this.packetLoginDisconnectMsg.get(args[0]));
                        } else {
                            return null;
                        }
                    }
                    meth.invoke(netManager, args);
                    if (this.setupInboundMethod == null && ctx.throwOnLoginSuccess && NmsNames.PACKET_LOGIN_SUCCESS.contains(nm)) {
                        throw new EaglerError(this.getPacketProfile(args[0]));
                    }
                    return null;
                }
                if (ctx.compressionDisable && (this.sendPacketMethod3 != null ? this.sendPacketMethod3.equals(meth) : this.sendPacketMethod2.equals(meth)) && NmsNames.PACKET_LOGIN_SET_COMPRESSION.contains(args[0].getClass().getSimpleName())) {
                    return null;
                }
                return meth.invoke(netManager, args);
            });
            channel.attr(attr).set(ctx);
            this.handshakeListenerNetManager.set(this.getHandlerMethod.invoke(netManager, new Object[0]), ret);
            this.netManagerChannel.set(ret, channel);
            return ret;
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
            throw new RuntimeException("wrapNetworkManager failed", e);
        }
    }

    private synchronized void bindPacketProfile(Object packet) {
        if (this.packetLoginSuccessClass != null) {
            return;
        }
        Field gameProfile = null;
        Class<?> clz = packet.getClass();
        for (Field f : clz.getDeclaredFields()) {
            if (!f.getType().equals(GameProfile.class)) continue;
            f.setAccessible(true);
            gameProfile = f;
            break;
        }
        if (gameProfile == null) {
            throw new IllegalStateException("Could not locate game profile field of " + clz.getName());
        }
        this.packetLoginSuccessGameProfile = gameProfile;
        this.packetLoginSuccessClass = (Class<Object>)clz;
    }

    private GameProfile getPacketProfile(Object packet) {
        Class<Object> clz = this.packetLoginSuccessClass;
        if (clz == null) {
            this.bindPacketProfile(packet);
            clz = this.packetLoginSuccessClass;
        }
        if (!clz.isAssignableFrom(packet.getClass())) {
            throw new IllegalStateException("Unknown PacketLoginOutSuccess type: " + packet.getClass().getName());
        }
        try {
            return (GameProfile)this.packetLoginSuccessGameProfile.get(packet);
        }
        catch (ReflectiveOperationException e) {
            throw Util.propagateReflectThrowable(e);
        }
    }

    private synchronized void bindLogin(Object loginListener) {
        if (this.loginListenerClass != null) {
            return;
        }
        Class<Object> clz2 = this.netManagerClass;
        if (clz2 == null) {
            throw new IllegalStateException();
        }
        try {
            Object[] obj;
            Class<?> loginListenerClass = loginListener.getClass();
            Class<?> mcServerClass = null;
            Constructor<?> loginListenerCtor = null;
            int ctorArgCount = 2;
            for (Constructor<?> ctor : loginListenerClass.getConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length == 2 && params[1] == clz2) {
                    loginListenerCtor = ctor;
                    mcServerClass = params[0];
                    ctorArgCount = 2;
                    break;
                }
                if (params.length != 3 || params[1] != clz2 || params[2] != Boolean.TYPE) continue;
                loginListenerCtor = ctor;
                mcServerClass = params[0];
                ctorArgCount = 3;
                break;
            }
            if (loginListenerCtor == null) {
                throw new IllegalStateException("Could not locate constructor of " + loginListenerClass.getName());
            }
            Field loginListenerServer = null;
            Field loginListenerNetManager = null;
            Class<?> enumProtocolState = null;
            Field loginListenerState = null;
            Field loginListenerPlayer = null;
            Field loginListenerTransferred = null;
            Field loginListenerGameProfile = null;
            for (Field f : loginListenerClass.getDeclaredFields()) {
                if (f.getType() == mcServerClass) {
                    f.setAccessible(true);
                    loginListenerServer = f;
                } else if (f.getType() == clz2) {
                    f.setAccessible(true);
                    loginListenerNetManager = f;
                } else if (NmsNames.LOGIN_STATE_ENUM_SIMPLE.contains(f.getType().getSimpleName()) && f.getType().getName().startsWith(loginListenerClass.getName())) {
                    f.setAccessible(true);
                    loginListenerState = f;
                    enumProtocolState = f.getType();
                } else if (NmsNames.matches(f.getType(), NmsNames.ENTITY_PLAYER)) {
                    f.setAccessible(true);
                    loginListenerPlayer = f;
                } else if (f.getType() == Boolean.TYPE && "transferred".equals(f.getName())) {
                    f.setAccessible(true);
                    loginListenerTransferred = f;
                } else if (f.getType() == GameProfile.class) {
                    f.setAccessible(true);
                    loginListenerGameProfile = f;
                }
                if (loginListenerServer != null && loginListenerNetManager != null && loginListenerState != null && loginListenerPlayer != null) break;
            }
            if (loginListenerServer == null) {
                throw new IllegalStateException("Could not locate server field of " + loginListenerClass.getName());
            }
            if (loginListenerNetManager == null) {
                throw new IllegalStateException("Could not locate network manager field of " + loginListenerClass.getName());
            }
            if (loginListenerState == null) {
                throw new IllegalStateException("Could not locate state field of " + loginListenerClass.getName());
            }
            if (loginListenerPlayer == null) {
                this.plugin.logger().info("ServerLoginPacketListenerImpl has no EntityPlayer field \u2014 skipping player field injection (expected on MC 1.20.2+)");
            }
            Method loginListenerTick = null;
            Method loginListenerDisconnect = null;
            try {
                Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
                loginListenerDisconnect = loginListenerClass.getMethod("disconnect", componentClass);
            }
            catch (ClassNotFoundException | NoSuchMethodException componentClass) {
                // empty catch block
            }
            if (loginListenerDisconnect == null) {
                try {
                    loginListenerDisconnect = loginListenerClass.getMethod("disconnect", String.class);
                }
                catch (NoSuchMethodException componentClass) {
                    // empty catch block
                }
            }
            if (loginListenerDisconnect == null) {
                Object stringDisconnect = null;
                Method componentDisconnect = null;
                Method anyDisconnect = null;
                for (Method m : loginListenerClass.getMethods()) {
                    if (!m.getName().equals("disconnect") || m.getParameterCount() != 1) continue;
                    Class<?> paramType = m.getParameterTypes()[0];
                    if (paramType == String.class) {
                        stringDisconnect = m;
                        continue;
                    }
                    if (paramType.getName().contains("Component")) {
                        componentDisconnect = m;
                        continue;
                    }
                    anyDisconnect = m;
                }
                Object object = stringDisconnect != null ? stringDisconnect : (loginListenerDisconnect = componentDisconnect != null ? componentDisconnect : anyDisconnect);
            }
            if (loginListenerDisconnect == null) {
                throw new IllegalStateException("Could not locate disconnect function of " + loginListenerClass.getName());
            }
            for (Class<?> clz : loginListenerClass.getInterfaces()) {
                String s = clz.getSimpleName();
                if (!s.equals("IUpdatePlayerListBox") && !s.equals("ITickable")) continue;
                loginListenerTick = loginListenerClass.getMethod(clz.getMethods()[0].getName(), new Class[0]);
                break;
            }
            if (loginListenerTick == null) {
                try {
                    loginListenerTick = loginListenerClass.getMethod("tick", new Class[0]);
                }
                catch (ReflectiveOperationException stringDisconnect) {
                    // empty catch block
                }
            }
            if (loginListenerTick == null) {
                throw new IllegalStateException("Could not locate tick function of " + loginListenerClass.getName());
            }
            // Resume at a state AFTER the ACCEPTED block (which sends SetCompression +
            // LoginSuccess). Resuming at READY_TO_ACCEPT re-enters ACCEPTED and re-sends both
            // login packets into the already-established play stream, where the client
            // misparses them (SetCompression 0x03 reads as TimeUpdate, LoginSuccess 0x02 reads
            // as Chat) and crashes. DELAY_ACCEPT / WAITING_FOR_DUPE_DISCONNECT are the states
            // the vanilla machine uses right after the sends.
            Object protocolStateOnResume = PlayerPostLoginInjector.findEnumValueByName(enumProtocolState, "DELAY_ACCEPT", "WAITING_FOR_DUPE_DISCONNECT");
            if (protocolStateOnResume == null && (obj = enumProtocolState.getEnumConstants()) != null && obj.length >= 2) {
                Object secondToLast = obj[obj.length - 2];
                if (secondToLast != null && !"ACCEPTED".equals(((Enum)secondToLast).name())) {
                    protocolStateOnResume = secondToLast;
                }
            }
            if (protocolStateOnResume == null) {
                protocolStateOnResume = PlayerPostLoginInjector.findEnumValueByName(enumProtocolState, "READY_TO_ACCEPT", "READY_TO_LOGIN", "WAITING_FOR_DUPE_DISCONNECT");
            }
            if (protocolStateOnResume == null && (obj = enumProtocolState.getEnumConstants()) != null && obj.length > 4) {
                protocolStateOnResume = obj[obj.length - 3];
            }
            if (protocolStateOnResume == null) {
                throw new IllegalStateException("Could not locate stalling state enum of " + enumProtocolState.getName());
            }
            this.loginListenerCtor = (Constructor<Object>)loginListenerCtor;
            this.loginListenerCtorArgCount = ctorArgCount;
            this.loginListenerProxy = (ClassProxy<Object>)ClassProxy.bindProxy(PlayerPostLoginInjector.class.getClassLoader(), loginListenerClass);
            this.loginListenerServer = loginListenerServer;
            this.loginListenerNetManager = loginListenerNetManager;
            this.enumProtocolState = (Class<Object>)enumProtocolState;
            this.protocolStateOnResume = protocolStateOnResume;
            this.loginListenerState = loginListenerState;
            this.loginListenerTick = loginListenerTick;
            this.loginListenerDisconnect = loginListenerDisconnect;
            this.loginListenerPlayer = loginListenerPlayer;
            this.loginListenerTransferred = loginListenerTransferred;
            this.loginListenerGameProfile = loginListenerGameProfile;
            this.loginListenerClass = (Class<Object>)loginListenerClass;
        }
        catch (ReflectiveOperationException e) {
            throw Util.propagateReflectThrowable(e);
        }
    }

    private Object wrapLoginListener(Object loginListener, LoginEventContext ctx) {
        Class<Object> loginListenerClass = this.loginListenerClass;
        if (loginListenerClass == null) {
            this.bindLogin(loginListener);
            loginListenerClass = this.loginListenerClass;
        }
        if (!loginListenerClass.isAssignableFrom(loginListener.getClass())) {
            throw new IllegalStateException("Unknown LoginListener type: " + loginListener.getClass().getName());
        }
        ctx.loginListener = loginListener;
        if (this.loginListenerGameProfile != null) {
            try {
                UUID profileUUID;
                GameProfile llProfile = (GameProfile)this.loginListenerGameProfile.get(loginListener);
                if (llProfile != null && (profileUUID = AuthlibCompat.getProfileId(llProfile)) != null) {
                    this.registerCtxByUUID(profileUUID, ctx);
                }
            }
            catch (Throwable llProfile) {
                // empty catch block
            }
        }
        try {
            Object[] ctorArgs;
            if (this.loginListenerCtorArgCount == 3) {
                boolean transferred = false;
                if (this.loginListenerTransferred != null) {
                    try {
                        transferred = this.loginListenerTransferred.getBoolean(loginListener);
                    }
                    catch (Exception exception) {
                        // empty catch block
                    }
                }
                ctorArgs = new Object[]{this.loginListenerServer.get(loginListener), ctx.proxiedNetworkManager, transferred};
            } else {
                ctorArgs = new Object[]{this.loginListenerServer.get(loginListener), ctx.proxiedNetworkManager};
            }
            return this.loginListenerProxy.createProxy(this.loginListenerCtor, ctorArgs, (obj, meth, args) -> {
                if (this.loginListenerTick.equals(meth)) {
                    if (this.setupInboundMethod == null) {
                        ctx.markThrowOnLoginSuccess(true);
                        try {
                            Object object = meth.invoke(loginListener, args);
                            ctx.markThrowOnLoginSuccess(false);
                            return object;
                        }
                        catch (Throwable throwable) {
                            try {
                                ctx.markThrowOnLoginSuccess(false);
                                throw throwable;
                            }
                            catch (InvocationTargetException ex) {
                                Throwable er = ex.getCause();
                                if (er instanceof EaglerError) {
                                    EaglerError err = (EaglerError)er;
                                    Player player = null;
                                    GameProfile gameProfile = err.gameProfile;
                                    synchronized (gameProfile) {
                                        Iterator itr = AuthlibCompat.getProperties(err.gameProfile).values().iterator();
                                        while (itr.hasNext()) {
                                            Property prop = (Property)itr.next();
                                            String propName = AuthlibCompat.getName(prop);
                                            if (propName == null || !propName.startsWith("$eaglerMarker_")) continue;
                                            Player e = (Player)this.entityPlayers.remove(prop);
                                            if (e != null) {
                                                player = e;
                                            }
                                            try {
                                                itr.remove();
                                            }
                                            catch (UnsupportedOperationException unsupportedOperationException) {}
                                        }
                                    }
                                    if (player != null) {
                                        Player playerFinal = player;
                                        this.fireEventLoginPostAsync(playerFinal, ctx, res -> {
                                            Runnable task = () -> {
                                                block15: {
                                                    try {
                                                        if (!res.isCancelled()) {
                                                            this.handlerAdded.set(ctx.originalNetworkManager, false);
                                                            try {
                                                                ctx.channel.pipeline().replace("packet_handler", "packet_handler", (ChannelHandler)ctx.originalNetworkManager);
                                                            }
                                                            catch (NoSuchElementException nse) {
                                                                try {
                                                                    ctx.channel.pipeline().addFirst("eagler-restored-handler", (ChannelHandler)ctx.originalNetworkManager);
                                                                }
                                                                catch (Throwable t2) {
                                                                    this.plugin.logger().error("EaglerXServer: could not restore NetworkManager after packet_handler was missing", t2);
                                                                    try {
                                                                        ctx.channel.close();
                                                                    }
                                                                    catch (Throwable t9) {
                                                                        // empty catch block
                                                                    }
                                                                    return;
                                                                }
                                                            }
                                                            Object entityPlayer = BukkitUnsafe.getHandle(playerFinal);
                                                            this.loginListenerNetManager.set(loginListener, ctx.originalNetworkManager);
                                                            if (this.loginListenerPlayer != null) {
                                                                this.loginListenerPlayer.set(loginListener, entityPlayer);
                                                            }
                                                            if (this.setupInboundMethod == null && this.loginListenerState != null) {
                                                                this.loginListenerState.set(loginListener, this.protocolStateOnResume);
                                                            }
                                                            break block15;
                                                        }
                                                        BaseComponent comp = res.getMessage();
                                                        if (comp == null) {
                                                            comp = new TextComponent("Connection Closed");
                                                        }
                                                        String legacyText = comp.toLegacyText();
                                                        Object arg = legacyText;
                                                        Class<?> paramType = this.loginListenerDisconnect.getParameterTypes()[0];
                                                        if (paramType != String.class) {
                                                            arg = PlayerPostLoginInjector.convertToComponent(legacyText, paramType);
                                                        }
                                                        this.loginListenerDisconnect.invoke(loginListener, arg);
                                                    }
                                                    catch (Throwable e) {
                                                        this.plugin.logger().error("EaglerXServer: post-login finalize failed, closing channel", e);
                                                        try {
                                                            ctx.channel.close();
                                                        }
                                                        catch (Throwable t8) {
                                                            // empty catch block
                                                        }
                                                    }
                                                }
                                            };
                                            if (ctx.channel.eventLoop().inEventLoop()) {
                                                task.run();
                                            } else {
                                                try {
                                                    ctx.channel.eventLoop().submit(task);
                                                }
                                                catch (Throwable t) {
                                                    this.plugin.logger().error("EaglerXServer: failed to schedule post-login finalize on event loop", t);
                                                    try {
                                                        ctx.channel.close();
                                                    }
                                                    catch (Throwable t9) {
                                                        // empty catch block
                                                    }
                                                }
                                            }
                                        });
                                        return null;
                                    }
                                    ctx.pendingPostLogin = true;
                                    return null;
                                }
                                if (er instanceof RuntimeException) {
                                    throw (RuntimeException)er;
                                }
                                throw new RuntimeException(er);
                            }
                        }
                    }
                    return meth.invoke(loginListener, args);
                }
                return meth.invoke(loginListener, args);
            });
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
            throw new RuntimeException("wrapLoginListener failed", e);
        }
    }

    private static Object findEnumValueByName(Class<?> enumClass, String ... names) {
        if (enumClass == null || !enumClass.isEnum()) {
            return null;
        }
        Object[] constants = enumClass.getEnumConstants();
        if (constants == null) {
            return null;
        }
        for (Object constant : constants) {
            String n = ((Enum)constant).name();
            for (String want : names) {
                if (!want.equals(n)) continue;
                return constant;
            }
        }
        return null;
    }

    private static Object convertToComponent(String legacyText, Class<?> paramType) {
        String typeName = paramType.getName();
        try {
            if (typeName.startsWith("net.minecraft.network.chat.") && typeName.endsWith("Component")) {
                try {
                    Method literal = paramType.getMethod("literal", String.class);
                    return literal.invoke(null, legacyText);
                }
                catch (NoSuchMethodException literal) {
                    try {
                        Class<?> textComponentClass = Class.forName("net.minecraft.network.chat.TextComponent");
                        return textComponentClass.getConstructor(String.class).newInstance(legacyText);
                    }
                    catch (ClassNotFoundException | NoSuchMethodException textComponentClass) {
                        // empty catch block
                    }
                }
            }
            if (typeName.startsWith("net.kyori.adventure.text.")) {
                try {
                    Class<?> adventureComponent = Class.forName("net.kyori.adventure.text.Component");
                    Method text = adventureComponent.getMethod("text", String.class);
                    return text.invoke(null, legacyText);
                }
                catch (ClassNotFoundException | NoSuchMethodException reflectiveOperationException) {}
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return legacyText;
    }

    private synchronized void bindPacketPlayDisconnect(Class<?> loginDisconnectPacket) {
        if (this.packetPlayDisconnect != null) {
            return;
        }
        try {
            Class<?> clz = null;
            for (String fqn : KNOWN_PLAY_DISCONNECT_FQNS) {
                try {
                    clz = Class.forName(fqn);
                    break;
                }
                catch (ClassNotFoundException classNotFoundException) {
                }
            }
            if (clz == null) {
                String nm2 = loginDisconnectPacket.getName();
                nm2 = nm2.substring(0, nm2.lastIndexOf(46) + 1);
                try {
                    clz = Class.forName(nm2 + "PacketPlayOutKickDisconnect");
                }
                catch (ReflectiveOperationException ex) {
                    if (nm2.endsWith(".login.")) {
                        clz = Class.forName(nm2.substring(0, nm2.length() - 7) + ".game.PacketPlayOutKickDisconnect");
                    }
                    throw ex;
                }
            }
            Constructor<?> ctor = null;
            Class<?> cmp = null;
            AccessibleObject f = null;
            for (Constructor<?> constructor : loginDisconnectPacket.getConstructors()) {
                if (constructor.getParameterCount() != 1) continue;
                Class<?>[] params = constructor.getParameterTypes();
                try {
                    ctor = clz.getConstructor(params);
                    cmp = params[0];
                    break;
                }
                catch (NoSuchMethodException e) {
                    // empty catch block
                }
            }
            if (ctor == null) {
                throw new ReflectiveOperationException();
            }
            for (AccessibleObject accessibleObject : loginDisconnectPacket.getDeclaredFields()) {
                if (!cmp.equals(((Field)accessibleObject).getType())) continue;
                ((Field)accessibleObject).setAccessible(true);
                f = accessibleObject;
                break;
            }
            if (f == null) {
                throw new ReflectiveOperationException();
            }
            this.packetLoginDisconnectMsg = (Field)f;
            this.packetPlayDisconnectCtor = ctor;
            this.packetPlayDisconnect = clz;
        }
        catch (ReflectiveOperationException ex) {
            this.packetPlayDisconnect = Void.TYPE;
        }
    }

    public void handleLoginEvent(PlayerLoginEvent event) {
        block9: {
            String markerName = "$eaglerMarker_" + ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
            try {
                Object player = BukkitUnsafe.getHandle(event.getPlayer());
                GameProfile profile = BukkitUnsafe.getGameProfile(player);
                if (AuthlibCompat.containsKey(AuthlibCompat.getProperties(profile), "floodgate:is_bedrock")) {
                    return;
                }
                Property marker = BukkitUnsafe.injectProfileProperty(player, markerName, "TMP", null);
                if (marker == null) {
                    this.plugin.logger().warn("EaglerXServer: could not inject $eaglerMarker_ property into GameProfile for player " + event.getPlayer().getName() + " \u2014 post-login Eagler features will be unavailable");
                    return;
                }
                this.entityPlayers.put(marker, event.getPlayer());
                if (!this.isModernLoginFlow()) break block9;
                LoginEventContext ctx = (LoginEventContext)this.ctxByUUID.get(event.getPlayer().getUniqueId());
                if (ctx == null) {
                    this.plugin.logger().warn("EaglerXServer: no login context found for Eagler player " + event.getPlayer().getName() + " \u2014 post-login Eagler features will be unavailable for this connection");
                }
                if (ctx == null) break block9;
                this.ctxByUUID.remove(event.getPlayer().getUniqueId());
                Object nm = ctx.originalNetworkManager;
                Channel ch = ctx.channel;
                Runnable cleanupTask = () -> {
                    block7: {
                        try {
                            try {
                                Method m = PlayerPostLoginInjector.findDisableCompressionMethod(nm.getClass());
                                if (m != null) {
                                    if (m.getParameterCount() == 2) {
                                        m.invoke(nm, -1, false);
                                    } else {
                                        m.invoke(nm, -1);
                                    }
                                    break block7;
                                }
                                PlayerPostLoginInjector.swapCompressionNOP(ch, "decompress");
                                PlayerPostLoginInjector.swapCompressionNOP(ch, "compress");
                            }
                            catch (Throwable ignored) {
                                PlayerPostLoginInjector.swapCompressionNOP(ch, "decompress");
                                PlayerPostLoginInjector.swapCompressionNOP(ch, "compress");
                            }
                        }
                        catch (Throwable e) {
                            this.plugin.logger().error("EaglerXServer: compression cleanup failed", e);
                        }
                    }
                };
                if (ch.eventLoop().inEventLoop()) {
                    cleanupTask.run();
                } else {
                    try {
                        ch.eventLoop().submit(cleanupTask);
                    }
                    catch (Throwable t) {
                        this.plugin.logger().error("EaglerXServer: failed to schedule cleanup", t);
                    }
                }
                this.fireEventLoginPostAsync(event.getPlayer(), ctx, res -> {});
            }
            catch (Throwable t) {
                this.plugin.logger().warn("EaglerXServer: handleLoginEvent failed for player " + event.getPlayer().getName() + " \u2014 post-login Eagler features will be unavailable", t);
            }
        }
    }

    public void fireEventLoginInit(Channel channel) {
        this.plugin.getServer().getPluginManager().callEvent((Event)new PlayerLoginInitEventImpl(channel));
    }

    private void fireEventLoginPostAsync(Player player, LoginEventContext ctx, Consumer<PlayerLoginPostEvent> callback) {
        PlayerLoginPostEventImpl evt = new PlayerLoginPostEventImpl(player, ctx, callback);
        this.plugin.getServer().getPluginManager().callEvent((Event)evt);
        evt.complete();
    }

    private static void swapCompressionNOP(Channel ch, String name) {
        ChannelHandler handler = ch.pipeline().get(name);
        if (handler != null && handler.getClass().getSimpleName().contains("ompress")) {
            try {
                ch.pipeline().replace(name, name, (ChannelHandler)NOPDummyHandler.INSTANCE);
            }
            catch (Throwable t8) {
                // empty catch block
            }
        }
    }

    private static Method findDisableCompressionMethod(Class<?> netManagerClass) {
        try {
            return netManagerClass.getMethod("setupCompression", Integer.TYPE, Boolean.TYPE);
        }
        catch (NoSuchMethodException noSuchMethodException) {
            for (String name : LEGACY_COMPRESSION_METHOD_NAMES) {
                try {
                    return netManagerClass.getMethod(name, Integer.TYPE);
                }
                catch (NoSuchMethodException noSuchMethodException2) {
                }
            }
            for (Method m : netManagerClass.getMethods()) {
                if (m.getParameterCount() != 1 || m.getParameterTypes()[0] != Integer.TYPE || m.getReturnType() != Void.TYPE) continue;
                if (!m.getName().contains("ompress")) continue;
                return m;
            }
            return null;
        }
    }

    public static void setPlayState(PlayerLoginPostEvent evt) {
        ((PlayerLoginPostEventImpl)evt).ctx.clientPlayState = true;
    }

    public static class LoginEventContext {
        protected final Object originalNetworkManager;
        protected final Channel channel;
        protected volatile Object proxiedNetworkManager;
        protected volatile boolean compressionDisable;
        protected volatile boolean throwOnLoginSuccess;
        protected volatile boolean clientPlayState;
        protected volatile boolean pendingPostLogin;
        protected volatile Object loginListener;

        protected LoginEventContext(Object originalNetworkManager, Channel channel) {
            this.originalNetworkManager = originalNetworkManager;
            this.channel = channel;
        }

        public Object originalNetworkManager() {
            return this.originalNetworkManager;
        }

        public void markCompressionDisable(boolean en) {
            this.compressionDisable = en;
        }

        public void markThrowOnLoginSuccess(boolean en) {
            this.throwOnLoginSuccess = en;
        }

        public void markClientPlayState(boolean en) {
            this.clientPlayState = en;
        }
    }

    public static class EaglerError
    extends Error {
        protected final GameProfile gameProfile;

        public EaglerError(GameProfile gameProfile) {
            this.gameProfile = gameProfile;
        }
    }
}

