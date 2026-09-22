/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonParseException
 *  net.md_5.bungee.api.chat.BaseComponent
 *  net.md_5.bungee.api.chat.ClickEvent$Action
 *  net.md_5.bungee.api.chat.HoverEvent
 *  net.md_5.bungee.api.chat.TextComponent
 *  net.md_5.bungee.api.chat.TranslatableComponent
 *  net.md_5.bungee.chat.ComponentSerializer
 */
package net.lax1dude.eaglercraft.backend.server.bungee.chat;

import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformComponentBuilder;
import net.lax1dude.eaglercraft.backend.server.adapter.IPlatformComponentHelper;
import net.lax1dude.eaglercraft.backend.server.bungee.chat.BungeeComponentBuilder;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import net.md_5.bungee.chat.ComponentSerializer;

public class BungeeComponentHelper
implements IPlatformComponentHelper {
    public static final boolean LEGACY_FLAG_SUPPORT;
    public static final boolean LEGACY_COMPONENT_OK;
    public static final ClickEvent.Action CLICK_ACTION_COPY_TO_CLIPBOARD;
    private final BungeeComponentBuilder builder = new BungeeComponentBuilder();
    private final Object kickAlreadyPlayer;

    public BungeeComponentHelper(Object kickAlreadyPlayer) {
        this.kickAlreadyPlayer = kickAlreadyPlayer;
    }

    @Override
    public IPlatformComponentBuilder builder() {
        return this.builder;
    }

    @Override
    public Class<?> getComponentType() {
        return BaseComponent.class;
    }

    @Override
    public Object getStandardKickAlreadyPlaying() {
        return this.kickAlreadyPlayer;
    }

    @Override
    public String serializeLegacySection(Object component) {
        if (!(component instanceof BaseComponent)) {
            throw new IllegalArgumentException("Not a component!");
        }
        BaseComponent bc = (BaseComponent)component;
        if (LEGACY_COMPONENT_OK) {
            return bc.toLegacyText();
        }
        List<BaseComponent> bruh = bc.getExtra();
        if (bruh != null) {
            bc = bc.duplicate();
            bruh = new ArrayList<BaseComponent>(bruh);
            bc.setExtra(Collections.emptyList());
        }
        String lt = bc.toLegacyText();
        if (bc.getColorRaw() == null && (lt.startsWith("\u00a7f") || lt.startsWith("\u00a7F"))) {
            lt = lt.substring(2);
        }
        if (bruh == null) {
            return lt;
        }
        StringBuilder res = new StringBuilder(lt);
        for (BaseComponent bc2 : bruh) {
            res.append(this.serializeLegacySection(bc2));
        }
        return res.toString();
    }

    @Override
    public String serializePlainText(Object component) {
        return ((BaseComponent)component).toPlainText();
    }

    @Override
    public String serializeGenericJSON(Object component) {
        return ComponentSerializer.toString((BaseComponent)((BaseComponent)component));
    }

    @Override
    public String serializeLegacyJSON(Object component) {
        BaseComponent bc = (BaseComponent)component;
        if (LEGACY_FLAG_SUPPORT) {
            BungeeComponentHelper.setLegacyHover(bc, true);
        }
        return ComponentSerializer.toString((BaseComponent)bc);
    }

    @Override
    public String serializeModernJSON(Object component) {
        BaseComponent bc = (BaseComponent)component;
        if (LEGACY_FLAG_SUPPORT) {
            BungeeComponentHelper.setLegacyHover(bc, false);
        }
        return ComponentSerializer.toString((BaseComponent)bc);
    }

    public static void setLegacyHover(BaseComponent component, boolean legacy) {
        TranslatableComponent cmp;
        List with;
        List extra;
        HoverEvent evt = component.getHoverEvent();
        if (evt != null) {
            evt.setLegacy(legacy);
        }
        if ((extra = component.getExtra()) != null) {
            int l = extra.size();
            for (int i = 0; i < l; ++i) {
                BungeeComponentHelper.setLegacyHover((BaseComponent)extra.get(0), legacy);
            }
        }
        if (component instanceof TranslatableComponent && (with = (cmp = (TranslatableComponent)component).getWith()) != null) {
            int l = with.size();
            for (int i = 0; i < l; ++i) {
                BungeeComponentHelper.setLegacyHover((BaseComponent)with.get(0), legacy);
            }
        }
    }

    @Override
    public Object parseGenericJSON(String json) throws IllegalArgumentException {
        BaseComponent ret;
        BaseComponent[] components;
        try {
            components = ComponentSerializer.parse((String)json);
        }
        catch (IllegalArgumentException ex) {
            throw ex;
        }
        catch (JsonParseException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex.getCause());
        }
        catch (Exception ex) {
            throw new IllegalArgumentException("Could not parse JSON chat component", ex);
        }
        if (components.length == 1) {
            ret = components[0];
        } else if (components.length == 0) {
            ret = new TextComponent();
        } else {
            ret = components[0];
            for (int i = 1; i < components.length; ++i) {
                ret.addExtra(components[i]);
            }
        }
        if (this.serializeLegacySection(ret).equals(json)) {
            throw new IllegalArgumentException("Could not parse JSON chat component", new Exception("Not a valid JSON component"));
        }
        return ret;
    }

    @Override
    public Object parseLegacyJSON(String json) throws IllegalArgumentException {
        return this.parseGenericJSON(json);
    }

    @Override
    public Object parseModernJSON(String json) throws IllegalArgumentException {
        return this.parseGenericJSON(json);
    }

    static {
        ClickEvent.Action action;
        boolean b;
        try {
            BaseComponent.class.getMethod("setLegacy", Boolean.TYPE);
            b = true;
        }
        catch (NoSuchMethodException | SecurityException ex) {
            b = false;
        }
        LEGACY_FLAG_SUPPORT = b;
        try {
            action = ClickEvent.Action.valueOf((String)"COPY_TO_CLIPBOARD");
        }
        catch (IllegalArgumentException ex) {
            action = null;
        }
        CLICK_ACTION_COPY_TO_CLIPBOARD = action;
        LEGACY_COMPONENT_OK = ComponentSerializer.parse((String)"\"e\"")[0].toLegacyText().equals("e");
    }
}

