/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.channel.Channel
 */
package net.lax1dude.eaglercraft.backend.server.base;

import io.netty.channel.Channel;
import java.net.SocketAddress;
import java.util.Map;
import java.util.UUID;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformPlayer;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformSubLogger;
import net.lax1dude.eaglercraft.backend.server.api.EnumCapabilitySpec;
import net.lax1dude.eaglercraft.backend.server.api.EnumCapabilityType;
import net.lax1dude.eaglercraft.backend.server.api.EnumWebSocketHeader;
import net.lax1dude.eaglercraft.backend.server.api.IBasePlayer;
import net.lax1dude.eaglercraft.backend.server.api.IEaglerListenerInfo;
import net.lax1dude.eaglercraft.backend.server.api.IEaglerPlayer;
import net.lax1dude.eaglercraft.backend.server.api.IUpdateCertificate;
import net.lax1dude.eaglercraft.backend.server.api.SHA1Sum;
import net.lax1dude.eaglercraft.backend.server.api.rewind.IEaglerXRewindProtocol;
import net.lax1dude.eaglercraft.backend.server.api.supervisor.ISupervisorService;
import net.lax1dude.eaglercraft.backend.server.base.BasePlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.CapabilityBits;
import net.lax1dude.eaglercraft.backend.server.base.NettyPipelineData;
import net.lax1dude.eaglercraft.backend.server.base.PlayerRateLimits;
import net.lax1dude.eaglercraft.backend.server.base.collect.ObjectHashSet;
import net.lax1dude.eaglercraft.backend.server.base.message.MessageController;
import net.lax1dude.eaglercraft.backend.server.base.message.RewindMessageControllerHandle;
import net.lax1dude.eaglercraft.backend.server.base.notifications.NotificationManagerPlayer;
import net.lax1dude.eaglercraft.backend.server.base.pause_menu.PauseMenuManager;
import net.lax1dude.eaglercraft.backend.server.base.rpc.EaglerPlayerRPCManager;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.ISupervisorServiceImpl;
import net.lax1dude.eaglercraft.backend.server.base.skins.SkinManagerEagler;
import net.lax1dude.eaglercraft.backend.server.base.supervisor.ISupervisorResolverImpl;
import net.lax1dude.eaglercraft.backend.server.base.update.IUpdateCertificateImpl;
import net.lax1dude.eaglercraft.backend.server.base.voice.IVoiceManagerImpl;
import net.lax1dude.eaglercraft.backend.server.base.webview.WebViewManager;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.GamePluginMessageProtocol;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.GameMessagePacket;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketOtherPlayerClientUUIDV4EAG;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketRedirectClientV4EAG;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketSetServerCookieV4EAG;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketUpdateCertEAG;

