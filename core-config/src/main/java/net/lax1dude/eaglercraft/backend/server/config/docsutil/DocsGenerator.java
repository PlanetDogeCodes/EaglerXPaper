/*
 * Decompiled with CFR 0.152.
 */
package net.lax1dude.eaglercraft.backend.server.config.docsutil;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.lax1dude.eaglercraft.backend.server.config.docsutil.DocsDirectory;
import net.lax1dude.eaglercraft.backend.server.config.docsutil.DocsList;
import net.lax1dude.eaglercraft.backend.server.config.docsutil.DocsSection;
import net.lax1dude.eaglercraft.backend.server.config.docsutil.DocsValue;

public class DocsGenerator {
    private final String title;
    private final String desc;
    private final Map<String, DocsDirectory> map = new HashMap<String, DocsDirectory>();
    private String primaryFile;

    public DocsGenerator(String title, String desc) {
        this.title = title;
        this.desc = desc;
    }

    public void addPlatform(String platform, IDocDirLoader provider) throws IOException {
        DocsDirectory directory = this.map.get(platform);
        if (directory == null) {
            directory = new DocsDirectory();
            this.map.put(platform, directory);
        }
        provider.call(directory);
    }

    private static void flatten(Map<String, Header> headers, Map<String, Content> content, String platf, String pfx, String title, DocsSection section) {
        for (Map.Entry<String, Object> etr : section.entries.entrySet()) {
            Object v;
            if (etr.getValue() instanceof DocsValue) {
                v = (DocsValue)etr.getValue();
                DocsGenerator.flatten(content, platf, pfx + etr.getKey(), title + "`" + etr.getKey() + "`", (DocsValue)v);
                continue;
            }
            if (etr.getValue() instanceof DocsSection) {
                v = (DocsSection)etr.getValue();
                Header headerObj = new Header(title + "`" + etr.getKey() + "`");
                headerObj.platforms.add(platf);
                if (((DocsSection)v).comment != null && !((DocsSection)v).comment.isEmpty()) {
                    Summary summaryObj = new Summary(((DocsSection)v).comment);
                    summaryObj.platforms.add(platf);
                    headerObj.summary.add(summaryObj);
                }
                DocsGenerator.merge(headers, pfx + etr.getKey(), headerObj);
                DocsGenerator.flatten(headers, content, platf, pfx + etr.getKey() + ":", title + "`" + etr.getKey() + "` : ", (DocsSection)v);
                continue;
            }
            if (etr.getValue() instanceof DocsList) {
                v = (DocsList)etr.getValue();
                DocsGenerator.flatten(headers, content, platf, pfx + etr.getKey(), title + "`" + etr.getKey() + "`", (DocsList)v);
                continue;
            }
            throw new IllegalStateException();
        }
    }

    private static void flatten(Map<String, Header> headers, Map<String, Content> content, String platf, String pfx, String title, DocsList section) {
        boolean hasHeader = false;
        ArrayList<String> values = new ArrayList<String>();
        for (Object etr : section.entries) {
            String t2;
            int idx;
            Object v;
            if (etr instanceof DocsValue) {
                v = (DocsValue)etr;
                values.add(((DocsValue)v).value == null ? "null" : (((DocsValue)v).type != DocsValue.Type.STR ? ((DocsValue)v).value : "\"" + ((DocsValue)v).value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""));
                continue;
            }
            if (etr instanceof DocsSection) {
                v = (DocsSection)etr;
                idx = title.lastIndexOf(": ");
                if (idx == -1) {
                    idx = title.lastIndexOf("&gt; ") + 3;
                }
                t2 = title.substring(0, idx + 2) + "[" + title.substring(idx + 2) + "]";
                DocsGenerator.flatten(headers, content, platf, pfx + ":{}:", t2 + " : ", (DocsSection)v);
                hasHeader = true;
                continue;
            }
            if (etr instanceof DocsList) {
                v = (DocsList)etr;
                idx = title.lastIndexOf(": ");
                if (idx == -1) {
                    idx = title.lastIndexOf("&gt; ") + 3;
                }
                t2 = title.substring(0, idx + 2) + "[" + title.substring(idx + 2) + "]";
                DocsGenerator.flatten(headers, content, platf, pfx + ":[]:", t2, (DocsList)v);
                hasHeader = true;
                continue;
            }
            throw new IllegalStateException();
        }
        if (hasHeader) {
            int idx = title.lastIndexOf(": ");
            if (idx == -1) {
                idx = title.lastIndexOf("&gt; ") + 3;
            }
            String t2 = title.substring(0, idx + 2) + "[" + title.substring(idx + 2) + "]";
            Header headerObj = new Header(t2);
            headerObj.platforms.add(platf);
            if (section.comment != null && !section.comment.isEmpty()) {
                Summary summaryObj = new Summary(section.comment);
                summaryObj.platforms.add(platf);
                headerObj.summary.add(summaryObj);
            }
            DocsGenerator.merge(headers, pfx, headerObj);
        } else {
            Content contentObj = new Content(title);
            contentObj.platforms.add(platf);
            if (section.comment != null && !section.comment.isEmpty()) {
                Summary summaryObj = new Summary(section.comment);
                summaryObj.platforms.add(platf);
                contentObj.summary.add(summaryObj);
            }
            Defaults defaultsObj = new Defaults("[ " + String.join((CharSequence)", ", values) + " ]");
            defaultsObj.platforms.add(platf);
            contentObj.defaults.add(defaultsObj);
            DocsGenerator.merge(content, pfx, contentObj);
        }
    }

