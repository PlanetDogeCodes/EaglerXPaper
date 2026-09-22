/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  io.netty.channel.Channel
 */
package net.lax1dude.eaglercraft.backend.server.base.webview;

import io.netty.channel.Channel;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformComponentBuilder;
import net.lax1dude.eaglercraft.backend.server.api.IEaglerPlayer;
import net.lax1dude.eaglercraft.backend.server.api.SHA1Sum;
import net.lax1dude.eaglercraft.backend.server.api.event.IEaglercraftWebViewChannelEvent;
import net.lax1dude.eaglercraft.backend.server.api.event.IEaglercraftWebViewMessageEvent;
import net.lax1dude.eaglercraft.backend.server.api.pause_menu.IPauseMenuManager;
import net.lax1dude.eaglercraft.backend.server.api.webview.EnumWebViewPerms;
import net.lax1dude.eaglercraft.backend.server.api.webview.IWebViewBlob;
import net.lax1dude.eaglercraft.backend.server.api.webview.IWebViewManager;
import net.lax1dude.eaglercraft.backend.server.api.webview.IWebViewProvider;
import net.lax1dude.eaglercraft.backend.server.api.webview.IWebViewService;
import net.lax1dude.eaglercraft.backend.server.base.EaglerPlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.pause_menu.PauseMenuManager;
import net.lax1dude.eaglercraft.backend.server.base.rpc.BasePlayerRPCManager;
import net.lax1dude.eaglercraft.backend.server.base.webview.WebViewBlob;
import net.lax1dude.eaglercraft.backend.server.base.webview.WebViewService;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketDisplayWebViewBlobV5EAG;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketDisplayWebViewURLV5EAG;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketServerInfoDataChunkV4EAG;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.server.SPacketWebViewMessageV4EAG;

