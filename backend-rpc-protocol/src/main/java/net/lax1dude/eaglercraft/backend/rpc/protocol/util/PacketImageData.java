/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.rpc.protocol.util;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

public final class PacketImageData {
    public final int width;
    public final int height;
    public final int[] rgba;

    public PacketImageData(int width, int height, int[] rgba) {
        this.width = width;
        this.height = height;
        this.rgba = rgba;
    }

    public int getByteLengthRGB16() {
        return 2 + (this.rgba.length << 1);
    }

    public int hashCode() {
        int result = 1;
        result = 31 * result + this.height;
        result = 31 * result + Arrays.hashCode(this.rgba);
        result = 31 * result + this.width;
        return result;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PacketImageData)) {
            return false;
        }
        PacketImageData other = (PacketImageData)obj;
        if (this.height != other.height) {
            return false;
        }
        if (!Arrays.equals(this.rgba, other.rgba)) {
            return false;
        }
        return this.width == other.width;
    }

    public static PacketImageData readRGB16(DataInput buffer) throws IOException {
        int w = buffer.readUnsignedByte();
        int h = buffer.readUnsignedByte();
        int pixelCount = w * h;
        int[] pixels = new int[pixelCount];
        for (int j = 0; j < pixelCount; ++j) {
            int pB;
            int pG;
            int p = buffer.readUnsignedShort();
            int pR = p >>> 11 & 0x1F;
            if (pR + (pG = p >>> 5 & 0x3F) + (pB = p & 0x1F) > 0) {
                pB = (pB - 1) * 255 / 30;
                pixels[j] = 0xFF000000 | pR << 19 | pG << 10 | pB;
                continue;
            }
            pixels[j] = 0;
        }
        return new PacketImageData(w, h, pixels);
    }

    public static void writeRGB16(DataOutput buffer, PacketImageData imageData) throws IOException {
        if (imageData.width < 1 || imageData.width > 255 || imageData.height < 1 || imageData.height > 255) {
            throw new IOException("Invalid image dimensions in packet, must be between 1x1 and 255x255, got " + imageData.width + "x" + imageData.height);
        }
        buffer.writeByte(imageData.width);
        buffer.writeByte(imageData.height);
        int pixelCount = imageData.width * imageData.height;
        for (int j = 0; j < pixelCount; ++j) {
            int p = imageData.rgba[j];
            if (p >>> 24 > 127) {
                int pR = p >>> 19 & 0x1F;
                int pG = p >>> 10 & 0x3F;
                int pB = (p & 0xFF) * 30 / 255 + 1;
                buffer.writeShort(pR << 11 | pG << 5 | pB);
                continue;
            }
            buffer.writeShort(0);
        }
    }
}

