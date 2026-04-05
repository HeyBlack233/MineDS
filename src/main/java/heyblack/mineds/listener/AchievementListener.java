package heyblack.mineds.listener;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import heyblack.mineds.MineDS;
import heyblack.mineds.config.ConfigManager;
import heyblack.mineds.config.ConfigOption;
import heyblack.mineds.dsapi.ApiCallType;
import heyblack.mineds.dsapi.DSApiHandler;
import heyblack.mineds.dsapi.response.RegularResponseHandler;
import heyblack.mineds.filter.AchievementFilter;
import heyblack.mineds.initializer.MineDSClient;
import heyblack.mineds.util.SentenceSplitter;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementDisplay;
import net.minecraft.client.MinecraftClient;

import java.lang.reflect.Type;
import java.util.List;

/**
 * Listener for advancement grant events.
 * Triggers API calls when player earns achievements.
 */
public class AchievementListener {
    
    private static final ConfigManager configManager = ConfigManager.getInstance();
    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();
    
    /**
     * Handles advancement grant event.
     * Called from AdvancementToastMixin when player earns an achievement.
     * 
     * @param advancement the advancement that was granted
     */
    public static void onAdvancementGranted(Advancement advancement) {
        // Check if advancement call is enabled
        if (!Boolean.parseBoolean(configManager.get(ConfigOption.ADVANCEMENT_CALL.id))) {
            return;
        }
        
        // Extract advancement information
        AdvancementDisplay display = advancement.getDisplay();
        if (display == null) {
            MineDS.LOGGER.warn("[MineDS] Advancement has no display: {}", advancement.getId());
            return;
        }
        
        String advancementId = advancement.getId().toString();
        
        // Check if advancement should be filtered
        if (!isAdvancementAllowed(advancementId)) {
            MineDS.LOGGER.info("[MineDS] Advancement filtered: {}", advancementId);
            return;
        }
        
        String title = display.getTitle().getString();
        String description = display.getDescription().getString();
        
        MineDS.LOGGER.info("[MineDS] Processing advancement: {} ({})", title, advancementId);
        
        // Generate context message
        String message = generateAdvancementMessage(title, description, advancementId);
        
        // Submit API call request
        submitAdvancementApiCall(message, title);
    }
    
    /**
     * Checks if an advancement should trigger API call based on filter rules.
     * 
     * @param advancementId the advancement ID to check
     * @return true if the advancement is allowed, false if filtered
     */
    private static boolean isAdvancementAllowed(String advancementId) {
        try {
            String filterMode = configManager.get(ConfigOption.ADVANCEMENT_FILTER_MODE.id);
            String filtersJson = configManager.get(ConfigOption.ADVANCEMENT_FILTERS.id);
            
            // Parse filters from JSON
            Type listType = new TypeToken<List<String>>(){}.getType();
            List<String> patterns = MineDS.GSON.fromJson(filtersJson, listType);
            
            AchievementFilter filter = new AchievementFilter(filterMode, patterns);
            return filter.shouldAllow(advancementId);
            
        } catch (Exception e) {
            MineDS.LOGGER.error("[MineDS] Error checking advancement filter: ", e);
            // Allow advancement if there's an error
            return true;
        }
    }
    
    /**
     * Generates a contextual message for the advancement.
     * 
     * @param title the advancement title
     * @param description the advancement description
     * @param advancementId the advancement ID
     * @return formatted message for API context
     */
    private static String generateAdvancementMessage(String title, String description, String advancementId) {
        return String.format(
            "[Advancement Earned]\nTitle: %s\nDescription: %s\nID: %s\n\nPlease respond to this achievement.",
            title,
            description,
            advancementId
        );
    }
    
    /**
     * Submits an API call for the advancement event.
     * 
     * @param message the context message
     * @param advancementTitle the advancement title for logging
     */
    private static void submitAdvancementApiCall(String message, String advancementTitle) {
        MineDS.LOGGER.info("[MineDS] Submitting API call for advancement: {}", advancementTitle);
        
        // Send chat prefix to player
        CLIENT.execute(() -> {
            if (CLIENT.player != null) {
                CLIENT.player.sendMessage(
                        MineDSClient.getChatPrefix()
                                .append(new net.minecraft.text.LiteralText("Advancement triggered: " + advancementTitle)),
                        false
                );
            }
        });
        
        // Submit API request
        MineDSClient.getExecutor().submit(() -> {
            SentenceSplitter splitter = new SentenceSplitter();
            
            try {
                DSApiHandler.callApiStreaming(
                        message,
                        configManager.getConfig(),
                        false, // Don't pull content from last chat for advancements
                        ApiCallType.REGULAR,
                        new RegularResponseHandler(
                                splitter,
                                CLIENT,
                                DSApiHandler.populateRequestBody(message, configManager.getConfig(), false)
                        )
                );
            } catch (Exception e) {
                MineDS.LOGGER.error("[MineDS] Error in advancement API call: ", e);
                CLIENT.execute(() -> {
                    if (CLIENT.player != null) {
                        CLIENT.player.sendMessage(
                                MineDSClient.getChatPrefix()
                                        .append(new net.minecraft.text.LiteralText("API call failed: " + e.getMessage())),
                                false
                        );
                    }
                });
            }
        });
    }
}
