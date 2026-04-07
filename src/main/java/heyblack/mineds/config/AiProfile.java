package heyblack.mineds.config;

import com.google.gson.JsonObject;

/**
 * Represents a single AI profile configuration.
 */
public class AiProfile {
    private String name;
    private String url;
    private String model;
    private String apiKey;
    private double temperature;
    private int maxTokens;
    private String systemMessage;

    public AiProfile() {}

    public static AiProfile fromJson(String name, JsonObject json) {
        AiProfile profile = new AiProfile();
        profile.name = name;
        profile.url = json.has("url") ? json.get("url").getAsString() : "";
        profile.model = json.has("model") ? json.get("model").getAsString() : "";
        profile.apiKey = json.has("api_key") ? json.get("api_key").getAsString() : "";
        profile.temperature = json.has("temperature") ? json.get("temperature").getAsDouble() : 0.7;
        profile.maxTokens = json.has("max_tokens") ? json.get("max_tokens").getAsInt() : 4096;
        profile.systemMessage = json.has("system_message") ? json.get("system_message").getAsString() : "";
        return profile;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("url", url);
        json.addProperty("model", model);
        json.addProperty("api_key", apiKey);
        json.addProperty("temperature", temperature);
        json.addProperty("max_tokens", maxTokens);
        json.addProperty("system_message", systemMessage);
        return json;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public String getSystemMessage() { return systemMessage; }
    public void setSystemMessage(String systemMessage) { this.systemMessage = systemMessage; }

    @Override
    public String toString() { return name + " (" + model + ")"; }
}
