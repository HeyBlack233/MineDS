package heyblack.mineds.dsapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import heyblack.mineds.MineDS;
import heyblack.mineds.config.ConfigOption;
import heyblack.mineds.util.ApiCallResult;
import heyblack.mineds.util.ApiLogger;
import heyblack.mineds.util.Message;
import okhttp3.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DSApiHandler {
    public static String callApiOnCommand(String message, Map<String, String> config) {
        try {
            MineDS.LOGGER.info("[MineDS] Calling api");

            MineDS.LOGGER.info("[MineDS] okhttp start");
            OkHttpClient client = new OkHttpClient();
            MineDS.LOGGER.info("[MineDS] okhttp end");

            JsonObject requestObj = populateRequestFromUserInput(message, config);

            MineDS.LOGGER.info("[MineDS] gson start");
            String requestString = MineDS.GSON.toJson(requestObj);
            MineDS.LOGGER.info("[MineDS] gson end");



            JsonObject responseObj;
            String responseString;

            MineDS.LOGGER.info("[MineDS] Building request");
            Request request = new Request.Builder()
                    .url(config.get(ConfigOption.URL.id))
                    .addHeader("Authorization", "Bearer " + config.get(ConfigOption.API_KEY.id))
                    .post(RequestBody.create(requestString, MediaType.parse("application/json; charset=utf-8")))
                    .build();
            MineDS.LOGGER.info("[MineDS] Built request");

            try (Response response = client.newCall(request).execute()) {
//            MineDS.LOGGER.info("[MineDS] ");
                if (response.isSuccessful()) {
                    MineDS.LOGGER.info("[MineDS] Api call success");

                    responseString = response.body().string();
                    responseObj = MineDS.GSON.fromJson(responseString, JsonObject.class);

                    ApiLogger.log(new ApiCallResult(requestObj, responseObj, true));

//                return responseString.split("\"content\": \"")[1].split("\"")[0];

                    JsonArray choices = responseObj.getAsJsonArray("choices");
                    if (choices != null && !choices.isJsonNull()) {
                        JsonObject firstChoice = choices.get(0).getAsJsonObject();
                        JsonObject jsonMessage = firstChoice.getAsJsonObject("message");
                        return jsonMessage.get("content").getAsString();
                    } else {
                        return "响应中未找到有效内容";
                    }
                } else {
                    MineDS.LOGGER.warn("[MineDS] Api call fail: " + response.code());

                    responseString = response.body().string();
                    responseObj = MineDS.GSON.fromJson(responseString, JsonObject.class);

                    ApiLogger.log(new ApiCallResult(requestObj, responseObj, false));

                    return "请求失败: " + response.code();
                }

            } catch (Throwable e) {
                MineDS.LOGGER.error("[MineDS] Error when calling api " + e);
                e.printStackTrace();

                return "请求出现异常: " + e;
            }

        } catch (Throwable e) {
            MineDS.LOGGER.error(e.getCause());
            return "error";
        }
    }

    private static JsonObject populateRequestFromUserInput(String message, Map<String, String> config) {
        MineDS.LOGGER.info("[MineDS] pplt start");
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", config.get(ConfigOption.SYSTEM_MESSAGE.id)));
        messages.add(new Message("user", message));

        JsonObject body = new JsonObject();
        body.addProperty("model", config.get(ConfigOption.MODEL.id));
        body.add("messages", MineDS.GSON.toJsonTree(messages));
        body.addProperty("temperature", Double.parseDouble(config.get(ConfigOption.TEMPERATURE.id)));
        body.addProperty("max_tokens", Integer.parseInt(config.get(ConfigOption.MAX_TOKENS.id)));


        MineDS.LOGGER.info("[MineDS] pplt end");

        return body;
    }
}
