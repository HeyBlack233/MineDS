package heyblack.mineds.config;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import heyblack.mineds.MineDS;
import heyblack.mineds.session.SessionType;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
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
    private boolean changed = false;

    /**
     * Load config from config file in MineDS.CONFIG_PATH
     * @throws IOException
     */
    public void loadConfig() throws IOException {
        // extract this method for implementing config reload
        if (Files.exists(MineDS.CONFIG_PATH)) {
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

    public void setConfig(String key, String value) {
        config.put(key, value);
        changed = true;
    }

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
                    MineDS.LOGGER.error("[MineDS] Failed to save config! " + retry + "/" + maxRetry);
                }
            }
            MineDS.LOGGER.error(String.format("[MineDS] Failed to save config after %d retries! Closing without save config!", maxRetry));
        }
    }

    /**
     * checks the entries and values of the input config and fix it if needed
     * @param cfgToCheck the config that is going to be fixed by this method
     * @return whether the cfgToCheck is modified or not
     */
    private static boolean fixConfig(Map<String, String> cfgToCheck) {
        Map<String, String> checker = new LinkedHashMap<>();

        for (ConfigOption option : ConfigOption.values()) {
            checker.put(option.id, option.defaultValue);
        }

        boolean bl = false;

        // Migrate old advancement_filters to new separate lists
        if (cfgToCheck.containsKey("advancement_filters") && !cfgToCheck.containsKey(ConfigOption.ADVANCEMENT_BLACKLIST.id)) {
            String oldFilters = cfgToCheck.get("advancement_filters");
            String modeName = cfgToCheck.getOrDefault(ConfigOption.ADVANCEMENT_FILTER_MODE.id, AdvancementFilterMode.BLACKLIST.name);
            AdvancementFilterMode mode = AdvancementFilterMode.fromName(modeName);

            if (mode == AdvancementFilterMode.WHITELIST) {
                cfgToCheck.put(ConfigOption.ADVANCEMENT_WHITELIST.id, oldFilters != null ? oldFilters : "[]");
                cfgToCheck.put(ConfigOption.ADVANCEMENT_BLACKLIST.id, "[]");
            } else {
                cfgToCheck.put(ConfigOption.ADVANCEMENT_BLACKLIST.id, oldFilters != null ? oldFilters : "[]");
                cfgToCheck.put(ConfigOption.ADVANCEMENT_WHITELIST.id, "[]");
            }

            cfgToCheck.remove("advancement_filters");
            MineDS.LOGGER.info("[MineDS] Migrated advancement_filters to separate blacklist/whitelist lists (mode: {})", mode.name);
            bl = true;
        }

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

    // ── Multi-AI Profile Helpers ──────────────────────────────────────

    public AiProfile getAiProfile(String name) {
        String profilesJson = get(ConfigOption.AI_PROFILES.id);
        if (profilesJson == null || profilesJson.equals("{}") || profilesJson.isEmpty()) {
            return buildAiProfileFromLegacyConfig(name);
        }
        try {
            JsonObject profiles = MineDS.GSON.fromJson(profilesJson, JsonObject.class);
            if (profiles.has(name)) {
                return AiProfile.fromJson(name, profiles.getAsJsonObject(name));
            }
        } catch (Exception e) {
            MineDS.LOGGER.warn("[MineDS] Failed to parse ai_profiles, falling back to legacy config");
        }
        return buildAiProfileFromLegacyConfig(name);
    }

    private AiProfile buildAiProfileFromLegacyConfig(String name) {
        AiProfile profile = new AiProfile();
        profile.setName(name);
        profile.setUrl(get(ConfigOption.URL.id));
        profile.setModel(get(ConfigOption.MODEL.id));
        profile.setApiKey(get(ConfigOption.API_KEY.id));
        try { profile.setTemperature(Double.parseDouble(get(ConfigOption.TEMPERATURE.id))); }
        catch (NumberFormatException e) { profile.setTemperature(0.7); }
        try { profile.setMaxTokens(Integer.parseInt(get(ConfigOption.MAX_TOKENS.id))); }
        catch (NumberFormatException e) { profile.setMaxTokens(4096); }
        profile.setSystemMessage(get(ConfigOption.SYSTEM_MESSAGE.id));
        return profile;
    }

    public String getAiNameForSessionType(SessionType type) {
        switch (type) {
            case COMMAND: return get(ConfigOption.COMMAND_SESSION_AI.id);
            case ADVANCEMENT: return get(ConfigOption.ADVANCEMENT_SESSION_AI.id);
            default: return "default";
        }
    }

    public int getSessionTtlHours() {
        try { return Integer.parseInt(get(ConfigOption.SESSION_TTL_HOURS.id)); }
        catch (NumberFormatException e) { return 24; }
    }

    public int getMaxCommandSessions() {
        try { return Integer.parseInt(get(ConfigOption.MAX_COMMAND_SESSIONS.id)); }
        catch (NumberFormatException e) { return 20; }
    }

    public int getMaxAdvancementSessions() {
        try { return Integer.parseInt(get(ConfigOption.MAX_ADVANCEMENT_SESSIONS.id)); }
        catch (NumberFormatException e) { return 10; }
    }
}
