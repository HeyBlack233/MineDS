package heyblack.mineds.filter;

import heyblack.mineds.config.AdvancementFilterMode;

import java.util.Collections;
import java.util.List;

/**
 * Filters advancements based on whitelist/blacklist rules.
 * Supports wildcard matching using asterisk (*).
 * Maintains separate lists for blacklist and whitelist,
 * and uses the appropriate list based on the current filter mode.
 */
public class AdvancementFilter {

    private List<String> blacklist;
    private List<String> whitelist;

    /**
     * Creates a new AdvancementFilter with separate blacklist and whitelist.
     *
     * @param blacklist the list of patterns for blacklist mode
     * @param whitelist the list of patterns for whitelist mode
     */
    public AdvancementFilter(List<String> blacklist, List<String> whitelist) {
        this.blacklist = blacklist != null ? blacklist : Collections.emptyList();
        this.whitelist = whitelist != null ? whitelist : Collections.emptyList();
    }

    /**
     * Checks if an advancement should be allowed based on the current filter mode.
     *
     * @param advancementId the advancement ID to check
     * @param mode the filter mode
     * @return true if the advancement should trigger API call, false if filtered
     */
    public boolean shouldAllow(String advancementId, AdvancementFilterMode mode) {
        // Select the active list based on mode
        List<String> activeList = mode == AdvancementFilterMode.WHITELIST ? whitelist : blacklist;

        if (activeList.isEmpty()) {
            // Blacklist empty -> allow all
            // Whitelist empty -> deny all
            return mode != AdvancementFilterMode.WHITELIST;
        }

        boolean matches = matchesAnyPattern(advancementId, activeList);

        if (mode == AdvancementFilterMode.WHITELIST) {
            // Whitelist: allow if matches
            return matches;
        } else {
            // Blacklist: deny if matches
            return !matches;
        }
    }

    /**
     * Checks if the advancement ID matches any pattern in the given list.
     *
     * @param advancementId the advancement ID
     * @param patterns the list of patterns to check
     * @return true if any pattern matches
     */
    private boolean matchesAnyPattern(String advancementId, List<String> patterns) {
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
     * Gets the blacklist patterns.
     *
     * @return the blacklist
     */
    public List<String> getBlacklist() {
        return blacklist;
    }

    /**
     * Gets the whitelist patterns.
     *
     * @return the whitelist
     */
    public List<String> getWhitelist() {
        return whitelist;
    }
}