    private static void flatten(Map<String, Content> content, String platf, String pfx, String title, DocsValue value) {
        Content contentObj = new Content(title);
        contentObj.platforms.add(platf);
        if (value.comment != null && !value.comment.isEmpty()) {
            Summary summaryObj = new Summary(value.comment);
            summaryObj.platforms.add(platf);
            contentObj.summary.add(summaryObj);
        }
        if (!value.randomized) {
            Defaults defaultsObj = new Defaults(value.value == null ? "null" : (value.type != DocsValue.Type.STR ? value.value : "\"" + value.value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""));
            defaultsObj.platforms.add(platf);
            contentObj.defaults.add(defaultsObj);
        }
        DocsGenerator.merge(content, pfx, contentObj);
    }

    private static void merge(Map<String, Header> headers, String pfx, Header item) {
        Header existing = headers.get(pfx);
        if (existing == null) {
            headers.put(pfx, item);
            return;
        }
        for (String str : item.platforms) {
            if (existing.platforms.contains(str)) continue;
            existing.platforms.add(str);
        }
        block1: for (Summary str1 : item.summary) {
            for (Summary str2 : existing.summary) {
                if (!str1.summary.equals(str2.summary)) continue;
                for (String str : str1.platforms) {
                    if (str2.platforms.contains(str)) continue;
                    str2.platforms.add(str);
                }
                continue block1;
            }
            existing.summary.add((Summary)str1);
        }
    }

    private static void merge(Map<String, Content> content, String id, Content item) {
        Content existing = content.get(id);
        if (existing == null) {
            content.put(id, item);
            return;
        }
        for (String str : item.platforms) {
            if (existing.platforms.contains(str)) continue;
            existing.platforms.add(str);
        }
        block1: for (Object str1 : item.summary) {
            for (Object str2 : existing.summary) {
                if (!((Summary)str1).summary.equals(((Summary)str2).summary)) continue;
                for (String str : ((Summary)str1).platforms) {
                    if (((Summary)str2).platforms.contains(str)) continue;
                    ((Summary)str2).platforms.add(str);
                }
                continue block1;
            }
            existing.summary.add((Summary)str1);
        }
        block4: for (Object str1 : item.defaults) {
            for (Object str2 : existing.defaults) {
                if (!((Defaults)str1).defaults.equals(((Defaults)str2).defaults)) continue;
                for (String str : ((Defaults)str1).platforms) {
                    if (((Defaults)str2).platforms.contains(str)) continue;
                    ((Defaults)str2).platforms.add(str);
                }
                continue block4;
            }
            existing.defaults.add((Defaults)str1);
        }
    }

