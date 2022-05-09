package com.alttd.datalock;

import com.google.common.base.Throwables;
import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import ninja.leaping.configurate.yaml.YAMLConfigurationLoader;
import org.yaml.snakeyaml.DumperOptions;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class Config {
    private static final Pattern PATH_PATTERN = Pattern.compile("\\.");
    private static final String HEADER = "";

    public static ConfigurationNode config;
    public static YAMLConfigurationLoader configLoader;

    static int version;
    static boolean verbose;

    public static File CONFIG_PATH;

    public static void init() { // todo setup share for the config
        CONFIG_PATH = new File(DataLock.getDataDirectory().toAbsolutePath() + File.separator + "DataLock");
        File configFile = new File(CONFIG_PATH, "config.yml");

        configLoader = YAMLConfigurationLoader.builder()
                .setFile(configFile)
                .setFlowStyle(DumperOptions.FlowStyle.BLOCK)
                .build();
        if (!configFile.getParentFile().exists()) {
            if (!configFile.getParentFile().mkdirs()) {
                return;
            }
        }
        if (!configFile.exists()) {
            try {
                if (!configFile.createNewFile()) {
                    return;
                }
            } catch (IOException error) {
                error.printStackTrace();
            }
        }

        try {
            config = configLoader.load(ConfigurationOptions.defaults().setHeader(HEADER));
        } catch (IOException e) {
            e.printStackTrace();
        }

        verbose = getBoolean("verbose", true);
        version = getInt("config-version", 1);

        readConfig(Config.class, null);
        try {
            configLoader.save(config);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void readConfig(Class<?> clazz, Object instance) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (Modifier.isPrivate(method.getModifiers())) {
                if (method.getParameterTypes().length == 0 && method.getReturnType() == Void.TYPE) {
                    try {
                        method.setAccessible(true);
                        method.invoke(instance);
                    } catch (InvocationTargetException | IllegalAccessException ex) {
                        throw Throwables.propagate(ex.getCause());
                    }
                }
            }
        }
        try {
            configLoader.save(config);
        } catch (IOException ex) {
            throw Throwables.propagate(ex.getCause());
        }
    }

    public static void saveConfig() {
        try {
            configLoader.save(config);
        } catch (IOException ex) {
            throw Throwables.propagate(ex.getCause());
        }
    }

    private static Object[] splitPath(String key) {
        return PATH_PATTERN.split(key);
    }

    private static void set(String path, Object def) {
        if (config.getNode(splitPath(path)).isVirtual())
            config.getNode(splitPath(path)).setValue(def);
    }

    private static void setString(String path, String def) {
        try {
            if (config.getNode(splitPath(path)).isVirtual())
                config.getNode(splitPath(path)).setValue(TypeToken.of(String.class), def);
        } catch (ObjectMappingException ex) {
        }
    }

    private static boolean getBoolean(String path, boolean def) {
        set(path, def);
        return config.getNode(splitPath(path)).getBoolean(def);
    }

    private static double getDouble(String path, double def) {
        set(path, def);
        return config.getNode(splitPath(path)).getDouble(def);
    }

    private static int getInt(String path, int def) {
        set(path, def);
        return config.getNode(splitPath(path)).getInt(def);
    }

    private static String getString(String path, String def) {
        setString(path, def);
        return config.getNode(splitPath(path)).getString(def);
    }

    private static Long getLong(String path, Long def) {
        set(path, def);
        return config.getNode(splitPath(path)).getLong(def);
    }

    private static <T> List<String> getList(String path, T def) {
        try {
            set(path, def);
            return config.getNode(splitPath(path)).getList(TypeToken.of(String.class));
        } catch (ObjectMappingException ex) {
        }
        return new ArrayList<>();
    }

    /**
     * ONLY EDIT ANYTHING BELOW THIS LINE
     **/
    public static List<String> PLUGIN_MESSAGE_CHANNELS = new ArrayList<>(List.of("example_plugin:table_1"));

    private static void loadGroups() {
        PLUGIN_MESSAGE_CHANNELS = getList("settings.channels", new ArrayList<>(List.of("example_plugin:table_1")));
    }
}
