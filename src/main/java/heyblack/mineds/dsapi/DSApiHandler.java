package heyblack.mineds.dsapi;

import com.google.gson.JsonObject;
import heyblack.mineds.MineDS;
import heyblack.mineds.config.ConfigOption;
import heyblack.mineds.dsapi.response.ResponseHandler;
import heyblack.mineds.util.message.RegularInputMessage;
import heyblack.mineds.util.result.ResultLogger;

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

/**
 * DeepSeek API 调用处理器。
 * 负责构建请求、调用 API、处理流式响应和错误。
 * 支持 SiliconFlow 等兼容 OpenAI 格式的 API。
 */
public class DSApiHandler {
    /**
     * 调用 DeepSeek API 进行流式对话。
     *
     * @param message                 用户输入的消息
     * @param config                  当前配置 Map
     * @param pullContentFromLastChat 是否从上次对话日志中恢复上下文
     * @param type                    API 调用类型
     * @param handler                 响应处理器
     */
    public static void callApiStreaming(
            String message,
            Map<String, String> config,
            boolean pullContentFromLastChat,
            ApiCallType type,
            ResponseHandler handler) {
        MineDS.LOGGER.info("[MineDS] DSApi - Calling API");
        try {
            JsonObject requestBody = populateRequestBody(message, config, pullContentFromLastChat);

            URL url = new URL(config.get(ConfigOption.URL.id));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000); // 10s 连接超时
            connection.setReadTimeout(30000); // 30s 读取超时
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + config.get(ConfigOption.API_KEY.id));
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(MineDS.GSON.toJson(requestBody).getBytes(StandardCharsets.UTF_8));
            }

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                MineDS.LOGGER.info("[MineDS] DSApi - API call success");
                processStream(connection, handler);
                handler.onComplete(message, pullContentFromLastChat);
            } else {
                MineDS.LOGGER.warn("[MineDS] DSApi - API call failed with code: " + connection.getResponseCode());
                handler.onError(getError(connection));
            }
        } catch (Exception e) {
            MineDS.LOGGER.error("[MineDS] DSApi - API call error: " + e.getMessage(), e);
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("error", e.getMessage());
            handler.onError(jsonObject);
        }
    }

    private static void processStream(HttpURLConnection connection, ResponseHandler handler) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String jsonData = line.substring(6).trim();
                    if ("[DONE]".equals(jsonData))
                        break;

                    JsonObject response = MineDS.GSON.fromJson(jsonData, JsonObject.class);
                    String content = extractDeltaContent(response);
                    String reasoning_content = extractDeltaContentReasoning(response);
                    handler.onContentChunk(content, reasoning_content);
                }
            }
        }
    }

    public static JsonObject getError(HttpURLConnection connection) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder jsonData = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                jsonData.append(line);
            }

            return MineDS.GSON.fromJson(jsonData.toString(), JsonObject.class);
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
            JsonObject delta = response.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("delta");

            if (delta.has("reasoning_content")) {
                return delta.get("reasoning_content").getAsString();
            } else if (delta.has("reasoning")) {
                return delta.get("reasoning").getAsString();
            }
        } catch (Exception e) {
            MineDS.LOGGER.warn("[MineDS] DSApi - Failed to extract reasoning content: " + e.getMessage());
        }
        return "";
    }

    /**
     * 构建 API 请求体。
     * 根据是否恢复上下文决定是否包含系统提示和历史消息。
     *
     * @param message                 用户输入的消息
     * @param config                  当前配置 Map
     * @param pullContentFromLastChat 是否从上次对话日志中恢复上下文
     * @return 构建好的请求体 JsonObject
     * @throws Exception 如果恢复上下文失败
     */
    public static JsonObject populateRequestBody(String message, Map<String, String> config,
            boolean pullContentFromLastChat) throws Exception {
        List<RegularInputMessage> messages = new ArrayList<>();
        if (pullContentFromLastChat) {
            MineDS.LOGGER.info("[MineDS] DSApi - Pulling context from last api call result");
            messages.addAll(ResultLogger.getContext());
        } else { // system prompt should only be sent when starting new chat
            messages.add(new RegularInputMessage("system", config.get(ConfigOption.SYSTEM_MESSAGE.id)));
        }

        messages.add(new RegularInputMessage("user", message)); // new input message should always be sent

        JsonObject requestBody = BaseRequest.populate();
        requestBody.add("messages", MineDS.GSON.toJsonTree(messages));

        return requestBody;
    }
}
