/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.buffer.Unpooled
 *  io.netty.channel.Channel
 *  io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
 */
package net.lax1dude.eaglercraft.backend.server.base.rpc;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import net.lax1dude.eaglercraft.backend.rpc.protocol.EaglerBackendRPCProtocol;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCNotifBadgeShow;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCNotifIconRegister;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.client.CPacketRPCSetPauseMenuCustom;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCEventToggledVoice;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCEventWebViewMessage;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCEventWebViewOpenClose;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeBrandDataV2;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeBytes;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeCookie;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeNull;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeString;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeVoiceStatus;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeWebViewStatus;
import net.lax1dude.eaglercraft.backend.rpc.protocol.pkt.server.SPacketRPCResponseTypeWebViewStatusV2;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformLogger;
import net.lax1dude.eaglercraft.backend.server.api.EnumWebSocketHeader;
import net.lax1dude.eaglercraft.backend.server.api.SHA1Sum;
import net.lax1dude.eaglercraft.backend.server.api.notifications.INotificationManager;
import net.lax1dude.eaglercraft.backend.server.api.pause_menu.IPauseMenuManager;
import net.lax1dude.eaglercraft.backend.server.api.skins.EnumEnableFNAW;
import net.lax1dude.eaglercraft.backend.server.api.voice.EnumVoiceState;
import net.lax1dude.eaglercraft.backend.server.api.voice.IVoiceManager;
import net.lax1dude.eaglercraft.backend.server.api.webview.EnumWebViewPerms;
import net.lax1dude.eaglercraft.backend.server.api.webview.IWebViewManager;
import net.lax1dude.eaglercraft.backend.server.api.webview.IWebViewProvider;
import net.lax1dude.eaglercraft.backend.server.base.BasePlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.EaglerPlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.notifications.NotificationManagerBase;
import net.lax1dude.eaglercraft.backend.server.base.pause_menu.PauseMenuManager;
import net.lax1dude.eaglercraft.backend.server.base.rpc.BasePlayerRPCContext;
import net.lax1dude.eaglercraft.backend.server.base.rpc.EaglerPlayerRPCManager;
import net.lax1dude.eaglercraft.backend.server.base.rpc.NotificationRPCHelper;
import net.lax1dude.eaglercraft.backend.server.base.rpc.PauseMenuRPCHelper;
import net.lax1dude.eaglercraft.backend.server.base.rpc.TextureDataHelper;
import net.lax1dude.eaglercraft.backend.server.base.skins.SkinManagerEagler;
import net.lax1dude.eaglercraft.backend.server.base.webview.WebViewManager;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketNotifBadgeShowV4EAG;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketNotifIconsRegisterV4EAG;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketWebViewMessageV4EAG;

