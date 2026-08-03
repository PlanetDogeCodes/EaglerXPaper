/*
 * Copyright (c) 2025 lax1dude. All Rights Reserved.
 *
 * Detects whether ViaVersion/ViaBackwards/ViaRewind are installed.
 * Results are stored as static volatile fields so the cross-platform
 * command layer can access them without Bukkit API dependencies.
 */

package net.lax1dude.eaglercraft.backend.server.base.command;

public class ViaVersionDetector {

    private static volatile Boolean viaVersionInstalled = null;
    private static volatile Boolean viaBackwardsInstalled = null;
    private static volatile Boolean viaRewindInstalled = null;

    public static void setInstalled(boolean viaVersion, boolean viaBackwards, boolean viaRewind) {
        viaVersionInstalled = viaVersion;
        viaBackwardsInstalled = viaBackwards;
        viaRewindInstalled = viaRewind;
    }

    public static Boolean isInstalled() {
        return viaVersionInstalled;
    }

    public static Boolean isBackwardsInstalled() {
        return viaBackwardsInstalled;
    }

    public static Boolean isRewindInstalled() {
        return viaRewindInstalled;
    }
}
