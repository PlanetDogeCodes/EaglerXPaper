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

package net.lax1dude.eaglercraft.backend.server.bukkit.async;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerLoginEvent;

import com.google.common.collect.MapMaker;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GenericFutureListener;
import net.lax1dude.eaglercraft.backend.server.api.bukkit.event.PlayerLoginPostEvent;
import net.lax1dude.eaglercraft.backend.server.bukkit.BukkitUnsafe;
import net.lax1dude.eaglercraft.backend.server.bukkit.NmsNames;
import net.lax1dude.eaglercraft.backend.server.bukkit.PlatformPluginBukkit;
import net.lax1dude.eaglercraft.backend.server.util.ClassProxy;
import net.lax1dude.eaglercraft.backend.server.util.Util;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

public class PlayerPostLoginInjector {

        private static final VarHandle NETMANAGERCLASS_HANDLE;
        private static final VarHandle LOGINLISTENERCLASS_HANDLE;
        private static final VarHandle PACKETLOGINSUCCESSCLASS_HANDLE;
        private static final VarHandle PACKETPLAYDISCONNECT_HANDLE;

        static {
                try {
                        MethodHandles.Lookup lookup = MethodHandles.lookup();
                        NETMANAGERCLASS_HANDLE = lookup.findVarHandle(PlayerPostLoginInjector.class, "netManagerClass", Class.class);
                        LOGINLISTENERCLASS_HANDLE = lookup.findVarHandle(PlayerPostLoginInjector.class, "loginListenerClass", Class.class);
                        PACKETLOGINSUCCESSCLASS_HANDLE = lookup.findVarHandle(PlayerPostLoginInjector.class, "packetLoginSuccessClass", Class.class);
                        PACKETPLAYDISCONNECT_HANDLE = lookup.findVarHandle(PlayerPostLoginInjector.class, "packetPlayDisconnect", Class.class);
                } catch(ReflectiveOperationException ex) {
                        throw new ExceptionInInitializerError(ex);
                }
        }

        public static final AttributeKey<LoginEventContext> attr = AttributeKey.valueOf("eagler-postlogin-hack");

        protected final PlatformPluginBukkit plugin;

        protected volatile Class<Object> netManagerClass;
        protected Constructor<Object> netManagerCtor;
        protected ClassProxy<Object> netManagerProxy;
        protected Field netManagerDir;
        protected Field netManagerChannel;
        protected Method setHandlerMethod;
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

        /**
         * The 'transferred' boolean field on ServerLoginPacketListenerImpl (1.20.5+).
         * Null on older versions. Used to pass the correct value to the 3-arg constructor.
         */
        protected Field loginListenerTransferred;

        protected volatile Class<Object> packetLoginSuccessClass;
        protected Field packetLoginSuccessGameProfile;

        protected volatile Class<?> packetPlayDisconnect;
        protected Constructor<?> packetPlayDisconnectCtor;
        protected Field packetLoginDisconnectMsg;

        protected final ConcurrentMap<Property, Player> entityPlayers;

        public PlayerPostLoginInjector(PlatformPluginBukkit plugin) {
                this.plugin = plugin;
                this.entityPlayers = (new MapMaker()).concurrencyLevel(8).weakKeys().weakValues().makeMap();
        }

