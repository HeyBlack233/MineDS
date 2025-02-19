package heyblack.mineds.dsapi;

import com.google.gson.JsonObject;
import heyblack.mineds.MineDS;
import heyblack.mineds.config.ConfigOption;
import heyblack.mineds.util.message.UserMessage;

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
    public interface StreamResponseHandler {
        void onContentChunk(String content, String reasoning_content);
        void onComplete();
        void onError(String error);
    }
    public static void callApiStreaming(String message, Map<String, String> config, StreamResponseHandler handler) {
        MineDS.LOGGER.info("[MineDS] Calling API");
        try {
            JsonObject requestBody = populateRequestFromUserInput(message, config);

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
                handler.onComplete();
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
            StreamResponseHandler handler
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

    public static JsonObject populateRequestFromUserInput(String message, Map<String, String> config) {
        List<UserMessage> messages = new ArrayList<>();
        messages.add(new UserMessage("system", config.get(ConfigOption.SYSTEM_MESSAGE.id)));
        messages.add(new UserMessage("user", message));

        JsonObject body = new JsonObject();
        body.addProperty("model", config.get(ConfigOption.MODEL.id));
        body.add("messages", MineDS.GSON.toJsonTree(messages));
        body.addProperty("temperature", Double.parseDouble(config.get(ConfigOption.TEMPERATURE.id)));
        body.addProperty("max_tokens", Integer.parseInt(config.get(ConfigOption.MAX_TOKENS.id)));
        body.addProperty("stream", true);

        return body;
    }
}
