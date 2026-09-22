/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.common.collect.ImmutableList
 *  com.google.common.collect.ImmutableList$Builder
 *  org.yaml.snakeyaml.nodes.MappingNode
 *  org.yaml.snakeyaml.nodes.Node
 *  org.yaml.snakeyaml.nodes.NodeTuple
 *  org.yaml.snakeyaml.nodes.ScalarNode
 *  org.yaml.snakeyaml.nodes.SequenceNode
 *  org.yaml.snakeyaml.nodes.Tag
 */
package net.lax1dude.eaglercraft.backend.server.config.snakeyaml.legacy;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.lax1dude.eaglercraft.backend.server.config.IEaglerConfList;
import net.lax1dude.eaglercraft.backend.server.config.IEaglerConfSection;
import net.lax1dude.eaglercraft.backend.server.config.snakeyaml.legacy.LegacyHelper;
import net.lax1dude.eaglercraft.backend.server.config.snakeyaml.legacy.YAMLConfigBase;
import net.lax1dude.eaglercraft.backend.server.config.snakeyaml.legacy.YAMLConfigList;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

public class YAMLConfigSection
implements IEaglerConfSection {
    private final YAMLConfigBase owner;
    final MappingNode yaml;
    final Map<String, NodeTuple> accelerator = new HashMap<String, NodeTuple>();
    private final boolean exists;
    private boolean initialized;

    protected YAMLConfigSection(YAMLConfigBase owner, MappingNode yaml, boolean exists) {
        this.owner = owner;
        this.yaml = yaml;
        this.exists = this.initialized = exists;
        this.rehash();
    }

    @Override
    public boolean exists() {
        return this.exists;
    }

    @Override
    public boolean initialized() {
        return this.initialized;
    }

    public void rehash() {
        this.accelerator.clear();
        for (NodeTuple t : this.yaml.getValue()) {
            Node key = t.getKeyNode();
            if (!(key instanceof ScalarNode)) continue;
            ScalarNode key2 = (ScalarNode)key;
            this.accelerator.put(key2.getValue(), t);
        }
    }

    @Override
    public void setComment(String comment) {
    }

    @Override
    public IEaglerConfSection getIfSection(String name) {
        NodeTuple t = this.accelerator.get(name);
        if (t != null && t.getValueNode() != null && t.getValueNode() instanceof MappingNode) {
            MappingNode value2 = (MappingNode)t.getValueNode();
            return new YAMLConfigSection(this.owner, value2, true);
        }
        return null;
    }

    @Override
    public IEaglerConfSection getSection(String name) {
        NodeTuple t = this.accelerator.get(name);
        if (t != null && t.getValueNode() != null && t.getValueNode() instanceof MappingNode) {
            MappingNode value2 = (MappingNode)t.getValueNode();
            return new YAMLConfigSection(this.owner, value2, true);
        }
        ScalarNode key = LegacyHelper.scalarNode(Tag.STR, name, null);
        MappingNode obj = LegacyHelper.mappingNode(Tag.MAP, new ArrayList<NodeTuple>());
        NodeTuple tt = new NodeTuple((Node)key, (Node)obj);
        this.accelerator.put(name, tt);
        this.yaml.getValue().add(tt);
        this.owner.modified = true;
        this.initialized = true;
        return new YAMLConfigSection(this.owner, obj, false);
    }

    @Override
    public IEaglerConfList getIfList(String name) {
        NodeTuple t = this.accelerator.get(name);
        if (t != null && t.getValueNode() != null && t.getValueNode() instanceof SequenceNode) {
            SequenceNode value2 = (SequenceNode)t.getValueNode();
            return new YAMLConfigList(this.owner, value2, true);
        }
        return null;
    }

    @Override
    public IEaglerConfList getList(String name) {
        NodeTuple t = this.accelerator.get(name);
        if (t != null && t.getValueNode() != null && t.getValueNode() instanceof SequenceNode) {
            SequenceNode value2 = (SequenceNode)t.getValueNode();
            return new YAMLConfigList(this.owner, value2, true);
        }
        ScalarNode key = LegacyHelper.scalarNode(Tag.STR, name, null);
        SequenceNode obj = LegacyHelper.sequenceNode(Tag.SEQ, new ArrayList<Node>());
        NodeTuple tt = new NodeTuple((Node)key, (Node)obj);
        this.accelerator.put(name, tt);
        this.yaml.getValue().add(tt);
        this.owner.modified = true;
        this.initialized = true;
        return new YAMLConfigList(this.owner, obj, false);
    }

    @Override
    public List<String> getKeys() {
        ImmutableList.Builder builder = ImmutableList.builder();
        for (NodeTuple t : this.yaml.getValue()) {
            Node key = t.getKeyNode();
            if (!(key instanceof ScalarNode)) continue;
            ScalarNode key2 = (ScalarNode)key;
            builder.add((Object)key2.getValue());
        }
        return builder.build();
    }

    @Override
    public boolean isBoolean(String name) {
        NodeTuple t = this.accelerator.get(name);
        if (t != null && t.getValueNode() != null && t.getValueNode() instanceof ScalarNode) {
            ScalarNode value2 = (ScalarNode)t.getValueNode();
            String str = value2.getValue().toLowerCase();
            return "false".equals(str) || "true".equals(str);
        }
        return false;
    }

    @Override
    public boolean getBoolean(String name) {
        NodeTuple t = this.accelerator.get(name);
        return t != null && t.getValueNode() != null && t.getValueNode() instanceof ScalarNode && Boolean.valueOf(((ScalarNode)t.getValueNode()).getValue()) != false;
    }

    @Override
    public boolean getBoolean(String name, boolean defaultValue, String comment) {
        NodeTuple t = this.accelerator.get(name);
        if (t != null && t.getValueNode() != null && t.getValueNode() instanceof ScalarNode) {
            ScalarNode value2 = (ScalarNode)t.getValueNode();
            String str = value2.getValue().toLowerCase();
            boolean b = false;
            if ("false".equals(str) || (b = "true".equals(str))) {
                return b;
            }
        }
        ScalarNode key = LegacyHelper.scalarNode(Tag.STR, name, null);
        ScalarNode node = LegacyHelper.scalarNode(Tag.BOOL, Boolean.toString(defaultValue), null);
        t = new NodeTuple((Node)key, (Node)node);
        this.accelerator.put(name, t);
        this.yaml.getValue().add(t);
        this.owner.modified = true;
        this.initialized = true;
        return defaultValue;
    }

    @Override
    public boolean getBoolean(String name, Supplier<Boolean> defaultValue, String comment) {
        NodeTuple t = this.accelerator.get(name);
        if (t != null && t.getValueNode() != null && t.getValueNode() instanceof ScalarNode) {
            ScalarNode value2 = (ScalarNode)t.getValueNode();
            String str = value2.getValue().toLowerCase();
            boolean b = false;
            if ("false".equals(str) || (b = "true".equals(str))) {
                return b;
            }
        }
        boolean b = defaultValue.get();
        ScalarNode key = LegacyHelper.scalarNode(Tag.STR, name, null);
        ScalarNode node = LegacyHelper.scalarNode(Tag.BOOL, Boolean.toString(b), null);
        t = new NodeTuple((Node)key, (Node)node);
        this.accelerator.put(name, t);
        this.yaml.getValue().add(t);
        this.owner.modified = true;
        this.initialized = true;
        return b;
    }

    @Override
    public boolean isInteger(String name) {
        NodeTuple t = this.accelerator.get(name);
        if (t != null && t.getValueNode() != null && t.getValueNode() instanceof ScalarNode) {
            ScalarNode value2 = (ScalarNode)t.getValueNode();
            try {
                Double.parseDouble(value2.getValue());
            }
            catch (NumberFormatException ex) {
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public int getInteger(String name, int defaultValue, String comment) {
        NodeTuple t = this.accelerator.get(name);
        if (t != null && t.getValueNode() != null && t.getValueNode() instanceof ScalarNode) {
            ScalarNode value2 = (ScalarNode)t.getValueNode();
            try {
                return (int)Double.parseDouble(value2.getValue());
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
        }
        ScalarNode key = LegacyHelper.scalarNode(Tag.STR, name, null);
        ScalarNode node = LegacyHelper.scalarNode(Tag.INT, Integer.toString(defaultValue), null);
        t = new NodeTuple((Node)key, (Node)node);
        this.accelerator.put(name, t);
        this.yaml.getValue().add(t);
        this.owner.modified = true;
        this.initialized = true;
        return defaultValue;
    }

    @Override
    public int getInteger(String name, Supplier<Integer> defaultValue, String comment) {
        NodeTuple t = this.accelerator.get(name);
        if (t != null && t.getValueNode() != null && t.getValueNode() instanceof ScalarNode) {
            ScalarNode value2 = (ScalarNode)t.getValueNode();
            try {
                return (int)Double.parseDouble(value2.getValue());
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
        }
        int i = defaultValue.get();
        ScalarNode key = LegacyHelper.scalarNode(Tag.STR, name, null);
        ScalarNode node = LegacyHelper.scalarNode(Tag.INT, Integer.toString(i), null);
        t = new NodeTuple((Node)key, (Node)node);
        this.accelerator.put(name, t);
        this.yaml.getValue().add(t);
        this.owner.modified = true;
        this.initialized = true;
        return i;
    }

    @Override
    public boolean isString(String name) {
        NodeTuple t = this.accelerator.get(name);
        return t != null && t.getValueNode() != null && t.getValueNode() instanceof ScalarNode;
    }

    @Override
    public String getIfString(String name) {
        NodeTuple t = this.accelerator.get(name);
        if (t != null && t.getValueNode() != null && t.getValueNode() instanceof ScalarNode) {
            ScalarNode value2 = (ScalarNode)t.getValueNode();
            return value2.getValue();
        }
        return null;
    }

    @Override
    public String getString(String name, String defaultValue, String comment) {
        NodeTuple t = this.accelerator.get(name);
        if (t != null && t.getValueNode() != null && t.getValueNode() instanceof ScalarNode) {
            ScalarNode value2 = (ScalarNode)t.getValueNode();
            return value2.getValue();
        }
        ScalarNode key = LegacyHelper.scalarNode(Tag.STR, name, null);
        ScalarNode node = LegacyHelper.scalarNode(Tag.STR, defaultValue, Character.valueOf('\''));
        t = new NodeTuple((Node)key, (Node)node);
        this.accelerator.put(name, t);
        this.yaml.getValue().add(t);
        this.owner.modified = true;
        this.initialized = true;
        return defaultValue;
    }

    @Override
    public String getString(String name, Supplier<String> defaultValue, String comment) {
        NodeTuple t = this.accelerator.get(name);
        if (t != null && t.getValueNode() != null && t.getValueNode() instanceof ScalarNode) {
            ScalarNode value2 = (ScalarNode)t.getValueNode();
            return value2.getValue();
        }
        String str = defaultValue.get();
        ScalarNode key = LegacyHelper.scalarNode(Tag.STR, name, null);
        ScalarNode node = LegacyHelper.scalarNode(Tag.STR, str, Character.valueOf('\''));
        t = new NodeTuple((Node)key, (Node)node);
        this.accelerator.put(name, t);
        this.yaml.getValue().add(t);
        this.owner.modified = true;
        this.initialized = true;
        return str;
    }
}

