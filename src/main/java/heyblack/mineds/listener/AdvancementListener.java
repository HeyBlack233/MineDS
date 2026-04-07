package heyblack.mineds.listener;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import heyblack.mineds.MineDS;
import heyblack.mineds.config.AiProfile;
import heyblack.mineds.config.ConfigManager;
import heyblack.mineds.config.ConfigOption;
import heyblack.mineds.config.AdvancementFilterMode;
import heyblack.mineds.dsapi.DSApiHandler;
import heyblack.mineds.dsapi.response.RegularResponseHandler;
import heyblack.mineds.filter.AdvancementFilter;
import heyblack.mineds.initializer.MineDSClient;
import heyblack.mineds.session.AdvancementSession;
import heyblack.mineds.session.ChainState;
import heyblack.mineds.session.PendingAdvancement;
import heyblack.mineds.session.SessionManager;
import heyblack.mineds.session.SessionType;
import heyblack.mineds.util.SentenceSplitter;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.client.MinecraftClient;

import java.lang.reflect.Type;
import java.util.List;

/**
 * Listener for advancement grant events.
 * Triggers API calls when player earns advancements.
 */
public class AdvancementListener {

    private static final ConfigManager configManager = ConfigManager.getInstance();
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();

    public static void onAdvancementGranted(Advancement advancement) {
        MineDS.LOGGER.info("[MineDS] Advancement event received: {}", advancement.getId());
        if (!Boolean.parseBoolean(configManager.get(ConfigOption.ADVANCEMENT_CALL.id))) {
            MineDS.LOGGER.info("[MineDS] Advancement call is disabled (advancement_call=false)");
            return;
        }

        AdvancementDisplay display = advancement.getDisplay();
        if (display == null) {
            MineDS.LOGGER.warn("[MineDS] Advancement has no display: {}", advancement.getId());
            return;
        }

        String advancementId = advancement.getId().toString();
        if (!isAdvancementAllowed(advancementId)) {
            MineDS.LOGGER.info("[MineDS] Advancement filtered: {}", advancementId);
            return;
        }

        String title = display.getTitle().getString();
        String description = display.getDescription().getString();
        MineDS.LOGGER.info("[MineDS] Processing advancement: {} ({})", title, advancementId);

        AdvancementSession session = (AdvancementSession) SessionManager.getInstance().getOrCreateSession(SessionType.ADVANCEMENT);
        session.setNameFromAdvancement(title);
        String aiName = configManager.getAiNameForSessionType(SessionType.ADVANCEMENT);
        session.setAssignedAi(aiName);

        if (session.getChainState() == ChainState.IDLE) {
            String message = generateAdvancementMessage(title, description, advancementId);
            submitAdvancementApiCall(message, title, session);
        } else {
            session.addPendingAdvancement(new PendingAdvancement(title, description, advancementId));
        }
    }

    private static boolean isAdvancementAllowed(String advancementId) {
        try {
            if (!Boolean.parseBoolean(configManager.get(ConfigOption.ADVANCEMENT_FILTER_ENABLED.id))) return true;
            String filterModeName = configManager.get(ConfigOption.ADVANCEMENT_FILTER_MODE.id);
            AdvancementFilterMode filterMode = AdvancementFilterMode.fromName(filterModeName);
            String blacklistJson = configManager.get(ConfigOption.ADVANCEMENT_BLACKLIST.id);
            String whitelistJson = configManager.get(ConfigOption.ADVANCEMENT_WHITELIST.id);
            Type listType = new TypeToken<List<String>>(){}.getType();
            List<String> blacklist = MineDS.GSON.fromJson(blacklistJson, listType);
            List<String> whitelist = MineDS.GSON.fromJson(whitelistJson, listType);
            AdvancementFilter filter = new AdvancementFilter(blacklist, whitelist);
            return filter.shouldAllow(advancementId, filterMode);
        } catch (Exception e) {
            MineDS.LOGGER.error("[MineDS] Error checking advancement filter: ", e);
            return true;
        }
    }

