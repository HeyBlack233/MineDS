package heyblack.mineds.session;

import com.google.gson.JsonObject;
import heyblack.mineds.MineDS;
import heyblack.mineds.util.message.AbstractMessage;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Session for command-based API calls (/ds, /dsc).
 */
public class CommandSession extends Session {
    private static final AtomicInteger COUNTER = new AtomicInteger(0);
    private String lastCommandType;

    public CommandSession() {
        super(SessionType.COMMAND);
    }

    public CommandSession(String sessionId, Instant createdAt, Instant lastActiveAt, String assignedAi) {
        super(sessionId, SessionType.COMMAND, createdAt, lastActiveAt, assignedAi);
        this.lastCommandType = "ds";
    }

    @Override
    protected String generateSessionId() {
        return "cmd_" + COUNTER.incrementAndGet();
    }

    @Override
    public void onSessionCreated() {
        MineDS.LOGGER.info("[MineDS] Command session created: {}", sessionId);
    }

    @Override
    public void onMessageAdded(AbstractMessage message) {
        MineDS.LOGGER.debug("[MineDS] Command session {} message added: role={}", sessionId, message.getRole());
    }

    public void setLastCommandType(String type) {
        this.lastCommandType = type;
    }

    public boolean isContinuation() {
        return "dsc".equals(lastCommandType);
    }

    @Override
    protected JsonObject getMetadataJson() {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("lastCommandType", lastCommandType != null ? lastCommandType : "ds");
        return metadata;
    }

    @Override
    protected void fromMetadataJson(JsonObject metadata) {
        if (metadata.has("lastCommandType")) {
            this.lastCommandType = metadata.get("lastCommandType").getAsString();
        }
    }
}
