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

        JsonObject outputJson = new JsonObject();
        List<OutputMessage> msgOut = new ArrayList<>();
        msgOut.add(new OutputMessage(outputContent.toString().trim(), outputContentReasoning.toString().trim()));
        outputJson.add("message", MineDS.GSON.toJsonTree(msgOut));
        ResultLogger.log(new ApiCallResult(inputRequest, outputJson, true));

        if (session != null) {
            String assistantContent = outputContent.toString().trim();
            if (!assistantContent.isEmpty()) session.addMessage(new RegularInputMessage("assistant", assistantContent));
        }
    }

    @Override
    public void onError(JsonObject error) {
        JsonObject errorJson = new JsonObject();
        errorJson.add("error", error);
        ResultLogger.log(new ApiCallResult(inputRequest, errorJson, false));
        client.execute(() -> player.sendMessage(MineDSClient.getChatPrefix().append(new LiteralText("Error: " + error).formatted(Formatting.RED)), false));
    }

    private void sendAIMessage(String content) {
        player.sendMessage(MineDSClient.getChatPrefix().append(new LiteralText(configManager.get(ConfigOption.AI_NAME.id)).formatted(Formatting.BLUE)).append(new LiteralText(": " + content).formatted(Formatting.WHITE)), false);
    }
}
