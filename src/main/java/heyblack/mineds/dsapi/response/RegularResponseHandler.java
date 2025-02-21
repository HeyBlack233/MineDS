package heyblack.mineds.dsapi.response;

import com.google.gson.JsonObject;
import com.mojang.brigadier.context.CommandContext;
import heyblack.mineds.MineDS;
import heyblack.mineds.config.ConfigManager;
import heyblack.mineds.config.ConfigOption;
import heyblack.mineds.dsapi.DSApiHandler;
import heyblack.mineds.initializer.MineDSClient;
import heyblack.mineds.util.SentenceSplitter;
import heyblack.mineds.util.message.OutputMessage;
import heyblack.mineds.util.result.ApiCallResult;
import heyblack.mineds.util.result.CallResultLogHandler;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class RegularResponseHandler implements ResponseHandler {
    private final StringBuilder outputContent = new StringBuilder();
    private final StringBuilder outputContentReasoning = new StringBuilder();
    private JsonObject inputRequest;

    private final ConfigManager  configManager = ConfigManager.getInstance();
    
    private final SentenceSplitter splitter;
    private final MinecraftClient client;
    private final ClientPlayerEntity player;
    
    public RegularResponseHandler(SentenceSplitter splitter, MinecraftClient client, CommandContext<FabricClientCommandSource> context) {
        this.splitter = splitter;
        this.client = client;
        this.player = context.getSource().getPlayer();
    }

    @Override
    public void onContentChunk(String content, String reasoning_content) {
        outputContent.append(content);
        outputContentReasoning.append(reasoning_content);

        List<String> sentences = splitter.processChunk(content);

        if (!sentences.isEmpty()) {
            client.execute(() -> {
                for (String sentence : sentences) {
                    sendAIMessage(sentence.trim());
                }
            });
        }
    }

    @Override
    public void onComplete(String message, boolean pullContentFromLastChat) throws Exception {
        String remaining = splitter.getRemaining();
        if (!remaining.isEmpty()) {
            client.execute(() -> {
                sendAIMessage(remaining.trim());
            });
        }
        client.execute(() -> {
            player.sendMessage(
                    MineDSClient.getChatPrefix()
                            .append(new LiteralText("Output complete")
                                    .formatted(Formatting.ITALIC)),
                    false
            );
        });

        JsonObject outputJson = new JsonObject();
        List<OutputMessage> messageOut = new ArrayList<>();
        messageOut.add(new OutputMessage(
                outputContent.toString().trim(),
                outputContentReasoning.toString().trim()
        ));

        outputJson.add("message", MineDS.GSON.toJsonTree(messageOut));

        inputRequest = DSApiHandler.populateRequestBody(
                message,
                configManager.getConfig(),
                pullContentFromLastChat
        );

        CallResultLogHandler.log(new ApiCallResult(
                inputRequest,
                outputJson,
                true
        ));

    }

    @Override
    public void onError(String error) {
        JsonObject errorJson = new JsonObject();
        errorJson.addProperty("error", error);

        CallResultLogHandler.log(new ApiCallResult(
                inputRequest,
                errorJson,
                false
        ));

        client.execute(() -> {
            player.sendMessage(
                    MineDSClient.getChatPrefix()
                            .append(new LiteralText(
                                    "Error: " + error)
                                    .formatted(Formatting.RED)),
                    false
            );
        });
    }
    
    private void sendAIMessage (String content){
        player.sendMessage(
                MineDSClient.getChatPrefix()
                        .append(new LiteralText(
                                configManager.get(ConfigOption.AI_NAME.id))
                                .formatted(Formatting.BLUE))
                        .append(new LiteralText(": " + content)
                                .formatted(Formatting.WHITE)),
                false
        );
    }
}
