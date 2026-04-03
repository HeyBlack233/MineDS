package heyblack.mineds.util.result;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import heyblack.mineds.MineDS;
import heyblack.mineds.initializer.MineDSClient;
import heyblack.mineds.util.message.RegularInputMessage;
import heyblack.mineds.config.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * API call result logger.
 * Responsible for saving API call results to files and providing context
 * restoration.
 * Log files are named MineDS_<index>.json and use a .index file to track the
 * current index.
 */
public class ResultLogger {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(1);

    private static final String PREFIX = "MineDS_";
    private static final String SUFFIX = ".json";
    private static final Pattern PATTERN = Pattern.compile(PREFIX + "(\\d+)" + SUFFIX);

    private static final Path CACHE_PATH = MineDS.LOG_PATH.resolve(".index");

    /**
     * Logs an API call result to file.
     * Automatically increments index and updates the cache file.
     *
     * @param result the API call result to log
     */
    public static void log(ApiCallResult result) {
        try {
            int i = getOrCreateIndex() + 1;

            String fileName = String.format("%s%d%s", PREFIX, i, SUFFIX);
            MineDS.LOGGER.info("[MineDS] ResultLogger - Logging api call to " + fileName);

            // write log file
            Files.write(
                    MineDS.LOG_PATH.resolve(fileName),
                    MineDS.GSON.toJson(result).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE);
            // update cache file
            Files.write(
                    CACHE_PATH,
                    String.valueOf(i).getBytes(),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE);

            // 日志轮转：清理超出数量的旧日志
            rotateLogs();
        } catch (IOException e) {
            MineDS.LOGGER.error("[MineDS] Failed to log API call!", e);
            try {
                MinecraftClient.getInstance().player.sendMessage(
                        MineDSClient.getChatPrefix()
                                .append(new LiteralText("Failed to log API call!").formatted(Formatting.RED)),
                        false);

            } catch (NullPointerException n) {
                MineDS.LOGGER.warn("[MineDS] ResultLogger - Player not available to show error message");
            }
        }
    }

    /**
     * Gets conversation context from the most recent API call log.
     * Used to restore history when continuing a conversation.
     *
     * @return list of conversation messages
     * @throws Exception if reading or parsing the log fails
     */
    public static List<RegularInputMessage> getContext() throws Exception {
        List<RegularInputMessage> list = new ArrayList<>();

        int i = getOrCreateIndex();

        String fileName = String.format("%s%d%s", PREFIX, i, SUFFIX);

        String logAsString = new String(Files.readAllBytes(MineDS.LOG_PATH.resolve(fileName)), StandardCharsets.UTF_8);
        JsonObject root = MineDS.GSON.fromJson(logAsString, JsonObject.class);

        JsonObject input = root.getAsJsonObject("input");
        JsonArray messages = input.getAsJsonArray("messages");

        for (JsonElement element : messages) {
            JsonObject object = element.getAsJsonObject();

            String roleIn = object.get("role").getAsString();
            String contentIn = object.get("content").getAsString();

            list.add(new RegularInputMessage(roleIn, contentIn));
        }

        JsonObject output = root.getAsJsonObject("output");
        JsonObject message = output.getAsJsonArray("message").get(0).getAsJsonObject();

        String roleOut = message.get("role").getAsString();
        String contentOut = message.get("content").getAsString();

        list.add(new RegularInputMessage(roleOut, contentOut));

        return list;
    }

    /*
     * case: reading cache
     * case: index cache doesn't exist
     * index = has log file ? prev index : 1
     * case: index cache does exist
     * index = read from cache7
     */
    /**
     * Gets or creates the log index.
     * Reads from cache file if it exists, otherwise scans the log directory for the
     * max index.
     *
     * @return the current log index
     * @throws IOException if reading or writing the cache file fails
     */
    public static int getOrCreateIndex() throws IOException {
        if (!Files.exists(CACHE_PATH)) {
            int i;
            try (Stream<Path> files = Files.list(MineDS.LOG_PATH)) {
                if (files.findAny().isPresent()) {
                    i = findMaxIndex();
                } else {
                    i = 0;
                }
                Files.write(CACHE_PATH, String.valueOf(i).getBytes());

                return i;
            }
        } else {
            return Integer.parseInt(new String(Files.readAllBytes(CACHE_PATH)));
        }
    }

