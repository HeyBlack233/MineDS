package heyblack.mineds.util.result;

import com.google.gson.JsonObject;

import java.time.Instant;

public class ApiCallResult {
    public JsonObject input;
    public JsonObject output;
    public Status status;
    public String timestamp;

    public ApiCallResult(JsonObject input, JsonObject output, boolean isSuccess) {
        this.input = input;
        this.output = output;
        this.status = isSuccess ? Status.SUCCESS : Status.FAIL;
        this.timestamp = Instant.now().toString();
    }

    enum Status {
        SUCCESS, FAIL
    }
}