    private static String generateAdvancementMessage(String title, String description, String advancementId) {
        String template = configManager.get(ConfigOption.ADVANCEMENT_PROMPT.id);
        return template.replace("{title}", title).replace("{description}", description).replace("{id}", advancementId);
    }

    private static void submitAdvancementApiCall(String message, String advancementTitle, AdvancementSession session) {
        MineDS.LOGGER.info("[MineDS] Submitting API call for advancement: {}", advancementTitle);
        session.setChainState(ChainState.PROCESSING);

        CLIENT.execute(() -> {
            if (CLIENT.player != null) {
                CLIENT.player.sendMessage(MineDSClient.getChatPrefix().append(new net.minecraft.text.LiteralText("Advancement triggered: " + advancementTitle)), false);
            }
        });

        MineDSClient.getExecutor().submit(() -> {
            SentenceSplitter splitter = new SentenceSplitter();
            try {
                AiProfile aiProfile = configManager.getAiProfile(session.getAssignedAi());
                JsonObject inputRequest = DSApiHandler.populateRequestBody(message, session, aiProfile);
                DSApiHandler.callApiStreaming(message, session, new RegularResponseHandler(splitter, CLIENT, inputRequest));
            } catch (Exception e) {
                MineDS.LOGGER.error("[MineDS] Error in advancement API call: ", e);
                CLIENT.execute(() -> {
                    if (CLIENT.player != null) {
                        CLIENT.player.sendMessage(MineDSClient.getChatPrefix().append(new net.minecraft.text.LiteralText("API call failed: " + e.getMessage())), false);
                    }
                });
            } finally {
                // Process any buffered advancements that arrived during this call
                processNextBufferedAdvancement(session);
            }
        });
    }

    /**
     * Processes the next buffered advancement if any.
     * Sets chainState to IDLE when buffer is empty.
     */
    private static void processNextBufferedAdvancement(AdvancementSession session) {
        if (session.hasPendingAdvancements()) {
            List<PendingAdvancement> batch = session.drainPendingAdvancements();
            String mergedMessage = generateBatchAdvancementMessage(batch);

            MineDS.LOGGER.info("[MineDS] Processing {} buffered advancements", batch.size());
            CLIENT.execute(() -> {
                if (CLIENT.player != null) {
                    CLIENT.player.sendMessage(MineDSClient.getChatPrefix().append(new net.minecraft.text.LiteralText("Processing " + batch.size() + " buffered advancement(s)")), false);
                }
            });

            // Keep chainState as PROCESSING while processing buffered advancements
            MineDSClient.getExecutor().submit(() -> {
                SentenceSplitter splitter = new SentenceSplitter();
                try {
                    AiProfile aiProfile = configManager.getAiProfile(session.getAssignedAi());
                    JsonObject inputRequest = DSApiHandler.populateRequestBody(mergedMessage, session, aiProfile);
                    DSApiHandler.callApiStreaming(mergedMessage, session, new RegularResponseHandler(splitter, CLIENT, inputRequest));
                } catch (Exception e) {
                    MineDS.LOGGER.error("[MineDS] Error in batch advancement API call: ", e);
                } finally {
                    // Check again for more buffered advancements
                    processNextBufferedAdvancement(session);
                }
            });
        } else {
            // No more pending advancements, set to IDLE
            session.setChainState(ChainState.IDLE);
            MineDS.LOGGER.info("[MineDS] Advancement session {} returned to IDLE state", session.getSessionId());
            // Clear active session so next advancement creates a new one
            heyblack.mineds.session.SessionManager.getInstance().clearActiveSessionContext(
                    heyblack.mineds.session.SessionType.ADVANCEMENT);
        }
    }

    private static String generateBatchAdvancementMessage(List<PendingAdvancement> advancements) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Batch Advancement Triggered - ").append(advancements.size()).append(" achievement(s)]\n\n");
        for (int i = 0; i < advancements.size(); i++) {
            PendingAdvancement adv = advancements.get(i);
            sb.append(String.format("%d. Title: %s\n   Description: %s\n   ID: %s\n\n", i + 1, adv.title, adv.description, adv.id));
        }
        sb.append("Please respond to these achievements.");
        return sb.toString();
    }
}
