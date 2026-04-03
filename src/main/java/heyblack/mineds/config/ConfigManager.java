package heyblack.mineds.config;

import com.google.gson.reflect.TypeToken;
import heyblack.mineds.MineDS;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Configuration manager responsible for loading, saving, and validating mod
 * configuration.
 * Uses singleton pattern to ensure a single global instance.
 * Configuration is stored in JSON format at {@link MineDS#CONFIG_PATH}.
 */
public class ConfigManager {
    /**
     * Gets the singleton instance of the configuration manager.
     *
     * @return ConfigManager instance
     */
    public static ConfigManager getInstance() {
        return INSTANCE;
    }

    private static final ConfigManager INSTANCE = new ConfigManager();

    /**
     * Private constructor to prevent external instantiation.
     * Automatically loads configuration during construction.
     */
    private ConfigManager() {
        try {
            loadConfig();
        } catch (IOException e) {
            MineDS.LOGGER.error("[MineDS] ConfigManager - Failed to load config!", e);
            throw new RuntimeException(e);
        }
    }

    private Map<String, String> config = new LinkedHashMap<>();
    private boolean changed = false;

    /**
     * Loads configuration from the config file. Creates default config if file
     * doesn't exist.
     * Automatically validates and repairs missing or invalid options after loading.
     *
     * @throws IOException if reading or writing the config file fails
     */
    public void loadConfig() throws IOException {
        // extract this method for implementing config reload
        if (Files.exists(MineDS.CONFIG_PATH)) {
            try (Reader reader = new InputStreamReader(
                    new FileInputStream(MineDS.CONFIG_PATH.toFile()), StandardCharsets.UTF_8)) {
                config = MineDS.GSON.fromJson(reader, new TypeToken<Map<String, String>>() {
                }.getType());
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

    /**
     * Gets a copy of the configuration. Modifying the returned Map does not affect
     * internal config.
     *
     * @return a copy of the configuration Map
     */
    public Map<String, String> getConfig() {
        return new HashMap<>(config);
    }

    /**
     * Gets the configuration value for the specified key.
     *
     * @param key the config option ID
     * @return the config value, or null if key doesn't exist
     */
    public String get(String key) {
        return config.get(key);
    }

    /**
     * Sets the value of a config option. This marks the config as modified,
     * and {@link #saveConfig()} should be called at an appropriate time to persist
     * changes.
     *
     * @param key   the config option ID
     * @param value the new config value
     */
    public void setConfig(String key, String value) {
        config.put(key, value);
        changed = true;
    }

    /**
     * Saves configuration to file. Only saves if configuration has been modified.
     * Retries up to 3 times before giving up and logging an error.
     */
    public void saveConfig() {
        if (changed) {
            int maxRetry = 3;
            int retry = 0;

            while (retry < maxRetry) {
                try {
                    Files.write(MineDS.CONFIG_PATH, MineDS.GSON.toJson(config).getBytes(StandardCharsets.UTF_8));

                    return;
                } catch (IOException e) {
                    retry++;
                    MineDS.LOGGER.error("[MineDS] ConfigManager - Failed to save config! " + retry + "/" + maxRetry);
                }
            }
            MineDS.LOGGER.error(String
                    .format("[MineDS] ConfigManager - Failed to save config after %d retries! Closing without save config!",
                            maxRetry));
        }
    }

    /**
     * Reloads configuration from file. Re-reads and validates the config.
     *
     * @return true if reload succeeded, false otherwise
     */
    public boolean reloadConfig() {
        try {
            loadConfig();
            return true;
        } catch (IOException e) {
            MineDS.LOGGER.error("[MineDS] ConfigManager - Failed to reload config!", e);
            return false;
        }
    }

    /**
     * Checks and repairs configuration options. This method:
     * 1. Adds missing config options
     * 2. Validates numeric config values
     * 3. Checks config values are within valid ranges
     * 4. Repairs invalid values with defaults
     *
     * @param cfgToCheck the config Map to check and repair
     * @return true if config was modified, false otherwise
     */
    private static boolean fixConfig(Map<String, String> cfgToCheck) {
        boolean modified = false;

        // 检查并添加缺失的配置项
        for (ConfigOption option : ConfigOption.values()) {
            if (!cfgToCheck.containsKey(option.id)) {
                cfgToCheck.put(option.id, option.defaultValue);
                MineDS.LOGGER.warn("[MineDS] ConfigManager - Missing config option: " + option.id +
                        ", added with default value: " + option.defaultValue);
                modified = true;
            }
        }

        // 验证并修复数值类型的配置（包括范围检查）
        modified |= validateAndFix(cfgToCheck, ConfigOption.MAX_REQUEST);
        modified |= validateAndFix(cfgToCheck, ConfigOption.MAX_TOKENS);
        modified |= validateAndFix(cfgToCheck, ConfigOption.TEMPERATURE);

        return modified;
    }

    /**
     * Validates a config option's value (including type and range checks), repairs
     * with default if invalid.
     *
     * @param cfgToCheck the config Map
     * @param option     the config option to validate
     * @return true if config was repaired, false otherwise
     */
    private static boolean validateAndFix(Map<String, String> cfgToCheck, ConfigOption option) {
        String value = cfgToCheck.get(option.id);

        // 检查类型
        if (value == null) {
            cfgToCheck.put(option.id, option.defaultValue);
            MineDS.LOGGER.warn("[MineDS] ConfigManager - Null value for " + option.id +
                    ", replaced with default: " + option.defaultValue);
            return true;
        }

        // 检查数值类型和范围
        if (option.minValue != null && option.maxValue != null) {
            if (!option.isValid(value)) {
                cfgToCheck.put(option.id, option.defaultValue);
                MineDS.LOGGER.warn("[MineDS] ConfigManager - Out of range or invalid value for " + option.id +
                        " (value: " + value + ", range: " + option.minValue + "-" + option.maxValue +
                        "), replaced with default: " + option.defaultValue);
                return true;
            }
        }

        return false;
    }
}
