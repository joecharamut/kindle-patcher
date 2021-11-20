package rocks.spaghetti.classloader;

import com.electronwill.nightconfig.core.conversion.ObjectConverter;
import com.electronwill.nightconfig.core.file.FileConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class Config {
    private Config() {
        throw new IllegalStateException("Utility Class");
    }

    public static final File DATA_DIR = new File("/mnt/us/patcher");
    public static final File PATCH_DIR = new File(DATA_DIR, "patches");

    private static final String CONFIG_NAME = "patcher.toml";
    private static final File CONFIG_FILE = new File(DATA_DIR, CONFIG_NAME);
    private static final String DEFAULT_CONFIG = "defaults.toml";

    public static ConfigData getConfig() {
        if (!CONFIG_FILE.exists()) {
            InputStream defaultConfig = Config.class.getClassLoader().getResourceAsStream(DEFAULT_CONFIG);
            if (defaultConfig != null) {
                try {
                    Files.copy(defaultConfig, CONFIG_FILE.toPath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        FileConfig conf = FileConfig.of(CONFIG_FILE);
        conf.load();

        ObjectConverter converter = new ObjectConverter();
        return converter.toObject(conf, ConfigData::new);
    }

    static class ConfigData {
        public String version;

        @Override
        public String toString() {
            return "ConfigData{" +
                    "version='" + version + '\'' +
                    '}';
        }
    }
}
