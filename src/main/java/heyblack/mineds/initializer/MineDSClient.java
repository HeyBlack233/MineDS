package heyblack.mineds.initializer;

import heyblack.mineds.MineDS;
import heyblack.mineds.config.ConfigManager;
import heyblack.mineds.config.ConfigOption;
import heyblack.mineds.dsapi.DSApiHandler;
import heyblack.mineds.util.ApiLogger;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;

public class MineDSClient implements ClientModInitializer {
    private static final ConfigManager configManager = ConfigManager.getInstance();

    private static final ExecutorService requestExecutor = Executors.newFixedThreadPool(
            Integer.parseInt((configManager.get(ConfigOption.MAX_REQUEST.id)))
    );

    @Override
    public void onInitializeClient() {
        try {
            Files.createDirectories(MineDS.LOG_PATH);
            ApiLogger.initializeCacheOnStartup();
        } catch (IOException e) {
            MineDS.LOGGER.error("[MineDS] Failed to create log dir!");
            throw new RuntimeException(e);
        }

        ClientCommandManager.DISPATCHER.register(
                ClientCommandManager.literal("ds")
                        .then(ClientCommandManager.argument("message", greedyString())
                                .executes(context -> {
                                    String message = getString(context, "message");
                                    ClientPlayerEntity player = context.getSource().getPlayer();

                                    player.sendMessage(
                                            new LiteralText("[MineDS] ").formatted(Formatting.GRAY)
                                                    .append(
                                                            new LiteralText(player.getName().asString())
                                                                    .formatted(Formatting.LIGHT_PURPLE)
                                                    )
                                                    .append(new LiteralText(": " + message)
                                                            .formatted(Formatting.WHITE)),
                                            false
                                    );

                                    requestExecutor.submit(() -> {
                                        // handle api call in separate thread pool
                                        String output = DSApiHandler.callApiOnCommand(message, configManager.getConfig());

                                        // print output in server thread
                                        MinecraftClient client = MinecraftClient.getInstance();
                                        client.execute(() -> {
                                            MineDS.LOGGER.info("[MineDS] Sending output message");
                                            player.sendMessage(
                                                    new LiteralText("[MineDS] ").formatted(Formatting.GRAY)
                                                            .append(
                                                                    new LiteralText(configManager.get(ConfigOption.AI_NAME.id))
                                                                            .formatted(Formatting.BLUE)
                                                            )
                                                            .append(new LiteralText(": " + output)
                                                                    .formatted(Formatting.WHITE)),
                                                    false
                                            );
                                        });
                                    });

                            return 1;
                        }))

        );

        ClientCommandManager.DISPATCHER.register(
                ClientCommandManager.literal("mineds")
                        .then(ClientCommandManager.literal("reloadcfg")
                                .executes(context -> {
                                    try {
                                        configManager.loadConfig();
                                    } catch (IOException e) {
                                        context.getSource().getPlayer().sendMessage(
                                                new LiteralText("[MineDS] ").formatted(Formatting.GRAY)
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
                                            new LiteralText("[MineDS] ").formatted(Formatting.GRAY)
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
}
