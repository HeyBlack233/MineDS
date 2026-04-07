package heyblack.mineds.dsapi;

import com.google.gson.JsonObject;
import heyblack.mineds.config.AiProfile;
import heyblack.mineds.config.ConfigManager;

/**
 * Builds base API request body with model and parameters.
 */
public class BaseRequest {
    @Deprecated
    public static JsonObject populate() {
        return populateFromAiProfile(ConfigManager.getInstance().getAiProfile("default"));
    }

    public static JsonObject populateFromAiProfile(AiProfile aiProfile) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", aiProfile.getModel());
        requestBody.addProperty("temperature", aiProfile.getTemperature());
        requestBody.addProperty("max_tokens", aiProfile.getMaxTokens());
        requestBody.addProperty("stream", true);
        return requestBody;
    }
}