        private synchronized void bind(Object netManager) {
                if (NETMANAGERCLASS_HANDLE.getAcquire(this) != null) {
                        return;
                }
                try {
                        Class<Object> netManagerClass = (Class<Object>) netManager.getClass();
                        Class<Object> protocolDirType = null;
                        Field protocolDirField = null;
                        Field channelField = null;
                        // Walk superclass chain for EnumProtocolDirection/PacketFlow field
                        Class<?> nmWalk = netManagerClass;
                        pdir: do {
                                for (Field f : nmWalk.getDeclaredFields()) {
                                        Class<?> clz = f.getType();
                                        if (NmsNames.PROTOCOL_DIRECTION.contains(clz.getSimpleName())) {
                                                f.setAccessible(true);
                                                protocolDirType = (Class<Object>) f.getType();
                                                protocolDirField = f;
                                                break pdir;
                                        }
                                }
                        } while ((nmWalk = nmWalk.getSuperclass()) != Object.class);
                        // Walk superclass chain for Channel field (was using getFields)
                        Class<?> nmWalk2 = netManagerClass;
                        chf: do {
                                for (Field f : nmWalk2.getDeclaredFields()) {
                                        Class<?> clz = f.getType();
                                        if (Channel.class.isAssignableFrom(clz)) {
                                                f.setAccessible(true);
                                                channelField = f;
                                                break chf;
                                        }
                                }
                        } while ((nmWalk2 = nmWalk2.getSuperclass()) != Object.class);
                        if (protocolDirField == null) {
                                throw new IllegalStateException("Could not locate direction field of " + netManagerClass.getName());
                        }
                        if (channelField == null) {
                                throw new IllegalStateException("Could not locate channel field of " + netManagerClass.getName());
                        }
                        Method setHandlerMethod = null;
                        Method sendPacketMethod1 = null;
                        Method sendPacketMethod2 = null;
                        Method sendPacketMethod3 = null;
                        Method getHandlerMethod = null;
                        Class<?> futureListenerArr = Array.newInstance(GenericFutureListener.class, 0).getClass();
                        for (Method m : netManagerClass.getMethods()) {
                                Class<?>[] params = m.getParameterTypes();
                                if (setHandlerMethod == null && params.length == 1
                                                && params[0].getSimpleName().equals("PacketListener")) {
                                        setHandlerMethod = m;
                                } else if (sendPacketMethod1 == null && params.length == 1
                                                && params[0].getSimpleName().equals("Packet")) {
                                        sendPacketMethod1 = m;
                                } else if (sendPacketMethod3 == null && params.length == 3 && params[0].getSimpleName().equals("Packet")
                                                && GenericFutureListener.class.isAssignableFrom(params[1]) && params[2].equals(futureListenerArr)) {
                                        sendPacketMethod3 = m;
                                        sendPacketMethod2 = null;
                                } else if (sendPacketMethod3 == null && sendPacketMethod2 == null && params.length == 2
                                                && params[0].getSimpleName().equals("Packet")
                                                && GenericFutureListener.class.isAssignableFrom(params[1])) {
                                        sendPacketMethod2 = m;
                                } else if (getHandlerMethod == null && params.length == 0
                                                && m.getReturnType().getSimpleName().equals("PacketListener")) {
                                        getHandlerMethod = m;
                                }
                                if (setHandlerMethod != null && sendPacketMethod1 != null && sendPacketMethod3 != null
                                                && getHandlerMethod != null) {
                                        break;
                                }
                        }
                        if (setHandlerMethod == null) {
                                throw new IllegalStateException(
                                                "Could not locate set handler function of " + netManagerClass.getName());
                        }
                        if (sendPacketMethod1 == null) {
                                throw new IllegalStateException(
                                                "Could not locate send packet (1 param) function of " + netManagerClass.getName());
                        }
                        if (sendPacketMethod2 == null && sendPacketMethod3 == null) {
                                throw new IllegalStateException(
                                                "Could not locate send packet (2 or 3 param) function of " + netManagerClass.getName());
                        }
                        if (getHandlerMethod == null) {
                                throw new IllegalStateException(
                                                "Could not locate get handler function of " + netManagerClass.getName());
                        }
                        Object handshakeListener = getHandlerMethod.invoke(netManager);
                        Class<Object> handshakeListenerClass = (Class<Object>) handshakeListener.getClass();
                        Field handshakeListenerNetManager = null;
                        // Walk superclass chain for the netManager field (was only on immediate class)
                        Class<?> hslWalk = handshakeListenerClass;
                        hsl: do {
                                for (Field f : hslWalk.getDeclaredFields()) {
                                        if (f.getType() == netManagerClass) {
                                                f.setAccessible(true);
                                                handshakeListenerNetManager = f;
                                                break hsl;
                                        }
                                }
                        } while ((hslWalk = hslWalk.getSuperclass()) != Object.class);
                        if (handshakeListenerNetManager == null) {
                                throw new IllegalStateException(
                                                "Could not locate network manager field of " + handshakeListenerClass.getName());
                        }
                        this.netManagerCtor = netManagerClass.getDeclaredConstructor(protocolDirType);
                        this.netManagerCtor.setAccessible(true);
                        this.netManagerProxy = ClassProxy.bindProxy(PlayerPostLoginInjector.class.getClassLoader(),
                                        netManagerClass);
                        this.netManagerDir = protocolDirField;
                        this.netManagerChannel = channelField;
                        this.setHandlerMethod = setHandlerMethod;
                        this.sendPacketMethod1 = sendPacketMethod1;
                        this.sendPacketMethod2 = sendPacketMethod2;
                        this.sendPacketMethod3 = sendPacketMethod3;
                        this.getHandlerMethod = getHandlerMethod;
                        this.handshakeListenerClass = handshakeListenerClass;
                        this.handshakeListenerNetManager = handshakeListenerNetManager;
                        this.handlerAdded = ChannelHandlerAdapter.class.getDeclaredField("added");
                        this.handlerAdded.setAccessible(true);
                        NETMANAGERCLASS_HANDLE.setRelease(this, netManagerClass);
                } catch (ReflectiveOperationException e) {
                        throw Util.propagateReflectThrowable(e);
                }
        }