public class EaglerPlayerInstance<PlayerObject>
extends BasePlayerInstance<PlayerObject>
implements IEaglerPlayer<PlayerObject> {
    private final Channel channel;
    private final IEaglerListenerInfo listenerInfo;
    private final String eaglerBrandString;
    private final String eaglerVersionString;
    private final boolean wss;
    private final String headerHost;
    private final String headerOrigin;
    private final String headerUserAgent;
    private final String headerCookie;
    private final String headerAuthorization;
    private final String requestPath;
    private final String realAddress;
    private final int handshakeProtocol;
    private final GamePluginMessageProtocol gameProtocol;
    private final int minecraftProtocol;
    private final boolean handshakeAuthEnabled;
    private final byte[] handshakeAuthUsername;
    private final boolean cookieSupport;
    private final boolean cookieEnabled;
    private byte[] cookieData;
    private final Object rewindAttachment;
    private final IEaglerXRewindProtocol<?, ?> rewindProtocol;
    private final int rewindProtocolVersion;
    private final RewindMessageControllerHandle rewindMessageControllerHandle;
    private final int acceptedCapabilitiesMask;
    private final byte[] acceptedCapabilitiesVers;
    private final Map<UUID, Byte> acceptedExtendedCapabilities;
    private final IPlatformSubLogger playerLogger;
    private final ObjectHashSet<SHA1Sum> updateSent;
    private final boolean redirectSupport;
    private final boolean updateSupport;
    private final PlayerRateLimits rateLimits;
    private final UUID eaglerBrandUUID;
    MessageController messageController;
    IVoiceManagerImpl<PlayerObject> voiceManager;
    NotificationManagerPlayer<PlayerObject> notifManager;
    WebViewManager<PlayerObject> webViewManager;
    PauseMenuManager<PlayerObject> pauseMenuManager;
    IUpdateCertificateImpl updateCertificate;

    public EaglerPlayerInstance(IPlatformPlayer<PlayerObject> player, NettyPipelineData pipelineData, UUID brandUUID) {
        super(player, pipelineData.attributeHolder, (EaglerXServer<PlayerObject>)pipelineData.server);
        this.channel = pipelineData.channel;
        this.listenerInfo = pipelineData.listenerInfo;
        this.eaglerBrandString = pipelineData.eaglerBrandString.intern();
        this.eaglerVersionString = pipelineData.eaglerVersionString.intern();
        this.wss = pipelineData.wss;
        this.headerHost = pipelineData.headerHost != null ? pipelineData.headerHost.intern() : null;
        this.headerOrigin = pipelineData.headerOrigin != null ? pipelineData.headerOrigin.intern() : null;
        this.headerUserAgent = pipelineData.headerUserAgent != null ? pipelineData.headerUserAgent.intern() : null;
        this.headerCookie = pipelineData.headerCookie;
        this.headerAuthorization = pipelineData.headerAuthorization;
        this.requestPath = pipelineData.requestPath != null ? pipelineData.requestPath.intern() : null;
        this.realAddress = pipelineData.realAddress;
        this.handshakeProtocol = pipelineData.handshakeProtocol;
        this.gameProtocol = pipelineData.gameProtocol;
        this.minecraftProtocol = pipelineData.minecraftProtocol;
        this.handshakeAuthEnabled = pipelineData.handshakeAuthEnabled;
        this.handshakeAuthUsername = pipelineData.handshakeAuthUsername;
        this.cookieSupport = pipelineData.cookieSupport;
        this.cookieEnabled = pipelineData.cookieEnabled;
        this.cookieData = pipelineData.cookieData;
        this.rewindAttachment = pipelineData.rewindAttachment;
        this.rewindProtocol = pipelineData.rewindProtocol;
        this.rewindProtocolVersion = pipelineData.rewindProtocolVersion;
        this.rewindMessageControllerHandle = pipelineData.rewindMessageControllerHandle;
        this.acceptedCapabilitiesMask = pipelineData.acceptedCapabilitiesMask;
        this.acceptedCapabilitiesVers = pipelineData.acceptedCapabilitiesVers;
        this.acceptedExtendedCapabilities = pipelineData.acceptedExtendedCapabilities;
        this.playerLogger = pipelineData.connectionLogger;
        this.redirectSupport = this.hasCapability(EnumCapabilitySpec.REDIRECT_V0);
        this.updateSupport = this.hasCapability(EnumCapabilitySpec.UPDATE_V0);
        this.rateLimits = new PlayerRateLimits(this.server.rateLimitParams());
        this.eaglerBrandUUID = this.server.intern(brandUUID);
        this.updateSent = this.updateSupport && this.server.getUpdateService() != null ? new ObjectHashSet(16) : null;
    }

    @Override
    public SocketAddress getSocketAddress() {
        return this.channel.remoteAddress();
    }

    @Override
    public int getMinecraftProtocol() {
        return this.minecraftProtocol;
    }

    @Override
    public boolean hasCapability(EnumCapabilitySpec capability) {
        return CapabilityBits.hasCapability(this.acceptedCapabilitiesMask, this.acceptedCapabilitiesVers, capability.getId(), capability.getVer());
    }

    @Override
    public int getCapability(EnumCapabilityType capability) {
        return CapabilityBits.getCapability(this.acceptedCapabilitiesMask, this.acceptedCapabilitiesVers, capability.getId());
    }

    public int getCapabilityMask() {
        return this.acceptedCapabilitiesMask;
    }

    public byte[] getCapabilityVers() {
        return this.acceptedCapabilitiesVers;
    }

    @Override
    public boolean hasExtendedCapability(UUID extendedCapability, int version) {
        if (extendedCapability == null) {
            throw new NullPointerException("extendedCapability");
        }
        Byte b = this.acceptedExtendedCapabilities.get(extendedCapability);
        return b != null && (b & 0xFF) >= version;
    }

    @Override
    public int getExtendedCapability(UUID extendedCapability) {
        if (extendedCapability == null) {
            throw new NullPointerException("extendedCapability");
        }
        Byte b = this.acceptedExtendedCapabilities.get(extendedCapability);
        return b != null ? b & 0xFF : -1;
    }

    public Map<UUID, Byte> getExtCapabilities() {
        return this.acceptedExtendedCapabilities;
    }

    @Override
    public boolean isHandshakeAuthEnabled() {
        return this.handshakeAuthEnabled;
    }

    @Override
    public byte[] getAuthUsername() {
        return this.handshakeAuthUsername != null ? (byte[])this.handshakeAuthUsername.clone() : null;
    }

    public byte[] getAuthUsernameUnsafe() {
        return this.handshakeAuthUsername;
    }

    @Override
    public IEaglerListenerInfo getListenerInfo() {
        return this.listenerInfo;
    }

    @Override
    public String getRealAddress() {
        return this.realAddress;
    }

    @Override
    public boolean isWebSocketSecure() {
        return this.wss;
    }

    @Override
    public boolean isEaglerXRewindPlayer() {
        return this.rewindProtocol != null;
    }

    @Override
    public int getRewindProtocolVersion() {
        return this.rewindProtocolVersion;
    }

    public IEaglerXRewindProtocol<?, ?> getRewindProtocol() {
        return this.rewindProtocol;
    }

    public Object getRewindAttachment() {
        return this.rewindAttachment;
    }

    public RewindMessageControllerHandle getRewindMessageControllerHandle() {
        return this.rewindMessageControllerHandle;
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

    @Override
    public String getEaglerVersionString() {
        return this.eaglerVersionString;
    }

    @Override
    public String getEaglerBrandString() {
        return this.eaglerBrandString;
    }

    @Override
    public UUID getEaglerBrandUUID() {
        return this.eaglerBrandUUID;
    }

    @Override
    public int getHandshakeEaglerProtocol() {
        return this.handshakeProtocol;
    }

    @Override
    public GamePluginMessageProtocol getEaglerProtocol() {
        return this.gameProtocol;
    }

    @Override
    public boolean isEaglerPlayer() {
        return true;
    }

    @Override
    public EaglerPlayerInstance<PlayerObject> asEaglerPlayer() {
        return this;
    }

    @Override
    public void sendEaglerMessage(GameMessagePacket packet) {
        if (packet == null) {
            throw new NullPointerException("packet");
        }
        this.messageController.sendPacket(packet);
    }

    @Override
    public SkinManagerEagler<PlayerObject> getSkinManager() {
        return (SkinManagerEagler)this.skinManager;
    }

    @Override
    public boolean isRedirectPlayerSupported() {
        return this.redirectSupport;
    }

    @Override
    public void redirectPlayerToWebSocket(String webSocketURI) {
        if (webSocketURI == null) {
            throw new NullPointerException("webSocketURI");
        }
        if (this.redirectSupport) {
            this.sendEaglerMessage(new SPacketRedirectClientV4EAG(webSocketURI));
        } else {
            this.playerLogger.warn("Attempted to redirect player on an unsupported client");
        }
    }

    @Override
    public boolean isVoiceCapable() {
        return this.hasCapability(EnumCapabilitySpec.VOICE_V0);
    }

    @Override
    public boolean hasVoiceManager() {
        return this.voiceManager != null;
    }

    @Override
    public IVoiceManagerImpl<PlayerObject> getVoiceManager() {
        return this.voiceManager;
    }

    @Override
    public boolean isCookieSupported() {
        return this.cookieSupport;
    }

    @Override
    public boolean isCookieEnabled() {
        return this.cookieEnabled;
    }

    @Override
    public byte[] getCookieData() {
        return this.cookieData;
    }

    @Override
    public void setCookieData(byte[] data, long expiresAfterSec, boolean revokeQuerySupported, boolean clientSaveCookieToDisk) {
        if (this.cookieEnabled) {
            this.cookieData = data;
            this.sendEaglerMessage(new SPacketSetServerCookieV4EAG(data, expiresAfterSec, revokeQuerySupported, clientSaveCookieToDisk));
        } else {
            this.playerLogger.warn("Attempted to set cookie while cookies are disabled");
        }
    }

    @Override
    public boolean isNotificationSupported() {
        return this.notifManager != null;
    }

    @Override
    public NotificationManagerPlayer<PlayerObject> getNotificationManager() {
        return this.notifManager;
    }

    @Override
    public boolean isPauseMenuSupported() {
        return this.pauseMenuManager != null;
    }

    @Override
    public PauseMenuManager<PlayerObject> getPauseMenuManager() {
        return this.pauseMenuManager;
    }

    @Override
    public boolean isWebViewSupported() {
        return this.webViewManager != null;
    }

    @Override
    public WebViewManager<PlayerObject> getWebViewManager() {
        return this.webViewManager;
    }

    @Override
    public boolean isUpdateSystemSupported() {
        return this.updateSupport;
    }

    @Override
    public IUpdateCertificateImpl getUpdateCertificate() {
        return this.updateCertificate;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void offerUpdateCertificate(IUpdateCertificate cert) {
        boolean send;
        if (!(cert instanceof IUpdateCertificateImpl)) {
            throw new UnsupportedOperationException("Unknown certificate: " + cert);
        }
        IUpdateCertificateImpl impl = (IUpdateCertificateImpl)cert;
        if (this.updateSent == null) {
            return;
        }
        if (impl == this.updateCertificate) {
            return;
        }
        SHA1Sum csum = impl.checkSum();
        ObjectHashSet<SHA1Sum> objectHashSet = this.updateSent;
        synchronized (objectHashSet) {
            int s;
            send = this.updateSent.add(csum);
            if (send && (s = this.updateSent.size()) > 256) {
                this.removeRandomCertToken();
            }
        }
        if (send) {
            SPacketUpdateCertEAG pkt = impl.packet();
            this.server.getUpdateService().getLoop().pushRunnable(this.getEaglerProtocol() != GamePluginMessageProtocol.V4 ? () -> {
                this.sendEaglerMessage(pkt);
                return pkt.length();
            } : () -> {
                this.messageController.sendPacketImmediately(pkt);
                return pkt.length();
            });
        }
    }

    @Override
    public void sendUpdateCertificate(IUpdateCertificate cert) {
        if (!(cert instanceof IUpdateCertificateImpl)) {
            throw new UnsupportedOperationException("Unknown certificate: " + cert);
        }
        IUpdateCertificateImpl c2 = (IUpdateCertificateImpl)cert;
        if (this.updateSupport) {
            if (this.getEaglerProtocol() != GamePluginMessageProtocol.V4) {
                this.sendEaglerMessage(c2.packet());
            } else {
                this.messageController.sendPacketImmediately(c2.packet());
            }
        }
    }

    private void removeRandomCertToken() {
        this.updateSent.indexRemove(this.updateSent.iterator().next().index);
    }

    public IPlatformSubLogger logger() {
        return this.playerLogger;
    }

    public PlayerRateLimits getRateLimits() {
        return this.rateLimits;
    }

    public MessageController getMessageController() {
        return this.messageController;
    }

    public void handlePacketGetOtherClientUUID(long playerUUIDMost, long playerUUIDLeast, int requestId) {
        if (!this.rateLimits.ratelimitBrand()) {
            return;
        }
        UUID uuid = new UUID(playerUUIDMost, playerUUIDLeast);
        BasePlayerInstance<PlayerObject> player = (BasePlayerInstance<PlayerObject>)this.server.getPlayerByUUID(uuid);
        if (player != null) {
            UUID brandUUID = player.getEaglerBrandUUID();
            this.sendEaglerMessage(new SPacketOtherPlayerClientUUIDV4EAG(requestId, brandUUID.getMostSignificantBits(), brandUUID.getLeastSignificantBits()));
        } else {
            ISupervisorServiceImpl<PlayerObject> supervisorService = (ISupervisorServiceImpl<PlayerObject>)this.server.getSupervisorService();
            if (supervisorService.isSupervisorEnabled() && !supervisorService.shouldIgnoreUUID(uuid)) {
                if (!this.rateLimits.checkSvBrandAntagonist()) {
                    return;
                }
                supervisorService.getRemoteOnlyResolver().resolvePlayerBrandKeyed(this.getUniqueId(), uuid, res -> {
                    if (res != ISupervisorResolverImpl.UNAVAILABLE) {
                        if (res != null) {
                            this.sendEaglerMessage(new SPacketOtherPlayerClientUUIDV4EAG(requestId, res.getMostSignificantBits(), res.getLeastSignificantBits()));
                        } else {
                            this.rateLimits.ratelimitSvBrandAntagonist();
                            this.sendEaglerMessage(new SPacketOtherPlayerClientUUIDV4EAG(requestId, 0L, 0L));
                        }
                    }
                });
            } else {
                this.sendEaglerMessage(new SPacketOtherPlayerClientUUIDV4EAG(requestId, 0L, 0L));
            }
        }
    }

    @Override
    public EaglerPlayerRPCManager<PlayerObject> getPlayerRPCManager() {
        return (EaglerPlayerRPCManager)this.backendRPCManager;
    }
}

