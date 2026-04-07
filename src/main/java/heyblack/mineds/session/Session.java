package heyblack.mineds.session;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import heyblack.mineds.MineDS;
import heyblack.mineds.storage.SessionStorage;
import heyblack.mineds.util.message.AbstractMessage;
import heyblack.mineds.util.message.RegularInputMessage;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for all session types.
 * Each API call source (command, advancement, chat) should extend this class
 * to maintain isolated conversation context.
 */
public abstract class Session {

    private static final DateTimeFormatter LOCAL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    protected final String sessionId;
    protected String sessionName;
    protected final SessionType type;
    protected final List<AbstractMessage> context;
    protected final Instant createdAt;
    protected Instant lastActiveAt;
    protected boolean isFavorite;
    protected String assignedAi;

    // Path to this session's directory on disk (set after persistence)
    protected transient Path storagePath;
    protected transient String directoryName;

    protected Session(SessionType type) {
        this.type = type;
        this.sessionId = generateSessionId();
        this.sessionName = "";
        this.context = new ArrayList<>();
        this.createdAt = Instant.now();
        this.lastActiveAt = Instant.now();
        this.isFavorite = false;
        this.assignedAi = "default";
        onSessionCreated();
    }

    /** Constructor for deserialization */
    protected Session(String sessionId, SessionType type, Instant createdAt, Instant lastActiveAt, String assignedAi) {
        this.sessionId = sessionId;
        this.sessionName = "";
        this.type = type;
        this.context = new ArrayList<>();
        this.createdAt = createdAt;
        this.lastActiveAt = lastActiveAt;
        this.isFavorite = false;
        this.assignedAi = assignedAi;
    }

    /** Sets the session display name. */
    public void setSessionName(String name) {
        this.sessionName = name;
        persist();
    }

    /** Converts an Instant to local time string (no timezone). */
    protected static String toLocalTimeString(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(LOCAL_TIME_FORMATTER);
    }

    /** Parses a local time string back to Instant. */
    protected static Instant fromLocalTimeString(String str) {
        return LocalDateTime.parse(str, LOCAL_TIME_FORMATTER).toInstant(ZoneId.systemDefault().getRules().getOffset(Instant.now()));
    }

    /** Generate a unique session ID. */
    protected abstract String generateSessionId();

    /** Called when the session is first created. */
    public abstract void onSessionCreated();

    /** Called when a message is added to the context. */
    public abstract void onMessageAdded(AbstractMessage message);

    /** Adds a message to the conversation context and persists to disk. */
    public void addMessage(AbstractMessage message) {
        context.add(message);
        lastActiveAt = Instant.now();
        onMessageAdded(message);
        // Auto-persist after adding a message
        persist();
    }

    /** Persists this session to disk. Creates new directory if directoryName is null. */
    public void persist() {
        try {
            if (directoryName == null) {
                // Need to create a new directory
                directoryName = SessionStorage.generateDirectoryName();
                MineDS.LOGGER.info("[MineDS] Creating new directory for session {}: {}", sessionId, directoryName);
            }
            this.storagePath = SessionStorage.saveSession(this, directoryName, isFavorite);
        } catch (IOException e) {
            MineDS.LOGGER.error("[MineDS] Failed to persist session {}: {}", sessionId, e.getMessage());
        }
    }

    /** Sets the storage path and directory name after initial persistence. */
    public void setStorageInfo(Path path, String dirName) {
        this.storagePath = path;
        this.directoryName = dirName;
    }

    /** Returns an unmodifiable view of the conversation context. */
    public List<AbstractMessage> getContext() {
        return Collections.unmodifiableList(context);
    }

    /** Clears the conversation context but keeps the same storage directory. */
    public void clearContext() {
        context.clear();
        // Do NOT reset storage info - keep using the same directory for this session
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

    // ── Serialization ─────────────────────────────────────────────────

    /** Serializes this session to a JsonObject. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("sessionId", sessionId);
        json.addProperty("sessionName", sessionName);
        json.addProperty("type", type.name());
        json.addProperty("createdAt", toLocalTimeString(createdAt));
        json.addProperty("lastActiveAt", toLocalTimeString(lastActiveAt));
        json.addProperty("assignedAi", assignedAi);

        JsonArray contextArray = new JsonArray();
        for (AbstractMessage msg : context) {
            JsonObject msgJson = new JsonObject();
            msgJson.addProperty("role", msg.getRole());
            msgJson.addProperty("content", msg.getContent());
            contextArray.add(msgJson);
        }
        json.add("context", contextArray);

        JsonObject metadata = getMetadataJson();
        if (metadata != null) {
            json.add("metadata", metadata);
        }

        return json;
    }

    /** Returns type-specific metadata. Override in subclass. */
    protected JsonObject getMetadataJson() {
        return null;
    }

    /** Creates a session from JSON. Subclasses should override. */
    public static Session fromJson(JsonObject json) {
        String typeName = json.get("type").getAsString();
        SessionType type = SessionType.valueOf(typeName);

        String sessionId = json.get("sessionId").getAsString();
        Instant createdAt = fromLocalTimeString(json.get("createdAt").getAsString());
        Instant lastActiveAt = fromLocalTimeString(json.get("lastActiveAt").getAsString());
        String assignedAi = json.has("assignedAi") ? json.get("assignedAi").getAsString() : "default";
        String sessionName = json.has("sessionName") ? json.get("sessionName").getAsString() : "";

        Session session;
        if (type == SessionType.COMMAND) {
            session = new CommandSession(sessionId, createdAt, lastActiveAt, assignedAi);
        } else if (type == SessionType.ADVANCEMENT) {
            session = new AdvancementSession(sessionId, createdAt, lastActiveAt, assignedAi);
        } else {
            throw new IllegalArgumentException("Unknown session type: " + typeName);
        }

        // Restore sessionName (backward compat: generate if missing)
        session.sessionName = sessionName;

        // Restore context
        if (json.has("context")) {
            JsonArray contextArray = json.getAsJsonArray("context");
            for (JsonElement elem : contextArray) {
                JsonObject msgJson = elem.getAsJsonObject();
                session.context.add(new RegularInputMessage(
                        msgJson.get("role").getAsString(),
                        msgJson.get("content").getAsString()
                ));
            }
        }

        // Restore type-specific metadata
        if (json.has("metadata") && !json.get("metadata").isJsonNull()) {
            session.fromMetadataJson(json.getAsJsonObject("metadata"));
        }

        return session;
    }

    /** Restores type-specific metadata. Override in subclass. */
    protected void fromMetadataJson(JsonObject metadata) {
        // Default: do nothing
    }

    // ── Getters ───────────────────────────────────────────────────────

    public String getSessionId() { return sessionId; }
    public String getSessionName() { return sessionName; }
    public SessionType getType() { return type; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public boolean isFavorite() { return isFavorite; }
    public String getAssignedAi() { return assignedAi; }
    public void setAssignedAi(String assignedAi) { this.assignedAi = assignedAi; }
    public Path getStoragePath() { return storagePath; }
    public String getDirectoryName() { return directoryName; }
}
