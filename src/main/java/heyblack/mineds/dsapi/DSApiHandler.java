package heyblack.mineds.dsapi;

import com.google.gson.JsonObject;
import heyblack.mineds.MineDS;
import heyblack.mineds.config.ConfigOption;
import heyblack.mineds.dsapi.response.ResponseHandler;
import heyblack.mineds.util.message.RegularInputMessage;
import heyblack.mineds.util.result.CallResultLogHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DSApiHandler {
    public static void callApiStreaming(
            String message,
            Map<String, String> config,
            boolean pullContentFromLastChat,
            ApiCallType type,
            ResponseHandler handler
    ) {
        MineDS.LOGGER.info("[MineDS] Calling API");
        try {
            JsonObject requestBody = populateRequestBody(message, config, pullContentFromLastChat);

            URL url = new URL(config.get(ConfigOption.URL.id));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + config.get(ConfigOption.API_KEY.id));
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(MineDS.GSON.toJson(requestBody).getBytes(StandardCharsets.UTF_8));
            }

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                MineDS.LOGGER.info("[MineDS] API call success");
                processStream(connection, handler);
                handler.onComplete(message, pullContentFromLastChat);
            } else {
                MineDS.LOGGER.warn("[MineDS] API call fail");
                handler.onError("HTTP错误: " + connection.getResponseCode());
            }
        } catch (Exception e) {
            MineDS.LOGGER.error("[MineDS] API call error");
            handler.onError("请求失败: " + e.getMessage());
        }
    }

    private static void processStream(
            HttpURLConnection connection,
            ResponseHandler handler
    ) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String jsonData = line.substring(6).trim();
                    if ("[DONE]".equals(jsonData)) break;

                    JsonObject response = MineDS.GSON.fromJson(jsonData, JsonObject.class);
                    String content = extractDeltaContent(response);
                    String reasoning_content = extractDeltaContentReasoning(response);
                    handler.onContentChunk(content, reasoning_content);
                }
            }
        }
    }

    private static String extractDeltaContent(JsonObject response) {
        try {
            return response.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("delta")
                    .get("content").getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String extractDeltaContentReasoning(JsonObject response) {
        try {
            return response.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("delta")
                    .get("reasoning_content").getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    public static JsonObject populateRequestBody(String message, Map<String, String> config, boolean pullContentFromLastChat) throws Exception {
        List<RegularInputMessage> messages = new ArrayList<>();
        if (pullContentFromLastChat) {
            MineDS.LOGGER.info("[MineDS] Pulling context from last api call result");
            messages.addAll(CallResultLogHandler.getContext());
        } else { // system prompt should only be sent when starting new chat
            messages.add(new RegularInputMessage("system", config.get(ConfigOption.SYSTEM_MESSAGE.id)));
        }

        messages.add(new RegularInputMessage("user", message)); // new input message should always be sent

        JsonObject requestBody = BaseRequest.populate();
        requestBody.add("messages", MineDS.GSON.toJsonTree(messages));

        return requestBody;
    }
}
