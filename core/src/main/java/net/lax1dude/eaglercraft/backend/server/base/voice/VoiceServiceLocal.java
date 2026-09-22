/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.cache.CacheBuilder
 *  com.google.common.cache.CacheLoader
 *  com.google.common.cache.LoadingCache
 *  com.google.common.collect.ImmutableList
 */
package net.lax1dude.eaglercraft.backend.server.base.voice;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import net.lax1dude.eaglercraft.backend.server.api.EnumCapabilitySpec;
import net.lax1dude.eaglercraft.backend.server.api.IEaglerPlayer;
import net.lax1dude.eaglercraft.backend.server.api.IEaglerXServerAPI;
import net.lax1dude.eaglercraft.backend.server.api.voice.ICEServerEntry;
import net.lax1dude.eaglercraft.backend.server.api.voice.IVoiceChannel;
import net.lax1dude.eaglercraft.backend.server.base.EaglerPlayerInstance;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServer;
import net.lax1dude.eaglercraft.backend.server.base.config.ConfigDataSettings;
import net.lax1dude.eaglercraft.backend.server.base.voice.DisabledChannel;
import net.lax1dude.eaglercraft.backend.server.base.voice.IVoiceServiceImpl;
import net.lax1dude.eaglercraft.backend.server.base.voice.ManagedChannel;
import net.lax1dude.eaglercraft.backend.server.base.voice.VoiceChannel;
import net.lax1dude.eaglercraft.backend.server.base.voice.VoiceManagerLocal;

public class VoiceServiceLocal<PlayerObject>
implements IVoiceServiceImpl<PlayerObject> {
    private final EaglerXServer<PlayerObject> server;
    private final boolean allServer;
    private final boolean separateServer;
    private final Set<String> configServersEnabled;
    private final IVoiceChannel globalChannel;
    private final LoadingCache<String, IVoiceChannel> serverChannels;
    private Collection<ICEServerEntry> iceServers;
    private String[] iceServersStr;

    public VoiceServiceLocal(EaglerXServer<PlayerObject> server, ConfigDataSettings.ConfigDataVoiceService config) {
        this.server = server;
        this.allServer = config.isEnableVoiceChatAllServers();
        this.separateServer = config.isSeparateVoiceChannelsPerServer();
        this.configServersEnabled = config.getEnableVoiceChatOnServers();
        this.globalChannel = new ManagedChannel(this);
        this.serverChannels = this.separateServer ? CacheBuilder.newBuilder().weakValues().concurrencyLevel(8).initialCapacity(32).build((CacheLoader)new CacheLoader<String, IVoiceChannel>(){

            public IVoiceChannel load(String key) throws Exception {
                return new ManagedChannel(VoiceServiceLocal.this);
            }
        }) : null;
    }

    @Override
    public void setICEServers(Collection<ICEServerEntry> newICEServers) {
        if (newICEServers == null) {
            throw new NullPointerException("newICEServers");
        }
        newICEServers = this.iceServers = ImmutableList.copyOf(newICEServers);
        this.iceServersStr = VoiceServiceLocal.prepareICEServers((Collection<ICEServerEntry>)newICEServers);
    }

    static String[] prepareICEServers(Collection<ICEServerEntry> newICEServers) {
        String[] newArray = new String[newICEServers.size()];
        int i = 0;
        for (ICEServerEntry etr : newICEServers) {
            newArray[i++] = etr.toString();
        }
        if (i != newArray.length) {
            throw new IllegalStateException("fuck you");
        }
        return newArray;
    }

    @Override
    public Collection<ICEServerEntry> getICEServers() {
        return this.iceServers;
    }

    String[] getICEServersStr() {
        return this.iceServersStr;
    }

    @Override
    public IEaglerXServerAPI<PlayerObject> getServerAPI() {
        return this.server;
    }

    @Override
    public boolean isVoiceEnabled() {
        return true;
    }

    @Override
    public boolean isBackendRelayMode() {
        return false;
    }

    @Override
    public boolean isVoiceEnabledAllServers() {
        return this.allServer;
    }

    @Override
    public boolean isVoiceEnabledOnServer(String serverName) {
        if (serverName == null) {
            throw new NullPointerException("serverName");
        }
        return this.allServer || this.configServersEnabled.contains(serverName);
    }

    @Override
    public boolean isSeparateServerChannels() {
        return this.separateServer;
    }

    @Override
    public IVoiceChannel createVoiceChannel() {
        return new VoiceChannel(this);
    }

    @Override
    public IVoiceChannel getGlobalVoiceChannel() {
        return this.globalChannel;
    }

    @Override
    public IVoiceChannel getServerVoiceChannel(String serverName) {
        if (serverName == null) {
            throw new NullPointerException("serverName");
        }
        if (this.allServer || this.configServersEnabled.contains(serverName)) {
            if (this.separateServer) {
                try {
                    return this.serverChannels.get(serverName);
                }
                catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof RuntimeException) {
                        throw (RuntimeException)cause;
                    }
                    throw new RuntimeException(cause);
                }
            }
            return this.globalChannel;
        }
        return DisabledChannel.INSTANCE;
    }

    @Override
    public IVoiceChannel getDisabledVoiceChannel() {
        return DisabledChannel.INSTANCE;
    }

    @Override
    public Collection<IEaglerPlayer<PlayerObject>> getConnectedPlayers(IVoiceChannel channel) {
        if (channel == null) {
            throw new NullPointerException("Voice channel cannot be null!");
        }
        if (channel == DisabledChannel.INSTANCE) {
            throw new UnsupportedOperationException("Cannot list players connected to the disabled channel");
        }
        if (!(channel instanceof VoiceChannel) || ((VoiceChannel)channel).owner != this) {
            throw new IllegalArgumentException("Unknown voice channel");
        }
        VoiceChannel ch = (VoiceChannel)channel;
        return ch.listConnectedPlayers();
    }

    @Override
    public VoiceManagerLocal<PlayerObject> createVoiceManager(EaglerPlayerInstance<PlayerObject> player) {
        return player.hasCapability(EnumCapabilitySpec.VOICE_V0) ? new VoiceManagerLocal<PlayerObject>(player, this) : null;
    }
}

