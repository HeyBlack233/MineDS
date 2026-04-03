package heyblack.mineds.initializer;

import com.mojang.brigadier.context.CommandContext;
import heyblack.mineds.MineDS;
import heyblack.mineds.config.ConfigManager;
import heyblack.mineds.config.ConfigOption;
import heyblack.mineds.dsapi.ApiCallType;
import heyblack.mineds.dsapi.DSApiHandler;
import heyblack.mineds.dsapi.response.RegularResponseHandler;
import heyblack.mineds.util.result.ResultLogger;
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
import java.util.concurrent.TimeUnit;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;

public class MineDSClient implements ClientModInitializer {
        private static final ConfigManager configManager = ConfigManager.getInstance();

        private static ExecutorService requestExecutor = Executors.newFixedThreadPool(
                        Integer.parseInt(configManager.get(ConfigOption.MAX_REQUEST.id)));

        private static final MinecraftClient CLIENT = MinecraftClient.getInstance();

        /**
         * Gets or recreates the request executor.
         * Automatically recreates if the executor has been shut down.
         *
         * @return the request executor
         */
        private static synchronized ExecutorService getExecutor() {
                if (requestExecutor.isShutdown() || requestExecutor.isTerminated()) {
                        int poolSize = Integer.parseInt(configManager.get(ConfigOption.MAX_REQUEST.id));
                        MineDS.LOGGER.info("[MineDS] MineDSClient - Recreating executor with pool size: " + poolSize);
                        requestExecutor = Executors.newFixedThreadPool(poolSize);
                }
                return requestExecutor;
        }

        @Override
        public void onInitializeClient() {
                try {
                        Files.createDirectories(MineDS.LOG_PATH);
                        ResultLogger.initializeCacheOnStartup();
                } catch (IOException e) {
                        MineDS.LOGGER.error("[MineDS] MineDSClient - Failed to create log dir!", e);
                        throw new RuntimeException(e);
                }

                // register /ds command
                ClientCommandManager.DISPATCHER.register(
                                ClientCommandManager.literal("ds")
                                                .then(ClientCommandManager.argument("message", greedyString())
                                                                .executes(context -> callApiOnCommand(context,
                                                                                false))));

                // register /dsc command
                ClientCommandManager.DISPATCHER.register(
                                ClientCommandManager.literal("dsc")
                                                .then(ClientCommandManager.argument("message", greedyString())
                                                                .executes(context -> callApiOnCommand(context, true))));

                // register /mineds command with subcommands
                ClientCommandManager.DISPATCHER.register(
                                ClientCommandManager.literal("mineds")
                                                .then(ClientCommandManager.literal("reload")
                                                                .executes(this::reloadConfig))
                                                .then(ClientCommandManager.literal("forceshutdown")
                                                                .executes(this::forceShutdown))
                                                .then(ClientCommandManager.literal("info")
                                                                .executes(this::showInfo))
                                                .then(ClientCommandManager.literal("clearlogs")
                                                                .executes(this::clearLogs)));

                ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
                        MineDS.LOGGER.info("[MineDS] MineDSClient - Shutting down executor gracefully...");
                        requestExecutor.shutdown();
                        try {
                                if (!requestExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                                        MineDS.LOGGER.warn(
                                                        "[MineDS] MineDSClient - Executor did not terminate within 10s, forcing shutdown");
                                        requestExecutor.shutdownNow();
                                }
                        } catch (InterruptedException e) {
                                MineDS.LOGGER.error(
                                                "[MineDS] MineDSClient - Interrupted while waiting for executor shutdown");
                                requestExecutor.shutdownNow();
                                Thread.currentThread().interrupt();
                        }
                });

                ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
                        configManager.saveConfig();
                });
        }

        private int reloadConfig(CommandContext<FabricClientCommandSource> context) {
                boolean success = configManager.reloadConfig();
                ClientPlayerEntity player = context.getSource().getPlayer();
                if (success) {
                        player.sendMessage(getChatPrefix().append(
                                        new LiteralText("Config reloaded successfully").formatted(Formatting.GREEN)),
                                        false);
                } else {
                        player.sendMessage(getChatPrefix().append(new LiteralText("Failed to reload config, check logs")
                                        .formatted(Formatting.RED)), false);
                }
                return success ? 1 : 0;
        }

        private int forceShutdown(CommandContext<FabricClientCommandSource> context) {
                ClientPlayerEntity player = context.getSource().getPlayer();
                player.sendMessage(
                                getChatPrefix().append(new LiteralText("Shutting down all request executor threads")),
                                false);
                requestExecutor.shutdown();
                return 1;
        }

        private int showInfo(CommandContext<FabricClientCommandSource> context) {
                ClientPlayerEntity player = context.getSource().getPlayer();
                player.sendMessage(getChatPrefix().append(
                                new LiteralText("Active thread count: " +
                                                ((ThreadPoolExecutor) requestExecutor).getActiveCount() +
                                                ". Max count: " + configManager.get(ConfigOption.MAX_REQUEST.id))),
                                false);
                return 0;
        }

        private int clearLogs(CommandContext<FabricClientCommandSource> context) {
                boolean success = ResultLogger.clearAllLogs();
                ClientPlayerEntity player = context.getSource().getPlayer();
                if (success) {
                        player.sendMessage(getChatPrefix().append(
                                        new LiteralText("All logs cleared successfully").formatted(Formatting.GREEN)),
                                        false);
                } else {
                        player.sendMessage(getChatPrefix().append(
                                        new LiteralText("Failed to clear logs, check logs").formatted(Formatting.RED)),
                                        false);
                }
                return success ? 1 : 0;
        }

        /**
         * Handles the /ds and /dsc command execution.
         *
         * @param context                 the command context
         * @param pullContentFromLastChat whether to restore context from last chat log
         * @return command success code
         */
        public static int callApiOnCommand(CommandContext<FabricClientCommandSource> context,
                        boolean pullContentFromLastChat) {
                String message = getString(context, "message");
                ClientPlayerEntity player = context.getSource().getPlayer();

                player.sendMessage(
                                getChatPrefix()
                                                .append(new LiteralText(player.getName().asString())
                                                                .formatted(Formatting.LIGHT_PURPLE))
                                                .append(new LiteralText(": " + message)
                                                                .formatted(Formatting.WHITE)),
                                false);

                getExecutor().submit(() -> {
                        SentenceSplitter splitter = new SentenceSplitter();

                        try {
                                DSApiHandler.callApiStreaming(
                                                message,
                                                configManager.getConfig(),
                                                pullContentFromLastChat,
                                                ApiCallType.REGULAR,
                                                new RegularResponseHandler(splitter,
                                                                CLIENT,
                                                                DSApiHandler.populateRequestBody(message,
                                                                                configManager.getConfig(),
                                                                                pullContentFromLastChat)));
                        } catch (Exception e) {
                                MineDS.LOGGER.error("[MineDS] MineDSClient - API call error: " + e.getMessage(), e);
                        }
                });

                return 1;
        }

        /**
         * Gets the chat prefix for MineDS messages.
         *
         * @return the formatted chat prefix
         */
        public static MutableText getChatPrefix() {
                return new LiteralText("[MineDS] ").formatted(Formatting.GRAY);
        }
}
