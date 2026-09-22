/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.cache.Cache
 *  com.google.common.cache.CacheBuilder
 */
package net.lax1dude.eaglercraft.backend.server.base.skins;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import net.lax1dude.eaglercraft.backend.server.api.skins.EnumSkinModel;
import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerCape;
import net.lax1dude.eaglercraft.backend.server.api.skins.IEaglerPlayerSkin;
import net.lax1dude.eaglercraft.backend.server.api.skins.ISkinImageLoader;
import net.lax1dude.eaglercraft.backend.server.base.skins.SkinImageLoaderCacheOff;
import net.lax1dude.eaglercraft.backend.server.base.skins.SkinImageLoaderImpl;

class SkinImageLoaderCacheOn
extends SkinImageLoaderCacheOff {
    static final ISkinImageLoader INSTANCE = new SkinImageLoaderCacheOn();
    private static final Cache<File, IEaglerPlayerSkin> cachedSkinFiles = CacheBuilder.newBuilder().weakValues().build();
    private static final Cache<File, IEaglerPlayerCape> cachedCapeFiles = CacheBuilder.newBuilder().weakValues().build();

    SkinImageLoaderCacheOn() {
    }

    @Override
    public IEaglerPlayerSkin loadSkinImageData(File imageFile, EnumSkinModel modelId) throws IOException {
        return this.loadSkinImageData(imageFile, modelId.getId());
    }

    @Override
    public IEaglerPlayerSkin loadSkinImageData(File imageFile, int modelId) throws IOException {
        if (modelId < 0 || modelId >= 255) {
            throw new IllegalArgumentException("Invalid model id: " + modelId);
        }
        try {
            return SkinImageLoaderImpl.rewriteCustomSkinModelId((IEaglerPlayerSkin)cachedSkinFiles.get(imageFile, () -> SkinImageLoaderImpl.loadSkinImageData(imageFile, (modelId & 0x7F) == 1 ? 1 : 0)), modelId);
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException)cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException)cause;
            }
            throw new RuntimeException("Uncaught exception in lambda", cause);
        }
    }

    @Override
    public IEaglerPlayerCape loadCapeImageData(File imageFile) throws IOException {
        try {
            return (IEaglerPlayerCape)cachedCapeFiles.get(imageFile, () -> SkinImageLoaderImpl.loadCapeImageData(imageFile));
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException)cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException)cause;
            }
            throw new RuntimeException("Uncaught exception in lambda", cause);
        }
    }
}

