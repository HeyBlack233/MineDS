package heyblack.mineds.dsapi;

import com.google.gson.JsonObject;
import heyblack.mineds.config.ConfigManager;
import heyblack.mineds.config.ConfigOption;

import java.util.LinkedHashMap;
import java.util.Map;

public class BaseRequest {
    private static final Map<String, String> properties = new LinkedHashMap<>();

    private static final ConfigManager CONFIG_MANAGER = ConfigManager.getInstance();

    static {
        properties.put("model", CONFIG_MANAGER.get(ConfigOption.MODEL.id));
        properties.put(CONFIG_MANAGER.get(ConfigOption.TEMPERATURE.id), CONFIG_MANAGER.get(ConfigOption.TEMPERATURE.id));
        properties.put(CONFIG_MANAGER.get(ConfigOption.MAX_TOKENS.id), CONFIG_MANAGER.get(ConfigOption.MAX_TOKENS.id));
    }

    public static JsonObject populate() {
        JsonObject requestBody = new JsonObject();

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            requestBody.addProperty(entry.getKey(), entry.getValue());
        }
        requestBody.addProperty("stream", true);

        return requestBody;
    }
}
