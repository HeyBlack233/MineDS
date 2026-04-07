package heyblack.mineds.storage;

import heyblack.mineds.MineDS;
import heyblack.mineds.session.AdvancementSession;
import heyblack.mineds.session.ChainState;
import heyblack.mineds.session.CommandSession;
import heyblack.mineds.session.PendingAdvancement;
import heyblack.mineds.session.Session;
import heyblack.mineds.session.SessionType;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Handles persistence for sessions: saving, loading, and directory management.
 * 
 * Directory structure:
 *   sessions/{type}/yy-mm-dd_hh-mm_x/
 *   └── session.json
 *   fav_sessions/{type}/yy-mm-dd_hh-mm_x/
 *   └── session.json
 */
public class SessionStorage {

    private static final DateTimeFormatter DIR_FORMATTER = DateTimeFormatter.ofPattern("yy-MM-dd_HH-mm");
    private static final Path BASE_DIR = MineDS.LOG_PATH;
    private static final Path SESSIONS_DIR = BASE_DIR.resolve("sessions");
    private static final Path FAV_SESSIONS_DIR = BASE_DIR.resolve("fav_sessions");

    /** Initializes the directory structure. */
    public static void initialize() throws IOException {
        Files.createDirectories(SESSIONS_DIR.resolve("command"));
        Files.createDirectories(SESSIONS_DIR.resolve("advancement"));
        Files.createDirectories(FAV_SESSIONS_DIR.resolve("command"));
        Files.createDirectories(FAV_SESSIONS_DIR.resolve("advancement"));
    }

    public static Path getSessionsDir() { return SESSIONS_DIR; }
    public static Path getFavoritesDir() { return FAV_SESSIONS_DIR; }

    /**
     * Generates a unique directory name for a new session.
     * Format: yy-mm-dd_hh-mm_x (x increments on conflict)
     */
    public static String generateDirectoryName() {
        String base = LocalDateTime.now().format(DIR_FORMATTER);
        int counter = 1;
        String dirName;
        do {
            dirName = base + "_" + counter;
            counter++;
        } while (directoryExists(SESSIONS_DIR, "command", dirName) ||
                 directoryExists(SESSIONS_DIR, "advancement", dirName) ||
                 directoryExists(FAV_SESSIONS_DIR, "command", dirName) ||
                 directoryExists(FAV_SESSIONS_DIR, "advancement", dirName));
        return dirName;
    }

    private static boolean directoryExists(Path baseDir, String type, String dirName) {
        return Files.exists(baseDir.resolve(type).resolve(dirName));
    }

    /**
     * Saves a session to disk in its type's directory.
     * Uses atomic write (temp file + rename) for safety.
     */
    public static Path saveSession(Session session, String directoryName) throws IOException {
        Path typeDir = getSessionDirectory(session.getType(), directoryName, true); // true = sessions/, not fav_sessions/
        Files.createDirectories(typeDir);

        Path targetFile = typeDir.resolve("session.json");
        Path tempFile = typeDir.resolve("session.json.tmp");

        JsonObject json = session.toJson();
        String content = MineDS.GSON.toJson(json);
        Files.write(tempFile, content.getBytes(StandardCharsets.UTF_8));
        Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

        MineDS.LOGGER.info("[MineDS] Session saved to {}", targetFile);
        return typeDir;
    }

    /**
     * Loads a session from a given directory path.
     */
    public static Session loadSession(Path sessionDir) throws IOException {
        Path sessionFile = sessionDir.resolve("session.json");
        if (!Files.exists(sessionFile)) {
            throw new IOException("session.json not found in " + sessionDir);
        }

        String content = new String(Files.readAllBytes(sessionFile), StandardCharsets.UTF_8);
        JsonObject json = MineDS.GSON.fromJson(content, JsonObject.class);
        return Session.fromJson(json);
    }

    /**
     * Loads all sessions of a given type from both sessions/ and fav_sessions/.
     */
    public static List<Session> loadAllSessions(SessionType type) throws IOException {
        List<Session> sessions = new ArrayList<>();
        sessions.addAll(loadSessionsFromDir(SESSIONS_DIR.resolve(type.name().toLowerCase()), type));
        sessions.addAll(loadSessionsFromDir(FAV_SESSIONS_DIR.resolve(type.name().toLowerCase()), type));
        return sessions;
    }

    private static List<Session> loadSessionsFromDir(Path typeDir, SessionType type) throws IOException {
        List<Session> sessions = new ArrayList<>();
        if (!Files.exists(typeDir)) return sessions;

        try (Stream<Path> dirs = Files.list(typeDir)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                try {
                    Session session = loadSession(dir);
                    sessions.add(session);
                } catch (IOException e) {
                    MineDS.LOGGER.warn("[MineDS] Failed to load session from {}: {}", dir, e.getMessage());
                }
            });
        }
        return sessions;
    }

    /**
     * Moves a session directory between sessions/ and fav_sessions/.
     */
    public static void moveSession(Session session, String directoryName, boolean toFavorites) throws IOException {
        Path sourceDir = getSessionDirectory(session.getType(), directoryName, !toFavorites);
        Path targetDir = getSessionDirectory(session.getType(), directoryName, toFavorites);

        if (!Files.exists(sourceDir)) {
            throw new IOException("Source session directory not found: " + sourceDir);
        }

        // Copy first, then delete source (safer than rename across volumes)
        copyDirectory(sourceDir, targetDir);
        deleteDirectory(sourceDir);

        MineDS.LOGGER.info("[MineDS] Session {} moved to {}", session.getSessionId(),
                toFavorites ? "favorites" : "sessions");
    }

    /**
     * Deletes a session directory.
     */
    public static void deleteSession(SessionType type, String directoryName, boolean isFavorite) throws IOException {
        Path sessionDir = getSessionDirectory(type, directoryName, isFavorite);
        if (Files.exists(sessionDir)) {
            deleteDirectory(sessionDir);
            MineDS.LOGGER.info("[MineDS] Session directory deleted: {}", sessionDir);
        }
    }

    /**
     * Returns the list of session directories for a given type, sorted by name (oldest first).
     */
    public static List<Path> listSessionDirectories(SessionType type, boolean includeFavorites) throws IOException {
        List<Path> directories = new ArrayList<>();
        Path typeDir = includeFavorites
                ? FAV_SESSIONS_DIR.resolve(type.name().toLowerCase())
                : SESSIONS_DIR.resolve(type.name().toLowerCase());

        if (!Files.exists(typeDir)) return directories;

        try (Stream<Path> stream = Files.list(typeDir)) {
            stream.filter(Files::isDirectory).sorted().forEach(directories::add);
        }
        return directories;
    }

    // ── Directory Helpers ─────────────────────────────────────────────

    private static Path getSessionDirectory(SessionType type, String directoryName, boolean inSessions) {
        Path base = inSessions ? SESSIONS_DIR : FAV_SESSIONS_DIR;
        return base.resolve(type.name().toLowerCase()).resolve(directoryName);
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(src -> {
            try {
                Path dest = target.resolve(source.relativize(src));
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy " + src, e);
            }
        });
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.delete(path); } catch (IOException e) {
                    MineDS.LOGGER.warn("[MineDS] Failed to delete: {}", path);
                }
            });
        }
    }
}
