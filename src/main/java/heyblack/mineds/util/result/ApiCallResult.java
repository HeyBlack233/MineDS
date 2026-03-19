package heyblack.mineds.util.result;

import com.google.gson.JsonObject;
import heyblack.mineds.config.ConfigManager;
import heyblack.mineds.config.ConfigOption;

import java.time.Instant;

public class ApiCallResult {
    public JsonObject input;
    public JsonObject output;
    public Status status;
    public String timestamp;
    public String url;

    public ApiCallResult(JsonObject input, JsonObject output, boolean isSuccess) {
        this.input = input;
        this.output = output;
        this.status = isSuccess ? Status.SUCCESS : Status.FAIL;
        this.timestamp = Instant.now().toString();
        this.url = ConfigManager.getInstance().get(ConfigOption.URL.id);
    }

    enum Status {
        SUCCESS, FAIL
    }
}
