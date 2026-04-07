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
            // SessionStorage.initialize() is called in SessionManager constructor
            SessionManager.getInstance();
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
                        .then(ClientCommandManager.literal("reload")
                                .executes(context -> reloadConfig(context)))
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
                                        .then(ClientCommandManager.argument("sessionId", string())
                                                .suggests((ctx, builder) -> suggestSessionIds(ctx, builder))
                                                .executes(context -> toggleFavoriteSession(context, getString(context, "sessionId"))))
                                        .executes(context -> toggleFavoriteSession(context, null)))
                                .then(ClientCommandManager.literal("unfavorite")
                                        .then(ClientCommandManager.argument("sessionId", string())
                                                .suggests((ctx, builder) -> suggestSessionIds(ctx, builder))
                                                .executes(context -> unfavoriteSession(context, getString(context, "sessionId"))))
                                        .executes(context -> unfavoriteSession(context, null)))
                                .then(ClientCommandManager.literal("rename")
                                        .then(ClientCommandManager.argument("sessionId", string())
                                                .suggests((ctx, builder) -> suggestSessionIds(ctx, builder))
                                                .then(ClientCommandManager.argument("newName", greedyString())
                                                        .executes(context -> renameSession(context, getString(context, "sessionId"), getString(context, "newName")))))))
                        .then(ClientCommandManager.literal("aiprofile")
                                .then(ClientCommandManager.literal("list")
                                        .executes(context -> listAiProfiles(context)))
                                .then(ClientCommandManager.literal("add")
                                        .executes(context -> addAiProfileTemplate(context)))
                                .then(ClientCommandManager.literal("remove")
                                        .then(ClientCommandManager.argument("name", string())
                                                .executes(context -> removeAiProfile(context, getString(context, "name"))))))
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
        if (!pullContentFromLastChat) SessionManager.getInstance().clearActiveSessionContext(SessionType.COMMAND);

        String aiName = configManager.getAiNameForSessionType(SessionType.COMMAND);
        session.setAssignedAi(aiName);

        // Get AI profile to add system message to context if not present
        AiProfile aiProfile = configManager.getAiProfile(aiName);
        boolean hasSystemMessage = session.getContext().stream().anyMatch(m -> "system".equals(m.getRole()));
        if (!hasSystemMessage && aiProfile.getSystemMessage() != null && !aiProfile.getSystemMessage().isEmpty()) {
            session.addMessage(new heyblack.mineds.util.message.RegularInputMessage("system", aiProfile.getSystemMessage()));
        }

        // Add user message to session context (will be persisted automatically)
        session.addMessage(new heyblack.mineds.util.message.RegularInputMessage("user", message));

        // Update SessionManager's directory mapping if persist() created a new directory
        if (session.getDirectoryName() != null) {
            SessionManager.getInstance().updateActiveSessionDirectory(SessionType.COMMAND, session.getDirectoryName());
        }

        requestExecutor.submit(() -> {
            SentenceSplitter splitter = new SentenceSplitter();
            try {
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
                String name = session.getSessionName() != null && !session.getSessionName().isEmpty()
                        ? session.getSessionName()
                        : "(unnamed)";
                message.append(new LiteralText(String.format("  [%s] %s (messages: %d, AI: %s)\n",
                        session.getSessionId(), name, session.getContext().size(), session.getAssignedAi())));
            } else {
                message.append(new LiteralText("  " + type + ": (none)\n"));
            }
        }
        // Show favorite sessions
        MutableText favMessage = new LiteralText("Favorite Sessions:\n");
        boolean hasFavorites = false;
        for (heyblack.mineds.session.Session session : sm.getAllFavoriteSessions()) {
            hasFavorites = true;
            String name = session.getSessionName() != null && !session.getSessionName().isEmpty()
                    ? session.getSessionName()
                    : "(unnamed)";
            favMessage.append(new LiteralText(String.format("  [%s] %s (messages: %d, AI: %s, type: %s)\n",
                    session.getSessionId(), name, session.getContext().size(), session.getAssignedAi(), session.getType())));
        }
        if (!hasFavorites) {
            favMessage.append(new LiteralText("  (none)\n"));
        }
        // Show historical sessions on disk
        MutableText histMessage = new LiteralText("Historical Sessions:\n");
        boolean hasHistorical = false;
        for (heyblack.mineds.session.Session session : sm.getHistoricalSessions()) {
            hasHistorical = true;
            String name = session.getSessionName() != null && !session.getSessionName().isEmpty()
                    ? session.getSessionName()
                    : "(unnamed)";
            histMessage.append(new LiteralText(String.format("  [%s] %s (messages: %d, AI: %s, type: %s)\n",
                    session.getSessionId(), name, session.getContext().size(), session.getAssignedAi(), session.getType())));
        }
        if (!hasHistorical) {
            histMessage.append(new LiteralText("  (none)\n"));
        }
        player.sendMessage(getChatPrefix().append(message).append(favMessage).append(histMessage), false);
        return 1;
    }

    /**
     * Provides suggestions for session IDs.
     */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestSessionIds(
            CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        SessionManager sm = SessionManager.getInstance();
        String remaining = builder.getRemaining().toLowerCase();

        heyblack.mineds.session.Session cmdSession = sm.getActiveSession(SessionType.COMMAND);
        if (cmdSession != null && cmdSession.getSessionId().toLowerCase().contains(remaining)) {
            builder.suggest(cmdSession.getSessionId());
        }

        heyblack.mineds.session.Session advSession = sm.getActiveSession(SessionType.ADVANCEMENT);
        if (advSession != null && advSession.getSessionId().toLowerCase().contains(remaining)) {
            builder.suggest(advSession.getSessionId());
        }

        return java.util.concurrent.CompletableFuture.completedFuture(builder.build());
    }

    private static int toggleFavoriteSession(CommandContext<FabricClientCommandSource> context, String sessionId) {
        ClientPlayerEntity player = context.getSource().getPlayer();
        SessionManager sm = SessionManager.getInstance();

        heyblack.mineds.session.Session session;
        if (sessionId != null && !sessionId.isEmpty()) {
            session = sm.findSession(sessionId);
            if (session == null) {
                player.sendMessage(getChatPrefix().append(new LiteralText("Session not found: " + sessionId).formatted(Formatting.RED)), false);
                return 0;
            }
        } else {
            session = sm.getActiveSession(SessionType.COMMAND);
            if (session == null) {
                player.sendMessage(getChatPrefix().append(new LiteralText("No active command session.").formatted(Formatting.YELLOW)), false);
                return 0;
            }
            sessionId = session.getSessionId();
        }

        // If already favorited, do nothing (use unfavorite command instead)
        if (session.isFavorite()) {
            player.sendMessage(getChatPrefix().append(new LiteralText("Session [" + sessionId + "] is already favorited. Use /mineds session unfavorite to remove.").formatted(Formatting.YELLOW)), false);
            return 0;
        }

        session.toggleFavorite();
        // Move to favorites on disk
        try {
            String dirName = session.getDirectoryName();
            if (dirName != null) {
                heyblack.mineds.storage.SessionStorage.moveSession(session, dirName, true);
                sm.updateFavoriteStatus(session.getSessionId(), true);
            }
        } catch (java.io.IOException e) {
            MineDS.LOGGER.error("[MineDS] Failed to move session to favorites: ", e);
            player.sendMessage(getChatPrefix().append(new LiteralText("Failed to move session: " + e.getMessage()).formatted(Formatting.RED)), false);
            return 0;
        }

        player.sendMessage(getChatPrefix().append(new LiteralText("Session [" + sessionId + "] favorited").formatted(Formatting.GREEN)), false);
        return 1;
    }

    private static int unfavoriteSession(CommandContext<FabricClientCommandSource> context, String sessionId) {
        ClientPlayerEntity player = context.getSource().getPlayer();
        SessionManager sm = SessionManager.getInstance();

        heyblack.mineds.session.Session session;
        if (sessionId != null && !sessionId.isEmpty()) {
            session = sm.findSession(sessionId);
            if (session == null) {
                player.sendMessage(getChatPrefix().append(new LiteralText("Session not found: " + sessionId).formatted(Formatting.RED)), false);
                return 0;
            }
        } else {
            // Get most recent favorite session
            session = sm.getRecentFavoriteSession(SessionType.COMMAND);
            if (session == null) {
                player.sendMessage(getChatPrefix().append(new LiteralText("No favorited command session.").formatted(Formatting.YELLOW)), false);
                return 0;
            }
            sessionId = session.getSessionId();
        }

        if (!session.isFavorite()) {
            player.sendMessage(getChatPrefix().append(new LiteralText("Session [" + sessionId + "] is not favorited.").formatted(Formatting.YELLOW)), false);
            return 0;
        }

        session.toggleFavorite();
        // Move back to sessions on disk
        try {
            String dirName = session.getDirectoryName();
            if (dirName != null) {
                heyblack.mineds.storage.SessionStorage.moveSession(session, dirName, false);
                sm.updateFavoriteStatus(session.getSessionId(), false);
            }
        } catch (java.io.IOException e) {
            MineDS.LOGGER.error("[MineDS] Failed to move session from favorites: ", e);
            player.sendMessage(getChatPrefix().append(new LiteralText("Failed to move session: " + e.getMessage()).formatted(Formatting.RED)), false);
            return 0;
        }

        player.sendMessage(getChatPrefix().append(new LiteralText("Session [" + sessionId + "] unfavorited").formatted(Formatting.GREEN)), false);
        return 1;
    }

    private static int renameSession(CommandContext<FabricClientCommandSource> context, String sessionId, String newName) {
        ClientPlayerEntity player = context.getSource().getPlayer();
        SessionManager sm = SessionManager.getInstance();

        heyblack.mineds.session.Session session = sm.findSession(sessionId);
        if (session == null) {
            player.sendMessage(getChatPrefix().append(new LiteralText("Session not found: " + sessionId).formatted(Formatting.RED)), false);
            return 0;
        }

        if (newName == null || newName.trim().isEmpty()) {
            player.sendMessage(getChatPrefix().append(new LiteralText("Name cannot be empty.").formatted(Formatting.RED)), false);
            return 0;
        }

        session.setSessionName(newName.trim());
        player.sendMessage(getChatPrefix().append(new LiteralText("Session [" + sessionId + "] renamed to: " + newName)), false);
        return 1;
    }

    // ── Config Reload Command ────────────────────────────────────────

    private static int reloadConfig(CommandContext<FabricClientCommandSource> context) {
        ClientPlayerEntity player = context.getSource().getPlayer();
        try {
            configManager.loadConfig();
            // Re-initialize session manager with updated config
            SessionManager sm = SessionManager.getInstance();
            sm.cleanupByCount();
            player.sendMessage(getChatPrefix().append(new LiteralText("Configuration reloaded successfully.").formatted(Formatting.GREEN)), false);
            return 1;
        } catch (Exception e) {
            MineDS.LOGGER.error("[MineDS] Failed to reload config: ", e);
            player.sendMessage(getChatPrefix().append(new LiteralText("Failed to reload config: " + e.getMessage()).formatted(Formatting.RED)), false);
            return 0;
        }
    }

    // ── AI Profile Commands ─────────────────────────────────────────

    private static int listAiProfiles(CommandContext<FabricClientCommandSource> context) {
        ClientPlayerEntity player = context.getSource().getPlayer();
        Map<String, AiProfile> profiles = configManager.getValidAiProfiles();
        if (profiles.isEmpty()) {
            player.sendMessage(getChatPrefix().append(new LiteralText("No AI profiles configured.").formatted(Formatting.YELLOW)), false);
            return 1;
        }
        MutableText message = new LiteralText("AI Profiles:\n");
        for (Map.Entry<String, AiProfile> entry : profiles.entrySet()) {
            AiProfile p = entry.getValue();
            message.append(new LiteralText(String.format("  %s - model: %s, url: %s\n",
                    entry.getKey(), p.getModel(), p.getUrl())));
        }
        player.sendMessage(getChatPrefix().append(message), false);
        return 1;
    }

    private static int addAiProfileTemplate(CommandContext<FabricClientCommandSource> context) {
        ClientPlayerEntity player = context.getSource().getPlayer();
        String templateName = configManager.addAiProfileTemplate();
        player.sendMessage(getChatPrefix().append(new LiteralText(
                "AI profile template '" + templateName + "' created.\n" +
                "Please do the following:\n" +
                "1. Open the config file\n" +
                "2. Find ai_profiles." + templateName + "\n" +
                "3. Modify parameters (model, API Key, temperature, etc.)\n" +
                "4. Rename it to a meaningful name (must not start with __)\n" +
                "5. Use /mineds reload to apply changes"
        )), false);
        return 1;
    }

    private static int removeAiProfile(CommandContext<FabricClientCommandSource> context, String name) {
        ClientPlayerEntity player = context.getSource().getPlayer();
        // Prevent removing templates
        if (name.startsWith(ConfigManager.TEMPLATE_PREFIX)) {
            player.sendMessage(getChatPrefix().append(new LiteralText("Cannot remove template profiles (names starting with __).").formatted(Formatting.YELLOW)), false);
            return 0;
        }
        boolean removed = configManager.removeAiProfile(name);
        if (removed) {
            player.sendMessage(getChatPrefix().append(new LiteralText("AI profile '" + name + "' removed.").formatted(Formatting.GREEN)), false);
        } else {
            player.sendMessage(getChatPrefix().append(new LiteralText("AI profile '" + name + "' not found.").formatted(Formatting.RED)), false);
        }
        return removed ? 1 : 0;
    }
}
