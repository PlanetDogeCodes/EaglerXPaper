/*
 * Copyright (c) 2025 lax1dude. All Rights Reserved.
 *
 * Lightweight holder for EaglerXPaper-specific feature config values.
 * These are read from settings.yml by EaglerConfigLoader and stored here
 * so they can be accessed without modifying the upstream ConfigDataSettings
 * class hierarchy.
 *
 * Fields are volatile because they are written by the main thread (during
 * config load) and read by Netty event-loop threads (when connections arrive).
 */

package net.lax1dude.eaglercraft.backend.server.base.config;

public class EaglerXPaperConfig {
    public static volatile boolean enableSkinPrewarm = true;
    public static volatile int prewarmMaxPlayers = 50;

    public static volatile boolean enableAdaptiveBatching = true;
}
