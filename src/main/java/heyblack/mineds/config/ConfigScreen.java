package heyblack.mineds.config;

import com.google.common.collect.ImmutableList;
import heyblack.mineds.MineDS;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.TranslatableText;

import java.util.ArrayList;
import java.util.List;

public class ConfigScreen {
    private static final ConfigManager CONFIG_MANAGER = ConfigManager.getInstance();

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(new TranslatableText("mineds.config.title"));
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        {
            ConfigCategory api = builder.getOrCreateCategory(new TranslatableText("mineds.config.category.api"));

            api.addEntry(entryBuilder.startStrField(new TranslatableText("mineds.config.option.url"), CONFIG_MANAGER.get(ConfigOption.URL.id))
                    .setDefaultValue(ConfigOption.URL.defaultValue)
                    .setSaveConsumer(newValue -> CONFIG_MANAGER.setConfig(ConfigOption.URL.id, newValue))
                    .build());

            api.addEntry(entryBuilder.startStrField(new TranslatableText("mineds.config.option.api_key"), CONFIG_MANAGER.get(ConfigOption.API_KEY.id))
                    .setDefaultValue(ConfigOption.API_KEY.defaultValue)
                    .setSaveConsumer(newValue -> CONFIG_MANAGER.setConfig(ConfigOption.API_KEY.id, newValue))
                    .build());

            api.addEntry(entryBuilder.startStrField(new TranslatableText("mineds.config.option.model"), CONFIG_MANAGER.get(ConfigOption.MODEL.id))
                    .setDefaultValue(ConfigOption.MODEL.defaultValue)
                    .setSaveConsumer(newValue -> CONFIG_MANAGER.setConfig(ConfigOption.MODEL.id, newValue))
                    .build());

            api.addEntry(entryBuilder.startStrField(new TranslatableText("mineds.config.option.sys_message"), CONFIG_MANAGER.get(ConfigOption.SYSTEM_MESSAGE.id))
                    .setDefaultValue(ConfigOption.SYSTEM_MESSAGE.defaultValue)
                    .setSaveConsumer(newValue -> CONFIG_MANAGER.setConfig(ConfigOption.SYSTEM_MESSAGE.id, newValue))
                    .build());

            api.addEntry(entryBuilder.startIntField(new TranslatableText("mineds.config.option.max_tokens"), Integer.parseInt(CONFIG_MANAGER.get(ConfigOption.MAX_TOKENS.id)))
                    .setDefaultValue(Integer.parseInt(ConfigOption.MAX_TOKENS.defaultValue))
                    .setSaveConsumer(newValue -> CONFIG_MANAGER.setConfig(ConfigOption.MAX_TOKENS.id, String.valueOf(newValue)))
                    .build());

            api.addEntry(entryBuilder.startDoubleField(new TranslatableText("mineds.config.option.temperature"), Double.parseDouble(CONFIG_MANAGER.get(ConfigOption.TEMPERATURE.id)))
                    .setDefaultValue(Double.parseDouble(CONFIG_MANAGER.get(ConfigOption.TEMPERATURE.id)))
                    .setSaveConsumer(newValue -> CONFIG_MANAGER.setConfig(ConfigOption.TEMPERATURE.id, String.valueOf(newValue)))
                    .build());
        }

