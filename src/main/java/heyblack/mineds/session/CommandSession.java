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
    private static final int MAX_NAME_LENGTH = 30;
    private static final AtomicInteger COUNTER = new AtomicInteger(0);
    private String lastCommandType;
    private boolean nameInitialized = false;

    public CommandSession() {
        super(SessionType.COMMAND);
    }

    public CommandSession(String sessionId, Instant createdAt, Instant lastActiveAt, String assignedAi) {
        super(sessionId, SessionType.COMMAND, createdAt, lastActiveAt, assignedAi);
        this.lastCommandType = "ds";
        // Update counter to avoid ID collision after game restart
        try {
            String numStr = sessionId.substring("cmd_".length());
            int num = Integer.parseInt(numStr);
            COUNTER.updateAndGet(current -> Math.max(current, num));
        } catch (Exception ignored) {}
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
        // Set sessionName from first user message
        if (!nameInitialized && "user".equals(message.getRole())) {
            nameInitialized = true;
            String content = message.getContent();
            if (content != null && !content.isEmpty()) {
                if (this.sessionName == null || this.sessionName.isEmpty()) {
                    if (content.length() > MAX_NAME_LENGTH) {
                        this.sessionName = content.substring(0, MAX_NAME_LENGTH) + "...";
                    } else {
                        this.sessionName = content;
                    }
                    persist();
                }
            }
        }
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
