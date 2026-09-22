/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.base.skins;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.lax1dude.eaglercraft.backend.server.api.skins.EnumPresetCapes;
import net.lax1dude.eaglercraft.backend.server.api.skins.EnumPresetSkins;
import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerCape;
import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerSkin;
import net.lax1dude.eaglercraft.backend.server.base.skins.SkinConverterExt;
import net.lax1dude.eaglercraft.backend.server.base.skins.type.CustomCapeGeneric;
import net.lax1dude.eaglercraft.backend.server.base.skins.type.CustomSkinGeneric;
import net.lax1dude.eaglercraft.backend.server.base.skins.type.IModelRewritable;
import net.lax1dude.eaglercraft.backend.server.base.skins.type.InternUtils;
import net.lax1dude.eaglercraft.backend.skin_cache.SkinConverter;

public class SkinImageLoaderImpl {
    public static IEaglerPlayerSkin loadPresetSkin(int presetSkin) {
        if (presetSkin < 0 || presetSkin > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid preset skin id: " + presetSkin);
        }
        return InternUtils.getPresetSkin(presetSkin);
    }

    public static IEaglerPlayerSkin loadPresetSkin(EnumPresetSkins presetSkin) {
        return InternUtils.getPresetSkin(presetSkin.getId());
    }

    public static IEaglerPlayerSkin loadPresetSkin(UUID playerUUID) {
        return InternUtils.getPresetSkin((playerUUID.hashCode() & 1) != 0 ? 1 : 0);
    }

    public static IEaglerPlayerSkin rewriteCustomSkinModelId(IEaglerPlayerSkin skin, int modelId) {
        if (modelId < 0 || modelId >= 255) {
            throw new IllegalArgumentException("Invalid model id: " + modelId);
        }
        if (skin instanceof IModelRewritable) {
            IModelRewritable rw = (IModelRewritable)((Object)skin);
            return rw.rewriteModelInternal(modelId);
        }
        return skin;
    }

    public static IEaglerPlayerCape loadPresetNoCape() {
        return InternUtils.getPresetCape(0);
    }

    public static IEaglerPlayerCape loadPresetCape(int presetCape) {
        return InternUtils.getPresetCape(presetCape);
    }

    public static IEaglerPlayerCape loadPresetCape(EnumPresetCapes presetCape) {
        return InternUtils.getPresetCape(presetCape.getId());
    }

    public static IEaglerPlayerSkin loadSkinImageData64x64(int[] pixelsARGB8, int modelId) {
        if (pixelsARGB8.length != 4096) {
            throw new IllegalArgumentException("Skin data is the wrong length, should be 4096");
        }
        if (modelId < 0 || modelId >= 255) {
            throw new IllegalArgumentException("Invalid model id: " + modelId);
        }
        byte[] tmp = new byte[12288];
        SkinConverter.convertToBytes(pixelsARGB8, tmp);
        return CustomSkinGeneric.createV4(modelId, tmp);
    }

    public static IEaglerPlayerSkin loadSkinImageData64x64(byte[] pixelsRGBA8, int modelId) {
        if (pixelsRGBA8.length != 16384) {
            throw new IllegalArgumentException("Skin data is the wrong length, should be 16384");
        }
        if (modelId < 0 || modelId >= 255) {
            throw new IllegalArgumentException("Invalid model id: " + modelId);
        }
        return CustomSkinGeneric.createV3(modelId, pixelsRGBA8);
    }

    public static IEaglerPlayerSkin loadSkinImageData64x64Eagler(byte[] pixelsEagler, int modelId) {
        if (pixelsEagler.length != 12288) {
            throw new IllegalArgumentException("Skin data is the wrong length, should be 12288");
        }
        if (modelId < 0 || modelId >= 255) {
            throw new IllegalArgumentException("Invalid model id: " + modelId);
        }
        return CustomSkinGeneric.createV4(modelId, pixelsEagler);
    }

    public static IEaglerPlayerSkin loadSkinImageData64x32(int[] pixelsARGB8, int modelId) {
        if (pixelsARGB8.length != 2048) {
            throw new IllegalArgumentException("Skin data is the wrong length, should be 2048");
        }
        if (modelId < 0 || modelId >= 255) {
            throw new IllegalArgumentException("Invalid model id: " + modelId);
        }
        byte[] tmp = new byte[12288];
        SkinConverter.convert64x32To64x64(pixelsARGB8, tmp);
        return CustomSkinGeneric.createV4(modelId, tmp);
    }

