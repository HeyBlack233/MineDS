package heyblack.mineds.session;

import heyblack.mineds.MineDS;
import heyblack.mineds.util.message.AbstractMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for all session types.
 * Each API call source (command, advancement, chat) should extend this class
 * to maintain isolated conversation context.
 */
public abstract class Session {

    protected final String sessionId;
    protected final SessionType type;
    protected final List<AbstractMessage> context;
    protected final Instant createdAt;
    protected Instant lastActiveAt;
    protected boolean isFavorite;
    protected String assignedAi;

    protected Session(SessionType type) {
        this.type = type;
        this.sessionId = generateSessionId();
        this.context = new ArrayList<>();
        this.createdAt = Instant.now();
        this.lastActiveAt = Instant.now();
        this.isFavorite = false;
        this.assignedAi = "default";
        onSessionCreated();
    }

    /** Generate a unique session ID. */
    protected abstract String generateSessionId();

    /** Called when the session is first created. */
    public abstract void onSessionCreated();

    /** Called when a message is added to the context. */
    public abstract void onMessageAdded(AbstractMessage message);

    /** Adds a message to the conversation context. */
    public void addMessage(AbstractMessage message) {
        context.add(message);
        lastActiveAt = Instant.now();
        onMessageAdded(message);
    }

    /** Returns an unmodifiable view of the conversation context. */
    public List<AbstractMessage> getContext() {
        return Collections.unmodifiableList(context);
    }

    /** Clears the conversation context. */
    public void clearContext() {
        context.clear();
    }

    /** Toggles the favorite status of this session. */
    public void toggleFavorite() {
        this.isFavorite = !this.isFavorite;
        MineDS.LOGGER.info("[MineDS] Session {} favorite: {}", sessionId, isFavorite);
    }

    /** Returns true if this session has exceeded the given TTL and is not favorited. */
    public boolean isExpired(Duration ttl) {
        return !isFavorite && Duration.between(lastActiveAt, Instant.now()).compareTo(ttl) > 0;
    }

    // ── Getters ───────────────────────────────────────────────────────

    public String getSessionId() { return sessionId; }
    public SessionType getType() { return type; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public boolean isFavorite() { return isFavorite; }
    public String getAssignedAi() { return assignedAi; }
    public void setAssignedAi(String assignedAi) { this.assignedAi = assignedAi; }
}
