package heyblack.mineds.session;

import heyblack.mineds.MineDS;
import heyblack.mineds.util.message.AbstractMessage;

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
}