    public static IEaglerPlayerSkin loadSkinImageData(BufferedImage image, int modelId) {
        if (image == null) {
            throw new IllegalArgumentException("Image is null");
        }
        if (image.getWidth() == 64) {
            if (image.getHeight() == 64) {
                int[] tmp = new int[4096];
                image.getRGB(0, 0, 64, 64, tmp, 0, 64);
                return SkinImageLoaderImpl.loadSkinImageData64x64(tmp, modelId);
            }
            if (image.getHeight() == 32) {
                int[] tmp = new int[2048];
                image.getRGB(0, 0, 64, 32, tmp, 0, 64);
                return SkinImageLoaderImpl.loadSkinImageData64x32(tmp, modelId);
            }
        }
        throw new IllegalArgumentException("Image is the wrong size, should be 64x64 or 64x32");
    }

    public static IEaglerPlayerSkin loadSkinImageData(InputStream inputStream, int modelId) throws IOException {
        if (modelId < 0 || modelId >= 255) {
            throw new IllegalArgumentException("Invalid model id: " + modelId);
        }
        BufferedImage img = ImageIO.read(inputStream);
        if (img == null) {
            throw new IOException("Could not decode image from input stream");
        }
        return SkinImageLoaderImpl.loadSkinImageData(img, modelId);
    }

    public static IEaglerPlayerSkin loadSkinImageData(File imageFile, int modelId) throws IOException {
        if (modelId < 0 || modelId >= 255) {
            throw new IllegalArgumentException("Invalid model id: " + modelId);
        }
        try (FileInputStream is = new FileInputStream(imageFile);){
            IEaglerPlayerSkin iEaglerPlayerSkin = SkinImageLoaderImpl.loadSkinImageData(is, modelId);
            return iEaglerPlayerSkin;
        }
    }

    public static IEaglerPlayerCape loadCapeImageData64x32(int[] pixelsARGB8) {
        if (pixelsARGB8.length != 2048) {
            throw new IllegalArgumentException("Cape data is the wrong length, should be 2048");
        }
        byte[] tmp = new byte[1173];
        SkinConverter.convertCape64x32RGBAto23x17RGB(pixelsARGB8, tmp);
        return new CustomCapeGeneric(tmp);
    }

    public static IEaglerPlayerCape loadCapeImageData32x32(int[] pixelsARGB8) {
        if (pixelsARGB8.length != 1024) {
            throw new IllegalArgumentException("Cape data is the wrong length, should be 1024");
        }
        byte[] tmp = new byte[1173];
        SkinConverterExt.convertCape32x32ARGBto23x17RGB(pixelsARGB8, tmp);
        return new CustomCapeGeneric(tmp);
    }

    public static IEaglerPlayerCape loadCapeImageData32x32(byte[] pixelsRGBA8) {
        if (pixelsRGBA8.length != 4096) {
            throw new IllegalArgumentException("Cape data is the wrong length, should be 4096");
        }
        byte[] tmp = new byte[1173];
        SkinConverterExt.convertCape32x32ABGRto23x17RGB(pixelsRGBA8, tmp);
        return new CustomCapeGeneric(tmp);
    }

    public static IEaglerPlayerCape loadCapeImageData23x17Eagler(byte[] pixelsEagler) {
        if (pixelsEagler.length != 1173) {
            throw new IllegalArgumentException("Cape data is the wrong length, should be 1173");
        }
        return new CustomCapeGeneric(pixelsEagler);
    }

    public static IEaglerPlayerCape loadCapeImageData(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("Image is null");
        }
        if (image.getHeight() == 32) {
            if (image.getWidth() == 64) {
                int[] tmp = new int[2048];
                image.getRGB(0, 0, 64, 32, tmp, 0, 64);
                return SkinImageLoaderImpl.loadCapeImageData64x32(tmp);
            }
            if (image.getWidth() == 32) {
                int[] tmp = new int[1024];
                image.getRGB(0, 0, 32, 32, tmp, 0, 32);
                return SkinImageLoaderImpl.loadCapeImageData32x32(tmp);
            }
        }
        throw new IllegalArgumentException("Image is the wrong size, should be 64x32 or 32x32");
    }

    public static IEaglerPlayerCape loadCapeImageData(InputStream inputStream) throws IOException {
        BufferedImage img = ImageIO.read(inputStream);
        if (img == null) {
            throw new IOException("Could not decode image from input stream");
        }
        return SkinImageLoaderImpl.loadCapeImageData(img);
    }

    public static IEaglerPlayerCape loadCapeImageData(File imageFile) throws IOException {
        try (FileInputStream is = new FileInputStream(imageFile);){
            IEaglerPlayerCape iEaglerPlayerCape = SkinImageLoaderImpl.loadCapeImageData(is);
            return iEaglerPlayerCape;
        }
    }
}