    /**
     * Initializes the index cache on mod startup.
     * Scans the log directory to ensure the index is correct, regardless of cache
     * file existence.
     *
     * @throws IOException if reading or writing the cache file fails
     */
    public static void initializeCacheOnStartup() throws IOException {
        int i = 0;
        try (Stream<Path> files = Files.list(MineDS.LOG_PATH)) {
            if (files.findAny().isPresent()) {
                i = findMaxIndex();
            } else {
                i = 1;
            }
            Files.write(CACHE_PATH, String.valueOf(i).getBytes());
        }
    }

    private static int findMaxIndex() throws IOException {
        try (Stream<Path> files = Files.list(MineDS.LOG_PATH)) {
            return files
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .map(PATTERN::matcher)
                    .filter(Matcher::matches)
                    .map(matcher -> Integer.parseInt(matcher.group(1)))
                    .max(Comparator.naturalOrder())
                    .orElse(0);
        }
    }

    private static int findMaxIndexAsync() {
        try (Stream<Path> files = Files.list(MineDS.LOG_PATH)) {
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> files
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .map(PATTERN::matcher)
                    .filter(Matcher::matches)
                    .map(matcher -> Integer.parseInt(matcher.group(1)))
                    .max(Comparator.naturalOrder())
                    .orElse(0), EXECUTOR);
            return future.get();
        } catch (IOException | ExecutionException | InterruptedException e) {
            MineDS.LOGGER.error("[MineDS] ResultLogger - Failed to get index from cache!");
            return 0;
        }
    }

    /**
     * Log rotation: deletes old log files exceeding the configured limit.
     * Keeps the most recent N log files, where N is determined by the max_log_files
     * config option.
     */
    private static void rotateLogs() {
        try {
            int maxLogFiles = Integer.parseInt(ConfigManager.getInstance().get(ConfigOption.MAX_LOG_FILES.id));

            List<Path> logFiles = Files.list(MineDS.LOG_PATH)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .map(PATTERN::matcher)
                    .filter(Matcher::matches)
                    .map(m -> MineDS.LOG_PATH.resolve(m.group()))
                    .sorted(Comparator.comparing(path -> {
                        String filename = path.getFileName().toString();
                        return Integer.parseInt(filename.replace(PREFIX, "").replace(SUFFIX, ""));
                    }))
                    .collect(java.util.stream.Collectors.toList());

            int toDelete = logFiles.size() - maxLogFiles;
            if (toDelete > 0) {
                for (int i = 0; i < toDelete; i++) {
                    Path fileToDelete = logFiles.get(i);
                    Files.deleteIfExists(fileToDelete);
                    MineDS.LOGGER.info("[MineDS] ResultLogger - Rotated old log file: " + fileToDelete.getFileName());
                }
            }
        } catch (IOException e) {
            MineDS.LOGGER.warn("[MineDS] ResultLogger - Failed to rotate logs", e);
        }
    }

    /**
     * Clears all log files and resets the index cache.
     *
     * @return true if clearing succeeded, false otherwise
     */
    public static boolean clearAllLogs() {
        try {
            // 删除所有日志文件
            Files.list(MineDS.LOG_PATH)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(name -> name.startsWith(PREFIX) && name.endsWith(SUFFIX))
                    .forEach(name -> {
                        try {
                            Files.deleteIfExists(MineDS.LOG_PATH.resolve(name));
                        } catch (IOException e) {
                            MineDS.LOGGER.warn("[MineDS] ResultLogger - Failed to delete log file: " + name, e);
                        }
                    });

            // 重置索引缓存
            Files.write(CACHE_PATH, "0".getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);

            MineDS.LOGGER.info("[MineDS] ResultLogger - All logs cleared");
            return true;
        } catch (IOException e) {
            MineDS.LOGGER.error("[MineDS] ResultLogger - Failed to clear logs", e);
            return false;
        }
    }
}
