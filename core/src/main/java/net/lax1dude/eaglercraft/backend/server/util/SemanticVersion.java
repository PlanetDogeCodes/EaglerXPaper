/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.util;

public final class SemanticVersion {
    private final int major;
    private final int minor;
    private final int patch;

    public static SemanticVersion parse(String str) {
        char c;
        int end;
        int a = str.indexOf(46);
        if (a == -1) {
            throw new IllegalArgumentException();
        }
        int b = str.indexOf(46, a + 1);
        if (b == -1) {
            throw new IllegalArgumentException();
        }
        int len = str.length();
        for (end = b + 1; end < len && (c = str.charAt(end)) >= '0' && c <= '9'; ++end) {
        }
        if (end == b + 1) {
            throw new IllegalArgumentException();
        }
        return new SemanticVersion(Integer.parseInt(str.substring(0, a)), Integer.parseInt(str.substring(a + 1, b)), Integer.parseInt(str.substring(b + 1, end)));
    }

    public SemanticVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public boolean greaterThan(SemanticVersion ver) {
        return this.major > ver.major || this.major == ver.major && (this.minor > ver.minor || this.minor == ver.minor && this.patch > ver.patch);
    }

    public String toString() {
        return this.major + "." + this.minor + "." + this.patch;
    }

    public int hashCode() {
        int prime = 31;
        int result = 1;
        result = 31 * result + this.major;
        result = 31 * result + this.minor;
        result = 31 * result + this.patch;
        return result;
    }

    public boolean equals(Object obj) {
        return this == obj || obj instanceof SemanticVersion && this.major == ((SemanticVersion)obj).major && this.minor == ((SemanticVersion)obj).minor && this.patch == ((SemanticVersion)obj).patch;
    }
}

