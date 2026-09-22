/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.ImmutableList
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 */
package net.lax1dude.eaglercraft.backend.server.config.gson;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.lax1dude.eaglercraft.backend.server.config.IEaglerConfList;
import net.lax1dude.eaglercraft.backend.server.config.IEaglerConfSection;
import net.lax1dude.eaglercraft.backend.server.config.gson.GSONConfigBase;
import net.lax1dude.eaglercraft.backend.server.config.gson.GSONConfigList;

public class GSONConfigSection
implements IEaglerConfSection {
    private final GSONConfigBase owner;
    final JsonObject json;
    private final boolean exists;
    private boolean initialized;

    protected GSONConfigSection(GSONConfigBase owner, JsonObject json, boolean exists) {
        this.owner = owner;
        this.json = json;
        this.exists = this.initialized = exists;
    }

    @Override
    public boolean exists() {
        return this.exists;
    }

    @Override
    public boolean initialized() {
        return this.initialized;
    }

    @Override
    public void setComment(String comment) {
    }

    @Override
    public IEaglerConfSection getIfSection(String name) {
        JsonElement el = this.json.get(name);
        if (el != null && el.isJsonObject()) {
            return new GSONConfigSection(this.owner, el.getAsJsonObject(), true);
        }
        return null;
    }

    @Override
    public IEaglerConfSection getSection(String name) {
        JsonElement el = this.json.get(name);
        if (el != null && el.isJsonObject()) {
            return new GSONConfigSection(this.owner, el.getAsJsonObject(), true);
        }
        JsonObject obj = new JsonObject();
        this.json.add(name, (JsonElement)obj);
        this.owner.modified = true;
        this.initialized = true;
        return new GSONConfigSection(this.owner, obj, false);
    }

    @Override
    public IEaglerConfList getIfList(String name) {
        JsonElement el = this.json.get(name);
        if (el != null && el.isJsonArray()) {
            return new GSONConfigList(this.owner, el.getAsJsonArray(), true);
        }
        return null;
    }

    @Override
    public IEaglerConfList getList(String name) {
        JsonElement el = this.json.get(name);
        if (el != null && el.isJsonArray()) {
            return new GSONConfigList(this.owner, el.getAsJsonArray(), true);
        }
        JsonArray obj = new JsonArray();
        this.json.add(name, (JsonElement)obj);
        this.owner.modified = true;
        this.initialized = true;
        return new GSONConfigList(this.owner, obj, false);
    }

    @Override
    public List<String> getKeys() {
        return ImmutableList.copyOf((Collection)this.json.entrySet().stream().map(Map.Entry::getKey).collect(Collectors.toList()));
    }

    @Override
    public boolean isBoolean(String name) {
        JsonElement el = this.json.get(name);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean();
    }

    @Override
    public boolean getBoolean(String name) {
        JsonElement el = this.json.get(name);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean() && el.getAsBoolean();
    }

    @Override
    public boolean getBoolean(String name, boolean defaultValue, String comment) {
        JsonElement el = this.json.get(name);
        if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) {
            return el.getAsBoolean();
        }
        this.json.addProperty(name, Boolean.valueOf(defaultValue));
        this.owner.modified = true;
        this.initialized = true;
        return defaultValue;
    }

    @Override
    public boolean getBoolean(String name, Supplier<Boolean> defaultValue, String comment) {
        JsonElement el = this.json.get(name);
        if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) {
            return el.getAsBoolean();
        }
        boolean b = defaultValue.get();
        this.json.addProperty(name, Boolean.valueOf(b));
        this.owner.modified = true;
        this.initialized = true;
        return b;
    }

    @Override
    public boolean isInteger(String name) {
        JsonElement el = this.json.get(name);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber();
    }

    @Override
    public int getInteger(String name, int defaultValue, String comment) {
        JsonElement el = this.json.get(name);
        if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
            return el.getAsJsonPrimitive().getAsInt();
        }
        this.json.addProperty(name, (Number)defaultValue);
        this.owner.modified = true;
        this.initialized = true;
        return defaultValue;
    }

    @Override
    public int getInteger(String name, Supplier<Integer> defaultValue, String comment) {
        JsonElement el = this.json.get(name);
        if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
            return el.getAsJsonPrimitive().getAsInt();
        }
        Integer i = defaultValue.get();
        this.json.addProperty(name, (Number)i);
        this.owner.modified = true;
        this.initialized = true;
        return i;
    }

    @Override
    public boolean isString(String name) {
        JsonElement el = this.json.get(name);
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString();
    }

    @Override
    public String getIfString(String name) {
        JsonElement el = this.json.get(name);
        if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            return el.getAsString();
        }
        return null;
    }

    @Override
    public String getString(String name, String defaultValue, String comment) {
        JsonElement el = this.json.get(name);
        if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            return el.getAsString();
        }
        this.json.addProperty(name, defaultValue);
        this.owner.modified = true;
        this.initialized = true;
        return defaultValue;
    }

    @Override
    public String getString(String name, Supplier<String> defaultValue, String comment) {
        JsonElement el = this.json.get(name);
        if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            return el.getAsString();
        }
        String d = defaultValue.get();
        this.json.addProperty(name, d);
        this.owner.modified = true;
        this.initialized = true;
        return d;
    }
}

