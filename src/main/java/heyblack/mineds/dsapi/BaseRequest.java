package heyblack.mineds.dsapi;

import com.google.gson.JsonObject;
import heyblack.mineds.config.ConfigManager;
import heyblack.mineds.config.ConfigOption;

public class BaseRequest {
    private static final ConfigManager CONFIG_MANAGER = ConfigManager.getInstance();

    public static JsonObject populate() {
        JsonObject requestBody = new JsonObject();

        requestBody.addProperty("model", CONFIG_MANAGER.get(ConfigOption.MODEL.id));

        try {
            double temperature = Double.parseDouble(CONFIG_MANAGER.get(ConfigOption.TEMPERATURE.id));
            int maxTokens = Integer.parseInt(CONFIG_MANAGER.get(ConfigOption.MAX_TOKENS.id));

            requestBody.addProperty("temperature", temperature);
            requestBody.addProperty("max_tokens", maxTokens);
        } catch (NumberFormatException e) {
            requestBody.addProperty("temperature", 0.7);
            requestBody.addProperty("max_tokens", 4069);
        }

        requestBody.addProperty("stream", true);

        return requestBody;
    }
}
