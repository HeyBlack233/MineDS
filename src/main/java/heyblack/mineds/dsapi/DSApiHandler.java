package heyblack.mineds.dsapi;

import com.google.gson.JsonObject;
import heyblack.mineds.MineDS;
import heyblack.mineds.config.ConfigOption;
import heyblack.mineds.util.ApiCallResult;
import heyblack.mineds.util.ApiLogger;
import heyblack.mineds.util.Message;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DSApiHandler {
    public static String callApiOnCommand(String message, Map<String, String> config) {
        HttpURLConnection connection = null;
        try {
            MineDS.LOGGER.info("[MineDS] Calling API");

            JsonObject requestBody = populateRequestFromUserInput(message, config);
            String requestString = MineDS.GSON.toJson(requestBody);

            URL url = new URL(config.get(ConfigOption.URL.id));
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + config.get(ConfigOption.API_KEY.id));
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(180_000);
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int statusCode = connection.getResponseCode();
            if (statusCode == HttpURLConnection.HTTP_OK) {
                String responseBody = readInputStream(connection.getInputStream());
                JsonObject responseObj = MineDS.GSON.fromJson(responseBody, JsonObject.class);
                ApiLogger.log(new ApiCallResult(requestBody, responseObj, true));

                String content = extractContent(responseObj);
                return content;
            } else {
                String errorBody = readInputStream(connection.getErrorStream());
                MineDS.LOGGER.error("[MineDS] API request fail: HTTP " + statusCode + "\n" + errorBody);
                return "请求失败: " + statusCode;
            }
        } catch (Exception e) {
            MineDS.LOGGER.error("[MineDS] Error ", e);
            return "发生内部错误: " + e.getClass().getSimpleName();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readInputStream(InputStream inputStream) throws IOException {
        if (inputStream == null) return "";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = br.readLine()) != null) {
                response.append(line);
            }

            return response.toString();
        }
    }

    private static String extractContent(JsonObject response) {
        return response.getAsJsonArray("choices")
                .get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString();
    }

    private static JsonObject populateRequestFromUserInput(String message, Map<String, String> config) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", config.get(ConfigOption.SYSTEM_MESSAGE.id)));
        messages.add(new Message("user", message));

        JsonObject body = new JsonObject();
        body.addProperty("model", config.get(ConfigOption.MODEL.id));
        body.add("messages", MineDS.GSON.toJsonTree(messages));
        body.addProperty("temperature", Double.parseDouble(config.get(ConfigOption.TEMPERATURE.id)));
        body.addProperty("max_tokens", Integer.parseInt(config.get(ConfigOption.MAX_TOKENS.id)));

        return body;
    }
}
