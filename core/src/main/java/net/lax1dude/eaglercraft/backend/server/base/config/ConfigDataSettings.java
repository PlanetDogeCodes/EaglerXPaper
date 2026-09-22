/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.config;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public class ConfigDataSettings {
    private final String serverName;
    private final UUID serverUUID;
    private final String serverUUIDString;
    private final int eaglerLoginTimeout;
    private final int httpMaxInitialLineLength;
    private final int httpMaxHeaderSize;
    private final int httpMaxChunkSize;
    private final int httpMaxContentLength;
    private final int httpWebSocketCompressionLevel;
    private final int httpWebSocketFragmentSize;
    private final int httpWebSocketMaxFrameLength;
    private final boolean httpWebSocketPingIntervention;
    private final int tlsCertRefreshRate;
    private final boolean enableAuthenticationEvents;
    private final boolean enableBackendRPCAPI;
    private final boolean useModernizedChannelNames;
    private final int eaglerPlayersViewDistance;
    private final String eaglerPlayersVanillaSkin;
    private final boolean enableIsEaglerPlayerPropery;
    private final int protocolV4DefragSendDelay;
    private final int protocolV4DefragMaxPackets;
    private final int brandLookupRatelimit;
    private final int webviewDownloadRatelimit;
    private final int webviewMessageRatelimit;
    private final boolean debugLogNewChannels;
    private final boolean debugLogRealIPHeaders;
    private final boolean debugLogOriginHeaders;
    private final boolean debugLogClientBrands;
    private final ConfigDataProtocols protocols;
    private final ConfigDataSkinService skinService;
    private final ConfigDataVoiceService voiceService;
    private final ConfigDataUpdateService updateService;
    private final ConfigDataUpdateChecker updateChecker;

    public ConfigDataSettings(String serverName, UUID serverUUID, int eaglerLoginTimeout, int httpMaxInitialLineLength, int httpMaxHeaderSize, int httpMaxChunkSize, int httpMaxContentLength, int httpWebSocketCompressionLevel, int httpWebSocketFragmentSize, int httpWebSocketMaxFrameLength, boolean httpWebSocketPingIntervention, int tlsCertRefreshRate, boolean enableAuthenticationEvents, boolean enableBackendRPCAPI, boolean useModernizedChannelNames, int eaglerPlayersViewDistance, String eaglerPlayersVanillaSkin, boolean enableIsEaglerPlayerPropery, int protocolV4DefragSendDelay, int protocolV4DefragMaxPackets, int brandLookupRatelimit, int webviewDownloadRatelimit, int webviewMessageRatelimit, boolean debugLogNewChannels, boolean debugLogRealIPHeaders, boolean debugLogOriginHeaders, boolean debugLogClientBrands, ConfigDataProtocols protocols, ConfigDataSkinService skinService, ConfigDataVoiceService voiceService, ConfigDataUpdateService updateService, ConfigDataUpdateChecker updateChecker) {
        this.serverName = serverName;
        this.serverUUID = serverUUID;
        this.serverUUIDString = serverUUID.toString();
        this.eaglerLoginTimeout = eaglerLoginTimeout;
        this.httpMaxInitialLineLength = httpMaxInitialLineLength;
        this.httpMaxHeaderSize = httpMaxHeaderSize;
        this.httpMaxChunkSize = httpMaxChunkSize;
        this.httpMaxContentLength = httpMaxContentLength;
        this.httpWebSocketCompressionLevel = httpWebSocketCompressionLevel;
        this.httpWebSocketFragmentSize = httpWebSocketFragmentSize;
        this.httpWebSocketMaxFrameLength = httpWebSocketMaxFrameLength;
        this.httpWebSocketPingIntervention = httpWebSocketPingIntervention;
        this.tlsCertRefreshRate = tlsCertRefreshRate;
        this.enableAuthenticationEvents = enableAuthenticationEvents;
        this.enableBackendRPCAPI = enableBackendRPCAPI;
        this.useModernizedChannelNames = useModernizedChannelNames;
        this.eaglerPlayersViewDistance = eaglerPlayersViewDistance;
        this.eaglerPlayersVanillaSkin = eaglerPlayersVanillaSkin;
        this.enableIsEaglerPlayerPropery = enableIsEaglerPlayerPropery;
        this.protocolV4DefragSendDelay = protocolV4DefragSendDelay;
        this.protocolV4DefragMaxPackets = protocolV4DefragMaxPackets;
        this.brandLookupRatelimit = brandLookupRatelimit;
        this.webviewDownloadRatelimit = webviewDownloadRatelimit;
        this.webviewMessageRatelimit = webviewMessageRatelimit;
        this.debugLogNewChannels = debugLogNewChannels;
        this.debugLogRealIPHeaders = debugLogRealIPHeaders;
        this.debugLogOriginHeaders = debugLogOriginHeaders;
        this.debugLogClientBrands = debugLogClientBrands;
        this.protocols = protocols;
        this.skinService = skinService;
        this.voiceService = voiceService;
        this.updateService = updateService;
        this.updateChecker = updateChecker;
    }

    public String getServerName() {
        return this.serverName;
    }

    public UUID getServerUUID() {
        return this.serverUUID;
    }

    public String getServerUUIDString() {
        return this.serverUUIDString;
    }

    public int getEaglerLoginTimeout() {
        return this.eaglerLoginTimeout;
    }

    public int getHTTPMaxInitialLineLength() {
        return this.httpMaxInitialLineLength;
    }

    public int getHTTPMaxHeaderSize() {
        return this.httpMaxHeaderSize;
    }

    public int getHTTPMaxChunkSize() {
        return this.httpMaxChunkSize;
    }

    public int getHTTPMaxContentLength() {
        return this.httpMaxContentLength;
    }

    public int getHTTPWebSocketCompressionLevel() {
        return this.httpWebSocketCompressionLevel;
    }

    public int getHTTPWebSocketFragmentSize() {
        return this.httpWebSocketFragmentSize;
    }

    public int getHTTPWebSocketMaxFrameLength() {
        return this.httpWebSocketMaxFrameLength;
    }

    public boolean getHTTPWebSocketPingIntervention() {
        return this.httpWebSocketPingIntervention;
    }

    public int getTLSCertRefreshRate() {
        return this.tlsCertRefreshRate;
    }

    public boolean isEnableAuthenticationEvents() {
        return this.enableAuthenticationEvents;
    }

    public boolean isEnableBackendRPCAPI() {
        return this.enableBackendRPCAPI;
    }

    public boolean isUseModernizedChannelNames() {
        return this.useModernizedChannelNames;
    }

    public int getEaglerPlayersViewDistance() {
        return this.eaglerPlayersViewDistance;
    }

    public String getEaglerPlayersVanillaSkin() {
        return this.eaglerPlayersVanillaSkin;
    }

    public boolean isEnableIsEaglerPlayerProperty() {
        return this.enableIsEaglerPlayerPropery;
    }

    public int getProtocolV4DefragSendDelay() {
        return this.protocolV4DefragSendDelay;
    }

    public int getProtocolV4DefragMaxPackets() {
        return this.protocolV4DefragMaxPackets;
    }

    public int getBrandLookupRatelimit() {
        return this.brandLookupRatelimit;
    }

    public int getWebviewDownloadRatelimit() {
        return this.webviewDownloadRatelimit;
    }

    public int getWebviewMessageRatelimit() {
        return this.webviewMessageRatelimit;
    }

    public boolean isDebugLogNewChannels() {
        return this.debugLogNewChannels;
    }

    public boolean isDebugLogRealIPHeaders() {
        return this.debugLogRealIPHeaders;
    }

    public boolean isDebugLogOriginHeaders() {
        return this.debugLogOriginHeaders;
    }

    public boolean isDebugLogClientBrands() {
        return this.debugLogClientBrands;
    }

    public ConfigDataProtocols getProtocols() {
        return this.protocols;
    }

    public ConfigDataSkinService getSkinService() {
        return this.skinService;
    }

    public ConfigDataVoiceService getVoiceService() {
        return this.voiceService;
    }

    public ConfigDataUpdateService getUpdateService() {
        return this.updateService;
    }

    public ConfigDataUpdateChecker getUpdateChecker() {
        return this.updateChecker;
    }

    public static class ConfigDataProtocols {
        private final int minMinecraftProtocol;
        private final int maxMinecraftProtocol;
        private final int maxMinecraftProtocolV5;
        private final boolean eaglerXRewindAllowed;
        private final boolean protocolLegacyAllowed;
        private final boolean protocolV3Allowed;
        private final boolean protocolV4Allowed;
        private final boolean protocolV5Allowed;
        private final int minEaglerProtocol;
        private final int maxEaglerProtocol;

        public ConfigDataProtocols(int minMinecraftProtocol, int maxMinecraftProtocol, int maxMinecraftProtocolV5, boolean eaglerXRewindAllowed, boolean protocolLegacyAllowed, boolean protocolV3Allowed, boolean protocolV4Allowed, boolean protocolV5Allowed) {
            this.minMinecraftProtocol = minMinecraftProtocol;
            this.maxMinecraftProtocol = maxMinecraftProtocol;
            this.maxMinecraftProtocolV5 = maxMinecraftProtocolV5;
            this.eaglerXRewindAllowed = eaglerXRewindAllowed;
            this.protocolLegacyAllowed = protocolLegacyAllowed;
            this.protocolV3Allowed = protocolV3Allowed;
            this.protocolV4Allowed = protocolV4Allowed;
            this.protocolV5Allowed = protocolV5Allowed;
            this.minEaglerProtocol = protocolLegacyAllowed ? 1 : (protocolV3Allowed ? 3 : (protocolV4Allowed ? 4 : (protocolV5Allowed ? 5 : Integer.MAX_VALUE)));
            this.maxEaglerProtocol = protocolV5Allowed ? 5 : (protocolV4Allowed ? 4 : (protocolV3Allowed ? 3 : (protocolLegacyAllowed ? 1 : Integer.MIN_VALUE)));
        }

        public int getMinMinecraftProtocol() {
            return this.minMinecraftProtocol;
        }

        public int getMaxMinecraftProtocol() {
            return this.maxMinecraftProtocol;
        }

        public int getMaxMinecraftProtocolV5() {
            return this.maxMinecraftProtocolV5;
        }

        public boolean isEaglerXRewindAllowed() {
            return this.eaglerXRewindAllowed;
        }

        public boolean isProtocolLegacyAllowed() {
            return this.protocolLegacyAllowed;
        }

        public boolean isProtocolV3Allowed() {
            return this.protocolV3Allowed;
        }

        public boolean isProtocolV4Allowed() {
            return this.protocolV4Allowed;
        }

        public boolean isProtocolV5Allowed() {
            return this.protocolV5Allowed;
        }

        public int getMinEaglerProtocol() {
            return this.minEaglerProtocol;
        }

        public int getMaxEaglerProtocol() {
            return this.maxEaglerProtocol;
        }

        public boolean isEaglerHandshakeSupported(int vers) {
            switch (vers) {
                case 1: 
                case 2: {
                    return this.protocolLegacyAllowed;
                }
                case 3: {
                    return this.protocolV3Allowed;
                }
                case 4: {
                    return this.protocolV4Allowed;
                }
                case 5: {
                    return this.protocolV5Allowed;
                }
            }
            return false;
        }

        public boolean isEaglerProtocolSupported(int vers) {
            switch (vers) {
                case 3: {
                    return this.protocolLegacyAllowed || this.protocolV3Allowed;
                }
                case 4: {
                    return this.protocolV4Allowed;
                }
                case 5: {
                    return this.protocolV5Allowed;
                }
            }
            return false;
        }

        public boolean isMinecraftProtocolSupported(int vers) {
            return this.minMinecraftProtocol <= vers && (this.maxMinecraftProtocol >= vers || this.maxMinecraftProtocol == -1);
        }

        public boolean isMinecraftProtocolSupportedV5(int vers) {
            return this.minMinecraftProtocol <= vers && (this.maxMinecraftProtocolV5 >= vers || this.maxMinecraftProtocolV5 == -1);
        }
    }

    public static class ConfigDataSkinService {
        private final int skinLookupRatelimit;
        private final int capeLookupRatelimit;
        private final boolean downloadVanillaSkinsToClients;
        private final Set<String> validSkinDownloadURLs;
        private final String skinCacheDBURI;
        private final String skinCacheDriverClass;
        private final String skinCacheDriverPath;
        private final boolean skinCacheSQLiteCompatible;
        private final boolean skinCacheForceConnectionPool;
        private final int skinCacheThreadCount;
        private final int skinCacheCompressionLevel;
        private final int skinCacheMemoryKeepSeconds;
        private final int skinCacheMemoryMaxObjects;
        private final int skinCacheDiskKeepObjectsDays;
        private final int skinCacheDiskMaxObjects;
        private final int skinCacheAntagonistsRatelimit;
        private final boolean enableFNAWSkinModelsGlobal;
        private final Set<String> enableFNAWSkinModelsOnServers;
        private final boolean enableSkinsRestorerApplyHook;

        public ConfigDataSkinService(int skinLookupRatelimit, int capeLookupRatelimit, boolean downloadVanillaSkinsToClients, Set<String> validSkinDownloadURLs, String skinCacheDBURI, String skinCacheDriverClass, String skinCacheDriverPath, boolean skinCacheSQLiteCompatible, boolean skinCacheForceConnectionPool, int skinCacheThreadCount, int skinCacheCompressionLevel, int skinCacheMemoryKeepSeconds, int skinCacheMemoryMaxObjects, int skinCacheDiskKeepObjectsDays, int skinCacheDiskMaxObjects, int skinCacheAntagonistsRatelimit, boolean enableFNAWSkinModelsGlobal, Set<String> enableFNAWSkinModelsOnServers, boolean enableSkinsRestorerApplyHook) {
            this.skinLookupRatelimit = skinLookupRatelimit;
            this.capeLookupRatelimit = capeLookupRatelimit;
            this.downloadVanillaSkinsToClients = downloadVanillaSkinsToClients;
            this.validSkinDownloadURLs = validSkinDownloadURLs;
            this.skinCacheDBURI = skinCacheDBURI;
            this.skinCacheDriverClass = skinCacheDriverClass;
            this.skinCacheDriverPath = skinCacheDriverPath;
            this.skinCacheSQLiteCompatible = skinCacheSQLiteCompatible;
            this.skinCacheForceConnectionPool = skinCacheForceConnectionPool;
            this.skinCacheThreadCount = skinCacheThreadCount;
            this.skinCacheCompressionLevel = skinCacheCompressionLevel;
            this.skinCacheMemoryKeepSeconds = skinCacheMemoryKeepSeconds;
            this.skinCacheMemoryMaxObjects = skinCacheMemoryMaxObjects;
            this.skinCacheDiskKeepObjectsDays = skinCacheDiskKeepObjectsDays;
            this.skinCacheDiskMaxObjects = skinCacheDiskMaxObjects;
            this.skinCacheAntagonistsRatelimit = skinCacheAntagonistsRatelimit;
            this.enableFNAWSkinModelsGlobal = enableFNAWSkinModelsGlobal;
            this.enableFNAWSkinModelsOnServers = enableFNAWSkinModelsOnServers;
            this.enableSkinsRestorerApplyHook = enableSkinsRestorerApplyHook;
        }

        public int getSkinLookupRatelimit() {
            return this.skinLookupRatelimit;
        }

        public int getCapeLookupRatelimit() {
            return this.capeLookupRatelimit;
        }

        public boolean isDownloadVanillaSkinsToClients() {
            return this.downloadVanillaSkinsToClients;
        }

        public Set<String> getValidSkinDownloadURLs() {
            return this.validSkinDownloadURLs;
        }

        public String getSkinCacheDBURI() {
            return this.skinCacheDBURI;
        }

        public String getSkinCacheDriverClass() {
            return this.skinCacheDriverClass;
        }

        public String getSkinCacheDriverPath() {
            return this.skinCacheDriverPath;
        }

        public boolean isSkinCacheSQLiteCompatible() {
            return this.skinCacheSQLiteCompatible;
        }

        public boolean isSkinCacheForceConnectionPool() {
            return this.skinCacheForceConnectionPool;
        }

        public int getSkinCacheThreadCount() {
            return this.skinCacheThreadCount;
        }

        public int getSkinCacheCompressionLevel() {
            return this.skinCacheCompressionLevel;
        }

        public int getSkinCacheMemoryKeepSeconds() {
            return this.skinCacheMemoryKeepSeconds;
        }

        public int getSkinCacheMemoryMaxObjects() {
            return this.skinCacheMemoryMaxObjects;
        }

        public int getSkinCacheDiskKeepObjectsDays() {
            return this.skinCacheDiskKeepObjectsDays;
        }

        public int getSkinCacheDiskMaxObjects() {
            return this.skinCacheDiskMaxObjects;
        }

        public int getSkinCacheAntagonistsRatelimit() {
            return this.skinCacheAntagonistsRatelimit;
        }

        public boolean isEnableFNAWSkinModelsGlobal() {
            return this.enableFNAWSkinModelsGlobal;
        }

        public Set<String> getEnableFNAWSkinModelsOnServers() {
            return this.enableFNAWSkinModelsOnServers;
        }

        public Predicate<String> getFNAWSkinsPredicate() {
            if (this.enableFNAWSkinModelsGlobal) {
                return str -> true;
            }
            if (!this.enableFNAWSkinModelsOnServers.isEmpty()) {
                return this.enableFNAWSkinModelsOnServers::contains;
            }
            return str -> false;
        }

        public boolean isEnableSkinsRestorerApplyHook() {
            return this.enableSkinsRestorerApplyHook;
        }
    }

    public static class ConfigDataVoiceService {
        private final boolean enableVoiceService;
        private final boolean enableVoiceChatAllServers;
        private final Set<String> enableVoiceChatOnServers;
        private final boolean separateVoiceChannelsPerServer;
        private final boolean voiceBackendRelayMode;
        private final int voiceConnectRatelimit;
        private final int voiceRequestRatelimit;
        private final int voiceICERatelimit;

        public ConfigDataVoiceService(boolean enableVoiceService, boolean enableVoiceChatAllServers, Set<String> enableVoiceChatOnServers, boolean separateVoiceChannelsPerServer, boolean voiceBackendRelayMode, int voiceConnectRatelimit, int voiceRequestRatelimit, int voiceICERatelimit) {
            this.enableVoiceService = enableVoiceService;
            this.enableVoiceChatAllServers = enableVoiceChatAllServers;
            this.enableVoiceChatOnServers = enableVoiceChatOnServers;
            this.separateVoiceChannelsPerServer = separateVoiceChannelsPerServer;
            this.voiceBackendRelayMode = voiceBackendRelayMode;
            this.voiceConnectRatelimit = voiceConnectRatelimit;
            this.voiceRequestRatelimit = voiceRequestRatelimit;
            this.voiceICERatelimit = voiceICERatelimit;
        }

        public boolean isEnableVoiceService() {
            return this.enableVoiceService;
        }

        public boolean isEnableVoiceChatAllServers() {
            return this.enableVoiceChatAllServers;
        }

        public Set<String> getEnableVoiceChatOnServers() {
            return this.enableVoiceChatOnServers;
        }

        public boolean isSeparateVoiceChannelsPerServer() {
            return this.separateVoiceChannelsPerServer;
        }

        public boolean isVoiceBackendRelayMode() {
            return this.voiceBackendRelayMode;
        }

        public int getVoiceConnectRatelimit() {
            return this.voiceConnectRatelimit;
        }

        public int getVoiceRequestRatelimit() {
            return this.voiceRequestRatelimit;
        }

        public int getVoiceICERatelimit() {
            return this.voiceICERatelimit;
        }
    }

    public static class ConfigDataUpdateService {
        private final boolean enableUpdateSystem;
        private final boolean discardLoginPacketCerts;
        private final int certPacketDataRateLimit;
        private final boolean enableEagcertFolder;
        private final boolean downloadLatestCerts;
        private final List<URI> downloadCertsFrom;
        private final int checkForUpdateEvery;

        public ConfigDataUpdateService(boolean enableUpdateSystem, boolean discardLoginPacketCerts, int certPacketDataRateLimit, boolean enableEagcertFolder, boolean downloadLatestCerts, List<URI> downloadCertsFrom, int checkForUpdateEvery) {
            this.enableUpdateSystem = enableUpdateSystem;
            this.discardLoginPacketCerts = discardLoginPacketCerts;
            this.certPacketDataRateLimit = certPacketDataRateLimit;
            this.enableEagcertFolder = enableEagcertFolder;
            this.downloadLatestCerts = downloadLatestCerts;
            this.downloadCertsFrom = downloadCertsFrom;
            this.checkForUpdateEvery = checkForUpdateEvery;
        }

        public boolean isEnableUpdateSystem() {
            return this.enableUpdateSystem;
        }

        public boolean isDiscardLoginPacketCerts() {
            return this.discardLoginPacketCerts;
        }

        public int getCertPacketDataRateLimit() {
            return this.certPacketDataRateLimit;
        }

        public boolean isEnableEagcertFolder() {
            return this.enableEagcertFolder;
        }

        public boolean isDownloadLatestCerts() {
            return this.downloadLatestCerts;
        }

        public List<URI> getDownloadCertsFrom() {
            return this.downloadCertsFrom;
        }

        public int getCheckForUpdateEvery() {
            return this.checkForUpdateEvery;
        }
    }

    public static class ConfigDataUpdateChecker {
        private final boolean enableUpdateChecker;
        private final int checkForServerUpdateEvery;
        private final boolean updateChatMessages;

        public ConfigDataUpdateChecker(boolean enableUpdateChecker, int checkForServerUpdateEvery, boolean updateChatMessages) {
            this.enableUpdateChecker = enableUpdateChecker;
            this.checkForServerUpdateEvery = checkForServerUpdateEvery;
            this.updateChatMessages = updateChatMessages;
        }

        public boolean isEnableUpdateChecker() {
            return this.enableUpdateChecker;
        }

        public int getCheckForServerUpdateEvery() {
            return this.checkForServerUpdateEvery;
        }

        public boolean isUpdateChatMessages() {
            return this.updateChatMessages;
        }
    }
}

