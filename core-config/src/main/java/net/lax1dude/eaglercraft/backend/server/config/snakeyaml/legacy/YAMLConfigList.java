/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.yaml.snakeyaml.nodes.MappingNode
 *  org.yaml.snakeyaml.nodes.Node
 *  org.yaml.snakeyaml.nodes.NodeTuple
 *  org.yaml.snakeyaml.nodes.ScalarNode
 *  org.yaml.snakeyaml.nodes.SequenceNode
 *  org.yaml.snakeyaml.nodes.Tag
 */
package net.lax1dude.eaglercraft.backend.server.config.snakeyaml.legacy;

import java.util.ArrayList;
import java.util.List;
import net.lax1dude.eaglercraft.backend.server.config.IEaglerConfList;
import net.lax1dude.eaglercraft.backend.server.config.IEaglerConfSection;
import net.lax1dude.eaglercraft.backend.server.config.snakeyaml.legacy.LegacyHelper;
import net.lax1dude.eaglercraft.backend.server.config.snakeyaml.legacy.YAMLConfigBase;
import net.lax1dude.eaglercraft.backend.server.config.snakeyaml.legacy.YAMLConfigSection;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

public class YAMLConfigList
implements IEaglerConfList {
    private final YAMLConfigBase owner;
    final SequenceNode yaml;
    private final boolean exists;
    private boolean initialized;

    public YAMLConfigList(YAMLConfigBase owner, SequenceNode yaml, boolean exists) {
        this.owner = owner;
        this.yaml = yaml;
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
    public IEaglerConfSection appendSection() {
        MappingNode obj = LegacyHelper.mappingNode(Tag.MAP, new ArrayList<NodeTuple>());
        this.yaml.getValue().add(obj);
        this.owner.modified = true;
        this.initialized = true;
        return new YAMLConfigSection(this.owner, obj, false);
    }

    @Override
    public IEaglerConfList appendList() {
        SequenceNode obj = LegacyHelper.sequenceNode(Tag.SEQ, new ArrayList<Node>());
        this.yaml.getValue().add(obj);
        this.owner.modified = true;
        this.initialized = true;
        return new YAMLConfigList(this.owner, obj, false);
    }

    @Override
    public void appendInteger(int value) {
        this.yaml.getValue().add(LegacyHelper.scalarNode(Tag.INT, Integer.toString(value), null));
        this.owner.modified = true;
        this.initialized = true;
    }

    @Override
    public void appendString(String string) {
        this.yaml.getValue().add(LegacyHelper.scalarNode(Tag.STR, string, Character.valueOf('\'')));
        this.owner.modified = true;
        this.initialized = true;
    }

    @Override
    public int getLength() {
        return this.yaml.getValue().size();
    }

    @Override
    public IEaglerConfSection getIfSection(int index) {
        List lst = this.yaml.getValue();
        if (index < 0 || index >= lst.size()) {
            return null;
        }
        Node t = (Node)lst.get(index);
        if (t instanceof MappingNode) {
            MappingNode tt = (MappingNode)t;
            return new YAMLConfigSection(this.owner, tt, true);
        }
        return null;
    }

    @Override
    public IEaglerConfList getIfList(int index) {
        List lst = this.yaml.getValue();
        if (index < 0 || index >= lst.size()) {
            return null;
        }
        Node t = (Node)lst.get(index);
        if (t instanceof SequenceNode) {
            SequenceNode tt = (SequenceNode)t;
            return new YAMLConfigList(this.owner, tt, true);
        }
        return null;
    }

    @Override
    public boolean isInteger(int index) {
        List lst = this.yaml.getValue();
        if (index < 0 || index >= lst.size()) {
            return false;
        }
        Node t = (Node)lst.get(index);
        if (t != null && t instanceof ScalarNode) {
            ScalarNode tt = (ScalarNode)t;
            try {
                Double.parseDouble(tt.getValue());
            }
            catch (NumberFormatException ex) {
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public int getIfInteger(int index, int defaultVal) {
        List lst = this.yaml.getValue();
        if (index < 0 || index >= lst.size()) {
            return defaultVal;
        }
        Node t = (Node)lst.get(index);
        if (t != null && t instanceof ScalarNode) {
            ScalarNode tt = (ScalarNode)t;
            try {
                return (int)Double.parseDouble(tt.getValue());
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
        }
        return defaultVal;
    }

    @Override
    public boolean isString(int index) {
        List lst = this.yaml.getValue();
        if (index < 0 || index >= lst.size()) {
            return false;
        }
        Node t = (Node)lst.get(index);
        return t != null && t instanceof ScalarNode;
    }

    @Override
    public String getIfString(int index, String defaultVal) {
        List lst = this.yaml.getValue();
        if (index < 0 || index >= lst.size()) {
            return defaultVal;
        }
        Node t = (Node)lst.get(index);
        if (t != null && t instanceof ScalarNode) {
            ScalarNode tt = (ScalarNode)t;
            return tt.getValue();
        }
        return defaultVal;
    }
}

