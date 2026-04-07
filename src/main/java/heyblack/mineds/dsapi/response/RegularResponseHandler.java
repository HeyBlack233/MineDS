package heyblack.mineds.dsapi.response;

import com.google.gson.JsonObject;
import heyblack.mineds.MineDS;
import heyblack.mineds.config.ConfigManager;
import heyblack.mineds.config.ConfigOption;
import heyblack.mineds.initializer.MineDSClient;
import heyblack.mineds.session.Session;
import heyblack.mineds.util.SentenceSplitter;
import heyblack.mineds.util.message.OutputMessage;
import heyblack.mineds.util.message.RegularInputMessage;
import heyblack.mineds.util.result.ApiCallResult;
import heyblack.mineds.util.result.ResultLogger;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class RegularResponseHandler implements ResponseHandler {
    private final StringBuilder outputContent = new StringBuilder();
    private final StringBuilder outputContentReasoning = new StringBuilder();
    private final JsonObject inputRequest;
    private final ConfigManager configManager = ConfigManager.getInstance();
    private final SentenceSplitter splitter;
    private final MinecraftClient client;
    private final ClientPlayerEntity player;

    public RegularResponseHandler(SentenceSplitter splitter, MinecraftClient client, JsonObject inputRequest) {
        this.splitter = splitter;
        this.client = client;
        this.player = client.player;
        this.inputRequest = inputRequest;
    }

    @Override
    public void onContentChunk(String content, String reasoning_content) {
        outputContent.append(content);
        outputContentReasoning.append(reasoning_content);
        List<String> sentences = splitter.processChunk(content);
        if (!sentences.isEmpty()) {
            client.execute(() -> { for (String s : sentences) sendAIMessage(s.trim()); });
        }
    }

    @Override
    public void onComplete(Session session, String message) throws Exception {
        String remaining = splitter.getRemaining();
        if (!remaining.isEmpty()) client.execute(() -> sendAIMessage(remaining.trim()));
        client.execute(() -> player.sendMessage(MineDSClient.getChatPrefix().append(new LiteralText("Output complete").formatted(Formatting.ITALIC)), false));

        // Generate input summary (without full context)
        JsonObject inputSummary = heyblack.mineds.storage.LogManager.generateInputSummary(
                session.getContext(), message, configManager.getAiProfile(session.getAssignedAi()).getModel());

        // Build output summary
        JsonObject output = new JsonObject();
        output.addProperty("content", outputContent.toString().trim());
        output.addProperty("reasoningContent", outputContentReasoning.toString().trim());

        // Log the result
        heyblack.mineds.util.result.ApiCallResult result = new heyblack.mineds.util.result.ApiCallResult(
                session.getSessionId(), session.getType().name(),
                configManager.getAiProfile(session.getAssignedAi()).getModel(),
                configManager.getAiProfile(session.getAssignedAi()).getUrl(),
                inputSummary, output, true, 0, null);
        heyblack.mineds.util.result.ResultLogger.log(result, session);

        // Add assistant message to session context
        if (!outputContent.toString().trim().isEmpty()) {
            session.addMessage(new RegularInputMessage("assistant", outputContent.toString().trim()));
        }
    }

    @Override
    public void onError(JsonObject error) {
        String errorMsg = error.has("error") ? error.get("error").getAsString() : error.toString();
        heyblack.mineds.util.result.ApiCallResult result = new heyblack.mineds.util.result.ApiCallResult(
                "unknown", "UNKNOWN", "unknown", "unknown",
                new JsonObject(), null, false, 0, errorMsg);
        // Try to log if we have session info
        client.execute(() -> player.sendMessage(MineDSClient.getChatPrefix().append(new LiteralText("Error: " + errorMsg).formatted(Formatting.RED)), false));
    }

    private void sendAIMessage(String content) {
        player.sendMessage(MineDSClient.getChatPrefix().append(new LiteralText(configManager.get(ConfigOption.AI_NAME.id)).formatted(Formatting.BLUE)).append(new LiteralText(": " + content).formatted(Formatting.WHITE)), false);
    }
}
