package heyblack.mineds.util.result;

import com.google.gson.JsonObject;

/**
 * Represents the result of an API call for logging purposes.
 * Does NOT store full input context - that's managed by Session.
 */
public class ApiCallResult {
    public final String sessionId;
    public final String sessionType;
    public final String model;
    public final String url;
    public final JsonObject inputSummary;
    public final JsonObject output;
    public final boolean success;
    public final long durationMs;
    public final String error;

    public ApiCallResult(String sessionId, String sessionType, String model, String url,
                         JsonObject inputSummary, JsonObject output, boolean success,
                         long durationMs, String error) {
        this.sessionId = sessionId;
        this.sessionType = sessionType;
        this.model = model;
        this.url = url;
        this.inputSummary = inputSummary;
        this.output = output;
        this.success = success;
        this.durationMs = durationMs;
        this.error = error;
    }

    /** Converts this result to a JsonObject for serialization. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("timestamp", java.time.Instant.now().toString());
        json.addProperty("sessionId", sessionId);
        json.addProperty("type", sessionType);
        json.addProperty("status", success ? "SUCCESS" : "ERROR");
        json.addProperty("model", model);
        json.addProperty("url", url);
        json.add("inputSummary", inputSummary);
        if (output != null) {
            json.add("output", output);
        } else {
            json.add("output", null);
        }
        json.addProperty("durationMs", durationMs);
        if (error != null) {
            json.addProperty("error", error);
        } else {
            json.add("error", null);
        }
        return json;
    }
}
