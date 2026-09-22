/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.yaml.snakeyaml.DumperOptions
 *  org.yaml.snakeyaml.DumperOptions$FlowStyle
 *  org.yaml.snakeyaml.LoaderOptions
 *  org.yaml.snakeyaml.Yaml
 *  org.yaml.snakeyaml.comments.CommentLine
 *  org.yaml.snakeyaml.comments.CommentType
 *  org.yaml.snakeyaml.constructor.BaseConstructor
 *  org.yaml.snakeyaml.constructor.Constructor
 *  org.yaml.snakeyaml.error.YAMLException
 *  org.yaml.snakeyaml.nodes.MappingNode
 *  org.yaml.snakeyaml.nodes.Node
 *  org.yaml.snakeyaml.nodes.ScalarNode
 *  org.yaml.snakeyaml.nodes.Tag
 *  org.yaml.snakeyaml.representer.Representer
 */
package net.lax1dude.eaglercraft.backend.server.config.snakeyaml.modern;

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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.lax1dude.eaglercraft.backend.server.config.IEaglerConfig;
import net.lax1dude.eaglercraft.backend.server.config.WrapUtil;
import net.lax1dude.eaglercraft.backend.server.config.snakeyaml.modern.YAMLConfigBase;
import net.lax1dude.eaglercraft.backend.server.config.snakeyaml.modern.YAMLConfigSection;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.comments.CommentType;
import org.yaml.snakeyaml.constructor.BaseConstructor;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

public class YAMLConfigLoader {
    private static final Yaml YAML;
    public static final int YAML_COMMENT_WRAP = 80;

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
            obj = new MappingNode(Tag.MAP, new ArrayList(), DumperOptions.FlowStyle.BLOCK);
        }
        if (!(obj instanceof MappingNode)) {
            throw new IOException("Root node " + obj.getClass().getSimpleName() + " is not a map!");
        }
        return YAMLConfigLoader.getConfigFile(file, obj);
    }

    public static IEaglerConfig getConfigFile(File file, MappingNode node) throws IOException {
        YAMLConfigBase base = new YAMLConfigBase(file);
        base.root = new YAMLConfigSection(base, node, null, node.getValue().size() > 0);
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
                YAML.serialize(configIn, (Writer)writer);
            }
        }
    }

    public static void createComment(String text, List<CommentLine> ret) {
        if (text != null) {
            String[] lines = WrapUtil.wrap(text, 80, "\n", false, " ").split("\n");
            for (int i = 0; i < lines.length; ++i) {
                ret.add(new CommentLine(null, null, " " + lines[i], CommentType.BLOCK));
            }
        }
    }

    public static void createCommentHelper(String text, ScalarNode ret) {
        List<CommentLine> lst = ret.getBlockComments();
        if (lst == null) {
            lst = new ArrayList<CommentLine>();
            ret.setBlockComments(lst);
        } else {
            lst.clear();
        }
        YAMLConfigLoader.createComment(text, lst);
    }

    static {
        LoaderOptions loadOpts = new LoaderOptions();
        try {
            LoaderOptions.class.getMethod("setProcessComments", Boolean.TYPE).invoke((Object)loadOpts, true);
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            // empty catch block
        }
        DumperOptions dumpOpts = new DumperOptions();
        dumpOpts.setPrettyFlow(true);
        dumpOpts.setDefaultFlowStyle(DumperOptions.FlowStyle.FLOW);
        try {
            DumperOptions.class.getMethod("setProcessComments", Boolean.TYPE).invoke((Object)dumpOpts, true);
        }
        catch (ReflectiveOperationException reflectiveOperationException) {
            // empty catch block
        }
        YAML = new Yaml((BaseConstructor)new Constructor(loadOpts), new Representer(dumpOpts), dumpOpts);
    }
}

