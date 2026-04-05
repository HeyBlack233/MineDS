package heyblack.mineds.filter;

import heyblack.mineds.MineDS;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Filters advancements based on whitelist/blacklist rules.
 * Supports wildcard matching using asterisk (*).
 */
public class AchievementFilter {
    
    private enum FilterMode {
        WHITELIST,
        BLACKLIST
    }
    
    private FilterMode mode;
    private List<String> patterns;
    
    /**
     * Creates a new AchievementFilter.
     * 
     * @param mode the filter mode ("whitelist" or "blacklist")
     * @param patterns the list of patterns to match against
     */
    public AchievementFilter(String mode, List<String> patterns) {
        this.mode = "whitelist".equalsIgnoreCase(mode) ? FilterMode.WHITELIST : FilterMode.BLACKLIST;
        this.patterns = patterns != null ? patterns : Collections.emptyList();
    }
    
    /**
     * Checks if an advancement should be allowed based on filter rules.
     * 
     * @param advancementId the advancement ID to check
     * @return true if the advancement should trigger API call, false if filtered
     */
    public boolean shouldAllow(String advancementId) {
        if (patterns.isEmpty()) {
            // No patterns means allow all
            return true;
        }
        
        boolean matches = matchesAnyPattern(advancementId);
        
        if (mode == FilterMode.BLACKLIST) {
            // Blacklist: deny if matches, allow otherwise
            return !matches;
        } else {
            // Whitelist: allow if matches, deny otherwise
            return matches;
        }
    }
    
    /**
     * Checks if the advancement ID matches any pattern in the list.
     * Supports wildcard matching with asterisk (*).
     * 
     * @param advancementId the advancement ID
     * @return true if any pattern matches
     */
    private boolean matchesAnyPattern(String advancementId) {
        for (String pattern : patterns) {
            if (matchesPattern(pattern, advancementId)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Matches a single pattern against text.
     * Pattern can contain asterisk (*) for wildcard matching.
     * 
     * Examples:
     * - "minecraft:story/*" matches "minecraft:story/mine_stone"
     * - "minecraft:nether/*" matches "minecraft:nether/obtain_blaze_rod"
     * - "minecraft:adventure/root" matches exactly
     * 
     * @param pattern the pattern (may contain *)
     * @param text the text to match
     * @return true if the pattern matches the text
     */
    private boolean matchesPattern(String pattern, String text) {
        // Convert wildcard pattern to regex
        String regex = pattern.replace("*", ".*");
        return text.matches(regex);
    }
    
    /**
     * Gets the current filter mode.
     * 
     * @return the filter mode as string ("whitelist" or "blacklist")
     */
    public String getMode() {
        return mode == FilterMode.WHITELIST ? "whitelist" : "blacklist";
    }
    
    /**
     * Gets the filter patterns.
     * 
     * @return the list of patterns
     */
    public List<String> getPatterns() {
        return patterns;
    }
}
