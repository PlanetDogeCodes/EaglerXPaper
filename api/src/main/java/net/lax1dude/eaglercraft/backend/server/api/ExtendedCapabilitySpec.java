/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nonnull
 */
package net.lax1dude.eaglercraft.backend.server.api;

import java.util.Arrays;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class ExtendedCapabilitySpec {
    private final Version[] majorVersions;

    @Nonnull
    public static ExtendedCapabilitySpec create(@Nonnull UUID majorVersion, int ... minorVersions) {
        return new ExtendedCapabilitySpec(ExtendedCapabilitySpec.version(majorVersion, minorVersions));
    }

    @Nonnull
    public static ExtendedCapabilitySpec create(Version ... majorVersions) {
        return new ExtendedCapabilitySpec(majorVersions);
    }

    @Nonnull
    public static Version version(@Nonnull UUID majorVersion, int ... minorVersions) {
        return new Version(majorVersion, minorVersions);
    }

    private ExtendedCapabilitySpec(Version ... majorVersions) {
        this.majorVersions = majorVersions;
    }

    @Nonnull
    public Version[] getMajorVersions() {
        return this.majorVersions;
    }

    public int hashCode() {
        return Arrays.hashCode(this.majorVersions);
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof ExtendedCapabilitySpec)) {
            return false;
        }
        ExtendedCapabilitySpec other = (ExtendedCapabilitySpec)obj;
        return Arrays.equals(this.majorVersions, other.majorVersions);
    }

    public static final class Version {
        private final UUID majorVersion;
        private final int[] minorVersions;

        private Version(UUID majorVersion, int ... minorVersions) {
            for (int i = 0; i < minorVersions.length; ++i) {
                int j = minorVersions[i];
                if (j >= 0 && j <= 31) continue;
                throw new IllegalArgumentException("Illegal subversion " + minorVersions[i] + ", must be between 0 to 31");
            }
            this.majorVersion = majorVersion;
            this.minorVersions = minorVersions;
        }

        @Nonnull
        public UUID getMajorVersion() {
            return this.majorVersion;
        }

        @Nonnull
        public int[] getMinorVersions() {
            return this.minorVersions;
        }

        public int hashCode() {
            int result = 31 + (this.majorVersion == null ? 0 : this.majorVersion.hashCode());
            result = 31 * result + Arrays.hashCode(this.minorVersions);
            return result;
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof Version)) {
                return false;
            }
            Version other = (Version)obj;
            if (this.majorVersion == null ? other.majorVersion != null : !this.majorVersion.equals(other.majorVersion)) {
                return false;
            }
            return Arrays.equals(this.minorVersions, other.minorVersions);
        }
    }
}

