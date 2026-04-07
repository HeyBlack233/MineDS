package heyblack.mineds.util.result;

import com.google.gson.JsonObject;
import heyblack.mineds.MineDS;
import heyblack.mineds.initializer.MineDSClient;
import heyblack.mineds.session.Session;
import heyblack.mineds.storage.LogManager;
import heyblack.mineds.storage.SessionStorage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

/**
 * Handles logging API call results to disk.
 * Now uses Session-based storage: logs are saved in the session's time directory.
 * Old logs are archived on first use.
 */
public class ResultLogger {

    private static final DateTimeFormatter DIR_FORMATTER = DateTimeFormatter.ofPattern("yy-MM-dd_HH-mm");
    private static boolean archiveInitialized = false;

    /**
     * Logs an API call result.
     * The log is saved in the session's time directory alongside session.json.
     *
     * @param result    The API call result
     * @param session   The session that made the call (for directory lookup)
     */
    public static void log(ApiCallResult result, Session session) {
        // Archive old logs on first use
        if (!archiveInitialized) {
            archiveOldLogs();
            archiveInitialized = true;
        }

        try {
            // Use the session's storage path directly
            Path sessionDir = session.getStoragePath();
            if (sessionDir == null) {
                MineDS.LOGGER.warn("[MineDS] No session directory found for session {}, skipping log", session.getSessionId());
                return;
            }

            // Save log in session directory
            LogManager.saveLog(sessionDir, result.toJson());
        } catch (IOException e) {
            MineDS.LOGGER.error("[MineDS] Failed to log API call!", e);
            try {
                MinecraftClient.getInstance().player.sendMessage(
                        MineDSClient.getChatPrefix().append(new LiteralText("Failed to log API call!").formatted(Formatting.RED)),
                        false
                );
            } catch (NullPointerException ignored) {}
        }
    }

    /**
     * Archives old MineDS_*.json and .index files to archive/ directory.
     */
    private static void archiveOldLogs() {
        try {
            Path baseDir = MineDS.LOG_PATH;
            Path archiveDir = baseDir.resolve("archive");
            Files.createDirectories(archiveDir);

            // Move .index
            Path indexFile = baseDir.resolve(".index");
            if (Files.exists(indexFile)) {
                Files.move(indexFile, archiveDir.resolve(".index"), StandardCopyOption.REPLACE_EXISTING);
                MineDS.LOGGER.info("[MineDS] Archived .index to archive/");
            }

            // Move MineDS_*.json
            try (Stream<Path> files = Files.list(baseDir)) {
                files.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().startsWith("MineDS_") && p.getFileName().toString().endsWith(".json"))
                        .forEach(file -> {
                            try {
                                Files.move(file, archiveDir.resolve(file.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                                MineDS.LOGGER.info("[MineDS] Archived {} to archive/", file.getFileName());
                            } catch (IOException e) {
                                MineDS.LOGGER.warn("[MineDS] Failed to archive {}: {}", file.getFileName(), e.getMessage());
                            }
                        });
            }
        } catch (IOException e) {
            MineDS.LOGGER.error("[MineDS] Failed to archive old logs: ", e);
        }
    }
}
