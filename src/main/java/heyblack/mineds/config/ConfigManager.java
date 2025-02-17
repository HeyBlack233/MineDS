package heyblack.mineds.config;

import com.google.gson.reflect.TypeToken;
import heyblack.mineds.MineDS;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigManager {
    public static ConfigManager getInstance() {
        return INSTANCE;
    }
    private static final ConfigManager INSTANCE = new ConfigManager();
    private ConfigManager() {
        try {
            loadConfig();
        } catch (IOException e) {
            MineDS.LOGGER.error("[MineDS] Failed to load config!");
            throw new RuntimeException(e);
        }
    }

    private Map<String, String> config = new LinkedHashMap<>();

    public void loadConfig() throws IOException {
        // extract this method for implementing config reload
        if (Files.exists(MineDS.CONFIG_PATH)) {
//            String content = new String(Files.readAllBytes(MineDS.CONFIG_PATH));
//            config = MineDS.GSON.fromJson(content, Map.class);

            try (Reader reader = new InputStreamReader(
                    new FileInputStream(MineDS.CONFIG_PATH.toFile()), StandardCharsets.UTF_8
            )) {
                config = MineDS.GSON.fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
            }

            if (fixConfig(config)) {
                Files.write(MineDS.CONFIG_PATH, MineDS.GSON.toJson(config).getBytes(StandardCharsets.UTF_8));
            }
        } else {
            for (ConfigOption option : ConfigOption.values()) {
                config.put(option.id, option.defaultValue);
            }
            Files.write(MineDS.CONFIG_PATH, MineDS.GSON.toJson(config).getBytes(StandardCharsets.UTF_8));
        }
        MineDS.LOGGER.info("[MineDS] Config loaded");
    }

    public Map<String, String> getConfig() {
        return new HashMap<>(config);
    }

    public String get(String key) {
        return config.get(key);
    }

    private static boolean fixConfig(Map<String, String> cfgToCheck) {
        Map<String, String> checker = new LinkedHashMap<>();

        for (ConfigOption option : ConfigOption.values()) {
            checker.put(option.id, option.defaultValue);
        }

        boolean bl = false;

        for (Map.Entry<String, String> checkerEntry : checker.entrySet()) {
            if (!cfgToCheck.containsKey(checkerEntry.getKey())) {
                cfgToCheck.put(checkerEntry.getKey(), checkerEntry.getValue());
                MineDS.LOGGER.warn("[MineDS] Missing config option: " +
                        checkerEntry.getKey() + ", added with default value: " + checkerEntry.getValue());

                bl = true;
            }
        }

        try {
            Integer.parseInt(cfgToCheck.get(ConfigOption.MAX_REQUEST.id));
        } catch (NullPointerException | NumberFormatException e) {
            cfgToCheck.put(ConfigOption.MAX_REQUEST.id, ConfigOption.MAX_REQUEST.defaultValue);
            MineDS.LOGGER.warn("[MineDS] Invalid value found for config option " + ConfigOption.MAX_REQUEST.id +
                    ". replaced with default value " + ConfigOption.MAX_REQUEST.defaultValue);

            bl = true;
        }

        try {
            Integer.parseInt(cfgToCheck.get(ConfigOption.MAX_TOKENS.id));
        } catch (NullPointerException | NumberFormatException e) {
            cfgToCheck.put(ConfigOption.MAX_TOKENS.id, ConfigOption.MAX_TOKENS.defaultValue);
            MineDS.LOGGER.warn("[MineDS] Invalid value found for config option " + ConfigOption.MAX_TOKENS.id +
                    ". replaced with default value " + ConfigOption.MAX_TOKENS.defaultValue);

            bl = true;
        }

        try {
            Float.parseFloat(cfgToCheck.get(ConfigOption.TEMPERATURE.id));
        } catch (NullPointerException | NumberFormatException e) {
            cfgToCheck.put(ConfigOption.TEMPERATURE.id, ConfigOption.TEMPERATURE.defaultValue);
            MineDS.LOGGER.warn("[MineDS] Invalid value found for config option " + ConfigOption.TEMPERATURE.id +
                    ". replaced with default value " + ConfigOption.TEMPERATURE.defaultValue);

            bl = true;
        }

        return bl;
    }
}
