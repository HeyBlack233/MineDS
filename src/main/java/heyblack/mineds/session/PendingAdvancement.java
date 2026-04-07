package heyblack.mineds.session;

/**
 * Represents a pending advancement waiting to be processed in a chain.
 */
public class PendingAdvancement {
    public final String title;
    public final String description;
    public final String id;
    public final long timestamp;

    public PendingAdvancement(String title, String description, String id) {
        this.title = title;
        this.description = description;
        this.id = id;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return title + " (" + id + ")";
    }
}
