/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.ImmutableList
 */
package net.lax1dude.eaglercraft.backend.server.config.docsutil;

import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.lax1dude.eaglercraft.backend.server.config.IEaglerConfList;
import net.lax1dude.eaglercraft.backend.server.config.IEaglerConfSection;
import net.lax1dude.eaglercraft.backend.server.config.IRandomSupplier;
import net.lax1dude.eaglercraft.backend.server.config.docsutil.DocsList;
import net.lax1dude.eaglercraft.backend.server.config.docsutil.DocsValue;

class DocsSection
implements IEaglerConfSection {
    final Map<String, Object> entries = new HashMap<String, Object>();
    private boolean initialized;
    String comment;

    DocsSection() {
    }

    @Override
    public boolean exists() {
        return false;
    }

    @Override
    public boolean initialized() {
        return this.initialized;
    }

    @Override
    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public IEaglerConfSection getIfSection(String name) {
        Object obj = this.entries.get(name);
        if (obj instanceof DocsSection) {
            DocsSection s = (DocsSection)obj;
            return s;
        }
        return null;
    }

    @Override
    public IEaglerConfSection getSection(String name) {
        if (this.entries.get(name) instanceof DocsSection) {
            DocsSection s = (DocsSection)this.entries.get(name);
            return s;
        }
        DocsSection sec = new DocsSection();
        this.entries.put(name, sec);
        this.initialized = true;
        return sec;
    }

    @Override
    public IEaglerConfList getIfList(String name) {
        Object obj = this.entries.get(name);
        if (obj instanceof DocsList) {
            DocsList l = (DocsList)obj;
            return l;
        }
        return null;
    }

    @Override
    public IEaglerConfList getList(String name) {
        if (this.entries.get(name) instanceof DocsList) {
            DocsList l = (DocsList)this.entries.get(name);
            return l;
        }
        DocsList lst = new DocsList();
        this.entries.put(name, lst);
        this.initialized = true;
        return lst;
    }

    @Override
    public List<String> getKeys() {
        return ImmutableList.copyOf(this.entries.keySet());
    }

    @Override
    public boolean isBoolean(String name) {
        if (this.entries.get(name) instanceof DocsValue) {
            DocsValue v = (DocsValue)this.entries.get(name);
            return v.type == DocsValue.Type.BOOL;
        }
        return false;
    }

    @Override
    public boolean getBoolean(String name) {
        Object obj = this.entries.get(name);
        if (obj instanceof DocsValue && ((DocsValue)obj).type == DocsValue.Type.BOOL) {
            DocsValue v = (DocsValue)obj;
            return Boolean.parseBoolean(v.value);
        }
        return false;
    }

    @Override
    public boolean getBoolean(String name, boolean defaultValue, String comment) {
        Object obj = this.entries.get(name);
        if (obj instanceof DocsValue && ((DocsValue)obj).type == DocsValue.Type.BOOL) {
            DocsValue v = (DocsValue)obj;
            return Boolean.parseBoolean(v.value);
        }
        DocsValue val = new DocsValue(DocsValue.Type.BOOL, Boolean.toString(defaultValue), comment, false);
        this.entries.put(name, val);
        this.initialized = true;
        return defaultValue;
    }

    @Override
    public boolean getBoolean(String name, Supplier<Boolean> defaultValue, String comment) {
        Object obj = this.entries.get(name);
        if (obj instanceof DocsValue && ((DocsValue)obj).type == DocsValue.Type.BOOL) {
            DocsValue v = (DocsValue)obj;
            return Boolean.parseBoolean(v.value);
        }
        boolean def = defaultValue.get();
        DocsValue val = new DocsValue(DocsValue.Type.BOOL, Boolean.toString(def), comment, defaultValue instanceof IRandomSupplier);
        this.entries.put(name, val);
        this.initialized = true;
        return def;
    }

    @Override
    public boolean isInteger(String name) {
        if (this.entries.get(name) instanceof DocsValue) {
            DocsValue v = (DocsValue)this.entries.get(name);
            return v.type == DocsValue.Type.INT;
        }
        return false;
    }

    @Override
    public int getInteger(String name, int defaultValue, String comment) {
        Object obj = this.entries.get(name);
        if (obj instanceof DocsValue && ((DocsValue)obj).type == DocsValue.Type.INT) {
            DocsValue v = (DocsValue)obj;
            return Integer.parseInt(v.value);
        }
        DocsValue val = new DocsValue(DocsValue.Type.INT, Integer.toString(defaultValue), comment, false);
        this.entries.put(name, val);
        this.initialized = true;
        return defaultValue;
    }

    @Override
    public int getInteger(String name, Supplier<Integer> defaultValue, String comment) {
        Object obj = this.entries.get(name);
        if (obj instanceof DocsValue && ((DocsValue)obj).type == DocsValue.Type.INT) {
            DocsValue v = (DocsValue)obj;
            return Integer.parseInt(v.value);
        }
        int def = defaultValue.get();
        DocsValue val = new DocsValue(DocsValue.Type.INT, Integer.toString(def), comment, defaultValue instanceof IRandomSupplier);
        this.entries.put(name, val);
        this.initialized = true;
        return def;
    }

    @Override
    public boolean isString(String name) {
        if (this.entries.get(name) instanceof DocsValue) {
            DocsValue v = (DocsValue)this.entries.get(name);
            return v.type == DocsValue.Type.STR;
        }
        return false;
    }

    @Override
    public String getIfString(String name) {
        Object obj = this.entries.get(name);
        if (obj instanceof DocsValue && ((DocsValue)obj).type == DocsValue.Type.STR) {
            DocsValue v = (DocsValue)obj;
            return v.value;
        }
        return null;
    }

    @Override
    public String getString(String name, String defaultValue, String comment) {
        Object obj = this.entries.get(name);
        if (obj instanceof DocsValue && ((DocsValue)obj).type == DocsValue.Type.STR) {
            DocsValue v = (DocsValue)obj;
            return v.value;
        }
        DocsValue val = new DocsValue(DocsValue.Type.STR, defaultValue, comment, false);
        this.entries.put(name, val);
        this.initialized = true;
        return defaultValue;
    }

    @Override
    public String getString(String name, Supplier<String> defaultValue, String comment) {
        Object obj = this.entries.get(name);
        if (obj instanceof DocsValue && ((DocsValue)obj).type == DocsValue.Type.STR) {
            DocsValue v = (DocsValue)obj;
            return v.value;
        }
        String def = defaultValue.get();
        DocsValue val = new DocsValue(DocsValue.Type.STR, def, comment, defaultValue instanceof IRandomSupplier);
        this.entries.put(name, val);
        this.initialized = true;
        return def;
    }
}