    /*
     * WARNING - void declaration
     */
    public void writeDocs(PrintWriter writer) {
        String prefix;
        String[] descLines;
        ArrayList<String> sortedPlatforms = new ArrayList<String>(this.map.keySet());
        Collections.sort(sortedPlatforms);
        HashMap<String, Header> headers = new HashMap<String, Header>();
        HashMap<String, Content> sections = new HashMap<String, Content>();
        for (String platfName : sortedPlatforms) {
            DocsDirectory platf = this.map.get(platfName);
            for (Map.Entry<String, DocsSection> entry : platf.map.entrySet()) {
                DocsGenerator.flatten(headers, sections, platfName, entry.getKey() + ">", "`/" + entry.getKey() + ".cfg` &gt; ", entry.getValue());
            }
        }
        ArrayList<Map.Entry> sortedSections = new ArrayList<Map.Entry>(sections.entrySet());
        Collections.sort(sortedSections, (Comparator)Map.Entry.comparingByKey());
        if (this.primaryFile != null) {
            prefix = this.primaryFile + ">";
            ArrayList<Map.Entry> tmp = new ArrayList<Map.Entry>(sortedSections.size());
            for (Map.Entry entry : sortedSections) {
                if (!((String)entry.getKey()).startsWith(prefix)) continue;
                tmp.add(entry);
            }
            for (Map.Entry entry : sortedSections) {
                if (((String)entry.getKey()).startsWith(prefix)) continue;
                tmp.add(entry);
            }
            sortedSections = tmp;
        }
        writer.println("## " + this.title);
        descLines = this.desc.split("(\\r\\n|\\r|\\n)");
        for (int i = 0; i < descLines.length; ++i) {
            writer.println(descLines[i]);
        }
        writer.println();
        writer.println("*(Placeholder extension \".cfg\" replaced with \".yaml\", \".toml\", or \".gson\")*");
        String vigg = null;
        for (Map.Entry entry : sortedSections) {
            String string = (String)entry.getKey();
            int idx = string.lastIndexOf(58);
            if (!(idx == -1 || vigg != null && vigg.length() == idx && vigg.regionMatches(0, string, 0, idx))) {
                vigg = string.substring(0, idx);
                idx = -1;
                while ((idx = string.indexOf(58, idx + 1)) != -1) {
                    Header head = (Header)headers.remove(string.substring(0, idx));
                    if (head == null) continue;
                    writer.println();
                    writer.println("## " + head.title + "&emsp;<sub>(" + String.join((CharSequence)", ", head.platforms) + ")</sub>");
                    if (head.summary.isEmpty()) continue;
                    writer.println("**Summary:**");
                    for (Summary sum : head.summary) {
                        writer.println("- " + sum.summary + "&emsp;<sub>(" + String.join((CharSequence)", ", sum.platforms) + ")</sub>");
                    }
                }
            }
            Content sec = (Content)entry.getValue();
            writer.println();
            writer.println("## <small>" + sec.title + "&emsp;<sub>(" + String.join((CharSequence)", ", sec.platforms) + ")</sub></small>");
            if (!sec.summary.isEmpty()) {
                writer.println("**Summary:**");
                for (Summary sum : sec.summary) {
                    writer.println("- " + sum.summary + "&emsp;<sub>(" + String.join((CharSequence)", ", sum.platforms) + ")</sub>");
                }
            }
            if (sec.defaults.isEmpty()) continue;
            if (!sec.summary.isEmpty()) {
                writer.println();
            }
            writer.println("**Defaults:**");
            for (Defaults def : sec.defaults) {
                writer.println("- `" + def.defaults + "`&emsp;<sub>(" + String.join((CharSequence)", ", def.platforms) + ")</sub>");
            }
        }
        writer.flush();
    }

    public void setPrimaryFile(String primaryFile) {
        this.primaryFile = primaryFile;
    }

    public static interface IDocDirLoader {
        public void call(DocsDirectory var1) throws IOException;
    }

    private static class Header {
        private final String title;
        private final List<String> platforms;
        private final List<Summary> summary;

        private Header(String title) {
            this.title = title;
            this.platforms = new ArrayList<String>();
            this.summary = new ArrayList<Summary>();
        }
    }

    private static class Summary {
        private final String summary;
        private final List<String> platforms;

        private Summary(String summary) {
            this.summary = summary;
            this.platforms = new ArrayList<String>();
        }
    }

    private static class Content {
        private final String title;
        private final List<String> platforms;
        private final List<Summary> summary;
        private final List<Defaults> defaults;

        private Content(String title) {
            this.title = title;
            this.platforms = new ArrayList<String>();
            this.summary = new ArrayList<Summary>();
            this.defaults = new ArrayList<Defaults>();
        }
    }

    private static class Defaults {
        private final String defaults;
        private final List<String> platforms;

        private Defaults(String defaults) {
            this.defaults = defaults;
            this.platforms = new ArrayList<String>();
        }
    }
}

