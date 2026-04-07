package heyblack.mineds.session;

import heyblack.mineds.MineDS;
import heyblack.mineds.config.ConfigManager;
import heyblack.mineds.config.ConfigOption;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton manager for all session types.
 */
public class SessionManager {

    private static SessionManager instance;

    private final Map<SessionType, Session> activeSessions = new EnumMap<>(SessionType.class);
    private final Map<String, Session> favoriteSessions = new ConcurrentHashMap<>();
    private final Map<SessionType, String> aiAssignments = new EnumMap<>(SessionType.class);
    private Duration sessionTtl = Duration.ofHours(24);

    private SessionManager() {
        ConfigManager cm = ConfigManager.getInstance();
        aiAssignments.put(SessionType.COMMAND, cm.get(ConfigOption.COMMAND_SESSION_AI.id));
        aiAssignments.put(SessionType.ADVANCEMENT, cm.get(ConfigOption.ADVANCEMENT_SESSION_AI.id));
        int ttlHours = cm.getSessionTtlHours();
        sessionTtl = Duration.ofHours(ttlHours);
        MineDS.LOGGER.info("[MineDS] Session TTL set to {} hours", ttlHours);
    }

    public static SessionManager getInstance() {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager();
                }
            }
        }
        return instance;
    }

    public Session getOrCreateSession(SessionType type) {
        Session session = activeSessions.get(type);
        if (session == null || session.isExpired(sessionTtl)) {
            session = createSession(type);
            activeSessions.put(type, session);
            MineDS.LOGGER.info("[MineDS] Created new {} session: {}", type, session.getSessionId());
        }
        return session;
    }

    public Session getActiveSession(SessionType type) {
        return activeSessions.get(type);
    }

    private Session createSession(SessionType type) {
        switch (type) {
            case COMMAND: return new CommandSession();
            case ADVANCEMENT: return new AdvancementSession();
            default: throw new IllegalArgumentException("Unknown session type: " + type);
        }
    }

    public void cleanupExpiredSessions() {
        activeSessions.entrySet().removeIf(entry -> {
            Session session = entry.getValue();
            if (session.isExpired(sessionTtl)) {
                MineDS.LOGGER.info("[MineDS] Session expired and removed: {} (type: {})",
                        session.getSessionId(), session.getType());
                return true;
            }
            return false;
        });
    }

    public void setAiForType(SessionType type, String aiName) {
        aiAssignments.put(type, aiName);
        Session session = activeSessions.get(type);
        if (session != null) session.setAssignedAi(aiName);
        MineDS.LOGGER.info("[MineDS] AI for {} set to: {}", type, aiName);
    }

    public String getDefaultAiForType(SessionType type) {
        return aiAssignments.getOrDefault(type, "default");
    }

    public void toggleFavorite(String sessionId) {
        Session session = findSession(sessionId);
        if (session != null) {
            session.toggleFavorite();
            if (session.isFavorite()) {
                favoriteSessions.put(sessionId, session);
            } else {
                favoriteSessions.remove(sessionId);
            }
        }
    }

    private Session findSession(String sessionId) {
        for (Session s : activeSessions.values()) {
            if (s.getSessionId().equals(sessionId)) return s;
        }
        return favoriteSessions.get(sessionId);
    }

    public Duration getSessionTtl() { return sessionTtl; }
    public void setSessionTtl(Duration ttl) { this.sessionTtl = ttl; }
    public static void resetInstance() { instance = null; }
}
