/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformComponentBuilder;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformPlayer;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformTask;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServer;
import net.lax1dude.eaglercraft.backend.server.base.EaglerXServerVersion;
import net.lax1dude.eaglercraft.backend.server.base.config.ConfigDataSettings;
import net.lax1dude.eaglercraft.backend.server.base.pipeline.BufferUtils;
import net.lax1dude.eaglercraft.backend.server.util.SemanticVersion;

public class UpdateChecker {
    private final EaglerXServer<?> server;
    private final ConfigDataSettings.ConfigDataUpdateChecker config;
    private volatile String available;
    private IPlatformTask checkTask;

    public UpdateChecker(EaglerXServer<?> server, ConfigDataSettings.ConfigDataUpdateChecker config) {
        this.server = server;
        this.config = config;
    }

    public void handleEnable() {
        if ("https://lax1dude.net/eaglerxserver/version.txt" != null && this.config.isEnableUpdateChecker()) {
            this.check();
            int period = this.config.getCheckForServerUpdateEvery();
            if (period > 0) {
                this.checkTask = this.server.getPlatform().getScheduler().executeAsyncRepeatingTask(this::check, (long)period * 1000L, (long)period * 1000L);
            }
        }
    }

    public void handleDisable() {
        if ("https://lax1dude.net/eaglerxserver/version.txt" != null && this.config.isEnableUpdateChecker() && this.checkTask != null) {
            this.checkTask.cancel();
            this.checkTask = null;
        }
    }

    private void check() {
        this.server.getInternalHTTPClient().asyncRequest("GET", URI.create("https://lax1dude.net/eaglerxserver/version.txt"), res -> {
            block13: {
                try {
                    SemanticVersion ver;
                    if (res.exception != null) {
                        this.server.logger().error("Could not check for server updates: " + res.exception.toString());
                        break block13;
                    }
                    if (res.code != 200) {
                        this.server.logger().error("Could not check for server updates, response code " + res.code);
                        break block13;
                    }
                    if (res.data == null || res.data.readableBytes() == 0) {
                        this.server.logger().error("Could not check for server updates, received empty response");
                        break block13;
                    }
                    String str = BufferUtils.readCharSequence(res.data, res.data.readableBytes(), StandardCharsets.UTF_8).toString().trim();
                    try {
                        ver = SemanticVersion.parse(str);
                    }
                    catch (IllegalArgumentException ex) {
                        this.server.logger().error("Could not check for server updates, response invalid");
                        if (res.data != null) {
                            res.data.release();
                        }
                        return;
                    }
                    if (ver.greaterThan(EaglerXServerVersion.UPDATE_VERSION)) {
                        this.available = ver.toString();
                        this.server.logger().warn("=============================================");
                        this.server.logger().warn("=============================================");
                        this.server.logger().warn("");
                        this.server.logger().warn(" Updates are available for EaglerXServer");
                        this.server.logger().warn(" ---------------------------------------");
                        this.server.logger().warn("");
                        this.server.logger().warn("   :< Active: " + EaglerXServerVersion.UPDATE_VERSION);
                        this.server.logger().warn("");
                        this.server.logger().warn("   :> Latest: " + ver);
                        this.server.logger().warn("");
                        this.server.logger().warn(" https://lax1dude.net/eaglerxserver/");
                        this.server.logger().warn(" -----------------------------------");
                        this.server.logger().warn("");
                        this.server.logger().warn("=============================================");
                        this.server.logger().warn("=============================================");
                    } else {
                        this.available = null;
                        this.server.logger().info("You are running the latest version of EaglerXServer");
                    }
                }
                finally {
                    if (res.data != null) {
                        res.data.release();
                    }
                }
            }
        });
    }

    public void sendUpdateMessage(IPlatformPlayer<?> playerObj) {
        String avail;
        if ("https://lax1dude.net/eaglerxserver/version.txt" != null && this.config.isUpdateChatMessages() && (avail = this.available) != null) {
            this.server.getPlatform().getScheduler().executeAsyncDelayed(() -> {
                if (playerObj.isConnected()) {
                    playerObj.sendMessage(((IPlatformComponentBuilder.IBuilderComponentText)((IPlatformComponentBuilder.IBuilderComponentText)((IPlatformComponentBuilder.IBuilderComponentText)((IPlatformComponentBuilder.IBuilderComponentText)((IPlatformComponentBuilder.IBuilderComponentText)((IPlatformComponentBuilder.IBuilderComponentText)this.server.componentBuilder().buildTextComponent().beginStyle().color(IPlatformComponentBuilder.EnumChatColor.GOLD).end()).text("[EaglerXServer]").appendTextComponent().beginStyle().color(IPlatformComponentBuilder.EnumChatColor.AQUA).end()).text(" An updated version is available: ").end()).appendTextComponent().beginStyle().color(IPlatformComponentBuilder.EnumChatColor.YELLOW).underline(true).end()).beginClickEvent().clickAction(IPlatformComponentBuilder.EnumClickAction.OPEN_URL).clickValue("https://lax1dude.net/eaglerxserver/").end()).text(avail).end()).end());
                }
            }, 2000L);
        }
    }
}

