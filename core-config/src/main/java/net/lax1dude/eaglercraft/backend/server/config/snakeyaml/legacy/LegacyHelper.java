/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.yaml.snakeyaml.error.Mark
 *  org.yaml.snakeyaml.nodes.MappingNode
 *  org.yaml.snakeyaml.nodes.Node
 *  org.yaml.snakeyaml.nodes.NodeTuple
 *  org.yaml.snakeyaml.nodes.ScalarNode
 *  org.yaml.snakeyaml.nodes.SequenceNode
 *  org.yaml.snakeyaml.nodes.Tag
 */
package net.lax1dude.eaglercraft.backend.server.config.snakeyaml.legacy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.lax1dude.eaglercraft.backend.server.config.ReflectUtil;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

class LegacyHelper {
    private static final boolean HAS_FLOWSTYLE;
    private static final boolean HAS_SCALARSTYLE;
    private static final Constructor<MappingNode> ctor_MappingNode;
    private static final Constructor<SequenceNode> ctor_SequenceNode;
    private static final Object flowStyle;
    private static final Constructor<ScalarNode> ctor_ScalarNode;
    private static final Method meth_createStyle;

    LegacyHelper() {
    }

    public static MappingNode mappingNode(Tag tag, List<NodeTuple> lst) {
        try {
            if (HAS_FLOWSTYLE) {
                return ctor_MappingNode.newInstance(tag, lst, flowStyle);
            }
            return ctor_MappingNode.newInstance(tag, lst, Boolean.FALSE);
        }
        catch (ReflectiveOperationException ex) {
            throw ReflectUtil.propagateReflectThrowable(ex);
        }
    }

    public static SequenceNode sequenceNode(Tag tag, List<Node> lst) {
        try {
            if (HAS_FLOWSTYLE) {
                return ctor_SequenceNode.newInstance(tag, lst, flowStyle);
            }
            return ctor_SequenceNode.newInstance(tag, lst, Boolean.FALSE);
        }
        catch (ReflectiveOperationException ex) {
            throw ReflectUtil.propagateReflectThrowable(ex);
        }
    }

    public static ScalarNode scalarNode(Tag tag, String value, Character style) {
        try {
            if (HAS_SCALARSTYLE) {
                return ctor_ScalarNode.newInstance(tag, value, null, null, meth_createStyle.invoke(null, style));
            }
            return ctor_ScalarNode.newInstance(tag, value, null, null, style);
        }
        catch (ReflectiveOperationException ex) {
            throw ReflectUtil.propagateReflectThrowable(ex);
        }
    }

    public static void fixScalars(Node data, Field scalarStyle) {
        block6: {
            block7: {
                block5: {
                    if (!(data instanceof ScalarNode)) break block5;
                    ScalarNode s = (ScalarNode)data;
                    try {
                        Character c = (Character)scalarStyle.get(s);
                        if (c != null && c.charValue() == '\u0000') {
                            scalarStyle.set(s, null);
                        }
                    }
                    catch (ReflectiveOperationException reflectiveOperationException) {}
                    break block6;
                }
                if (!(data instanceof SequenceNode)) break block7;
                SequenceNode s = (SequenceNode)data;
                for (Node n : s.getValue()) {
                    LegacyHelper.fixScalars(n, scalarStyle);
                }
                break block6;
            }
            if (!(data instanceof MappingNode)) break block6;
            MappingNode s = (MappingNode)data;
            for (NodeTuple n : s.getValue()) {
                LegacyHelper.fixScalars(n.getKeyNode(), scalarStyle);
                LegacyHelper.fixScalars(n.getValueNode(), scalarStyle);
            }
        }
    }

    static {
        Class<?> flowStyleClz = null;
        Class<?> scalarStyleClz = null;
        try {
            flowStyleClz = Class.forName("org.yaml.snakeyaml.DumperOptions$FlowStyle");
        }
        catch (ClassNotFoundException classNotFoundException) {
            // empty catch block
        }
        try {
            scalarStyleClz = Class.forName("org.yaml.snakeyaml.DumperOptions$ScalarStyle");
        }
        catch (ClassNotFoundException classNotFoundException) {
            // empty catch block
        }
        try {
            Constructor _ctor_MappingNode = null;
            Constructor _ctor_SequenceNode = null;
            Object _flowStyle = null;
            if (flowStyleClz != null) {
                try {
                    _ctor_MappingNode = MappingNode.class.getConstructor(Tag.class, List.class, flowStyleClz);
                    _ctor_SequenceNode = SequenceNode.class.getConstructor(Tag.class, List.class, flowStyleClz);
                    _flowStyle = Enum.valueOf((Class)flowStyleClz, "BLOCK");
                }
                catch (ReflectiveOperationException ex) {
                    flowStyleClz = null;
                }
            }
            if (flowStyleClz == null) {
                _ctor_MappingNode = MappingNode.class.getConstructor(Tag.class, List.class, Boolean.class);
                _ctor_SequenceNode = SequenceNode.class.getConstructor(Tag.class, List.class, Boolean.class);
                _flowStyle = null;
            }
            Constructor _ctor_ScalarNode = null;
            Method _meth_createStyle = null;
            if (scalarStyleClz != null) {
                try {
                    _ctor_ScalarNode = ScalarNode.class.getConstructor(Tag.class, String.class, Mark.class, Mark.class, scalarStyleClz);
                    _meth_createStyle = scalarStyleClz.getMethod("createStyle", Character.class);
                }
                catch (ReflectiveOperationException ex) {
                    scalarStyleClz = null;
                }
            }
            if (scalarStyleClz == null) {
                _ctor_ScalarNode = ScalarNode.class.getConstructor(Tag.class, String.class, Mark.class, Mark.class, Character.class);
                _meth_createStyle = null;
            }
            ctor_MappingNode = _ctor_MappingNode;
            ctor_SequenceNode = _ctor_SequenceNode;
            flowStyle = _flowStyle;
            ctor_ScalarNode = _ctor_ScalarNode;
            meth_createStyle = _meth_createStyle;
        }
        catch (ReflectiveOperationException ex) {
            throw new ExceptionInInitializerError(ex);
        }
        HAS_FLOWSTYLE = flowStyleClz != null;
        HAS_SCALARSTYLE = scalarStyleClz != null;
    }
}

