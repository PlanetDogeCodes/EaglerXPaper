/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.ImmutableList
 *  io.netty.channel.Channel
 */
package net.lax1dude.eaglercraft.backend.server.base;

import com.google.common.collect.ImmutableList;
import io.netty.channel.Channel;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.List;
import javax.net.ssl.SSLException;
import net.lax1dude.eaglercraft.backend.server.adapter.IEaglerXServerListener;
import net.lax1dude.eaglercraft.backend.server.api.IEaglerListenerInfo;
import net.lax1dude.eaglercraft.backend.server.api.ITLSManager;
import net.lax1dude.eaglercraft.backend.server.api.attribute.IAttributeKey;
import net.lax1dude.eaglercraft.backend.server.base.CompoundRateLimiterMap;
import net.lax1dude.eaglercraft.backend.server.base.EaglerAttributeManager;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServer;
import net.lax1dude.eaglercraft.backend.server.base.ISSLContextProvider;
import net.lax1dude.eaglercraft.backend.server.base.SSLContextHolderPlugin;
import net.lax1dude.eaglercraft.backend.server.base.config.ConfigDataListener;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.WebSocketEaglerInitialHandler;
import net.lax1dude.eaglercraft.backend.server.util.RateLimiterExclusions;

public class EaglerListener
implements IEaglerListenerInfo,
IEaglerXServerListener {
    private final EaglerXServer<?> server;
    private final SocketAddress address;
    private final ConfigDataListener listenerConf;
    private final EaglerAttributeManager.EaglerAttributeHolder attrHolder;
    private final boolean sslPluginManaged;
    private final ISSLContextProvider sslContext;
    private final byte[] legacyRedirectAddressBuf;
    private volatile byte[] cachedServerIcon;
    private volatile List<String> cachedServerMOTD;
    private CompoundRateLimiterMap rateLimiter;

    EaglerListener(EaglerXServer<?> server, ConfigDataListener listenerConf) throws SSLException, IOException {
        this(server, listenerConf.getInjectAddress(), listenerConf);
    }

    EaglerListener(EaglerXServer<?> server, SocketAddress address, ConfigDataListener listenerConf) throws SSLException, IOException {
        this.server = server;
        this.address = address;
        this.listenerConf = listenerConf;
        this.attrHolder = server.getEaglerAttribManager().createEaglerHolder();
        if (listenerConf.isEnableTLS()) {
            this.sslPluginManaged = listenerConf.isTLSManagedByExternalPlugin();
            this.sslContext = this.sslPluginManaged ? new SSLContextHolderPlugin(this) : server.getCertificateManager().createHolder(new File(listenerConf.getTLSPublicChainFile()), new File(listenerConf.getTLSPrivateKeyFile()), listenerConf.getTLSPrivateKeyPassword(), listenerConf.isTLSAutoRefreshCert());
        } else {
            this.sslPluginManaged = false;
            this.sslContext = null;
        }
        this.legacyRedirectAddressBuf = (byte[])(listenerConf.getRedirectLegacyClientsTo() != null ? WebSocketEaglerInitialHandler.prepareRedirectAddr(listenerConf.getRedirectLegacyClientsTo()) : null);
        this.cachedServerMOTD = listenerConf.getServerMOTD();
        String iconName = listenerConf.getServerIcon();
        if (iconName != null && !iconName.isEmpty()) {
            try {
                this.cachedServerIcon = server.getServerIconLoader().loadServerIcon(new File(iconName));
            }
            catch (FileNotFoundException ex) {
                server.logger().error("Could not load server icon: " + iconName + " (not found)");
                this.cachedServerIcon = null;
            }
            catch (IOException ex) {
                server.logger().error("Could not load server icon: " + iconName + " (" + ex.getMessage() + ")");
                this.cachedServerIcon = null;
            }
            catch (Throwable t) {
                server.logger().error("Could not load server icon: " + iconName + " (" + t.getClass().getSimpleName() + ": " + t.getMessage() + ")");
                this.cachedServerIcon = null;
            }
        } else {
            this.cachedServerIcon = null;
        }
        this.rateLimiter = CompoundRateLimiterMap.create(listenerConf.getLimitIP(), listenerConf.getLimitLogin(), listenerConf.getLimitMOTD(), listenerConf.getLimitQuery(), listenerConf.getLimitHTTP(), RateLimiterExclusions.create(listenerConf.getLimitExclusions(), server.logger()));
    }

    public ISSLContextProvider getSSLContext() {
        return this.sslContext;
    }

    @Override
    public <T> T get(IAttributeKey<T> key) {
        return this.attrHolder.get(key);
    }

    @Override
    public <T> void set(IAttributeKey<T> key, T value) {
        this.attrHolder.set(key, value);
    }

    @Override
    public String getName() {
        return this.listenerConf.getListenerName();
    }

    @Override
    public SocketAddress getAddress() {
        return this.address;
    }

    @Override
    public boolean isDualStack() {
        return this.listenerConf.isDualStack();
    }

    @Override
    public boolean isTLSEnabled() {
        return this.listenerConf.isEnableTLS();
    }

    @Override
    public boolean isTLSRequired() {
        return this.listenerConf.isRequireTLS();
    }

    @Override
    public boolean isTLSManagedByPlugin() {
        return this.sslPluginManaged;
    }

    @Override
    public ITLSManager getTLSManager() throws IllegalStateException {
        if (!this.listenerConf.isEnableTLS()) {
            throw new IllegalStateException("TLS is not enabled on this listener!");
        }
        if (!this.sslPluginManaged) {
            throw new IllegalStateException("TLS manager is disabled for this listener! (Set 'tls_managed_by_external_plugin' to true)");
        }
        return (ITLSManager)((Object)this.sslContext);
    }

    @Override
    public byte[] getServerIcon() {
        return this.cachedServerIcon;
    }

    @Override
    public void setServerIcon(byte[] pixels) {
        if (pixels != null && pixels.length != 16384) {
            throw new IllegalArgumentException("Server icon is the wrong length, should be 16384");
        }
        this.cachedServerIcon = pixels;
    }

    @Override
    public List<String> getServerMOTD() {
        return this.cachedServerMOTD;
    }

    @Override
    public void setServerMOTD(List<String> motd) {
        if (motd == null || motd.size() == 0) {
            this.cachedServerMOTD = Collections.emptyList();
        } else if (motd.size() == 1) {
            this.cachedServerMOTD = ImmutableList.of(motd.get(0));
        } else {
            this.cachedServerMOTD = ImmutableList.of(motd.get(0), motd.get(1));
        }
    }

    @Override
    public boolean isForwardIP() {
        return this.listenerConf.isForwardIP();
    }

    @Override
    public boolean matchListenerAddress(SocketAddress addr) {
        if (addr.equals(this.listenerConf.getInjectAddress())) {
            return true;
        }
        if (addr instanceof InetSocketAddress) {
            SocketAddress injectAddress = this.listenerConf.getInjectAddress();
            if (injectAddress instanceof InetSocketAddress) {
                InetSocketAddress addr2 = (InetSocketAddress)addr;
                InetSocketAddress addr3 = (InetSocketAddress)injectAddress;
                if (this.isAllZeros(addr2) && this.isAllZeros(addr3)) {
                    return addr2.getPort() == addr3.getPort();
                }
            }
            return false;
        }
        return false;
    }

    private boolean isAllZeros(InetSocketAddress addr) {
        InetAddress addr2 = addr.getAddress();
        if (addr2 instanceof Inet4Address) {
            byte[] octets = ((Inet4Address)addr2).getAddress();
            return (octets[0] | octets[1] | octets[2] | octets[3]) == 0;
        }
        if (addr2 instanceof Inet6Address) {
            byte[] octets = ((Inet6Address)addr2).getAddress();
            return (octets[0] | octets[1] | octets[2] | octets[3] | octets[4] | octets[5] | octets[6] | octets[7] | octets[8] | octets[9] | octets[10] | octets[11] | octets[12] | octets[13] | octets[14] | octets[15]) == 0;
        }
        return false;
    }

    @Override
    public boolean isCloneListenerEnabled() {
        return this.listenerConf.isCloneListenerEnabled();
    }

    @Override
    public SocketAddress getCloneListenerAddress() {
        return this.listenerConf.getInjectAddress();
    }

    @Override
    public void reportVelocityInjected(Channel channel) {
        this.server.logger().info("Listener \"" + this.listenerConf.getListenerName() + "\" injected into channel " + channel + " successfully (Velocity method)");
    }

    @Override
    public void reportPaperMCInjected() {
        this.server.logger().info("Default listener injected into server channel successfully (PaperMC method)");
    }

    @Override
    public void reportNettyInjected(Channel channel) {
        this.server.logger().info("Listener \"" + this.listenerConf.getListenerName() + "\" injected into channel " + channel + " successfully (Generic Netty method)");
    }

    public byte[] getLegacyRedirectAddressBuf() {
        return this.legacyRedirectAddressBuf;
    }

    public boolean isAllowMOTD() {
        return this.listenerConf.isAllowMOTD();
    }

    public boolean isAllowQuery() {
        return this.listenerConf.isAllowQuery();
    }

    public boolean isShowMOTDPlayerList() {
        return this.listenerConf.isShowMOTDPlayerList();
    }

    public ConfigDataListener getConfigData() {
        return this.listenerConf;
    }

    public CompoundRateLimiterMap getRateLimiter() {
        return this.rateLimiter;
    }
}

