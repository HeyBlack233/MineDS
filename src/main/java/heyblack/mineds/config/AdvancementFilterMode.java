package heyblack.mineds.config;

/**
 * Represents the advancement filter mode and list type.
 * BLACKLIST: Advancements matching the blacklist are filtered out.
 * WHITELIST: Only advancements matching the whitelist are allowed.
 */
public enum AdvancementFilterMode {
    BLACKLIST("blacklist"),
    WHITELIST("whitelist");

    public final String name;

    AdvancementFilterMode(String name) {
        this.name = name;
    }

    /**
     * Gets the corresponding ConfigOption for this filter list.
     */
    public ConfigOption getConfigOption() {
        return this == BLACKLIST
                ? ConfigOption.ADVANCEMENT_BLACKLIST
                : ConfigOption.ADVANCEMENT_WHITELIST;
    }

    /**
     * Gets the AdvancementFilterMode from its string representation.
     *
     * @param name the string name
     * @return the corresponding AdvancementFilterMode, defaults to BLACKLIST
     */
    public static AdvancementFilterMode fromName(String name) {
        for (AdvancementFilterMode mode : values()) {
            if (mode.name.equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return BLACKLIST;
    }
}