        public static class LoginEventContext {

                protected final Object originalNetworkManager;
                protected final Channel channel;
                protected Object proxiedNetworkManager;
                protected boolean compressionDisable;
                protected boolean throwOnLoginSuccess;
                protected volatile boolean clientPlayState;

                protected LoginEventContext(Object originalNetworkManager, Channel channel) {
                        this.originalNetworkManager = originalNetworkManager;
                        this.channel = channel;
                }

                public Object originalNetworkManager() {
                        return originalNetworkManager;
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

        public static class EaglerError extends Error {

                protected final GameProfile gameProfile;

                public EaglerError(GameProfile gameProfile) {
                        this.gameProfile = gameProfile;
                }

        }

        public Object wrapNetworkManager(Object netManager, Channel channel) {
                Class<?> netManagerClass;
                if ((netManagerClass = (Class<?>) NETMANAGERCLASS_HANDLE.getAcquire(this)) == null) {
                        bind(netManager);
                        netManagerClass = (Class<?>) NETMANAGERCLASS_HANDLE.getAcquire(this);
                }
                if (!netManagerClass.isAssignableFrom(netManager.getClass())) {
                        throw new IllegalStateException("Unknown NetworkManager type: " + netManager.getClass().getName());
                }
                try {
                        final LoginEventContext ctx = new LoginEventContext(netManager, channel);
                        Object ret = netManagerProxy.createProxy(netManagerCtor, new Object[] { netManagerDir.get(netManager) },
                                        (obj, meth, args) -> {
                                                if (setHandlerMethod.equals(meth)) {
                                                        if (NmsNames.matches(args[0], NmsNames.LOGIN_LISTENER)) {
                                                                meth.invoke(netManager, args);
                                                                fireEventLoginInit(channel);
                                                                args[0] = wrapLoginListener(getHandlerMethod.invoke(netManager), ctx);
                                                                meth.invoke(netManager, args);
                                                                return null;
                                                        }
                                                } else if (sendPacketMethod1.equals(meth)) {
                                                        String nm = args[0].getClass().getSimpleName();
                                                        if (NmsNames.PACKET_LOGIN_DISCONNECT.contains(nm) && ctx.clientPlayState) {
                                                                Class<?> clz2;
                                                                if ((clz2 = (Class<?>) PACKETPLAYDISCONNECT_HANDLE.getAcquire(this)) == null) {
                                                                        bindPacketPlayDisconnect(args[0].getClass());
                                                                        clz2 = (Class<?>) PACKETPLAYDISCONNECT_HANDLE.getAcquire(this);
                                                                }
                                                                if (clz2 != void.class) {
                                                                        args[0] = packetPlayDisconnectCtor.newInstance(packetLoginDisconnectMsg.get(args[0]));
                                                                } else {
                                                                        return null;
                                                                }
                                                        }
                                                        meth.invoke(netManager, args);
                                                        if (ctx.throwOnLoginSuccess && NmsNames.PACKET_LOGIN_SUCCESS.contains(nm)) {
                                                                throw new EaglerError(getPacketProfile(args[0]));
                                                        }
                                                        return null;
                                                } else if (ctx.compressionDisable && (sendPacketMethod3 != null ? sendPacketMethod3.equals(meth)
                                                                : sendPacketMethod2.equals(meth))) {
                                                        if (NmsNames.PACKET_LOGIN_SET_COMPRESSION.contains(args[0].getClass().getSimpleName())) {
                                                                return null;
                                                        }
                                                }
                                                return meth.invoke(netManager, args);
                                        });
                        ctx.proxiedNetworkManager = ret;
                        channel.attr(attr).set(ctx);
                        handshakeListenerNetManager.set(getHandlerMethod.invoke(netManager), ret);
                        netManagerChannel.set(ret, channel);
                        return ret;
                } catch (ReflectiveOperationException e) {
                        throw Util.propagateReflectThrowable(e);
                }
        }

        private synchronized void bindPacketProfile(Object packet) {
                if (PACKETLOGINSUCCESSCLASS_HANDLE.getAcquire(this) != null) {
                        return;
                }
                Field gameProfile = null;
                Class<Object> clz = (Class<Object>) packet.getClass();
                for (Field f : clz.getDeclaredFields()) {
                        if (f.getType().equals(GameProfile.class)) {
                                f.setAccessible(true);
                                gameProfile = f;
                                break;
                        }
                }
                if (gameProfile == null) {
                        throw new IllegalStateException("Could not locate game profile field of " + clz.getName());
                }
                packetLoginSuccessGameProfile = gameProfile;
                PACKETLOGINSUCCESSCLASS_HANDLE.setRelease(this, clz);
        }

        private GameProfile getPacketProfile(Object packet) {
                Class<?> clz;
                if ((clz = (Class<?>) PACKETLOGINSUCCESSCLASS_HANDLE.getAcquire(this)) == null) {
                        bindPacketProfile(packet);
                        clz = (Class<?>) PACKETLOGINSUCCESSCLASS_HANDLE.getAcquire(this);
                }
                if (!clz.isAssignableFrom(packet.getClass())) {
                        throw new IllegalStateException("Unknown PacketLoginOutSuccess type: " + packet.getClass().getName());
                }
                try {
                        return (GameProfile) packetLoginSuccessGameProfile.get(packet);
                } catch (ReflectiveOperationException e) {
                        throw Util.propagateReflectThrowable(e);
                }
        }

        private synchronized void bindLogin(Object loginListener) {
                if (LOGINLISTENERCLASS_HANDLE.getAcquire(this) != null) {
                        return;
                }
                Class<?> clz2;
                if ((clz2 = (Class<?>) NETMANAGERCLASS_HANDLE.getAcquire(this)) == null) {
                        throw new IllegalStateException();
                }
                try {
                        Class<Object> loginListenerClass = (Class<Object>) loginListener.getClass();
                        Class<Object> mcServerClass = null;
                        Constructor<Object> loginListenerCtor = null;
                        int ctorArgCount = 2;
                        // Accept both 2-arg (MinecraftServer, Connection) and 3-arg (MinecraftServer, Connection, boolean)
                        // forms — the 3-arg form was added in some 1.21.x builds.
                        for (Constructor<? extends Object> ctor : loginListenerClass.getConstructors()) {
                                Class<?>[] params = ctor.getParameterTypes();
                                if (params.length == 2 && params[1] == clz2) {
                                        loginListenerCtor = (Constructor<Object>) ctor;
                                        mcServerClass = (Class<Object>) params[0];
                                        ctorArgCount = 2;
                                        break;
                                } else if (params.length == 3 && params[1] == clz2 && params[2] == boolean.class) {
                                        loginListenerCtor = (Constructor<Object>) ctor;
                                        mcServerClass = (Class<Object>) params[0];
                                        ctorArgCount = 3;
                                        break;
                                }
                        }
                        if (loginListenerCtor == null) {
                                throw new IllegalStateException("Could not locate constructor of " + loginListenerClass.getName());
                        }
                        Field loginListenerServer = null;
                        Field loginListenerNetManager = null;
                        Class<Object> enumProtocolState = null;
                        Field loginListenerState = null;
                        Field loginListenerPlayer = null;
                        Field loginListenerTransferred = null; // 1.20.5+ boolean field
                        for (Field f : loginListenerClass.getDeclaredFields()) {
                                if (f.getType() == mcServerClass) {
                                        f.setAccessible(true);
                                        loginListenerServer = f;
                                } else if (f.getType() == clz2) {
                                        f.setAccessible(true);
                                        loginListenerNetManager = f;
                                } else if (NmsNames.LOGIN_STATE_ENUM_SIMPLE.contains(f.getType().getSimpleName())
                                                && f.getType().getName().startsWith(loginListenerClass.getName())) {
                                        f.setAccessible(true);
                                        loginListenerState = f;
                                        enumProtocolState = (Class<Object>) f.getType();
                                } else if (NmsNames.matches(f.getType(), NmsNames.ENTITY_PLAYER)) {
                                        f.setAccessible(true);
                                        loginListenerPlayer = f;
                                } else if (f.getType() == boolean.class && "transferred".equals(f.getName())) {
                                        // 1.20.5+ has a 'transferred' boolean field
                                        f.setAccessible(true);
                                        loginListenerTransferred = f;
                                }
                                if (loginListenerServer != null && loginListenerNetManager != null && loginListenerState != null
                                                && loginListenerPlayer != null) {
                                        break;
                                }
                        }
                        if (loginListenerServer == null) {
                                throw new IllegalStateException("Could not locate server field of " + loginListenerClass.getName());
                        }
                        if (loginListenerNetManager == null) {
                                throw new IllegalStateException(
                                                "Could not locate network manager field of " + loginListenerClass.getName());
                        }
                        if (loginListenerState == null) {
                                throw new IllegalStateException("Could not locate state field of " + loginListenerClass.getName());
                        }
                        if (loginListenerPlayer == null) {
                                throw new IllegalStateException("Could not locate player field of " + loginListenerClass.getName());
                        }
                        Method loginListenerTick = null;
                        Method loginListenerDisconnect = null;
                        // Try disconnect(Component) first — 1.20+ uses net.kyori.adventure.text.Component
                        try {
                                Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
                                loginListenerDisconnect = loginListenerClass.getMethod("disconnect", componentClass);
                        } catch (ClassNotFoundException | NoSuchMethodException e) {
                        }
                        // Fall back to disconnect(String)
                        if (loginListenerDisconnect == null) {
                                try {
                                        loginListenerDisconnect = loginListenerClass.getMethod("disconnect", String.class);
                                } catch (NoSuchMethodException e) {
                                }
                        }
                        // Last resort: any single-arg disconnect method.
                        // Prefer String, then Component types, then anything else.
                        if (loginListenerDisconnect == null) {
                                Method stringDisconnect = null;
                                Method componentDisconnect = null;
                                Method anyDisconnect = null;
                                for (Method m : loginListenerClass.getMethods()) {
                                        if (m.getName().equals("disconnect") && m.getParameterCount() == 1) {
                                                Class<?> paramType = m.getParameterTypes()[0];
                                                if (paramType == String.class) {
                                                        stringDisconnect = m;
                                                } else if (paramType.getName().contains("Component")) {
                                                        componentDisconnect = m;
                                                } else {
                                                        anyDisconnect = m;
                                                }
                                        }
                                }
                                // Prefer String > Component > any
                                loginListenerDisconnect = stringDisconnect != null ? stringDisconnect
                                                : (componentDisconnect != null ? componentDisconnect : anyDisconnect);
                        }
                        if (loginListenerDisconnect == null) {
                                throw new IllegalStateException(
                                                "Could not locate disconnect function of " + loginListenerClass.getName());
                        }
                        for (Class<?> clz : loginListenerClass.getInterfaces()) {
                                String s = clz.getSimpleName();
                                if (s.equals("IUpdatePlayerListBox") || s.equals("ITickable")) {
                                        loginListenerTick = loginListenerClass.getMethod(clz.getMethods()[0].getName());
                                        break;
                                }
                        }
                        if (loginListenerTick == null) {
                                try {
                                        loginListenerTick = loginListenerClass.getMethod("tick");
                                } catch (ReflectiveOperationException ex) {
                                }
                        }
                        if (loginListenerTick == null) {
                                throw new IllegalStateException("Could not locate tick function of " + loginListenerClass.getName());
                        }
                        Object protocolStateOnResume = findEnumValueByName(enumProtocolState, "READY_TO_ACCEPT", "READY_TO_LOGIN");
                        if (protocolStateOnResume == null) {
                                Object[] obj = enumProtocolState.getEnumConstants();
                                if (obj != null && obj.length > 4) {
                                        protocolStateOnResume = obj[obj.length - 2];
                                }
                        }
                        if (protocolStateOnResume == null) {
                                throw new IllegalStateException(
                                                "Could not locate stalling state enum of " + enumProtocolState.getName());
                        }
                        this.loginListenerCtor = loginListenerCtor;
                        this.loginListenerCtorArgCount = ctorArgCount;
                        this.loginListenerProxy = ClassProxy.bindProxy(PlayerPostLoginInjector.class.getClassLoader(),
                                        loginListenerClass);
                        this.loginListenerServer = loginListenerServer;
                        this.loginListenerNetManager = loginListenerNetManager;
                        this.enumProtocolState = enumProtocolState;
                        this.protocolStateOnResume = protocolStateOnResume;
                        this.loginListenerState = loginListenerState;
                        this.loginListenerTick = loginListenerTick;
                        this.loginListenerDisconnect = loginListenerDisconnect;
                        this.loginListenerPlayer = loginListenerPlayer;
                        this.loginListenerTransferred = loginListenerTransferred;
                        LOGINLISTENERCLASS_HANDLE.setRelease(this, loginListenerClass);
                } catch (ReflectiveOperationException e) {
                        throw Util.propagateReflectThrowable(e);
                }
        }

        private Object wrapLoginListener(Object loginListener, LoginEventContext ctx) {
                Class<?> loginListenerClass;
                if ((loginListenerClass = (Class<?>) LOGINLISTENERCLASS_HANDLE.getAcquire(this)) == null) {
                        bindLogin(loginListener);
                        loginListenerClass = (Class<?>) LOGINLISTENERCLASS_HANDLE.getAcquire(this);
                }
                if (!loginListenerClass.isAssignableFrom(loginListener.getClass())) {
                        throw new IllegalStateException("Unknown LoginListener type: " + loginListener.getClass().getName());
                }
                try {
                        Object[] ctorArgs;
                        if (loginListenerCtorArgCount == 3) {
                                // Read the 'transferred' value from the original listener (1.20.5+).
                                // This preserves the correct transfer state for the proxy listener.
                                boolean transferred = false;
                                if (loginListenerTransferred != null) {
                                        try {
                                                transferred = loginListenerTransferred.getBoolean(loginListener);
                                        } catch (Exception e) {
                                                // Best effort — default to false
                                        }
                                }
                                ctorArgs = new Object[] { loginListenerServer.get(loginListener), ctx.proxiedNetworkManager,
                                                transferred };
                        } else {
                                ctorArgs = new Object[] { loginListenerServer.get(loginListener), ctx.proxiedNetworkManager };
                        }
                        return loginListenerProxy.createProxy(loginListenerCtor, ctorArgs, (obj, meth, args) -> {
                                                if (loginListenerTick.equals(meth)) {
                                                        try {
                                                                ctx.markThrowOnLoginSuccess(true);
                                                                try {
                                                                        return meth.invoke(loginListener, args);
                                                                } finally {
                                                                        ctx.markThrowOnLoginSuccess(false);
                                                                }
                                                        } catch (InvocationTargetException ex) {
                                                                Throwable er = ex.getCause();
                                                                if (er instanceof EaglerError err) {
                                                                        Player player = null;
                                                                        synchronized (err.gameProfile) {
                                                                                java.util.Iterator<Property> itr = BukkitUnsafe.getPropertyValuesSafe(err.gameProfile).iterator();
                                                                                while (itr.hasNext()) {
                                                                                        Property prop = itr.next();
                                                                                        if (prop.getName().startsWith("$eaglerMarker_")) {
                                                                                                Player e = entityPlayers.remove(prop);
                                                                                                if (e != null) {
                                                                                                        player = e;
                                                                                                }
                                                                                                itr.remove();
                                                                                        }
                                                                                }
                                                                        }
                                                                        if (player != null) {
                                                                                final Player playerFinal = player;
                                                                                fireEventLoginPostAsync(playerFinal, ctx, (res) -> {
                                                                                        try {
                                                                                                if (!res.isCancelled()) {
                                                                                                        handlerAdded.set(ctx.originalNetworkManager, false);
                                                                                                        ctx.channel.pipeline().replace("packet_handler", "packet_handler",
                                                                                                                        (ChannelHandler) ctx.originalNetworkManager);
                                                                                                        Object entityPlayer = BukkitUnsafe.getHandle(playerFinal);
                                                                                                        loginListenerNetManager.set(loginListener,
                                                                                                                        ctx.originalNetworkManager);
                                                                                                        loginListenerPlayer.set(loginListener, entityPlayer);
                                                                                                        loginListenerState.set(loginListener, protocolStateOnResume);
                                                                                                } else {
                                                                                                        BaseComponent comp = res.getMessage();
                                                                                                        if (comp == null) {
                                                                                                                comp = new TextComponent("Connection Closed");
                                                                                                        }
                                                                                                        String legacyText = comp.toLegacyText();
                                                                                                        // The disconnect method may take String (1.12-1.16) or
                                                                                                        // net.minecraft.network.chat.Component (1.17+) or
                                                                                                        // net.kyori.adventure.text.Component (Paper adventure).
                                                                                                        // Convert the legacy text to the correct parameter type.
                                                                                                        Object arg = legacyText;
                                                                                                        Class<?> paramType = loginListenerDisconnect.getParameterTypes()[0];
                                                                                                        if (paramType != String.class) {
                                                                                                                arg = convertToComponent(legacyText, paramType);
                                                                                                        }
                                                                                                        loginListenerDisconnect.invoke(loginListener, arg);
                                                                                                }
                                                                                        } catch (ReflectiveOperationException e) {
                                                                                                throw Util.propagateReflectThrowable(e);
                                                                                        }
                                                                                });
                                                                                return null;
                                                                        } else {
                                                                                throw new IllegalStateException();
                                                                        }
                                                                } else {
                                                                        if (er instanceof RuntimeException ee)
                                                                                throw ee;
                                                                        throw new RuntimeException(er);
                                                                }
                                                        }
                                                }
                                                return meth.invoke(loginListener, args);
                                        });
                } catch (ReflectiveOperationException e) {
                        throw Util.propagateReflectThrowable(e);
                }
        }

        private static Object findEnumValueByName(Class<?> enumClass, String... names) {
                if (enumClass == null || !enumClass.isEnum()) {
                        return null;
                }
                Object[] constants = enumClass.getEnumConstants();
                if (constants == null) {
                        return null;
                }
                for (Object constant : constants) {
                        String n = ((Enum<?>) constant).name();
                        for (String want : names) {
                                if (want.equals(n)) {
                                        return constant;
                                }
                        }
                }
                return null;
        }

        /**
         * Converts a legacy text string to the appropriate Component type for the
         * disconnect method's parameter. Supports:
         * - net.minecraft.network.chat.Component (1.17+ Mojang mappings)
         * - net.kyori.adventure.text.Component (Paper adventure)
         *
         * If conversion fails, returns the original string (which will cause an
         * IllegalArgumentException — logged but not swallowed, so the operator
         * can diagnose the mismatch).
         */
        private static Object convertToComponent(String legacyText, Class<?> paramType) {
                String typeName = paramType.getName();
                try {
                        // Try net.minecraft.network.chat.Component (1.17+ Mojang)
                        if (typeName.startsWith("net.minecraft.network.chat.") && typeName.endsWith("Component")) {
                                // Try Component.literal(text) (1.20+)
                                try {
                                        Method literal = paramType.getMethod("literal", String.class);
                                        return literal.invoke(null, legacyText);
                                } catch (NoSuchMethodException nsme) {
                                        // Fall through to try TextComponent constructor
                                }
                                // Try new TextComponent(text) (1.17-1.19)
                                try {
                                        Class<?> textComponentClass = Class.forName("net.minecraft.network.chat.TextComponent");
                                        return textComponentClass.getConstructor(String.class).newInstance(legacyText);
                                } catch (ClassNotFoundException | NoSuchMethodException e) {
                                        // Fall through
                                }
                        }
                        // Try net.kyori.adventure.text.Component (Paper adventure)
                        if (typeName.startsWith("net.kyori.adventure.text.")) {
                                try {
                                        Class<?> adventureComponent = Class.forName("net.kyori.adventure.text.Component");
                                        Method text = adventureComponent.getMethod("text", String.class);
                                        return text.invoke(null, legacyText);
                                } catch (ClassNotFoundException | NoSuchMethodException e) {
                                        // Fall through
                                }
                        }
                } catch (Exception e) {
                        // Conversion failed — return the string, which will fail with a clear error
                }
                return legacyText;
        }

        private static final String[] KNOWN_PLAY_DISCONNECT_FQNS = new String[] {
                        "net.minecraft.network.protocol.game.ClientboundDisconnectPacket",
                        "net.minecraft.server.v1_12_R1.PacketPlayOutKickDisconnect" };

        private synchronized void bindPacketPlayDisconnect(Class<?> loginDisconnectPacket) {
                if (PACKETPLAYDISCONNECT_HANDLE.getAcquire(this) != null) {
                        return;
                }
                try {
                        Class<?> clz = null;
                        // Try the known FQNs first (matches NmsNames.PACKET_PLAY_DISCONNECT)
                        for (String fqn : KNOWN_PLAY_DISCONNECT_FQNS) {
                                try {
                                        clz = Class.forName(fqn);
                                        break;
                                } catch (ClassNotFoundException e) {
                                }
                        }
                        if (clz == null) {
                                String nm2 = loginDisconnectPacket.getName();
                                nm2 = nm2.substring(0, nm2.lastIndexOf('.') + 1);
                                try {
                                        clz = Class.forName(nm2 + "PacketPlayOutKickDisconnect");
                                } catch (ReflectiveOperationException ex) {
                                        if (nm2.endsWith(".login.")) {
                                                clz = Class.forName(nm2.substring(0, nm2.length() - 7) + ".game.PacketPlayOutKickDisconnect");
                                        } else {
                                                throw ex;
                                        }
                                }
                        }
                        Constructor<?> ctor = null;
                        Class<?> cmp = null;
                        Field f = null;
                        for (Constructor<?> ctor2 : loginDisconnectPacket.getConstructors()) {
                                if (ctor2.getParameterCount() == 1) {
                                        Class<?>[] params = ctor2.getParameterTypes();
                                        try {
                                                ctor = clz.getConstructor(params);
                                                cmp = params[0];
                                                break;
                                        } catch (NoSuchMethodException e) {
                                                continue;
                                        }
                                }
                        }
                        if (ctor == null) {
                                throw new ReflectiveOperationException();
                        }
                        for (Field ff : loginDisconnectPacket.getDeclaredFields()) {
                                if (cmp.equals(ff.getType())) {
                                        ff.setAccessible(true);
                                        f = ff;
                                        break;
                                }
                        }
                        if (f == null) {
                                throw new ReflectiveOperationException();
                        }
                        packetLoginDisconnectMsg = f;
                        packetPlayDisconnectCtor = ctor;
                        PACKETPLAYDISCONNECT_HANDLE.setRelease(this, clz);
                } catch (ReflectiveOperationException ex) {
                        PACKETPLAYDISCONNECT_HANDLE.setRelease(this, void.class);
                }
        }

        public void handleLoginEvent(PlayerLoginEvent event) {
                Property marker = new Property("$eaglerMarker_" + ThreadLocalRandom.current().nextLong(Long.MAX_VALUE), "TMP");
                Object player = BukkitUnsafe.getHandle(event.getPlayer());
                GameProfile profile = BukkitUnsafe.getGameProfile(player);
                synchronized (profile) {
                        BukkitUnsafe.putPropertySafe(profile, marker.getName(), marker);
                }
                entityPlayers.put(marker, event.getPlayer());
        }

        private void fireEventLoginInit(Channel channel) {
                plugin.getServer().getPluginManager().callEvent(new PlayerLoginInitEventImpl(channel));
        }

        private void fireEventLoginPostAsync(Player player, LoginEventContext ctx, Consumer<PlayerLoginPostEvent> callback) {
                PlayerLoginPostEventImpl evt = new PlayerLoginPostEventImpl(player, ctx, callback);
                plugin.getServer().getPluginManager().callEvent(evt);
                evt.complete();
        }

        public static void setPlayState(PlayerLoginPostEvent evt) {
                ((PlayerLoginPostEventImpl) evt).ctx.clientPlayState = true;
        }

}
