package heyblack.mineds.config;

/**
 * Represents the action to perform on advancement filter lists.
 */
public enum AdvancementFilterAction {
    ADD("add"),
    REMOVE("remove"),
    LIST("list");

    public final String name;

    AdvancementFilterAction(String name) {
        this.name = name;
    }

    public static AdvancementFilterAction fromName(String name) {
        for (AdvancementFilterAction action : values()) {
            if (action.name.equalsIgnoreCase(name)) {
                return action;
            }
        }
        return LIST;
    }
}
