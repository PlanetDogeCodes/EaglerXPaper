/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.yaml.snakeyaml.DumperOptions
 *  org.yaml.snakeyaml.DumperOptions$FlowStyle
 *  org.yaml.snakeyaml.Yaml
 *  org.yaml.snakeyaml.error.YAMLException
 *  org.yaml.snakeyaml.nodes.MappingNode
 *  org.yaml.snakeyaml.nodes.Node
 *  org.yaml.snakeyaml.nodes.NodeTuple
 *  org.yaml.snakeyaml.nodes.ScalarNode
 *  org.yaml.snakeyaml.nodes.Tag
 *  org.yaml.snakeyaml.representer.BaseRepresenter
 *  org.yaml.snakeyaml.representer.Represent
 *  org.yaml.snakeyaml.representer.Representer
 */
package net.lax1dude.eaglercraft.backend.server.config.snakeyaml.legacy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import net.lax1dude.eaglercraft.backend.server.config.IEaglerConfig;
import net.lax1dude.eaglercraft.backend.server.config.snakeyaml.legacy.LegacyHelper;
import net.lax1dude.eaglercraft.backend.server.config.snakeyaml.legacy.YAMLConfigBase;
import net.lax1dude.eaglercraft.backend.server.config.snakeyaml.legacy.YAMLConfigSection;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.BaseRepresenter;
import org.yaml.snakeyaml.representer.Represent;
import org.yaml.snakeyaml.representer.Representer;

public class YAMLConfigLoader {
    private static final Yaml YAML;

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static IEaglerConfig getConfigFile(File file) throws IOException {
        MappingNode obj;
        try (InputStreamReader reader = new InputStreamReader((InputStream)new FileInputStream(file), StandardCharsets.UTF_8);){
            Yaml yaml = YAML;
            synchronized (yaml) {
                obj = (MappingNode)YAML.compose((Reader)reader);
            }
        }
        catch (FileNotFoundException ex) {
            obj = null;
        }
        catch (YAMLException ex) {
            throw new IOException("YAML config file has a syntax error: " + file.getAbsolutePath(), ex);
        }
        if (obj == null) {
            obj = LegacyHelper.mappingNode(Tag.MAP, new ArrayList<NodeTuple>());
        }
        if (!(obj instanceof MappingNode)) {
            throw new IOException("Root node " + obj.getClass().getSimpleName() + " is not a map!");
        }
        return YAMLConfigLoader.getConfigFile(file, obj);
    }

    public static IEaglerConfig getConfigFile(File file, MappingNode node) throws IOException {
        YAMLConfigBase base = new YAMLConfigBase(file);
        base.root = new YAMLConfigSection(base, node, node.getValue().size() > 0);
        return base;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void writeConfigFile(Node configIn, File file) throws IOException {
        File p = file.getAbsoluteFile().getParentFile();
        if (p != null && !p.isDirectory() && !p.mkdirs()) {
            throw new IOException("Could not create directory: " + p.getAbsolutePath());
        }
        try (OutputStreamWriter writer = new OutputStreamWriter((OutputStream)new FileOutputStream(file), StandardCharsets.UTF_8);){
            Yaml yaml = YAML;
            synchronized (yaml) {
                YAML.dump((Object)configIn, (Writer)writer);
            }
        }
    }

    static {
        Representer representer;
        DumperOptions dumpOpts = new DumperOptions();
        dumpOpts.setPrettyFlow(true);
        dumpOpts.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
        try {
            representer = (Representer)Representer.class.getConstructor(DumperOptions.class).newInstance(dumpOpts);
        }
        catch (ReflectiveOperationException ex) {
            try {
                representer = (Representer)Representer.class.getConstructor(new Class[0]).newInstance(new Object[0]);
            }
            catch (ReflectiveOperationException exx) {
                throw new ExceptionInInitializerError(exx);
            }
        }
        Field scalarStyleField = null;
        try {
            scalarStyleField = ScalarNode.class.getDeclaredField("style");
            if (scalarStyleField.getType() == Character.class) {
                scalarStyleField.setAccessible(true);
            } else {
                scalarStyleField = null;
            }
        }
        catch (ReflectiveOperationException exx) {
            // empty catch block
        }
        final Field scalarStyleFieldF = scalarStyleField;
        try {
            Field f = BaseRepresenter.class.getDeclaredField("multiRepresenters");
            f.setAccessible(true);
            ((Map)f.get(representer)).put(Node.class, new Represent(){

                public Node representData(Object data) {
                    if (scalarStyleFieldF != null) {
                        LegacyHelper.fixScalars((Node)data, scalarStyleFieldF);
                    }
                    return (Node)data;
                }
            });
        }
        catch (ReflectiveOperationException exx) {
            throw new ExceptionInInitializerError(exx);
        }
        YAML = new Yaml(representer, dumpOpts);
    }
}

