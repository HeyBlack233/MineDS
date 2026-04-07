package heyblack.mineds.storage;

import com.google.gson.JsonObject;
import heyblack.mineds.MineDS;
import heyblack.mineds.session.Session;
import heyblack.mineds.session.SessionType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Manages API call logs within session directories.
 * 
 * Log files are stored alongside session.json:
 *   sessions/{type}/yy-mm-dd_hh-mm_x/
 *   ├── session.json
 *   ├── log_001.json
 *   ├── log_002.json
 *   └── ...
 */
public class LogManager {

    private static final DateTimeFormatter FILE_FORMATTER = DateTimeFormatter.ofPattern("HH-mm-ss");

    /**
     * Saves a log entry in the session's directory.
     * Auto-increments the log number based existing files.
     *
     * @param sessionDir The session's time directory (returned by SessionStorage.saveSession)
     * @param logEntry   The log data as a JsonObject
     * @return The path to the created log file
     */
    public static Path saveLog(Path sessionDir, JsonObject logEntry) throws IOException {
        if (!Files.exists(sessionDir)) {
            throw new IOException("Session directory does not exist: " + sessionDir);
        }

        int nextNum = getNextLogNumber(sessionDir);
        String fileName = String.format("log_%03d.json", nextNum);
        Path logFile = sessionDir.resolve(fileName);

        String content = MineDS.GSON.toJson(logEntry);
        Files.write(logFile, content.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);

        MineDS.LOGGER.info("[MineDS] Log saved to {}", logFile);
        return logFile;
    }

    /**
     * Returns the next available log number for a session directory.
     */
    private static int getNextLogNumber(Path sessionDir) throws IOException {
        int maxNum = 0;
        if (!Files.exists(sessionDir)) return 1;

        try (Stream<Path> files = Files.list(sessionDir)) {
            maxNum = files
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.startsWith("log_") && name.endsWith(".json"))
                    .mapToInt(name -> {
                        try {
                            String numStr = name.substring(4, name.length() - 5);
                            return Integer.parseInt(numStr);
                        } catch (NumberFormatException e) {
                            return 0;
                        }
                    })
                    .max()
                    .orElse(0);
        }

        return maxNum + 1;
    }

    /**
     * Generates an input summary for logging (without storing full context).
     */
    public static JsonObject generateInputSummary(java.util.List<heyblack.mineds.util.message.AbstractMessage> context,
                                                   String currentUserMessage,
                                                   String model) {
        JsonObject summary = new JsonObject();
        summary.addProperty("messageCount", context.size() + 1); // +1 for current user message
        summary.addProperty("systemMessageIncluded", !context.isEmpty() && "system".equals(context.get(0).getRole()));
        summary.addProperty("lastUserMessage", currentUserMessage);
        summary.addProperty("contextLength", context.size());
        summary.addProperty("hasAssistantHistory", context.stream().anyMatch(m -> "assistant".equals(m.getRole())));
        return summary;
    }

    /**
     * Creates a log entry JsonObject.
     */
    public static JsonObject createLogEntry(Session session, String model, String url,
                                             JsonObject inputSummary, JsonObject output,
                                             long durationMs, String error) {
        JsonObject entry = new JsonObject();
        entry.addProperty("timestamp", java.time.Instant.now().toString());
        entry.addProperty("sessionId", session.getSessionId());
        entry.addProperty("type", session.getType().name());
        entry.addProperty("status", error == null ? "SUCCESS" : "ERROR");
        entry.addProperty("model", model);
        entry.addProperty("url", url);
        entry.add("inputSummary", inputSummary);
        
        if (output != null) {
            entry.add("output", output);
        } else {
            entry.add("output", null);
        }
        
        entry.addProperty("durationMs", durationMs);
        
        if (error != null) {
            entry.addProperty("error", error);
        } else {
            entry.add("error", null);
        }
        
        return entry;
    }
}
