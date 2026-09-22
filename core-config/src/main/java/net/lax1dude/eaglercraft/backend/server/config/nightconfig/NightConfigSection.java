/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.electronwill.nightconfig.core.CommentedConfig
 *  com.google.common.collect.ImmutableList
 */
package net.lax1dude.eaglercraft.backend.server.config.nightconfig;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.lax1dude.eaglercraft.backend.server.config.IEaglerConfList;
import net.lax1dude.eaglercraft.backend.server.config.IEaglerConfSection;
import net.lax1dude.eaglercraft.backend.server.config.nightconfig.NightConfigBase;
import net.lax1dude.eaglercraft.backend.server.config.nightconfig.NightConfigList;
import net.lax1dude.eaglercraft.backend.server.config.nightconfig.NightConfigLoader;

public class NightConfigSection
implements IEaglerConfSection {
    private final NightConfigBase owner;
    final CommentedConfig config;
    private final Consumer<String> commentSetter;
    private final boolean exists;
    private boolean initialized;

    public NightConfigSection(NightConfigBase owner, CommentedConfig config, Consumer<String> commentSetter, boolean exists) {
        this.owner = owner;
        this.config = config;
        this.commentSetter = commentSetter;
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
        if (this.commentSetter != null) {
            this.commentSetter.accept(NightConfigLoader.createComment(comment));
            this.owner.modified = true;
        }
    }

    @Override
    public IEaglerConfSection getIfSection(String name) {
        List<String> k = Collections.singletonList(name);
        Object o = this.config.get(k);
        return o instanceof CommentedConfig ? new NightConfigSection(this.owner, (CommentedConfig)o, str -> this.config.setComment(k, str), true) : null;
    }

    @Override
    public IEaglerConfSection getSection(String name) {
        List<String> k = Collections.singletonList(name);
        Object o = this.config.get(k);
        if (o instanceof CommentedConfig) {
            CommentedConfig o2 = (CommentedConfig)o;
            return new NightConfigSection(this.owner, o2, str -> this.config.setComment(k, str), true);
        }
        CommentedConfig sub = this.config.createSubConfig();
        this.config.set(k, (Object)sub);
        this.owner.modified = true;
        this.initialized = true;
        return new NightConfigSection(this.owner, sub, str -> this.config.setComment(k, str), false);
    }

    private NightConfigList.IContext bindListContext(final List<String> key) {
        return new NightConfigList.IContext(){

            @Override
            public void setComment(String comment) {
                NightConfigSection.this.config.setComment(key, comment);
            }

            @Override
            public CommentedConfig genSection() {
                return NightConfigSection.this.config.createSubConfig();
            }
        };
    }

    @Override
    public IEaglerConfList getIfList(String name) {
        List<String> k = Collections.singletonList(name);
        Object o = this.config.get(k);
        return o instanceof List ? new NightConfigList(this.owner, (List)o, this.bindListContext(k), true) : null;
    }

    @Override
    public IEaglerConfList getList(String name) {
        List<String> k = Collections.singletonList(name);
        Object o = this.config.get(k);
        if (o instanceof List) {
            return new NightConfigList(this.owner, (List)o, this.bindListContext(k), true);
        }
        ArrayList<Object> sub = new ArrayList<Object>();
        this.config.set(k, sub);
        this.owner.modified = true;
        this.initialized = true;
        return new NightConfigList(this.owner, sub, this.bindListContext(k), false);
    }

    @Override
    public List<String> getKeys() {
        return ImmutableList.copyOf(this.config.valueMap().keySet());
    }

    @Override
    public boolean isBoolean(String name) {
        List<String> k = Collections.singletonList(name);
        Object o = this.config.get(k);
        return o instanceof Boolean;
    }

    @Override
    public boolean getBoolean(String name) {
        List<String> k = Collections.singletonList(name);
        Object o = this.config.get(k);
        return o instanceof Boolean && (Boolean)o != false;
    }

    @Override
    public boolean getBoolean(String name, boolean defaultValue, String comment) {
        List<String> k = Collections.singletonList(name);
        Object o = this.config.get(k);
        if (o instanceof Boolean) {
            Boolean o2 = (Boolean)o;
            return o2;
        }
        this.config.set(k, (Object)defaultValue);
        if (comment != null) {
            this.config.setComment(k, NightConfigLoader.createComment(comment));
        }
        this.owner.modified = true;
        this.initialized = true;
        return defaultValue;
    }

    @Override
    public boolean getBoolean(String name, Supplier<Boolean> defaultValue, String comment) {
        List<String> k = Collections.singletonList(name);
        Object o = this.config.get(k);
        if (o instanceof Boolean) {
            Boolean o2 = (Boolean)o;
            return o2;
        }
        Boolean d = defaultValue.get();
        this.config.set(k, (Object)d);
        if (comment != null) {
            this.config.setComment(k, NightConfigLoader.createComment(comment));
        }
        this.owner.modified = true;
        this.initialized = true;
        return d;
    }

    @Override
    public boolean isInteger(String name) {
        List<String> k = Collections.singletonList(name);
        Object o = this.config.get(k);
        return o instanceof Number;
    }

    @Override
    public int getInteger(String name, int defaultValue, String comment) {
        List<String> k = Collections.singletonList(name);
        Object o = this.config.get(k);
        if (o instanceof Number) {
            Number o2 = (Number)o;
            return o2.intValue();
        }
        this.config.set(k, (Object)defaultValue);
        if (comment != null) {
            this.config.setComment(k, NightConfigLoader.createComment(comment));
        }
        this.owner.modified = true;
        this.initialized = true;
        return defaultValue;
    }

    @Override
    public int getInteger(String name, Supplier<Integer> defaultValue, String comment) {
        List<String> k = Collections.singletonList(name);
        Object o = this.config.get(k);
        if (o instanceof Number) {
            Number o2 = (Number)o;
            return o2.intValue();
        }
        Integer d = defaultValue.get();
        this.config.set(k, (Object)d);
        if (comment != null) {
            this.config.setComment(k, NightConfigLoader.createComment(comment));
        }
        this.owner.modified = true;
        this.initialized = true;
        return d;
    }

    @Override
    public boolean isString(String name) {
        List<String> k = Collections.singletonList(name);
        Object o = this.config.get(k);
        return o instanceof String;
    }

    @Override
    public String getIfString(String name) {
        List<String> k = Collections.singletonList(name);
        Object o = this.config.get(k);
        return o instanceof String ? (String)o : null;
    }

    @Override
    public String getString(String name, String defaultValue, String comment) {
        List<String> k = Collections.singletonList(name);
        Object o = this.config.get(k);
        if (o instanceof String) {
            String str = (String)o;
            return str;
        }
        this.config.set(k, (Object)defaultValue);
        if (comment != null) {
            this.config.setComment(k, NightConfigLoader.createComment(comment));
        }
        this.owner.modified = true;
        this.initialized = true;
        return defaultValue;
    }

    @Override
    public String getString(String name, Supplier<String> defaultValue, String comment) {
        List<String> k = Collections.singletonList(name);
        Object o = this.config.get(k);
        if (o instanceof String) {
            String str = (String)o;
            return str;
        }
        String d = defaultValue.get();
        this.config.set(k, (Object)d);
        if (comment != null) {
            this.config.setComment(k, NightConfigLoader.createComment(comment));
        }
        this.owner.modified = true;
        this.initialized = true;
        return d;
    }
}

