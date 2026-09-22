/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.electronwill.nightconfig.core.CommentedConfig
 */
package net.lax1dude.eaglercraft.backend.server.config.nightconfig;

import com.electronwill.nightconfig.core.CommentedConfig;
import java.util.ArrayList;
import java.util.List;
import net.lax1dude.eaglercraft.backend.server.config.IEaglerConfList;
import net.lax1dude.eaglercraft.backend.server.config.IEaglerConfSection;
import net.lax1dude.eaglercraft.backend.server.config.nightconfig.NightConfigBase;
import net.lax1dude.eaglercraft.backend.server.config.nightconfig.NightConfigLoader;
import net.lax1dude.eaglercraft.backend.server.config.nightconfig.NightConfigSection;

public class NightConfigList
implements IEaglerConfList {
    private final NightConfigBase owner;
    private final List<Object> data;
    private final IContext commentSetter;
    private final boolean exists;
    private boolean initialized;

    public NightConfigList(NightConfigBase owner, List<Object> list, IContext commentSetter, boolean exists) {
        this.owner = owner;
        this.data = list;
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
        this.commentSetter.setComment(NightConfigLoader.createComment(comment));
        this.owner.modified = true;
        this.initialized = true;
    }

    @Override
    public IEaglerConfSection appendSection() {
        CommentedConfig conf = this.commentSetter.genSection();
        this.data.add(conf);
        this.owner.modified = true;
        this.initialized = true;
        return new NightConfigSection(this.owner, conf, null, false);
    }

    @Override
    public IEaglerConfList appendList() {
        ArrayList<Object> list = new ArrayList<Object>();
        this.data.add(list);
        this.owner.modified = true;
        this.initialized = true;
        return new NightConfigList(this.owner, list, new IContext(){

            @Override
            public void setComment(String comment) {
            }

            @Override
            public CommentedConfig genSection() {
                return NightConfigList.this.commentSetter.genSection();
            }
        }, false);
    }

    @Override
    public void appendInteger(int value) {
        this.data.add(value);
        this.owner.modified = true;
        this.initialized = true;
    }

    @Override
    public void appendString(String string) {
        this.data.add(string);
        this.owner.modified = true;
        this.initialized = true;
    }

    @Override
    public int getLength() {
        return this.data.size();
    }

    @Override
    public IEaglerConfSection getIfSection(int index) {
        if (index < 0 || index >= this.data.size()) {
            return null;
        }
        Object val = this.data.get(index);
        return val instanceof CommentedConfig ? new NightConfigSection(this.owner, (CommentedConfig)val, null, true) : null;
    }

    @Override
    public IEaglerConfList getIfList(int index) {
        if (index < 0 || index >= this.data.size()) {
            return null;
        }
        Object val = this.data.get(index);
        return val instanceof List ? new NightConfigList(this.owner, (List)val, new IContext(){

            @Override
            public void setComment(String comment) {
            }

            @Override
            public CommentedConfig genSection() {
                return NightConfigList.this.commentSetter.genSection();
            }
        }, true) : null;
    }

    @Override
    public boolean isInteger(int index) {
        if (index < 0 || index >= this.data.size()) {
            return false;
        }
        return this.data.get(index) instanceof Number;
    }

    @Override
    public int getIfInteger(int index, int defaultVal) {
        if (index < 0 || index >= this.data.size()) {
            return defaultVal;
        }
        Object val = this.data.get(index);
        return val instanceof Number ? ((Number)val).intValue() : defaultVal;
    }

    @Override
    public boolean isString(int index) {
        if (index < 0 || index >= this.data.size()) {
            return false;
        }
        return this.data.get(index) instanceof String;
    }

    @Override
    public String getIfString(int index, String defaultVal) {
        if (index < 0 || index >= this.data.size()) {
            return defaultVal;
        }
        Object val = this.data.get(index);
        return val instanceof String ? (String)val : defaultVal;
    }

    public static interface IContext {
        public void setComment(String var1);

        public CommentedConfig genSection();
    }
}

