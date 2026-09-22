/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.ImmutableMap
 *  com.google.common.collect.ImmutableMap$Builder
 *  com.google.common.collect.ImmutableSet
 *  com.google.common.net.InetAddresses
 *  io.netty.buffer.ByteBuf
 *  io.netty.buffer.Unpooled
 *  io.netty.channel.Channel
 *  io.netty.channel.ChannelFutureListener
 *  io.netty.channel.ChannelHandlerContext
 *  io.netty.handler.timeout.IdleStateHandler
 *  io.netty.util.concurrent.GenericFutureListener
 */
package net.lax1dude.eaglercraft.backend.server.base;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.InetAddresses;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.GenericFutureListener;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import net.lax1dude.eaglercraft.backend.server.adapter.IPipelineData;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformSubLogger;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformTask;
import net.lax1dude.eaglercraft.backend.server.api.EnumCapabilityType;
import net.lax1dude.eaglercraft.backend.server.api.EnumWebSocketHeader;
import net.lax1dude.eaglercraft.backend.server.api.IEaglerConnection;
import net.lax1dude.eaglercraft.backend.server.api.IEaglerListenerInfo;
import net.lax1dude.eaglercraft.backend.server.api.IEaglerLoginConnection;
import net.lax1dude.eaglercraft.backend.server.api.IEaglerPendingConnection;
import net.lax1dude.eaglercraft.backend.server.api.INettyChannel;
import net.lax1dude.eaglercraft.backend.server.api.attribute.IAttributeKey;
import net.lax1dude.eaglercraft.backend.server.api.rewind.IEaglerXRewindProtocol;
import net.lax1dude.eaglercraft.backend.server.base.BrandService;
import net.lax1dude.eaglercraft.backend.server.base.CapabilityBits;
import net.lax1dude.eaglercraft.backend.server.base.CompoundRateLimiterMap;
import net.lax1dude.eaglercraft.backend.server.base.EaglerAttributeManager;
import net.lax1dude.eaglercraft.backend.server.base.EaglerListener;
import net.lax1dude.eaglercraft.backend.server.base.EaglerLoginStateAdapter;
import net.lax1dude.eaglercraft.backend.server.base.EaglerPendingStateAdapter;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServer;
import net.lax1dude.eaglercraft.backend.server.base.IIdentifiedConnection;
import net.lax1dude.eaglercraft.backend.server.base.RateLimitMessage;
import net.lax1dude.eaglercraft.backend.server.base.message.RewindMessageControllerHandle;
import net.lax1dude.eaglercraft.backend.server.util.EnumRateLimitState;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.GamePluginMessageProtocol;

