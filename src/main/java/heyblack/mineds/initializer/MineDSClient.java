package heyblack.mineds.initializer;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.google.gson.JsonObject;
import heyblack.mineds.MineDS;
import heyblack.mineds.config.*;
import heyblack.mineds.dsapi.DSApiHandler;
import heyblack.mineds.dsapi.response.RegularResponseHandler;
import heyblack.mineds.session.CommandSession;
import heyblack.mineds.session.SessionManager;
import heyblack.mineds.session.SessionType;
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
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static com.mojang.brigadier.arguments.BoolArgumentType.bool;
import static com.mojang.brigadier.arguments.BoolArgumentType.getBool;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.literal;
import static net.fabricmc.fabric.api.client.command.v1.ClientCommandManager.argument;

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
                                                    .append(new LiteralText("Shutting down all request executor threads")),
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
                        .then(ClientCommandManager.literal("session")
                                .then(ClientCommandManager.literal("list")
                                        .executes(context -> listSessions(context)))
                                .then(ClientCommandManager.literal("favorite")
                                        .executes(context -> toggleFavoriteSession(context))))
                        .then(ClientCommandManager.literal("advancementfilter")
                                .then(ClientCommandManager.argument("enabled", bool())
                                        .executes(context -> setAdvancementFilterEnabled(context, getBool(context, "enabled"))))
                                .then(ClientCommandManager.literal("mode")
                                        .then(ClientCommandManager.argument("type", string())
                                                .suggests((ctx, builder) -> suggestFilterListTypes(builder))
                                                .executes(context -> setAdvancementFilterMode(context,
                                                        AdvancementFilterMode.fromName(getString(context, "type"))))))
                                .then(ClientCommandManager.argument("listType", string())
                                        .suggests((ctx, builder) -> suggestFilterListTypes(builder))
                                        .then(ClientCommandManager.literal("add")
                                                .then(ClientCommandManager.argument("id", greedyString())
                                                        .suggests((ctx, builder) -> suggestAdvancementIds(builder))
                                                        .executes(context -> handleAdvancementFilter(context,
                                                                AdvancementFilterMode.fromName(getString(context, "listType")),
                                                                AdvancementFilterAction.ADD))))
                                        .then(ClientCommandManager.literal("remove")
                                                .then(ClientCommandManager.argument("id", greedyString())
                                                        .suggests((ctx, builder) -> suggestAdvancementIds(builder))
                                                        .executes(context -> handleAdvancementFilter(context,
                                                                AdvancementFilterMode.fromName(getString(context, "listType")),
                                                                AdvancementFilterAction.REMOVE))))
                                        .then(ClientCommandManager.literal("list")
                                                .executes(context -> handleAdvancementFilter(context,
                                                        AdvancementFilterMode.fromName(getString(context, "listType")),
                                                        AdvancementFilterAction.LIST)))))
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

        player.sendMessage(getChatPrefix().append(new LiteralText(player.getName().asString()).formatted(Formatting.LIGHT_PURPLE)).append(new LiteralText(": " + message).formatted(Formatting.WHITE)), false);

        CommandSession session = (CommandSession) SessionManager.getInstance().getOrCreateSession(SessionType.COMMAND);
        session.setLastCommandType(pullContentFromLastChat ? "dsc" : "ds");
        if (!pullContentFromLastChat) session.clearContext();

        String aiName = configManager.getAiNameForSessionType(SessionType.COMMAND);
        session.setAssignedAi(aiName);

        requestExecutor.submit(() -> {
            SentenceSplitter splitter = new SentenceSplitter();
            try {
                AiProfile aiProfile = configManager.getAiProfile(aiName);
                JsonObject inputRequest = DSApiHandler.populateRequestBody(message, session, aiProfile);
                DSApiHandler.callApiStreaming(message, session, new RegularResponseHandler(splitter, CLIENT, inputRequest));
            } catch (Exception e) {
                MineDS.LOGGER.error("[MineDS] Error: ", e);
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
     * Provides suggestions for filter list types (blacklist/whitelist).
     */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestFilterListTypes(SuggestionsBuilder builder) {
        for (AdvancementFilterMode mode : AdvancementFilterMode.values()) {
            builder.suggest(mode.name);
        }
        return java.util.concurrent.CompletableFuture.completedFuture(builder.build());
    }

    /**
     * Provides suggestions for advancement IDs.
     */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestAdvancementIds(SuggestionsBuilder builder) {
        try {
            String currentJson = configManager.get(ConfigOption.ADVANCEMENT_BLACKLIST.id);
            List<String> blacklist = MineDS.GSON.fromJson(currentJson, new com.google.gson.reflect.TypeToken<List<String>>(){}.getType());
            String currentJsonW = configManager.get(ConfigOption.ADVANCEMENT_WHITELIST.id);
            List<String> whitelist = MineDS.GSON.fromJson(currentJsonW, new com.google.gson.reflect.TypeToken<List<String>>(){}.getType());

            java.util.Set<String> allIds = new java.util.HashSet<>();
            if (blacklist != null) allIds.addAll(blacklist);
            if (whitelist != null) allIds.addAll(whitelist);

            String remaining = builder.getRemaining().toLowerCase();
            for (String id : allIds) {
                if (id.toLowerCase().contains(remaining)) {
                    builder.suggest(id);
                }
            }
        } catch (Exception ignored) {
        }
        return java.util.concurrent.CompletableFuture.completedFuture(builder.build());
    }

    /**
     * Unified handler for all advancement filter operations (add, remove, list).
     */
    private static int handleAdvancementFilter(CommandContext<FabricClientCommandSource> context, AdvancementFilterMode listType, AdvancementFilterAction action) {
        ClientPlayerEntity player = context.getSource().getPlayer();
        ConfigOption option = listType.getConfigOption();

        try {
            String currentJson = configManager.get(option.id);
            List<String> currentList = MineDS.GSON.fromJson(currentJson, new com.google.gson.reflect.TypeToken<List<String>>(){}.getType());
            if (currentList == null) {
                currentList = new ArrayList<>();
            }

            if (action == AdvancementFilterAction.LIST) {
                if (currentList.isEmpty()) {
                    player.sendMessage(
                            getChatPrefix().append(new LiteralText(listType.name + " is empty").formatted(Formatting.YELLOW)),
                            false
                    );
                } else {
                    MutableText message = new LiteralText(listType.name + " (" + currentList.size() + " entries):\n");
                    for (int i = 0; i < currentList.size(); i++) {
                        message.append(new LiteralText("  " + (i + 1) + ". " + currentList.get(i) + "\n"));
                    }
                    player.sendMessage(getChatPrefix().append(message), false);
                }
                return 1;
            }

            String id = context.getArgument("id", String.class).trim();
            boolean modified = false;

            if (action == AdvancementFilterAction.ADD) {
                if (!currentList.contains(id)) {
                    currentList.add(id);
                    modified = true;
                }
            } else if (action == AdvancementFilterAction.REMOVE) {
                if (currentList.contains(id)) {
                    currentList.remove(id);
                    modified = true;
                }
            }

            if (modified) {
                configManager.setConfig(option.id, MineDS.GSON.toJson(currentList));
                String actionText = action == AdvancementFilterAction.ADD ? "Added to" : "Removed from";
                player.sendMessage(
                        getChatPrefix().append(new LiteralText(actionText + " " + listType.name + ": " + id)),
                        false
                );
            } else {
                String message = action == AdvancementFilterAction.ADD ? "Already in " + listType.name : "Not in " + listType.name;
                player.sendMessage(
                        getChatPrefix().append(new LiteralText(message + ": " + id).formatted(Formatting.YELLOW)),
                        false
                );
            }

            return 1;
        } catch (Exception e) {
            MineDS.LOGGER.error("[MineDS] Error handling advancement filter: ", e);
            player.sendMessage(
                    getChatPrefix().append(new LiteralText("Error: " + e.getMessage()).formatted(Formatting.RED)),
                    false
            );
            return 0;
        }
    }

    /**
     * Handles enabling/disabling the advancement filter.
     */
    private static int setAdvancementFilterEnabled(CommandContext<FabricClientCommandSource> context, boolean enabled) {
        ClientPlayerEntity player = context.getSource().getPlayer();

        configManager.setConfig(ConfigOption.ADVANCEMENT_FILTER_ENABLED.id, String.valueOf(enabled));
        player.sendMessage(
                getChatPrefix().append(new LiteralText("Advancement filter " + (enabled ? "enabled" : "disabled"))),
                false
        );
        return 1;
    }

    /**
     * Handles setting the advancement filter mode.
     */
    private static int setAdvancementFilterMode(CommandContext<FabricClientCommandSource> context, AdvancementFilterMode mode) {
        ClientPlayerEntity player = context.getSource().getPlayer();
        configManager.setConfig(ConfigOption.ADVANCEMENT_FILTER_MODE.id, mode.name);
        player.sendMessage(getChatPrefix().append(new LiteralText("Filter mode set to: " + mode.name)), false);
        return 1;
    }

    // ── Session Management Commands ───────────────────────────────────

    private static int listSessions(CommandContext<FabricClientCommandSource> context) {
        ClientPlayerEntity player = context.getSource().getPlayer();
        SessionManager sm = SessionManager.getInstance();
        MutableText message = new LiteralText("Active Sessions:\n");
        for (SessionType type : SessionType.values()) {
            heyblack.mineds.session.Session session = sm.getActiveSession(type);
            if (session != null) {
                message.append(new LiteralText(String.format("  %s: %s (messages: %d, AI: %s, fav: %s)\n",
                        type, session.getSessionId(), session.getContext().size(), session.getAssignedAi(), session.isFavorite() ? "yes" : "no")));
            } else {
                message.append(new LiteralText("  " + type + ": (none)\n"));
            }
        }
        player.sendMessage(getChatPrefix().append(message), false);
        return 1;
    }

    private static int toggleFavoriteSession(CommandContext<FabricClientCommandSource> context) {
        ClientPlayerEntity player = context.getSource().getPlayer();
        heyblack.mineds.session.Session session = SessionManager.getInstance().getActiveSession(SessionType.COMMAND);
        if (session == null) {
            player.sendMessage(getChatPrefix().append(new LiteralText("No active command session.").formatted(Formatting.YELLOW)), false);
            return 0;
        }
        session.toggleFavorite();
        player.sendMessage(getChatPrefix().append(new LiteralText("Session " + session.getSessionId() + " " + (session.isFavorite() ? "favorited" : "unfavorited"))), false);
        return 1;
    }
}