public class WebViewManager<PlayerObject>
implements IWebViewManager<PlayerObject> {
    private static final AtomicReferenceFieldUpdater<WebViewManager, String> CHANNEL_NAME_HANDLE = AtomicReferenceFieldUpdater.newUpdater(WebViewManager.class, String.class, "channelName");
    private final EaglerPlayerInstance<PlayerObject> player;
    private final WebViewService<PlayerObject> service;
    private IWebViewProvider<PlayerObject> provider = null;
    private volatile String channelName = null;

    public WebViewManager(EaglerPlayerInstance<PlayerObject> player, WebViewService<PlayerObject> service) {
        this.player = player;
        this.service = service;
        this.provider = service.getDefaultProvider();
    }

    @Override
    public IEaglerPlayer<PlayerObject> getPlayer() {
        return this.player;
    }

    @Override
    public IWebViewService<PlayerObject> getWebViewService() {
        return this.service;
    }

    @Override
    public boolean isChannelAllowed() {
        IWebViewProvider<PlayerObject> provider = this.provider;
        return provider != null && provider.isChannelAllowed(this);
    }

    @Override
    public boolean isRequestAllowed() {
        IWebViewProvider<PlayerObject> provider = this.provider;
        return provider != null && provider.isRequestAllowed(this);
    }

    public boolean isChannelAllowedDefault() {
        IPauseMenuManager mgr = this.player.getPauseMenuManager();
        return mgr != null && ((PauseMenuManager)mgr).isWebViewChannelAllowedDefault();
    }

    public boolean isRequestAllowedDefault() {
        IPauseMenuManager mgr = this.player.getPauseMenuManager();
        return mgr != null && ((PauseMenuManager)mgr).isWebViewRequestAllowedDefault();
    }

    public void handleRequestDefault(SHA1Sum hash, Consumer<IWebViewBlob> callback) {
        IWebViewBlob tmp;
        IPauseMenuManager mgr = this.player.getPauseMenuManager();
        if (mgr != null && (tmp = ((PauseMenuManager)mgr).getWebViewBlobDefault()) != null && hash.equals(tmp.getHash())) {
            callback.accept(tmp);
            return;
        }
        callback.accept(this.service.getGlobalBlob(hash));
    }

    public SHA1Sum handleAliasDefault(String alias) {
        return this.service.getBlobFromAlias(alias);
    }

    @Override
    public IWebViewProvider<PlayerObject> getProvider() {
        return this.provider;
    }

    @Override
    public void setProvider(IWebViewProvider<PlayerObject> func) {
        if (func == null) {
            throw new NullPointerException("func");
        }
        this.provider = func;
    }

    public boolean isChannelOpen() {
        return this.getOpenChannel() != null;
    }

    @Override
    public boolean isChannelOpen(String channelName) {
        if (channelName == null) {
            throw new NullPointerException("channelName");
        }
        String str = this.getOpenChannel();
        return str != null && channelName.equals(str);
    }

    @Override
    public Set<String> getOpenChannels() {
        String str = this.getOpenChannel();
        if (str != null) {
            return Collections.singleton(str);
        }
        return Collections.emptySet();
    }

    public final String getOpenChannel() {
        return this.channelName;
    }

    private boolean validateChannel(String channelName) {
        if (channelName == null) {
            throw new NullPointerException("channelName");
        }
        String str = this.getOpenChannel();
        if (str != null && channelName.equals(str)) {
            return true;
        }
        this.player.logger().warn("Attempted to send web view message on closed channel: " + channelName);
        return false;
    }

    @Override
    public void sendMessageString(String channelName, String contents) {
        if (this.validateChannel(channelName)) {
            if (contents == null) {
                throw new NullPointerException("contents");
            }
            this.player.sendEaglerMessage(new SPacketWebViewMessageV4EAG(contents));
        }
    }

    @Override
    public void sendMessageString(String channelName, byte[] contents) {
        if (this.validateChannel(channelName)) {
            if (contents == null) {
                throw new NullPointerException("contents");
            }
            this.player.sendEaglerMessage(new SPacketWebViewMessageV4EAG(0, contents));
        }
    }

    @Override
    public void sendMessageBinary(String channelName, byte[] contents) {
        if (this.validateChannel(channelName)) {
            if (contents == null) {
                throw new NullPointerException("contents");
            }
            this.player.sendEaglerMessage(new SPacketWebViewMessageV4EAG(contents));
        }
    }

    @Override
    public boolean isDisplayWebViewSupported() {
        return this.player.getEaglerProtocol().ver >= 5;
    }

    @Override
    public void displayWebViewURL(String title, String url, Set<EnumWebViewPerms> permissions) {
        if (title == null) {
            throw new NullPointerException("title");
        }
        if (url == null) {
            throw new NullPointerException("url");
        }
        if (this.player.getEaglerProtocol().ver >= 5) {
            this.player.sendEaglerMessage(new SPacketDisplayWebViewURLV5EAG(permissions != null ? EnumWebViewPerms.toBits(permissions) : 0, title, url));
        } else {
            this.player.logger().warn("Attempted to display web view on an unsupported client");
        }
    }

    @Override
    public void displayWebViewBlob(String title, SHA1Sum hash, Set<EnumWebViewPerms> permissions) {
        if (title == null) {
            throw new NullPointerException("title");
        }
        if (hash == null) {
            throw new NullPointerException("hash");
        }
        if (this.player.getEaglerProtocol().ver >= 5) {
            this.player.sendEaglerMessage(new SPacketDisplayWebViewBlobV5EAG(permissions != null ? EnumWebViewPerms.toBits(permissions) : 0, title, hash.asBytes()));
        } else {
            this.player.logger().warn("Attempted to display web view on an unsupported client");
        }
    }

    public void handlePacketRequestData(byte[] hash) {
        if (!this.player.getRateLimits().ratelimitWebViewData()) {
            this.player.disconnect(((IPlatformComponentBuilder.IBuilderComponentText)this.service.getEaglerXServer().componentBuilder().buildTextComponent().beginStyle().color(IPlatformComponentBuilder.EnumChatColor.RED).end()).text("Too many WebView data requests!").end());
            return;
        }
        IWebViewProvider<PlayerObject> provider = this.provider;
        if (provider != null && provider.isRequestAllowed(this)) {
            SHA1Sum sum = SHA1Sum.create(hash);
            try {
                provider.handleRequest(this, sum, data -> {
                    if (data != null) {
                        this.sendDataToPlayer(((WebViewBlob)data).list);
                    } else {
                        try {
                            this.player.disconnect(((IPlatformComponentBuilder.IBuilderComponentText)this.service.getEaglerXServer().componentBuilder().buildTextComponent().beginStyle().color(IPlatformComponentBuilder.EnumChatColor.RED).end()).text("WebView content could not be found!").end());
                        }
                        catch (Exception exception) {
                            // empty catch block
                        }
                    }
                });
            }
            catch (Exception ex) {
                this.player.logger().error("Could not handle WebView data request for: " + sum, ex);
                this.player.disconnect(((IPlatformComponentBuilder.IBuilderComponentText)this.service.getEaglerXServer().componentBuilder().buildTextComponent().beginStyle().color(IPlatformComponentBuilder.EnumChatColor.RED).end()).text("Error handling webview data request!").end());
            }
        } else {
            this.player.disconnect(((IPlatformComponentBuilder.IBuilderComponentText)this.service.getEaglerXServer().componentBuilder().buildTextComponent().beginStyle().color(IPlatformComponentBuilder.EnumChatColor.RED).end()).text("Unexpected WebView data request!").end());
        }
    }

    private void sendDataToPlayer(List<SPacketServerInfoDataChunkV4EAG> list) {
        Channel ch;
        long rate;
        int chunkRate = this.service.getEaglerXServer().getConfig().getPauseMenu().getServerInfoButtonEmbedSendChunkRate();
        long l = rate = chunkRate > 0 ? 250L / (long)chunkRate : 250L;
        if (rate < 20L) {
            rate = 20L;
        }
        if ((ch = this.player.getChannel()).isActive()) {
            ch.eventLoop().execute((Runnable)new DataRunnable(list, rate, ch));
        }
    }

    public void handlePacketChannel(String channel, boolean open) {
        BasePlayerRPCManager rpcMgr;
        String prevChannel;
        String nextChannel;
        if (!this.player.getRateLimits().ratelimitWebViewMsg()) {
            this.player.disconnect(((IPlatformComponentBuilder.IBuilderComponentText)this.service.getEaglerXServer().componentBuilder().buildTextComponent().beginStyle().color(IPlatformComponentBuilder.EnumChatColor.RED).end()).text("Too many WebView messages!").end());
            return;
        }
        boolean allowed = open && this.isChannelAllowed();
        while (true) {
            nextChannel = null;
            prevChannel = this.channelName;
            if (open) {
                if (channel.equals(prevChannel)) {
                    prevChannel = null;
                    break;
                }
                nextChannel = channel;
                if (allowed && !CHANNEL_NAME_HANDLE.compareAndSet(this, prevChannel, channel)) continue;
                break;
            }
            if (CHANNEL_NAME_HANDLE.compareAndSet(this, prevChannel, null)) break;
        }
        if (prevChannel != null) {
            this.service.getEaglerXServer().eventDispatcher().dispatchWebViewChannelEvent(this.player, IEaglercraftWebViewChannelEvent.EnumEventType.CHANNEL_CLOSE, prevChannel, null);
            rpcMgr = this.player.getPlayerRPCManager();
            if (rpcMgr != null) {
                rpcMgr.fireWebViewOpenClose(false, prevChannel);
            }
        }
        if (nextChannel != null) {
            if (allowed) {
                this.service.getEaglerXServer().eventDispatcher().dispatchWebViewChannelEvent(this.player, IEaglercraftWebViewChannelEvent.EnumEventType.CHANNEL_OPEN, nextChannel, null);
                rpcMgr = this.player.getPlayerRPCManager();
                if (rpcMgr != null) {
                    rpcMgr.fireWebViewOpenClose(true, nextChannel);
                }
            } else {
                this.player.disconnect(((IPlatformComponentBuilder.IBuilderComponentText)this.service.getEaglerXServer().componentBuilder().buildTextComponent().beginStyle().color(IPlatformComponentBuilder.EnumChatColor.RED).end()).text("Unexpected WebView channel opened!").end());
            }
        }
    }

    public void handlePacketMessage(byte[] data, boolean binary) {
        if (!this.player.getRateLimits().ratelimitWebViewMsg()) {
            this.player.disconnect(((IPlatformComponentBuilder.IBuilderComponentText)this.service.getEaglerXServer().componentBuilder().buildTextComponent().beginStyle().color(IPlatformComponentBuilder.EnumChatColor.RED).end()).text("Too many WebView messages!").end());
            return;
        }
        String channel = this.getOpenChannel();
        if (channel != null) {
            this.service.getEaglerXServer().eventDispatcher().dispatchWebViewMessageEvent(this.player, channel, binary ? IEaglercraftWebViewMessageEvent.EnumMessageType.BINARY : IEaglercraftWebViewMessageEvent.EnumMessageType.STRING, data, null);
            BasePlayerRPCManager rpcMgr = this.player.getPlayerRPCManager();
            if (rpcMgr != null) {
                rpcMgr.fireWebViewMessage(channel, binary, data);
            }
        } else {
            this.player.disconnect(((IPlatformComponentBuilder.IBuilderComponentText)this.service.getEaglerXServer().componentBuilder().buildTextComponent().beginStyle().color(IPlatformComponentBuilder.EnumChatColor.RED).end()).text("Unexpected WebView packet!").end());
        }
    }

    private class DataRunnable
    implements Runnable {
        private final List<SPacketServerInfoDataChunkV4EAG> list;
        private final long rate;
        private final Channel chRef;
        private int chunk;

        protected DataRunnable(List<SPacketServerInfoDataChunkV4EAG> list, long rate, Channel chRef) {
            this.list = list;
            this.rate = rate;
            this.chRef = chRef;
        }

        @Override
        public void run() {
            try {
                int c;
                if (!this.chRef.isActive()) {
                    return;
                }
                if ((c = this.chunk++) >= this.list.size()) {
                    return;
                }
                WebViewManager.this.player.sendEaglerMessage(this.list.get(c));
                if (c + 1 < this.list.size()) {
                    this.chRef.eventLoop().schedule((Runnable)this, this.rate, TimeUnit.MILLISECONDS);
                }
            }
            catch (Exception ex) {
                WebViewManager.this.player.logger().warn("Failed to send server-info data chunk", ex);
            }
        }
    }
}

