package heyblack.mineds.dsapi;

import com.google.gson.JsonObject;
import heyblack.mineds.MineDS;
import heyblack.mineds.config.AiProfile;
import heyblack.mineds.config.ConfigManager;
import heyblack.mineds.dsapi.response.ResponseHandler;
import heyblack.mineds.session.Session;
import heyblack.mineds.util.message.AbstractMessage;
import heyblack.mineds.util.message.RegularInputMessage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for making API calls to AI providers.
 * Uses Session-based context management.
 */
public class DSApiHandler {

    private static final ConfigManager CONFIG_MANAGER = ConfigManager.getInstance();

    public static void callApiStreaming(String message, Session session, ResponseHandler handler) {
        MineDS.LOGGER.info("[MineDS] Calling API for session: {}", session.getSessionId());
        try {
            String aiName = session.getAssignedAi();
            AiProfile aiProfile = CONFIG_MANAGER.getAiProfile(aiName);
            JsonObject requestBody = populateRequestBody(message, session, aiProfile);

            URL url = new URL(aiProfile.getUrl());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + aiProfile.getApiKey());
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                os.write(MineDS.GSON.toJson(requestBody).getBytes(StandardCharsets.UTF_8));
            }

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                MineDS.LOGGER.info("[MineDS] API call success for session: {}", session.getSessionId());
                processStream(connection, handler);
                handler.onComplete(session, message);
            } else {
                MineDS.LOGGER.warn("[MineDS] API call failed: {}", connection.getResponseCode());
                handler.onError(getError(connection));
            }
        } catch (Exception e) {
            MineDS.LOGGER.error("[MineDS] API call error for session: {}", session.getSessionId(), e);
            JsonObject jo = new JsonObject();
            jo.addProperty("error", e.getMessage());
            handler.onError(jo);
        }
    }

    private static void processStream(HttpURLConnection connection, ResponseHandler handler) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String jsonData = line.substring(6).trim();
                    if ("[DONE]".equals(jsonData)) break;
                    JsonObject response = MineDS.GSON.fromJson(jsonData, JsonObject.class);
                    handler.onContentChunk(extractDeltaContent(response), extractDeltaContentReasoning(response));
                }
            }
        }
    }

    public static JsonObject getError(HttpURLConnection connection) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return MineDS.GSON.fromJson(sb.toString(), JsonObject.class);
        }
    }

    private static String extractDeltaContent(JsonObject response) {
        try {
            return response.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("delta").get("content").getAsString();
        } catch (Exception e) { return ""; }
    }

    private static String extractDeltaContentReasoning(JsonObject response) {
        try {
            JsonObject delta = response.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("delta");
            if (delta.has("reasoning_content")) return delta.get("reasoning_content").getAsString();
            else if (delta.has("reasoning")) return delta.get("reasoning").getAsString();
        } catch (Exception e) {}
        return "";
    }

    public static JsonObject populateRequestBody(String message, Session session, AiProfile aiProfile) {
        List<RegularInputMessage> messages = new ArrayList<>();
        List<AbstractMessage> context = session.getContext();

        // Add system message first
        messages.add(new RegularInputMessage("system", aiProfile.getSystemMessage()));

        // Add context messages (user and assistant history)
        for (AbstractMessage msg : context) {
            messages.add(new RegularInputMessage(msg.getRole(), msg.getContent()));
        }

        // Add current user message (already in session context, but included here for the API request)
        // The user message is the last item in context if it was added before the API call
        boolean userAlreadyInContext = !context.isEmpty() && "user".equals(context.get(context.size() - 1).getRole());
        if (!userAlreadyInContext) {
            messages.add(new RegularInputMessage("user", message));
        }

        JsonObject requestBody = BaseRequest.populateFromAiProfile(aiProfile);
        requestBody.add("messages", MineDS.GSON.toJsonTree(messages));
        return requestBody;
    }
}