        {
            ConfigCategory multiAi = builder.getOrCreateCategory(new TranslatableText("mineds.config.category.multi_ai"));
            multiAi.addEntry(entryBuilder.startTextDescription(new TranslatableText("mineds.config.option.ai_profiles_desc")).build());

            multiAi.addEntry(entryBuilder.startStrField(new TranslatableText("mineds.config.option.command_session_ai"), CONFIG_MANAGER.get(ConfigOption.COMMAND_SESSION_AI.id))
                    .setDefaultValue(ConfigOption.COMMAND_SESSION_AI.defaultValue)
                    .setSaveConsumer(newValue -> CONFIG_MANAGER.setConfig(ConfigOption.COMMAND_SESSION_AI.id, newValue))
                    .setTooltip(new TranslatableText("mineds.config.tooltip.command_session_ai")).build());

            multiAi.addEntry(entryBuilder.startStrField(new TranslatableText("mineds.config.option.advancement_session_ai"), CONFIG_MANAGER.get(ConfigOption.ADVANCEMENT_SESSION_AI.id))
                    .setDefaultValue(ConfigOption.ADVANCEMENT_SESSION_AI.defaultValue)
                    .setSaveConsumer(newValue -> CONFIG_MANAGER.setConfig(ConfigOption.ADVANCEMENT_SESSION_AI.id, newValue))
                    .setTooltip(new TranslatableText("mineds.config.tooltip.advancement_session_ai")).build());

            multiAi.addEntry(entryBuilder.startIntField(new TranslatableText("mineds.config.option.session_ttl_hours"), CONFIG_MANAGER.getSessionTtlHours())
                    .setDefaultValue(Integer.parseInt(ConfigOption.SESSION_TTL_HOURS.defaultValue))
                    .setMin(1).setMax(168)
                    .setSaveConsumer(newValue -> CONFIG_MANAGER.setConfig(ConfigOption.SESSION_TTL_HOURS.id, String.valueOf(newValue)))
                    .setTooltip(new TranslatableText("mineds.config.tooltip.session_ttl_hours")).build());

            multiAi.addEntry(entryBuilder.startIntField(new TranslatableText("mineds.config.option.max_command_sessions"), CONFIG_MANAGER.getMaxCommandSessions())
                    .setDefaultValue(Integer.parseInt(ConfigOption.MAX_COMMAND_SESSIONS.defaultValue))
                    .setMin(1).setMax(100)
                    .setSaveConsumer(newValue -> CONFIG_MANAGER.setConfig(ConfigOption.MAX_COMMAND_SESSIONS.id, String.valueOf(newValue)))
                    .setTooltip(new TranslatableText("mineds.config.tooltip.max_command_sessions")).build());

            multiAi.addEntry(entryBuilder.startIntField(new TranslatableText("mineds.config.option.max_advancement_sessions"), CONFIG_MANAGER.getMaxAdvancementSessions())
                    .setDefaultValue(Integer.parseInt(ConfigOption.MAX_ADVANCEMENT_SESSIONS.defaultValue))
                    .setMin(1).setMax(100)
                    .setSaveConsumer(newValue -> CONFIG_MANAGER.setConfig(ConfigOption.MAX_ADVANCEMENT_SESSIONS.id, String.valueOf(newValue)))
                    .setTooltip(new TranslatableText("mineds.config.tooltip.max_advancement_sessions")).build());
        }

        {
            ConfigCategory inGameBehaviourGeneral = builder.getOrCreateCategory(new TranslatableText("mineds.config.category.in_game_behaviour_general"));

            inGameBehaviourGeneral.addEntry(entryBuilder.startStrField(new TranslatableText("mineds.config.option.ai_name"), CONFIG_MANAGER.get(ConfigOption.AI_NAME.id))
                    .setDefaultValue(ConfigOption.AI_NAME.defaultValue)
                    .setSaveConsumer(newValue -> CONFIG_MANAGER.setConfig(ConfigOption.AI_NAME.id, newValue))
                    .build());

            inGameBehaviourGeneral.addEntry(entryBuilder.startIntField(new TranslatableText("mineds.config.option.max_request"), Integer.parseInt(CONFIG_MANAGER.get(ConfigOption.MAX_REQUEST.id)))
                    .setDefaultValue(Integer.parseInt(ConfigOption.MAX_REQUEST.defaultValue))
                    .setSaveConsumer(newValue -> CONFIG_MANAGER.setConfig(ConfigOption.MAX_REQUEST.id, String.valueOf(newValue)))
                    .build());
        }

