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
 * 配置管理器，负责加载、保存和验证模组配置。
 * 使用单例模式确保全局唯一实例。
 * 配置以 JSON 格式存储在 {@link MineDS#CONFIG_PATH}。
 */
public class ConfigManager {
    /**
     * 获取配置管理器单例实例
     * 
     * @return ConfigManager 实例
     */
    public static ConfigManager getInstance() {
        return INSTANCE;
    }

    private static final ConfigManager INSTANCE = new ConfigManager();

    /**
     * 私有构造函数，防止外部实例化
     * 在构造时自动加载配置
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
     * 从配置文件加载配置。如果文件不存在则创建默认配置。
     * 加载后会自动验证配置完整性并修复缺失或无效的选项。
     *
     * @throws IOException 如果读取或写入配置文件失败
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
     * 获取配置的副本。修改返回的 Map 不会影响内部配置。
     *
     * @return 配置 Map 的副本
     */
    public Map<String, String> getConfig() {
        return new HashMap<>(config);
    }

    /**
     * 获取指定键的配置值
     *
     * @param key 配置项的 ID
     * @return 配置值，如果键不存在则返回 null
     */
    public String get(String key) {
        return config.get(key);
    }

    /**
     * 设置配置项的值。此操作会标记配置为已修改，
     * 需要在适当时机调用 {@link #saveConfig()} 保存。
     *
     * @param key   配置项的 ID
     * @param value 新的配置值
     */
    public void setConfig(String key, String value) {
        config.put(key, value);
        changed = true;
    }

    /**
     * 保存配置到文件。仅当配置被修改过时才执行保存操作。
     * 保存时会进行 3 次重试，如果都失败则放弃保存并记录错误日志。
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
     * 重新加载配置。从配置文件重新读取配置并验证。
     *
     * @return 如果加载成功返回 true，否则返回 false
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
     * 检查并修复配置项。此方法会：
     * 1. 添加缺失的配置项
     * 2. 验证数值类型的配置是否有效
     * 3. 检查配置值是否在有效范围内
     * 4. 对无效的配置使用默认值修复
     *
     * @param cfgToCheck 需要检查和修复的配置 Map
     * @return 如果配置被修改过则返回 true，否则返回 false
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
     * 验证指定配置项的值是否有效（包括类型和范围检查），无效则使用默认值修复。
     *
     * @param cfgToCheck 配置 Map
     * @param option     要验证的配置选项
     * @return 如果配置被修复则返回 true
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
