/*
 * Copyright (c) 2025 lax1dude. All Rights Reserved.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */

package net.lax1dude.eaglercraft.backend.server.base.config;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.lax1dude.eaglercraft.backend.server.adapter.IPlatform;
import net.lax1dude.eaglercraft.backend.server.config.EnumConfigFormat;
import net.lax1dude.eaglercraft.backend.server.config.IEaglerConfig;

public class ConfigHelper {

        private static final List<EnumConfigFormat> PREFERRED_ORDER = Arrays.asList(EnumConfigFormat.TOML,
                        EnumConfigFormat.YAML, EnumConfigFormat.JSON);

        private final Set<EnumConfigFormat> supported;
        private final Map<String, EnumConfigFormat> fromExtension;
        private final EnumConfigFormat preferred;

        public ConfigHelper(IPlatform<?> platform) {
                supported = platform.getConfigFormats();
                EnumConfigFormat pref = null;
                shit: {
                        for (EnumConfigFormat fmt : PREFERRED_ORDER) {
                                if (supported.contains(fmt)) {
                                        pref = fmt;
                                        break shit;
                                }
                        }
                        if (!supported.isEmpty()) {
                                pref = supported.iterator().next();
                                break shit;
                        }
                        throw new IllegalStateException("No supported config formats on this platform!");
                }
                preferred = pref;
                fromExtension = new HashMap<>();
                for (EnumConfigFormat fmt : supported) {
                        for (String ext : fmt.getExts()) {
                                fromExtension.put(ext, fmt);
                        }
                }
        }

        public interface IConfigDirectoryLoader<T> {
                T load(IConfigDirectory dir) throws IOException;
        }

        public <T> T getConfigDirectory(IPlatform<?> platform, IConfigDirectoryLoader<T> handler) throws IOException {
                String singleFile = System.getProperty("eaglerxserver.singleConfigFile");
                String formatProperty = System.getProperty("eaglerxserver.configFormat");
                final EnumConfigFormat defaultFormat;
                if (formatProperty != null) {
                        defaultFormat = fromExtension.get(formatProperty.toLowerCase());
                        if (defaultFormat == null) {
                                throw new UnsupportedOperationException("Unknown eaglerxserver.configFormat: " + formatProperty);
                        }
                } else {
                        if (singleFile != null) {
                                int idx = singleFile.lastIndexOf('.');
                                if (idx != -1) {
                                        defaultFormat = fromExtension.getOrDefault(singleFile.substring(idx + 1), preferred);
                                } else {
                                        defaultFormat = preferred;
                                }
                        } else {
                                defaultFormat = preferred;
                        }
                }
                if (singleFile != null) {
                        File f = new File(singleFile);
                        platform.logger().info("Using single config file at: " + f.getAbsolutePath());
                        IEaglerConfig confTemp;
                        try {
                                confTemp = defaultFormat.getConfigFile(f);
                        } catch (IOException e) {
                                platform.logger().warn("Config file " + f.getName() + " has a syntax error: " + e.getMessage());
                                platform.logger().warn("Backing up " + f.getName() + " to " + f.getName() + ".broken and regenerating...");
                                File backup = new File(f.getParentFile(), f.getName() + ".broken");
                                if (backup.exists()) backup.delete();
                                if (!f.renameTo(backup)) f.delete();
                                boolean created;
                                try {
                                        created = f.createNewFile();
                                } catch (IOException ioe) {
                                        throw new IOException("Failed to create empty config file at " + f.getAbsolutePath(), ioe);
                                }
                                if (!created) {
                                        throw new IOException("Failed to create empty config file at " + f.getAbsolutePath() + " (file already exists or permission denied)");
                                }
                                try {
                                        confTemp = defaultFormat.getConfigFile(f);
                                } catch (IOException retryEx) {
                                        throw new IOException("Config file recovery failed — retry on empty file also failed for " + f.getAbsolutePath(), retryEx);
                                }
                        }
                        final IEaglerConfig conf = confTemp;
                        File parent = f.getParentFile();
                        T result = handler.load(new IConfigDirectory() {
                                @Override
                                public File getBaseDir() {
                                        return parent;
                                }

                                @Override
                                public <V> V loadConfig(String fileName, IConfigLoadFunction<V> func) throws IOException {
                                        return func.call(conf.getRoot().getSection(fileName));
                                }
                        });
                        if (conf.saveIfModified()) {
                                platform.logger().info("Config file was updated: " + f.getAbsolutePath());
                        }
                        return result;
                } else {
                        File dataFolder = platform.getDataFolder();
                        return handler.load(new IConfigDirectory() {
                                @Override
                                public File getBaseDir() {
                                        return dataFolder;
                                }

                                @Override
                                public <V> V loadConfig(String fileName, IConfigLoadFunction<V> func) throws IOException {
                                        EnumConfigFormat fmt = defaultFormat;
                                        File f = new File(dataFolder, fileName + "." + fmt.getDefaultExt());
                                        if (!f.isFile()) {
                                                search: for (EnumConfigFormat fmt2 : supported) {
                                                        if (fmt != fmt2) {
                                                                for (String s : fmt2.getExts()) {
                                                                        File f2 = new File(dataFolder, fileName + "." + s);
                                                                        if (f2.isFile()) {
                                                                                fmt = fmt2;
                                                                                f = f2;
                                                                                break search;
                                                                        }
                                                                }
                                                        }
                                                }
                                        }
                                        IEaglerConfig conf;
                                        try {
                                                conf = fmt.getConfigFile(f);
                                        } catch (IOException e) {
                                                // Config file has a syntax error (likely from a version mismatch
                                                // or corrupted comment insertion). Rename the broken file and
                                                // regenerate from scratch so the server can start.
                                                platform.logger().warn("Config file " + f.getName() + " has a syntax error: " + e.getMessage());
                                                platform.logger().warn("Backing up " + f.getName() + " to " + f.getName() + ".broken and regenerating...");
                                                File backup = new File(f.getParentFile(), f.getName() + ".broken");
                                                // If .broken already exists, delete it first
                                                if (backup.exists()) {
                                                        backup.delete();
                                                }
                                                if (!f.renameTo(backup)) {
                                                        // Can't rename — try deleting instead
                                                        f.delete();
                                                }
                                                // Now create a fresh empty file so getConfigFile succeeds.
                                                // createNewFile() returns false if the file already exists (e.g.
                                                // because both renameTo and delete failed on a locked file) or
                                                // cannot be created — in either case the retry below would
                                                // re-read the broken file, so we fail fast with a clear message.
                                                boolean created;
                                                try {
                                                        created = f.createNewFile();
                                                } catch (IOException ioe) {
                                                        throw new IOException("Failed to create empty config file at " + f.getAbsolutePath(), ioe);
                                                }
                                                if (!created) {
                                                        throw new IOException("Failed to create empty config file at " + f.getAbsolutePath() + " (file already exists or permission denied)");
                                                }
                                                try {
                                                        conf = fmt.getConfigFile(f);
                                                } catch (IOException retryEx) {
                                                        throw new IOException("Config file recovery failed — retry on empty file also failed for " + f.getAbsolutePath(), retryEx);
                                                }
                                        }
                                        V ret = func.call(conf.getRoot());
                                        if (conf.saveIfModified()) {
                                                platform.logger().info("Config file was updated: " + f.getAbsolutePath());
                                        }
                                        return ret;
                                }
                        });
                }
        }

}
