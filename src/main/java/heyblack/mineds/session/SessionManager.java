package heyblack.mineds.session;

import heyblack.mineds.MineDS;
import heyblack.mineds.config.ConfigManager;
import heyblack.mineds.config.ConfigOption;
import heyblack.mineds.storage.SessionStorage;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton manager for all session types.
 * Handles persistence: loads sessions on startup, saves on modification.
 */
public class SessionManager {

    private static SessionManager instance;

    // Maps session directory name (yy-mm-dd_hh-mm_x) -> Session
    private final Map<String, Session> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, Session> favoriteSessions = new ConcurrentHashMap<>();
    private final Map<SessionType, String> activeSessionDirs = new EnumMap<>(SessionType.class);
    private final Map<SessionType, String> favoriteSessionDirs = new EnumMap<>(SessionType.class);
    private final Map<SessionType, String> aiAssignments = new EnumMap<>(SessionType.class);
    private final Map<SessionType, Integer> maxSessions = new EnumMap<>(SessionType.class);

    private SessionManager() {
        ConfigManager cm = ConfigManager.getInstance();
        aiAssignments.put(SessionType.COMMAND, cm.get(ConfigOption.COMMAND_SESSION_AI.id));
        aiAssignments.put(SessionType.ADVANCEMENT, cm.get(ConfigOption.ADVANCEMENT_SESSION_AI.id));
        maxSessions.put(SessionType.COMMAND, cm.getMaxCommandSessions());
        maxSessions.put(SessionType.ADVANCEMENT, cm.getMaxAdvancementSessions());

        // Initialize directories and load existing sessions
        try {
            SessionStorage.initialize();
            loadAllSessions();
            cleanupByCount();
        } catch (IOException e) {
            MineDS.LOGGER.error("[MineDS] Failed to initialize session storage: ", e);
        }

        MineDS.LOGGER.info("[MineDS] SessionManager initialized with {} active, {} favorite sessions",
                activeSessions.size(), favoriteSessions.size());
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

    /**
     * Loads all sessions from disk (both sessions/ and fav_sessions/).
     */
    private void loadAllSessions() {
        for (SessionType type : SessionType.values()) {
            try {
                List<Path> activeDirs = SessionStorage.listSessionDirectories(type, false);
                for (Path dir : activeDirs) {
                    try {
                        Session session = SessionStorage.loadSession(dir);
                        session.setStorageInfo(dir, dir.getFileName().toString());
                        activeSessions.put(session.getSessionId(), session);
                    } catch (IOException e) {
                        MineDS.LOGGER.warn("[MineDS] Failed to load session from {}: {}", dir, e.getMessage());
                    }
                }
                activeSessionDirs.put(type, activeDirs.isEmpty() ? null : activeDirs.get(activeDirs.size() - 1).getFileName().toString());

                List<Path> favDirs = SessionStorage.listSessionDirectories(type, true);
                for (Path dir : favDirs) {
                    try {
                        Session session = SessionStorage.loadSession(dir);
                        session.setStorageInfo(dir, dir.getFileName().toString());
                        favoriteSessions.put(session.getSessionId(), session);
                    } catch (IOException e) {
                        MineDS.LOGGER.warn("[MineDS] Failed to load favorite session from {}: {}", dir, e.getMessage());
                    }
                }
                if (!favDirs.isEmpty()) {
                    favoriteSessionDirs.put(type, favDirs.get(favDirs.size() - 1).getFileName().toString());
                }
            } catch (IOException e) {
                MineDS.LOGGER.warn("[MineDS] Failed to list {} session directories: {}", type, e.getMessage());
            }
        }
    }

    /**
     * Gets or creates a session for the given type.
     * If no active session exists, creates a new one and persists it.
     */
    public Session getOrCreateSession(SessionType type) {
        Session session = getActiveSession(type);
        if (session == null) {
            session = createAndPersistSession(type);
        }
        return session;
    }

    public Session getActiveSession(SessionType type) {
        String dirName = activeSessionDirs.get(type);
        if (dirName == null) return null;
        return activeSessions.values().stream()
                .filter(s -> {
                    try {
                        // Match by checking if session exists in active list
                        return activeSessions.containsKey(s.getSessionId());
                    } catch (Exception e) {
                        return false;
                    }
                })
                .filter(s -> s.getType() == type)
                .findFirst()
                .orElse(null);
    }

    /**
     * Creates a new session and persists it to disk.
     */
    private Session createAndPersistSession(SessionType type) {
        Session session;
        switch (type) {
            case COMMAND: session = new CommandSession(); break;
            case ADVANCEMENT: session = new AdvancementSession(); break;
            default: throw new IllegalArgumentException("Unknown session type: " + type);
        }

        String dirName = SessionStorage.generateDirectoryName();
        try {
            Path savedPath = SessionStorage.saveSession(session, dirName);
            session.setStorageInfo(savedPath, dirName);
            activeSessionDirs.put(type, dirName);
            activeSessions.put(session.getSessionId(), session);
            MineDS.LOGGER.info("[MineDS] Created new {} session: {} (dir: {})", type, session.getSessionId(), dirName);

            // Cleanup by count after creating
            cleanupByCount();
        } catch (IOException e) {
            MineDS.LOGGER.error("[MineDS] Failed to save session: ", e);
        }

        return session;
    }

    /**
     * Cleans up sessions by count limit.
     * Removes oldest sessions if count exceeds the limit for each type.
     */
    public void cleanupByCount() {
        for (SessionType type : SessionType.values()) {
            int maxCount = maxSessions.getOrDefault(type, 20);
            try {
                List<Path> dirs = SessionStorage.listSessionDirectories(type, false);
                if (dirs.size() > maxCount) {
                    int toRemove = dirs.size() - maxCount;
                    MineDS.LOGGER.info("[MineDS] Cleaning up {} old {} sessions (limit: {})", toRemove, type, maxCount);

                    for (int i = 0; i < toRemove && i < dirs.size(); i++) {
                        Path dir = dirs.get(i);
                        String dirName = dir.getFileName().toString();
                        try {
                            // Find and remove from active sessions map
                            String sessionIdToRemove = null;
                            for (Map.Entry<String, Session> entry : activeSessions.entrySet()) {
                                if (entry.getValue().getDirectoryName() != null && 
                                    entry.getValue().getDirectoryName().equals(dirName)) {
                                    sessionIdToRemove = entry.getKey();
                                    break;
                                }
                            }
                            
                            SessionStorage.deleteSession(type, dirName, false);
                            if (sessionIdToRemove != null) {
                                activeSessions.remove(sessionIdToRemove);
                            }
                            if (activeSessionDirs.get(type) != null && activeSessionDirs.get(type).equals(dirName)) {
                                activeSessionDirs.remove(type);
                            }
                        } catch (IOException e) {
                            MineDS.LOGGER.warn("[MineDS] Failed to delete session directory {}: {}", dirName, e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                MineDS.LOGGER.warn("[MineDS] Failed to cleanup {} sessions: {}", type, e.getMessage());
            }
        }
    }

    /**
     * Toggles favorite status of a session and moves it between sessions/ and fav_sessions/.
     */
    public void toggleFavorite(String sessionId) {
        Session session = activeSessions.get(sessionId);
        if (session == null) {
            MineDS.LOGGER.warn("[MineDS] Session {} not found in active sessions", sessionId);
            return;
        }

        boolean isCurrentlyFavorite = favoriteSessions.containsKey(sessionId);
        String dirName = isCurrentlyFavorite
                ? favoriteSessionDirs.get(session.getType())
                : activeSessionDirs.get(session.getType());

        if (dirName == null) {
            MineDS.LOGGER.warn("[MineDS] No directory mapping for session {}", sessionId);
            return;
        }

        try {
            session.toggleFavorite();
            SessionStorage.moveSession(session, dirName, !isCurrentlyFavorite);

            if (!isCurrentlyFavorite) {
                favoriteSessions.put(sessionId, session);
                favoriteSessionDirs.put(session.getType(), dirName);
                activeSessions.remove(sessionId);
                activeSessionDirs.remove(session.getType());
                MineDS.LOGGER.info("[MineDS] Session {} moved to favorites", sessionId);
            } else {
                activeSessions.put(sessionId, session);
                activeSessionDirs.put(session.getType(), dirName);
                favoriteSessions.remove(sessionId);
                favoriteSessionDirs.remove(session.getType());
                MineDS.LOGGER.info("[MineDS] Session {} moved back to sessions", sessionId);
            }
        } catch (IOException e) {
            MineDS.LOGGER.error("[MineDS] Failed to toggle favorite for session {}: ", sessionId, e);
        }
    }

    public void setAiForType(SessionType type, String aiName) {
        aiAssignments.put(type, aiName);
        Session session = getActiveSession(type);
        if (session != null) session.setAssignedAi(aiName);
        MineDS.LOGGER.info("[MineDS] AI for {} set to: {}", type, aiName);
    }

    public String getAiNameForSessionType(SessionType type) {
        return aiAssignments.getOrDefault(type, "default");
    }

    /**
     * Clears context for the active session and removes directory mapping.
     * A new directory will be created on next persist().
     */
    public void clearActiveSessionContext(SessionType type) {
        String oldDirName = activeSessionDirs.get(type);
        if (oldDirName != null) {
            activeSessionDirs.remove(type);
            Session session = activeSessions.values().stream()
                    .filter(s -> s.getType() == type)
                    .filter(s -> oldDirName.equals(s.getDirectoryName()))
                    .findFirst()
                    .orElse(null);
            if (session != null) {
                session.clearContext();
                MineDS.LOGGER.info("[MineDS] Cleared context for {} session {}", type, session.getSessionId());
            }
        }
    }

    /**
     * Updates the directory mapping for a session after persist creates a new directory.
     */
    public void updateActiveSessionDirectory(SessionType type, String directoryName) {
        activeSessionDirs.put(type, directoryName);
    }

    public Duration getSessionTtl() { return Duration.ofHours(24); }
    public static void resetInstance() { instance = null; }
}
