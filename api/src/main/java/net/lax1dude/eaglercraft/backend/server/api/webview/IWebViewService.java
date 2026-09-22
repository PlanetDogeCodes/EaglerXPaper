/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nonnull
 *  javax.annotation.Nullable
 */
package net.lax1dude.eaglercraft.backend.server.api.webview;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.lax1dude.eaglercraft.backend.server.api.IEaglerPlayer;
import net.lax1dude.eaglercraft.backend.server.api.IEaglerXServerAPI;
import net.lax1dude.eaglercraft.backend.server.api.SHA1Sum;
import net.lax1dude.eaglercraft.backend.server.api.pause_menu.IPauseMenuService;
import net.lax1dude.eaglercraft.backend.server.api.webview.ITemplateLoader;
import net.lax1dude.eaglercraft.backend.server.api.webview.ITranslationProvider;
import net.lax1dude.eaglercraft.backend.server.api.webview.IWebViewBlob;
import net.lax1dude.eaglercraft.backend.server.api.webview.IWebViewBlobBuilder;
import net.lax1dude.eaglercraft.backend.server.api.webview.IWebViewManager;
import net.lax1dude.eaglercraft.backend.server.api.webview.IWebViewProvider;

public interface IWebViewService<PlayerObject> {
    @Nonnull
    public IEaglerXServerAPI<PlayerObject> getServerAPI();

    @Nullable
    default public IWebViewManager<PlayerObject> getWebViewManager(@Nonnull PlayerObject player) {
        IEaglerPlayer<PlayerObject> eagPlayer = this.getServerAPI().getEaglerPlayer(player);
        return eagPlayer != null ? eagPlayer.getWebViewManager() : null;
    }

    @Nonnull
    public IPauseMenuService<PlayerObject> getPauseMenuService();

    @Nonnull
    public IWebViewProvider<PlayerObject> getDefaultProvider();

    @Nonnull
    public IWebViewBlobBuilder<OutputStream> createWebViewBlobBuilderStream();

    @Nonnull
    public IWebViewBlobBuilder<Writer> createWebViewBlobBuilderWriter();

    @Nonnull
    default public IWebViewBlob createWebViewBlob(@Nonnull CharSequence markupIn) {
        if (markupIn == null) {
            throw new NullPointerException("markupIn");
        }
        IWebViewBlobBuilder<Writer> builder = this.createWebViewBlobBuilderWriter();
        try (Writer os = builder.stream();){
            os.append(markupIn);
        }
        catch (IOException ex) {
            throw new RuntimeException("Unexpected IOException thrown", ex);
        }
        return builder.build();
    }

    @Nonnull
    default public IWebViewBlob createWebViewBlob(@Nonnull byte[] bytesIn) {
        if (bytesIn == null) {
            throw new NullPointerException("bytesIn");
        }
        IWebViewBlobBuilder<OutputStream> builder = this.createWebViewBlobBuilderStream();
        try (OutputStream os = builder.stream();){
            os.write(bytesIn);
        }
        catch (IOException ex) {
            throw new RuntimeException("Unexpected IOException thrown", ex);
        }
        return builder.build();
    }

    @Nonnull
    default public IWebViewBlob createWebViewBlob(@Nonnull InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new NullPointerException("inputStream");
        }
        IWebViewBlobBuilder<OutputStream> builder = this.createWebViewBlobBuilderStream();
        try (OutputStream os = builder.stream();){
            int i;
            byte[] transferBuffer = new byte[8192];
            while ((i = inputStream.read(transferBuffer)) != -1) {
                os.write(transferBuffer, 0, i);
            }
        }
        return builder.build();
    }

    @Nonnull
    default public IWebViewBlob createWebViewBlob(@Nonnull File file) throws IOException {
        if (file == null) {
            throw new NullPointerException("file");
        }
        try (FileInputStream is = new FileInputStream(file);){
            IWebViewBlob iWebViewBlob = this.createWebViewBlob(is);
            return iWebViewBlob;
        }
    }

    @Nonnull
    public SHA1Sum registerGlobalBlob(@Nonnull IWebViewBlob var1);

    default public void unregisterGlobalBlob(@Nonnull IWebViewBlob blob) {
        this.unregisterGlobalBlob(blob.getHash());
    }

    public void unregisterGlobalBlob(@Nonnull SHA1Sum var1);

    public void registerBlobAlias(@Nonnull String var1, @Nonnull SHA1Sum var2);

    public void unregisterBlobAlias(@Nonnull String var1);

    @Nullable
    public SHA1Sum getBlobFromAlias(@Nonnull String var1);

    @Nullable
    public Map<String, String> getTemplateGlobals();

    public void setTemplateGlobal(@Nonnull String var1, @Nullable String var2);

    public void removeTemplateGlobal(@Nonnull String var1);

    @Nonnull
    default public ITemplateLoader createTemplateLoader() {
        return this.createTemplateLoader(null, null, null, false);
    }

    @Nonnull
    default public ITemplateLoader createTemplateLoader(@Nonnull File baseDir) {
        if (baseDir == null) {
            throw new NullPointerException("baseDir");
        }
        return this.createTemplateLoader(baseDir, null, null, false);
    }

    @Nonnull
    default public ITemplateLoader createTemplateLoader(@Nonnull File baseDir, boolean allowEvalMacro) {
        if (baseDir == null) {
            throw new NullPointerException("baseDir");
        }
        return this.createTemplateLoader(baseDir, null, null, allowEvalMacro);
    }

    @Nonnull
    default public ITemplateLoader createTemplateLoader(@Nonnull File baseDir, @Nonnull Map<String, String> variables, boolean allowEvalMacro) {
        if (baseDir == null) {
            throw new NullPointerException("baseDir");
        }
        if (variables == null) {
            throw new NullPointerException("variables");
        }
        return this.createTemplateLoader(baseDir, variables, null, allowEvalMacro);
    }

    @Nonnull
    default public ITemplateLoader createTemplateLoader(@Nonnull File baseDir, @Nonnull ITranslationProvider translations, boolean allowEvalMacro) {
        if (baseDir == null) {
            throw new NullPointerException("baseDir");
        }
        if (translations == null) {
            throw new NullPointerException("translations");
        }
        return this.createTemplateLoader(baseDir, null, translations, allowEvalMacro);
    }

    @Nonnull
    public ITemplateLoader createTemplateLoader(@Nullable File var1, @Nullable Map<String, String> var2, @Nullable ITranslationProvider var3, boolean var4);
}