        {
            ConfigCategory inGameBehaviourAdvancement = builder.getOrCreateCategory(new TranslatableText("mineds.config.category.in_game_behaviour_advancement"));

            inGameBehaviourAdvancement.addEntry(entryBuilder.startBooleanToggle(new TranslatableText("mineds.config.option.advancement_call"), Boolean.parseBoolean(CONFIG_MANAGER.get(ConfigOption.ADVANCEMENT_CALL.id)))
                    .setDefaultValue(Boolean.parseBoolean(ConfigOption.ADVANCEMENT_CALL.defaultValue))
                    .setSaveConsumer(newValue -> CONFIG_MANAGER.setConfig(ConfigOption.ADVANCEMENT_CALL.id, String.valueOf(newValue)))
                    .build());

            inGameBehaviourAdvancement.addEntry(entryBuilder.startBooleanToggle(new TranslatableText("mineds.config.option.advancement_filter_enabled"), Boolean.parseBoolean(CONFIG_MANAGER.get(ConfigOption.ADVANCEMENT_FILTER_ENABLED.id)))
                    .setDefaultValue(Boolean.parseBoolean(ConfigOption.ADVANCEMENT_FILTER_ENABLED.defaultValue))
                    .setSaveConsumer(newValue -> CONFIG_MANAGER.setConfig(ConfigOption.ADVANCEMENT_FILTER_ENABLED.id, String.valueOf(newValue)))
                    .build());

            inGameBehaviourAdvancement.addEntry(entryBuilder.startEnumSelector(
                            new TranslatableText("mineds.config.option.advancement_filter_mode"),
                            AdvancementFilterMode.class,
                            AdvancementFilterMode.fromName(CONFIG_MANAGER.get(ConfigOption.ADVANCEMENT_FILTER_MODE.id)))
                    .setEnumNameProvider(mode -> {
                        AdvancementFilterMode afm = (AdvancementFilterMode) mode;
                        return new TranslatableText("mineds.config.enum.advancement_filter_mode." + afm.name.toLowerCase());
                    })
                    .setDefaultValue(AdvancementFilterMode.BLACKLIST)
                    .setSaveConsumer(newValue -> CONFIG_MANAGER.setConfig(ConfigOption.ADVANCEMENT_FILTER_MODE.id, newValue.name))
                    .setTooltip(new TranslatableText("mineds.config.tooltip.advancement_filter_mode"))
                    .build());

            List<String> currentBlacklist = parseFilterList(CONFIG_MANAGER.get(ConfigOption.ADVANCEMENT_BLACKLIST.id));
            inGameBehaviourAdvancement.addEntry(entryBuilder.startStrList(new TranslatableText("mineds.config.option.advancement_blacklist"), new ArrayList<>(currentBlacklist))
                    .setDefaultValue(new ArrayList<>())
                    .setSaveConsumer(newValue -> {
                        List<String> filtered = new ArrayList<>();
                        for (String s : newValue) {
                            if (s != null && !s.trim().isEmpty()) {
                                filtered.add(s.trim());
                            }
                        }
                        CONFIG_MANAGER.setConfig(ConfigOption.ADVANCEMENT_BLACKLIST.id, listToJson(filtered));
                    })
                    .setTooltip(new TranslatableText("mineds.config.tooltip.advancement_blacklist"))
                    .setExpanded(true)
                    .setInsertInFront(true)
                    .build());

            List<String> currentWhitelist = parseFilterList(CONFIG_MANAGER.get(ConfigOption.ADVANCEMENT_WHITELIST.id));
            inGameBehaviourAdvancement.addEntry(entryBuilder.startStrList(new TranslatableText("mineds.config.option.advancement_whitelist"), new ArrayList<>(currentWhitelist))
                    .setDefaultValue(new ArrayList<>())
                    .setSaveConsumer(newValue -> {
                        List<String> filtered = new ArrayList<>();
                        for (String s : newValue) {
                            if (s != null && !s.trim().isEmpty()) {
                                filtered.add(s.trim());
                            }
                        }
                        CONFIG_MANAGER.setConfig(ConfigOption.ADVANCEMENT_WHITELIST.id, listToJson(filtered));
                    })
                    .setTooltip(new TranslatableText("mineds.config.tooltip.advancement_whitelist"))
                    .setExpanded(true)
                    .setInsertInFront(true)
                    .build());

            inGameBehaviourAdvancement.addEntry(entryBuilder.startStrField(new TranslatableText("mineds.config.option.advancement_prompt"), CONFIG_MANAGER.get(ConfigOption.ADVANCEMENT_PROMPT.id))
                    .setDefaultValue(ConfigOption.ADVANCEMENT_PROMPT.defaultValue)
                    .setSaveConsumer(newValue -> CONFIG_MANAGER.setConfig(ConfigOption.ADVANCEMENT_PROMPT.id, newValue))
                    .setTooltip(new TranslatableText("mineds.config.tooltip.advancement_prompt"))
                    .build());
        }

        return builder.build();
    }
    
    /**
     * Parses JSON string to list for the filter config.
     * 
     * @param json the JSON string
     * @return the list of filter patterns
     */
    private static List<String> parseFilterList(String json) {
        try {
            return MineDS.GSON.fromJson(json, new com.google.gson.reflect.TypeToken<List<String>>(){}.getType());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    /**
     * Converts list to JSON string for storage.
     * 
     * @param list the list to convert
     * @return JSON string
     */
    private static String listToJson(List<String> list) {
        return MineDS.GSON.toJson(list);
    }
}
