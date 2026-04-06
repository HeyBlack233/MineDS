package heyblack.mineds.initializer;

import com.mojang.brigadier.context.CommandContext;
import heyblack.mineds.MineDS;
import heyblack.mineds.config.ConfigManager;
import heyblack.mineds.config.ConfigOption;
import heyblack.mineds.config.AdvancementFilterMode;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;

public class MineDSClient implements ClientModInitializer {
    private static final ConfigManager configManager = ConfigManager.getInstance();

    private static ExecutorService requestExecutor = Executors.newFixedThreadPool(
            Integer.parseInt((configManager.get(ConfigOption.MAX_REQUEST.id)))
    );

    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();

    @Override
    public void onInitializeClient() {
        try {
            Files.createDirectories(MineDS.LOG_PATH);
            ResultLogger.initializeCacheOnStartup();
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
                        .then(ClientCommandManager.literal("advancementfilter")
                                .then(ClientCommandManager.literal("add")
                                        .then(ClientCommandManager.literal("blacklist")
                                                .then(ClientCommandManager.argument("id", string())
                                                        .executes(context -> modifyAdvancementFilter(context, "add", "blacklist"))))
                                        .then(ClientCommandManager.literal("whitelist")
                                                .then(ClientCommandManager.argument("id", string())
                                                        .executes(context -> modifyAdvancementFilter(context, "add", "whitelist")))))
                                .then(ClientCommandManager.literal("remove")
                                        .then(ClientCommandManager.literal("blacklist")
                                                .then(ClientCommandManager.argument("id", string())
                                                        .executes(context -> modifyAdvancementFilter(context, "remove", "blacklist"))))
                                        .then(ClientCommandManager.literal("whitelist")
                                                .then(ClientCommandManager.argument("id", string())
                                                        .executes(context -> modifyAdvancementFilter(context, "remove", "whitelist")))))
                                .then(ClientCommandManager.literal("list")
                                        .then(ClientCommandManager.literal("blacklist")
                                                .executes(context -> listAdvancementFilter(context, "blacklist")))
                                        .then(ClientCommandManager.literal("whitelist")
                                                .executes(context -> listAdvancementFilter(context, "whitelist"))))
                                .then(ClientCommandManager.literal("mode")
                                        .then(ClientCommandManager.literal("blacklist")
                                                .executes(context -> setAdvancementFilterMode(context, "blacklist")))
                                        .then(ClientCommandManager.literal("whitelist")
                                                .executes(context -> setAdvancementFilterMode(context, "whitelist")))))
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

            try {
                DSApiHandler.callApiStreaming(
                        message,
                        configManager.getConfig(),
                        pullContentFromLastChat,
                        ApiCallType.REGULAR,
                        new RegularResponseHandler(splitter,
                                CLIENT,
                                DSApiHandler.populateRequestBody(message, configManager.getConfig(), pullContentFromLastChat)
                        )
                );
            } catch (Exception e) {
                MineDS.LOGGER.error("[MineDS] Error: " + e);
            }
        });

