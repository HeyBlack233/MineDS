package heyblack.mineds.initializer;

import com.mojang.brigadier.context.CommandContext;
import heyblack.mineds.MineDS;
import heyblack.mineds.config.ConfigManager;
import heyblack.mineds.config.ConfigOption;
import heyblack.mineds.dsapi.ApiCallType;
import heyblack.mineds.dsapi.DSApiHandler;
import heyblack.mineds.dsapi.response.RegularResponseHandler;
import heyblack.mineds.util.result.CallResultLogHandler;
import heyblack.mineds.util.SentenceSplitter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;

public class MineDSClient implements ClientModInitializer {
    private static final ConfigManager configManager = ConfigManager.getInstance();

    private static final ExecutorService requestExecutor = Executors.newFixedThreadPool(
            Integer.parseInt((configManager.get(ConfigOption.MAX_REQUEST.id)))
    );

    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();

    @Override
    public void onInitializeClient() {
        try {
            Files.createDirectories(MineDS.LOG_PATH);
            CallResultLogHandler.initializeCacheOnStartup();
        } catch (IOException e) {
            MineDS.LOGGER.error("[MineDS] Failed to create log dir!");
            throw new RuntimeException(e);
        }

        ClientCommandManager.DISPATCHER.register(
                ClientCommandManager.literal("ds")
                        .then(ClientCommandManager.argument("message", greedyString())
                                .executes(context -> callApiOnCommand(context, false))
                        )
        );

        ClientCommandManager.DISPATCHER.register(
                ClientCommandManager.literal("dsc")
                        .then(ClientCommandManager.argument("message", greedyString())
                                .executes(context -> callApiOnCommand(context, true)))
        );

        ClientCommandManager.DISPATCHER.register(
                ClientCommandManager.literal("mineds")
                        .then(ClientCommandManager.literal("reloadcfg")
                                .executes(context -> {
                                    try {
                                        configManager.loadConfig();
                                    } catch (IOException e) {
                                        context.getSource().getPlayer().sendMessage(
                                                getChatPrefix()
                                                        .append(
                                                                new LiteralText("Failed to reload config!")
                                                                        .formatted(Formatting.WHITE)
                                                        ),
                                                false
                                        );
                                        MineDS.LOGGER.error("[MineDS] Failed to reload config!" + e);
                                    }
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("forceshutdown")
                                .executes(context -> {
                                    context.getSource().getPlayer().sendMessage(
                                            getChatPrefix()
                                                    .append(
                                                            new LiteralText("Shutting down all request executor threads")
                                                    ),
                                            false
                                    );
                                    requestExecutor.shutdown();
                                    return 1;
                                }))
                        .then(ClientCommandManager.literal("info")
                                .executes(context -> {
                                    context.getSource().getPlayer().sendMessage(
                                            getChatPrefix().append(
                                                    new LiteralText("Active thread count: " +
                                                            ((ThreadPoolExecutor) requestExecutor).getActiveCount() +
                                                            ". Max count: " + configManager.get(ConfigOption.MAX_REQUEST.id))
                                            ),
                                            false
                                    );

                                    return 0;
                                }))
        );

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            requestExecutor.shutdown();
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            configManager.saveConfig();
        });
    }

    public static int callApiOnCommand(CommandContext<FabricClientCommandSource> context, boolean pullContentFromLastChat) {
        String message = getString(context, "message");
        ClientPlayerEntity player = context.getSource().getPlayer();

        player.sendMessage(
                getChatPrefix()
                        .append(new LiteralText(player.getName().asString())
                                .formatted(Formatting.LIGHT_PURPLE))
                        .append(new LiteralText(": " + message)
                                .formatted(Formatting.WHITE)),
                false
        );

        requestExecutor.submit(() -> {
            SentenceSplitter splitter = new SentenceSplitter();

            DSApiHandler.callApiStreaming(
                    message,
                    configManager.getConfig(),
                    pullContentFromLastChat,
                    ApiCallType.REGULAR,
                    new RegularResponseHandler(splitter, MinecraftClient.getInstance(), context)
            );
        });

        return 1;
    }

    public static MutableText getChatPrefix() {
        return new LiteralText("[MineDS] ").formatted(Formatting.GRAY);
    }
}
