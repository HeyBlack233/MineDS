package heyblack.mineds.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import heyblack.mineds.MineDS;

public class ApiCallResult {
    public JsonObject input;
    public JsonObject output;
    public Status status;

    public ApiCallResult(JsonObject input, JsonObject output, boolean isSuccess) {
        this.status = isSuccess ? Status.SUCCESS : Status.FAIL;
        this.input = input;
        this.output = output;
    }

    enum Status {
        SUCCESS, FAIL
    }
}