        return 1;
    }

    public static MutableText getChatPrefix() {
        return new LiteralText("[MineDS] ").formatted(Formatting.GRAY);
    }
    
    /**
     * Gets the request executor for submitting API calls.
     * Recreates the executor if it has been shutdown.
     *
     * @return the executor service
     */
    public static ExecutorService getExecutor() {
        if (requestExecutor.isShutdown() || requestExecutor.isTerminated()) {
            synchronized (MineDSClient.class) {
                if (requestExecutor.isShutdown() || requestExecutor.isTerminated()) {
                    requestExecutor = Executors.newFixedThreadPool(
                            Integer.parseInt(configManager.get(ConfigOption.MAX_REQUEST.id))
                    );
                }
            }
        }
        return requestExecutor;
    }

    /**
     * Handles adding or removing advancement filter entries.
     *
     * @param context the command context
     * @param action the action (add/remove)
     * @param listType the list type (blacklist/whitelist)
     * @return command result code
     */
    private static int modifyAdvancementFilter(CommandContext<FabricClientCommandSource> context, String action, String listType) {
        String id = getString(context, "id");
        ClientPlayerEntity player = context.getSource().getPlayer();
        
        ConfigOption option = "blacklist".equals(listType) 
                ? ConfigOption.ADVANCEMENT_BLACKLIST 
                : ConfigOption.ADVANCEMENT_WHITELIST;
        
        try {
            // Parse current list
            String currentJson = configManager.get(option.id);
            List<String> currentList = MineDS.GSON.fromJson(currentJson, new com.google.gson.reflect.TypeToken<List<String>>(){}.getType());
            if (currentList == null) {
                currentList = new ArrayList<>();
            }
            
            boolean modified = false;
            if ("add".equals(action)) {
                if (!currentList.contains(id)) {
                    currentList.add(id);
                    modified = true;
                }
            } else if ("remove".equals(action)) {
                if (currentList.contains(id)) {
                    currentList.remove(id);
                    modified = true;
                }
            }
            
            if (modified) {
                // Save updated list
                configManager.setConfig(option.id, MineDS.GSON.toJson(currentList));
                
                String actionText = "add".equals(action) ? "Added to" : "Removed from";
                player.sendMessage(
                        getChatPrefix().append(new LiteralText(actionText + " " + listType + ": " + id)),
                        false
                );
            } else {
                String message = "add".equals(action) ? "Already in " + listType : "Not in " + listType;
                player.sendMessage(
                        getChatPrefix().append(new LiteralText(message + ": " + id).formatted(Formatting.YELLOW)),
                        false
                );
            }
            
            return 1;
        } catch (Exception e) {
            MineDS.LOGGER.error("[MineDS] Error modifying advancement filter: ", e);
            player.sendMessage(
                    getChatPrefix().append(new LiteralText("Error: " + e.getMessage()).formatted(Formatting.RED)),
                    false
            );
            return 0;
        }
    }

    /**
     * Handles listing advancement filter entries.
     *
     * @param context the command context
     * @param listType the list type (blacklist/whitelist)
     * @return command result code
     */
    private static int listAdvancementFilter(CommandContext<FabricClientCommandSource> context, String listType) {
        ClientPlayerEntity player = context.getSource().getPlayer();
        
        ConfigOption option = "blacklist".equals(listType) 
                ? ConfigOption.ADVANCEMENT_BLACKLIST 
                : ConfigOption.ADVANCEMENT_WHITELIST;
        
        try {
            String currentJson = configManager.get(option.id);
            List<String> currentList = MineDS.GSON.fromJson(currentJson, new com.google.gson.reflect.TypeToken<List<String>>(){}.getType());
            
            if (currentList == null || currentList.isEmpty()) {
                player.sendMessage(
                        getChatPrefix().append(new LiteralText(listType + " is empty").formatted(Formatting.YELLOW)),
                        false
                );
            } else {
                MutableText message = new LiteralText(listType + " (" + currentList.size() + " entries):\n");
                for (int i = 0; i < currentList.size(); i++) {
                    message.append(new LiteralText("  " + (i + 1) + ". " + currentList.get(i) + "\n"));
                }
                player.sendMessage(getChatPrefix().append(message), false);
            }
            
            return 1;
        } catch (Exception e) {
            MineDS.LOGGER.error("[MineDS] Error listing advancement filter: ", e);
            player.sendMessage(
                    getChatPrefix().append(new LiteralText("Error: " + e.getMessage()).formatted(Formatting.RED)),
                    false
            );
            return 0;
        }
    }

    /**
     * Handles setting the advancement filter mode.
     *
     * @param context the command context
     * @param mode the mode to set (blacklist/whitelist)
     * @return command result code
     */
    private static int setAdvancementFilterMode(CommandContext<FabricClientCommandSource> context, String mode) {
        ClientPlayerEntity player = context.getSource().getPlayer();
        
        try {
            AdvancementFilterMode filterMode = AdvancementFilterMode.fromName(mode);
            configManager.setConfig(ConfigOption.ADVANCEMENT_FILTER_MODE.id, filterMode.name);
            player.sendMessage(
                    getChatPrefix().append(new LiteralText("Filter mode set to: " + filterMode.name)),
                    false
            );
            return 1;
        } catch (Exception e) {
            MineDS.LOGGER.error("[MineDS] Error setting filter mode: ", e);
            player.sendMessage(
                    getChatPrefix().append(new LiteralText("Error: " + e.getMessage()).formatted(Formatting.RED)),
                    false
            );
            return 0;
        }
    }
}
