package heyblack.mineds.session;

import com.google.gson.JsonObject;
import heyblack.mineds.MineDS;
import heyblack.mineds.util.message.AbstractMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Session for advancement-triggered API calls.
 * Supports chain processing: first advancement triggers immediately,
 * subsequent ones are buffered and merged after the current call completes.
 */
public class AdvancementSession extends Session {

    private final Queue<PendingAdvancement> pendingBuffer = new ConcurrentLinkedQueue<>();
    private ChainState chainState = ChainState.IDLE;

    public AdvancementSession() {
        super(SessionType.ADVANCEMENT);
    }

    public AdvancementSession(String sessionId, Instant createdAt, Instant lastActiveAt, String assignedAi) {
        super(sessionId, SessionType.ADVANCEMENT, createdAt, lastActiveAt, assignedAi);
        // Reset chain state on load since no API call is in progress after game restart
        this.chainState = ChainState.IDLE;
    }

    @Override
    protected String generateSessionId() {
        return "adv_" + System.currentTimeMillis();
    }

    @Override
    public void onSessionCreated() {
        MineDS.LOGGER.info("[MineDS] Advancement session created: {}", sessionId);
    }

    @Override
    public void onMessageAdded(AbstractMessage message) {
        MineDS.LOGGER.debug("[MineDS] Advancement session {} message added: role={}", sessionId, message.getRole());
    }

    public void addPendingAdvancement(PendingAdvancement advancement) {
        pendingBuffer.add(advancement);
        MineDS.LOGGER.info("[MineDS] Advancement buffered: {} (session: {})", advancement.title, sessionId);
    }

    public List<PendingAdvancement> drainPendingAdvancements() {
        List<PendingAdvancement> batch = new ArrayList<>(pendingBuffer);
        pendingBuffer.clear();
        return batch;
    }

    public boolean hasPendingAdvancements() {
        return !pendingBuffer.isEmpty();
    }

    public int getPendingCount() {
        return pendingBuffer.size();
    }

    public ChainState getChainState() {
        return chainState;
    }

    public void setChainState(ChainState state) {
        this.chainState = state;
        MineDS.LOGGER.info("[MineDS] Advancement session {} state: {}", sessionId, state);
        persist();
    }

    /** Sets the session name from the first advancement's title. */
    public void setNameFromAdvancement(String title) {
        if (this.sessionName == null || this.sessionName.isEmpty()) {
            this.sessionName = "Advancement: " + title;
            persist();
        }
    }

    @Override
    protected JsonObject getMetadataJson() {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("chainState", chainState.name());
        return metadata;
    }

    @Override
    protected void fromMetadataJson(JsonObject metadata) {
        if (metadata.has("chainState")) {
            try {
                this.chainState = ChainState.valueOf(metadata.get("chainState").getAsString());
            } catch (IllegalArgumentException e) {
                this.chainState = ChainState.IDLE;
            }
        }
    }
}
