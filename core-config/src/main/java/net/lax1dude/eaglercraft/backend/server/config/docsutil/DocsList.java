/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.config.docsutil;

import java.util.ArrayList;
import java.util.List;
import net.lax1dude.eaglercraft.backend.server.config.IEaglerConfList;
import net.lax1dude.eaglercraft.backend.server.config.IEaglerConfSection;
import net.lax1dude.eaglercraft.backend.server.config.docsutil.DocsSection;
import net.lax1dude.eaglercraft.backend.server.config.docsutil.DocsValue;

class DocsList
implements IEaglerConfList {
    final List<Object> entries = new ArrayList<Object>();
    private boolean initialized;
    String comment;

    DocsList() {
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
    public IEaglerConfSection appendSection() {
        DocsSection object = new DocsSection();
        this.entries.add(object);
        this.initialized = true;
        return object;
    }

    @Override
    public IEaglerConfList appendList() {
        DocsList object = new DocsList();
        this.entries.add(object);
        this.initialized = true;
        return object;
    }

    @Override
    public void appendInteger(int value) {
        DocsValue object = new DocsValue(DocsValue.Type.INT, Integer.toString(value), null, false);
        this.entries.add(object);
        this.initialized = true;
    }

    @Override
    public void appendString(String string) {
        DocsValue object = new DocsValue(DocsValue.Type.STR, string, null, false);
        this.entries.add(object);
        this.initialized = true;
    }

    @Override
    public int getLength() {
        return this.entries.size();
    }

    @Override
    public IEaglerConfSection getIfSection(int index) {
        if (index < 0 || index >= this.entries.size()) {
            return null;
        }
        if (this.entries.get(index) instanceof DocsSection) {
            DocsSection s = (DocsSection)this.entries.get(index);
            return s;
        }
        return null;
    }

    @Override
    public IEaglerConfList getIfList(int index) {
        if (index < 0 || index >= this.entries.size()) {
            return null;
        }
        if (this.entries.get(index) instanceof DocsList) {
            DocsList l = (DocsList)this.entries.get(index);
            return l;
        }
        return null;
    }

    @Override
    public boolean isInteger(int index) {
        if (index < 0 || index >= this.entries.size()) {
            return false;
        }
        if (this.entries.get(index) instanceof DocsValue) {
            DocsValue v = (DocsValue)this.entries.get(index);
            return v.type == DocsValue.Type.INT;
        }
        return false;
    }

    @Override
    public int getIfInteger(int index, int defaultVal) {
        if (index < 0 || index >= this.entries.size()) {
            return defaultVal;
        }
        Object obj = this.entries.get(index);
        if (obj instanceof DocsValue && ((DocsValue)obj).type == DocsValue.Type.INT) {
            DocsValue v = (DocsValue)obj;
            return Integer.parseInt(v.value);
        }
        return defaultVal;
    }

    @Override
    public boolean isString(int index) {
        if (index < 0 || index >= this.entries.size()) {
            return false;
        }
        if (this.entries.get(index) instanceof DocsValue) {
            DocsValue v = (DocsValue)this.entries.get(index);
            return v.type == DocsValue.Type.STR;
        }
        return false;
    }

    @Override
    public String getIfString(int index, String defaultVal) {
        if (index < 0 || index >= this.entries.size()) {
            return defaultVal;
        }
        Object obj = this.entries.get(index);
        if (obj instanceof DocsValue && ((DocsValue)obj).type == DocsValue.Type.STR) {
            DocsValue v = (DocsValue)obj;
            return v.value;
        }
        return defaultVal;
    }
}