public class NettyPipelineData
extends IIdentifiedConnection.Base
implements IEaglerConnection,
INettyChannel.NettyUnsafe,
IPipelineData {
    private static final AtomicReferenceFieldUpdater<NettyPipelineData, Runnable> PLAY_STATE_REACHED_HANDLE = AtomicReferenceFieldUpdater.newUpdater(NettyPipelineData.class, Runnable.class, "playStateReached");
    private static final Set<String> profileDataStandard = ImmutableSet.of("skin_v1", "skin_v2", "cape_v1", "update_cert_v1", "brand_uuid_v1");
    public final Channel channel;
    public final EaglerXServer<?> server;
    public final EaglerAttributeManager.EaglerAttributeHolder attributeHolder;
    public final Consumer<SocketAddress> realAddressHandle;
    public SocketAddress realSocketAddressInstance;
    public CompoundRateLimiterMap.ICompoundRatelimits rateLimits;
    public IdleStateHandler idleStateHandler;
    public boolean initStall;
    public EaglerListener listenerInfo;
    public String eaglerBrandString;
    public String eaglerVersionString;
    public boolean wss;
    public String headerHost;
    public String headerOrigin;
    public String headerUserAgent;
    public String headerCookie;
    public String headerAuthorization;
    public String requestPath;
    public String realAddress;
    public InetAddress realInetAddress;
    public int handshakeProtocol;
    public GamePluginMessageProtocol gameProtocol;
    public int minecraftProtocol;
    public boolean handshakeAuthEnabled;
    public byte[] handshakeAuthUsername;
    public String username;
    public UUID uuid;
    public String requestedServer;
    public boolean authEventEnabled;
    public byte authType;
    public String authMessage;
    public boolean nicknameSelectionEnabled;
    public byte[] authSalt;
    public boolean cookieSupport;
    public boolean cookieEnabled;
    public boolean cookieAuthEventEnabled;
    public byte[] cookieData;
    public Map<String, byte[]> profileDatas;
    public int acceptedCapabilitiesMask;
    public byte[] acceptedCapabilitiesVers;
    public Map<UUID, Byte> acceptedExtendedCapabilities;
    public IPlatformSubLogger connectionLogger;
    public Object rewindAttachment;
    public IEaglerXRewindProtocol<?, ?> rewindProtocol;
    public int rewindProtocolVersion = -1;
    public RewindMessageControllerHandle rewindMessageControllerHandle;
    public EaglerPendingStateAdapter pendingConnection;
    public EaglerLoginStateAdapter loginConnection;
    private volatile IPlatformTask disconnectTask = null;
    private volatile int loginTimeoutGeneration = 0;
    private static final Runnable REACHED = () -> {};
    private volatile Runnable playStateReached = null;

    public NettyPipelineData(Channel channel, EaglerXServer<?> server, EaglerListener listenerInfo, EaglerAttributeManager.EaglerAttributeHolder attributeHolder, Consumer<SocketAddress> realAddressHandle, CompoundRateLimiterMap.ICompoundRatelimits rateLimits) {
        this.channel = channel;
        this.server = server;
        this.listenerInfo = listenerInfo;
        this.attributeHolder = attributeHolder;
        this.realAddressHandle = realAddressHandle;
        this.connectionLogger = server.logger().createSubLogger("" + channel.remoteAddress());
        this.rateLimits = rateLimits;
    }

    @Override
    public SocketAddress getSocketAddress() {
        return this.channel.remoteAddress();
    }

    public SocketAddress getPlayerAddress() {
        return this.realSocketAddressInstance != null ? this.realSocketAddressInstance : this.channel.remoteAddress();
    }

    @Override
    public String getRealAddress() {
        return this.realAddress;
    }

    @Override
    public boolean isEaglerPlayer() {
        return this.listenerInfo != null;
    }

    @Override
    public boolean isCompressionDisable() {
        return this.listenerInfo != null;
    }

    @Override
    public boolean isConnected() {
        return this.channel.isActive();
    }

    @Override
    public void disconnect() {
        this.channel.close();
    }

    @Override
    public Object getIdentityToken() {
        return this.attributeHolder;
    }

    @Override
    public <T> T get(IAttributeKey<T> key) {
        return this.attributeHolder.get(key);
    }

    @Override
    public <T> void set(IAttributeKey<T> key, T value) {
        this.attributeHolder.set(key, value);
    }

    @Override
    public IEaglerListenerInfo getListenerInfo() {
        return this.listenerInfo;
    }

    @Override
    public boolean isWebSocketSecure() {
        return this.wss;
    }

    @Override
    public String getWebSocketHeader(EnumWebSocketHeader header) {
        if (header == null) {
            throw new NullPointerException("header");
        }
        switch (header) {
            case HEADER_HOST: {
                return this.headerHost;
            }
            case HEADER_ORIGIN: {
                return this.headerOrigin;
            }
            case HEADER_USER_AGENT: {
                return this.headerUserAgent;
            }
            case HEADER_COOKIE: {
                return this.headerCookie;
            }
            case HEADER_AUTHORIZATION: {
                return this.headerAuthorization;
            }
        }
        return null;
    }

    @Override
    public String getWebSocketPath() {
        return this.requestPath;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void scheduleLoginTimeoutHelper() {
        if (this.disconnectTask == null) {
            NettyPipelineData nettyPipelineData = this;
            synchronized (nettyPipelineData) {
                if (this.disconnectTask != null) {
                    return;
                }
                int scheduledGeneration = ++this.loginTimeoutGeneration;
                this.disconnectTask = this.server.getPlatform().getScheduler().executeAsyncDelayedTask(() -> {
                    if (scheduledGeneration == this.loginTimeoutGeneration) {
                        this.channel.close();
                    }
                }, this.server.getConfig().getSettings().getEaglerLoginTimeout());
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void cancelLoginTimeoutHelper() {
        if (this.disconnectTask != null) {
            IPlatformTask task;
            NettyPipelineData nettyPipelineData = this;
            synchronized (nettyPipelineData) {
                task = this.disconnectTask;
                if (task == null) {
                    return;
                }
                this.disconnectTask = null;
                ++this.loginTimeoutGeneration;
            }
            task.cancel();
        }
    }

    public ProfileDataHolder profileDataHelper() {
        if (this.profileDatas != null) {
            byte[] skinV2 = this.profileDatas.get("skin_v2");
            byte[] skinV1 = skinV2 == null ? this.profileDatas.get("skin_v1") : null;
            byte[] cape = this.profileDatas.get("cape_v1");
            byte[] updateCert = this.profileDatas.get("update_cert_v1");
            byte[] uuid = this.profileDatas.get("brand_uuid_v1");
            UUID brandUUID = null;
            if (uuid != null && uuid.length == 16) {
                ByteBuf buf = Unpooled.wrappedBuffer((byte[])uuid);
                UUID ret = new UUID(buf.readLong(), buf.readLong());
                if (((BrandService)this.server.getBrandService()).sanitizeUUID(ret)) {
                    brandUUID = ret;
                }
            }
            if (brandUUID == null) {
                brandUUID = ((BrandService)this.server.getBrandService()).getBrandUUIDClientLegacy(this.eaglerBrandString);
            }
            ImmutableMap.Builder ret = null;
            for (Map.Entry<String, byte[]> extra : this.profileDatas.entrySet()) {
                if (profileDataStandard.contains(extra.getKey())) continue;
                if (ret == null) {
                    ret = ImmutableMap.builder();
                }
                ret.put((Object)extra.getKey(), (Object)extra.getValue());
            }
            return new ProfileDataHolder(skinV1, skinV2, cape, updateCert, brandUUID, (Map<String, byte[]>)(ret != null ? ret.build() : null));
        }
        return new ProfileDataHolder(null, null, null, null, ((BrandService)this.server.getBrandService()).getBrandUUIDClientLegacy(this.eaglerBrandString), Collections.emptyMap());
    }

    @Override
    public INettyChannel.NettyUnsafe netty() {
        return this;
    }

    @Override
    public Channel getChannel() {
        return this.channel;
    }

    public IEaglerPendingConnection asPendingConnection() {
        if (this.loginConnection != null) {
            return this.loginConnection;
        }
        if (this.pendingConnection != null) {
            return this.pendingConnection;
        }
        this.pendingConnection = new EaglerPendingStateAdapter(this);
        return this.pendingConnection;
    }

    public IEaglerLoginConnection asLoginConnection() {
        if (this.loginConnection != null) {
            return this.loginConnection;
        }
        this.loginConnection = new EaglerLoginStateAdapter(this);
        this.pendingConnection = null;
        return this.loginConnection;
    }

    public boolean processRealAddress() {
        Consumer<SocketAddress> handle;
        if (this.realAddress != null && (handle = this.realAddressHandle) != null) {
            InetAddress addr;
            if (this.realInetAddress != null) {
                addr = this.realInetAddress;
            } else {
                try {
                    addr = InetAddresses.forString((String)this.realAddress);
                }
                catch (IllegalArgumentException ex) {
                    this.connectionLogger.error("Connected with an invalid \"" + this.listenerInfo.getConfigData().getForwardIPHeader() + "\" header, disconnecting...", ex);
                    return false;
                }
            }
            int port = 65535;
            SocketAddress addr2 = this.channel.remoteAddress();
            if (addr2 instanceof InetSocketAddress) {
                port = ((InetSocketAddress)addr2).getPort();
            }
            this.realSocketAddressInstance = new InetSocketAddress(addr, port);
            handle.accept(this.realSocketAddressInstance);
        }
        return true;
    }

    public boolean processLoginRatelimit(ChannelHandlerContext ctx) {
        EnumRateLimitState state;
        if (this.rateLimits != null && !(state = this.rateLimits.rateLimitLogin()).isOk()) {
            switch (state) {
                case BLOCKED: {
                    ctx.writeAndFlush((Object)RateLimitMessage.getBlockedLoginMessage()).addListener((GenericFutureListener)ChannelFutureListener.CLOSE);
                    break;
                }
                case BLOCKED_LOCKED: {
                    ctx.writeAndFlush((Object)RateLimitMessage.getLockedLoginMessage()).addListener((GenericFutureListener)ChannelFutureListener.CLOSE);
                    break;
                }
                default: {
                    ctx.close();
                }
            }
            return false;
        }
        return true;
    }

    public boolean processQueryRatelimit(ChannelHandlerContext ctx) {
        EnumRateLimitState state;
        if (this.rateLimits != null && !(state = this.rateLimits.rateLimitQuery()).isOk()) {
            switch (state) {
                case BLOCKED: {
                    ctx.writeAndFlush((Object)RateLimitMessage.getBlockedQueryMessage()).addListener((GenericFutureListener)ChannelFutureListener.CLOSE);
                    break;
                }
                case BLOCKED_LOCKED: {
                    ctx.writeAndFlush((Object)RateLimitMessage.getLockedQueryMessage()).addListener((GenericFutureListener)ChannelFutureListener.CLOSE);
                    break;
                }
                default: {
                    ctx.close();
                }
            }
            return false;
        }
        return true;
    }

    public boolean hasLoginStateRedirectCap() {
        return this.gameProtocol.ver >= 5 && CapabilityBits.hasCapability(this.acceptedCapabilitiesMask, this.acceptedCapabilitiesVers, EnumCapabilityType.REDIRECT.getId(), 0);
    }

    public void signalPlayState() {
        Runnable runnable = PLAY_STATE_REACHED_HANDLE.getAndSet(this, REACHED);
        if (runnable != null) {
            runnable.run();
        }
    }

    @Override
    public void awaitPlayState(Runnable continueHandler) {
        if (this.listenerInfo != null) {
            if (!PLAY_STATE_REACHED_HANDLE.compareAndSet(this, null, continueHandler)) {
                continueHandler.run();
            }
        } else {
            continueHandler.run();
        }
    }

    public static class ProfileDataHolder {
        public final byte[] skinDataV1Init;
        public final byte[] skinDataV2Init;
        public final byte[] capeDataInit;
        public final byte[] updateCertInit;
        public final UUID brandUUID;
        public final Map<String, byte[]> extraData;

        protected ProfileDataHolder(byte[] skinDataV1Init, byte[] skinDataV2Init, byte[] capeDataInit, byte[] updateCertInit, UUID brandUUID, Map<String, byte[]> extraData) {
            this.skinDataV1Init = skinDataV1Init;
            this.skinDataV2Init = skinDataV2Init;
            this.capeDataInit = capeDataInit;
            this.updateCertInit = updateCertInit;
            this.brandUUID = brandUUID;
            this.extraData = extraData;
        }
    }
}

