package heyblack.mineds.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.TranslatableText;

public class ConfigScreen {
    private static final ConfigManager CONFIG_MANAGER = ConfigManager.getInstance();

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(new TranslatableText("mineds.config.title"));

        ConfigCategory api = builder.getOrCreateCategory(new TranslatableText("mineds.config.category.api"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

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

        api.addEntry(entryBuilder.startStrField(new TranslatableText("mineds.config.option.ai_name"), CONFIG_MANAGER.get(ConfigOption.AI_NAME.id))
                .setDefaultValue(ConfigOption.AI_NAME.defaultValue)
                .setSaveConsumer(newValue -> CONFIG_MANAGER.setConfig(ConfigOption.AI_NAME.id, newValue))
                .build());

        api.addEntry(entryBuilder.startIntField(new TranslatableText("mineds.config.option.max_request"), Integer.parseInt(CONFIG_MANAGER.get(ConfigOption.MAX_REQUEST.id)))
                .setDefaultValue(Integer.parseInt(ConfigOption.MAX_REQUEST.defaultValue))
                .setSaveConsumer(newValue -> CONFIG_MANAGER.setConfig(ConfigOption.MAX_REQUEST.id, String.valueOf(newValue)))
                .build());

        api.addEntry(entryBuilder.startIntField(new TranslatableText("mineds.config.option.max_tokens"), Integer.parseInt(CONFIG_MANAGER.get(ConfigOption.MAX_TOKENS.id)))
                .setDefaultValue(Integer.parseInt(ConfigOption.MAX_TOKENS.defaultValue))
                .setSaveConsumer(newValue -> CONFIG_MANAGER.setConfig(ConfigOption.MAX_TOKENS.id, String.valueOf(newValue)))
                .build());

        api.addEntry(entryBuilder.startDoubleField(new TranslatableText("mineds.config.option.temperature"), Double.parseDouble(CONFIG_MANAGER.get(ConfigOption.TEMPERATURE.id)))
                .setDefaultValue(Double.parseDouble(CONFIG_MANAGER.get(ConfigOption.TEMPERATURE.id)))
                .setSaveConsumer(newValue -> CONFIG_MANAGER.setConfig(ConfigOption.TEMPERATURE.id, String.valueOf(newValue)))
                .build());

        return builder.build();
    }
}