public class EaglerPlayerRPCContext<PlayerObject>
extends BasePlayerRPCContext<PlayerObject> {
    protected final EaglerPlayerRPCManager<PlayerObject> manager;
    protected boolean subscribeWebViewOpenClose;
    protected boolean subscribeWebViewMessage;
    protected boolean subscribeToggleVoice;

    EaglerPlayerRPCContext(EaglerPlayerRPCManager<PlayerObject> manager, EaglerBackendRPCProtocol protocol) {
        super(protocol, manager.getPlayer().getSerializationContext());
        this.manager = manager;
    }

    @Override
    protected EaglerPlayerRPCManager<PlayerObject> manager() {
        return this.manager;
    }

    @Override
    protected IPlatformLogger logger() {
        return ((EaglerPlayerInstance)this.manager.getPlayer()).logger();
    }

    @Override
    void handleRequestRealIP(int requestID) {
        String realIP = ((EaglerPlayerInstance)((EaglerPlayerRPCManager)this.manager()).getPlayer()).getRealAddress();
        if (realIP != null) {
            this.sendRPCPacket(new SPacketRPCResponseTypeString(requestID, realIP));
        } else {
            this.sendRPCPacket(new SPacketRPCResponseTypeNull(requestID));
        }
    }

    @Override
    void handleRequestHeader(int requestID, EnumWebSocketHeader header) {
        String str = ((EaglerPlayerInstance)((EaglerPlayerRPCManager)this.manager()).getPlayer()).getWebSocketHeader(header);
        if (str != null) {
            this.sendRPCPacket(new SPacketRPCResponseTypeString(requestID, str));
        } else {
            this.sendRPCPacket(new SPacketRPCResponseTypeNull(requestID));
        }
    }

    @Override
    void handleRequestPath(int requestID) {
        String str = ((EaglerPlayerInstance)((EaglerPlayerRPCManager)this.manager()).getPlayer()).getWebSocketPath();
        if (str != null) {
            this.sendRPCPacket(new SPacketRPCResponseTypeString(requestID, str));
        } else {
            this.sendRPCPacket(new SPacketRPCResponseTypeNull(requestID));
        }
    }

    @Override
    void handleRequestCookie(int requestID) {
        BasePlayerInstance player = ((EaglerPlayerRPCManager)this.manager()).getPlayer();
        SPacketRPCResponseTypeCookie pkt = ((EaglerPlayerInstance)player).isCookieEnabled() ? new SPacketRPCResponseTypeCookie(requestID, true, ((EaglerPlayerInstance)player).getCookieData()) : new SPacketRPCResponseTypeCookie(requestID, false, null);
        this.sendRPCPacket(pkt);
    }

    @Override
    void handleRequestBrandOld(int requestID) {
        this.sendRPCPacket(new SPacketRPCResponseTypeString(requestID, ((EaglerPlayerInstance)((EaglerPlayerRPCManager)this.manager()).getPlayer()).getEaglerBrandString()));
    }

    @Override
    void handleRequestVersionOld(int requestID) {
        this.sendRPCPacket(new SPacketRPCResponseTypeString(requestID, ((EaglerPlayerInstance)((EaglerPlayerRPCManager)this.manager()).getPlayer()).getEaglerVersionString()));
    }

    @Override
    void handleRequestBrandVersionOld(int requestID) {
        BasePlayerInstance player = ((EaglerPlayerRPCManager)this.manager()).getPlayer();
        this.sendRPCPacket(new SPacketRPCResponseTypeString(requestID, ((EaglerPlayerInstance)player).getEaglerBrandString() + " " + ((EaglerPlayerInstance)player).getEaglerVersionString()));
    }

    @Override
    void handleRequestVoiceStatus(int requestID) {
        int response;
        IVoiceManager voice = ((EaglerPlayerInstance)((EaglerPlayerRPCManager)this.manager()).getPlayer()).getVoiceManager();
        if (voice != null) {
            switch (voice.getVoiceState()) {
                case DISABLED: {
                    response = 1;
                    break;
                }
                case ENABLED: {
                    response = 2;
                    break;
                }
                default: {
                    response = 0;
                    break;
                }
            }
        } else {
            response = 0;
        }
        this.sendRPCPacket(new SPacketRPCResponseTypeVoiceStatus(requestID, response));
    }

    @Override
    void handleRequestWebViewStatus(int requestID) {
        String channel;
        int response;
        IWebViewManager webview = ((EaglerPlayerInstance)((EaglerPlayerRPCManager)this.manager()).getPlayer()).getWebViewManager();
        if (webview != null) {
            if (!((WebViewManager)webview).isRequestAllowed() || !((WebViewManager)webview).isChannelOpen()) {
                response = 1;
                channel = null;
            } else {
                channel = ((WebViewManager)webview).getOpenChannel();
                response = channel != null ? 3 : 2;
            }
        } else {
            response = 0;
            channel = null;
        }
        this.sendRPCPacket(new SPacketRPCResponseTypeWebViewStatus(requestID, response, channel));
    }

    @Override
    void handleRequestBrandData(int requestID) {
        BasePlayerInstance player = ((EaglerPlayerRPCManager)this.manager()).getPlayer();
        this.sendRPCPacket(new SPacketRPCResponseTypeBrandDataV2(requestID, ((EaglerPlayerInstance)player).getEaglerBrandString(), ((EaglerPlayerInstance)player).getEaglerVersionString(), ((EaglerPlayerInstance)player).getEaglerBrandUUID()));
    }

    @Override
    void handleRequestAuthUsername(int requestID) {
        this.sendRPCPacket(new SPacketRPCResponseTypeBytes(requestID, ((EaglerPlayerInstance)((EaglerPlayerRPCManager)this.manager()).getPlayer()).getAuthUsernameUnsafe()));
    }

    @Override
    void handleRequestWebViewStatusV2(int requestID) {
        IWebViewManager webview = ((EaglerPlayerInstance)((EaglerPlayerRPCManager)this.manager()).getPlayer()).getWebViewManager();
        if (webview != null) {
            this.sendRPCPacket(new SPacketRPCResponseTypeWebViewStatusV2(requestID, ((WebViewManager)webview).isRequestAllowed(), ((WebViewManager)webview).isChannelAllowed(), ((WebViewManager)webview).getOpenChannels()));
        } else {
            this.sendRPCPacket(new SPacketRPCResponseTypeWebViewStatusV2(requestID, false, false, null));
        }
    }

    @Override
    void handleSetSubscribeWebViewOpenClose(boolean enable) {
        this.subscribeWebViewOpenClose = enable;
    }

    @Override
    void fireWebViewOpenClose(boolean open, String channel) {
        if (this.subscribeWebViewOpenClose) {
            this.sendRPCPacket(new SPacketRPCEventWebViewOpenClose(open, channel));
        }
    }

    @Override
    void handleSetSubscribeWebViewMessage(boolean enable) {
        this.subscribeWebViewMessage = enable;
    }

    @Override
    void fireWebViewMessage(String channel, boolean binary, byte[] data) {
        if (this.subscribeWebViewMessage) {
            int type = binary ? 1 : 0;
            this.sendRPCPacket(new SPacketRPCEventWebViewMessage(channel, type, data));
        }
    }

    @Override
    void handleSetSubscribeToggleVoice(boolean enable) {
        this.subscribeToggleVoice = enable;
    }

    @Override
    void fireToggleVoice(EnumVoiceState oldVoiceState, EnumVoiceState newVoiceState) {
        if (this.subscribeToggleVoice) {
            this.sendRPCPacket(new SPacketRPCEventToggledVoice(this.mapToggleVoice(oldVoiceState), this.mapToggleVoice(newVoiceState)));
        }
    }

    private int mapToggleVoice(EnumVoiceState state) {
        switch (state) {
            case DISABLED: {
                return 1;
            }
            case ENABLED: {
                return 2;
            }
        }
        return 0;
    }

    @Override
    void handleSetPlayerCookie(byte[] cookieData, long expiresSec, boolean revokeQuerySupported, boolean saveToDisk) {
        BasePlayerInstance player = ((EaglerPlayerRPCManager)this.manager()).getPlayer();
        if (((EaglerPlayerInstance)player).isCookieEnabled()) {
            ((EaglerPlayerInstance)player).setCookieData(cookieData, expiresSec, revokeQuerySupported, saveToDisk);
        }
    }

    @Override
    void handleSetPlayerFNAWEn(boolean enable, boolean force) {
        EnumEnableFNAW en = force ? EnumEnableFNAW.FORCED : (enable ? EnumEnableFNAW.ENABLED : EnumEnableFNAW.DISABLED);
        ((SkinManagerEagler)((EaglerPlayerInstance)((EaglerPlayerRPCManager)this.manager()).getPlayer()).getSkinManager()).setEnableFNAWSkins(en);
    }

    @Override
    void handleResetPlayerMulti(boolean resetSkin, boolean resetCape, boolean resetFNAWForce, boolean notifyOthers) {
        super.handleResetPlayerMulti(resetSkin, resetCape, false, notifyOthers);
        if (resetFNAWForce) {
            ((SkinManagerEagler)((EaglerPlayerInstance)((EaglerPlayerRPCManager)this.manager()).getPlayer()).getSkinManager()).resetEnableFNAWSkins();
        }
    }

    @Override
    void handleRedirectPlayer(String redirectURI) {
        BasePlayerInstance player = ((EaglerPlayerRPCManager)this.manager()).getPlayer();
        if (((EaglerPlayerInstance)player).isRedirectPlayerSupported()) {
            ((EaglerPlayerInstance)player).redirectPlayerToWebSocket(redirectURI);
        }
    }

    @Override
    void handleSendWebViewMessage(String channelName, int messageType, byte[] messageContent) {
        BasePlayerInstance player = ((EaglerPlayerRPCManager)this.manager()).getPlayer();
        IWebViewManager mgr = ((EaglerPlayerInstance)player).getWebViewManager();
        if (mgr != null && ((WebViewManager)mgr).isChannelOpen(channelName)) {
            ((EaglerPlayerInstance)player).sendEaglerMessage(new SPacketWebViewMessageV4EAG(messageType, messageContent));
        }
    }

    @Override
    void handleSetPauseMenuCustom(CPacketRPCSetPauseMenuCustom packet) {
        IPauseMenuManager pauseMenuMgr = ((EaglerPlayerInstance)((EaglerPlayerRPCManager)this.manager()).getPlayer()).getPauseMenuManager();
        if (pauseMenuMgr != null) {
            ((PauseMenuManager)pauseMenuMgr).updatePauseMenuRPC(PauseMenuRPCHelper.translateRPCPacket(this.manager(), packet));
        }
    }

    @Override
    void handleNotifIconRegister(Collection<CPacketRPCNotifIconRegister.RegisterIcon> icons) {
        if (icons.isEmpty()) {
            return;
        }
        INotificationManager notifManager = ((EaglerPlayerInstance)((EaglerPlayerRPCManager)this.manager()).getPlayer()).getNotificationManager();
        if (notifManager != null) {
            int l = icons.size();
            SPacketNotifIconsRegisterV4EAG.CreateIcon[] arr = new SPacketNotifIconsRegisterV4EAG.CreateIcon[l];
            int i = 0;
            for (CPacketRPCNotifIconRegister.RegisterIcon etr : icons) {
                if (i >= l) break;
                arr[i++] = new SPacketNotifIconsRegisterV4EAG.CreateIcon(etr.uuid.getMostSignificantBits(), etr.uuid.getLeastSignificantBits(), TextureDataHelper.packetImageDataRPCToCore(etr.image));
            }
            if (i != l) {
                throw new IllegalStateException();
            }
            ((NotificationManagerBase)notifManager).registerUnmanagedNotificationIconsRaw(Arrays.asList(arr));
        }
    }

    @Override
    void handleNotifIconRelease(Collection<UUID> icons) {
        if (icons.isEmpty()) {
            return;
        }
        INotificationManager notifManager = ((EaglerPlayerInstance)((EaglerPlayerRPCManager)this.manager()).getPlayer()).getNotificationManager();
        if (notifManager != null) {
            ((NotificationManagerBase)notifManager).releaseUnmanagedNotificationIcons(icons);
        }
    }

    @Override
    void handleNotifBadgeShow(CPacketRPCNotifBadgeShow packet) {
        INotificationManager notifManager = ((EaglerPlayerInstance)((EaglerPlayerRPCManager)this.manager()).getPlayer()).getNotificationManager();
        if (notifManager != null) {
            SPacketNotifBadgeShowV4EAG eagPacket = NotificationRPCHelper.translateRPCPacket(packet);
            if (packet.managed) {
                ((NotificationManagerBase)notifManager).showNotificationBadge(eagPacket, packet.mainIconUUID, packet.titleIconUUID);
            } else {
                ((NotificationManagerBase)notifManager).showUnmanagedNotificationBadge(eagPacket);
            }
        }
    }

    @Override
    void handleNotifBadgeHide(UUID badge) {
        INotificationManager notifManager = ((EaglerPlayerInstance)((EaglerPlayerRPCManager)this.manager()).getPlayer()).getNotificationManager();
        if (notifManager != null) {
            ((NotificationManagerBase)notifManager).hideNotificationBadge(badge);
        }
    }

    @Override
    void handleInjectRawBinaryFrame(byte[] data) {
        Channel channel = ((EaglerPlayerRPCManager)this.manager()).getPlayer().getChannel();
        if (channel.isActive()) {
            channel.writeAndFlush((Object)new BinaryWebSocketFrame(Unpooled.wrappedBuffer((byte[])data)), channel.voidPromise());
        }
    }

    @Override
    void handleDisplayWebViewURL(String title, String url, Set<EnumWebViewPerms> perms) {
        IWebViewManager webViewManager = ((EaglerPlayerInstance)((EaglerPlayerRPCManager)this.manager()).getPlayer()).getWebViewManager();
        if (webViewManager != null) {
            ((WebViewManager)webViewManager).displayWebViewURL(title, url, perms);
        }
    }

    @Override
    void handleDisplayWebViewBlob(String title, SHA1Sum hash, Set<EnumWebViewPerms> perms) {
        IWebViewManager webViewManager = ((EaglerPlayerInstance)((EaglerPlayerRPCManager)this.manager()).getPlayer()).getWebViewManager();
        if (webViewManager != null) {
            ((WebViewManager)webViewManager).displayWebViewBlob(title, hash, perms);
        }
    }

    @Override
    void handleDisplayWebViewAlias(String title, String name, Set<EnumWebViewPerms> perms) {
        SHA1Sum hash;
        IWebViewProvider provider;
        IWebViewManager webViewManager = ((EaglerPlayerInstance)((EaglerPlayerRPCManager)this.manager()).getPlayer()).getWebViewManager();
        if (webViewManager != null && (provider = ((WebViewManager)webViewManager).getProvider()) != null && (hash = provider.handleAlias(webViewManager, name)) != null) {
            ((WebViewManager)webViewManager).displayWebViewBlob(title, hash, perms);
        }
    }
}

