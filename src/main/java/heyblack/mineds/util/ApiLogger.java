package heyblack.mineds.util;

import heyblack.mineds.MineDS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ApiLogger {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(1);

    private static final String PREFIX = "MineDS_";
    private static final String SUFFIX = ".json";
    private static final Pattern PATTERN = Pattern.compile(PREFIX + "(\\d+)" + SUFFIX);

    private static final Path CACHE_PATH = MineDS.LOG_PATH.resolve(".index");

    public static void log(ApiCallResult result) {
        try {
            int i = getLastIndex() + 1;

            String fileName = String.format("%s%d%s", PREFIX, i, SUFFIX);
            MineDS.LOGGER.info("[MineDS] Logging api call to " + fileName);

            // write log file
            Files.write(
                    MineDS.LOG_PATH.resolve(fileName),
                    MineDS.GSON.toJson(result).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE
            );
            // update cache file
            Files.write(
                    CACHE_PATH,
                    String.valueOf(i).getBytes(),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.CREATE
            );
        } catch (IOException e) {
            MineDS.LOGGER.error("[MineDS] Failed to log API call!", e);
        }
    }

    private static int getLastIndex() throws IOException {
        if (!Files.exists(CACHE_PATH)) {
            return initializeCacheOnQuery();
        }

        return Integer.parseInt(new String(Files.readAllBytes(CACHE_PATH)));
    }

    /*
    case: reading cache
        case: index cache doesn't exist
            index = has log file ? prev index : 1
        case: index cache does exist
            index = read from cache
     */
    public static int initializeCacheOnQuery() throws IOException {
        if (!Files.exists(CACHE_PATH)) {
            int i;
            try (Stream<Path> files = Files.list(MineDS.LOG_PATH)) {
                if (files.findAny().isPresent()) {
                    i = findMaxIndex();
                } else {
                    i = 1;
                }
                Files.write(CACHE_PATH, String.valueOf(i).getBytes());

                return i;
            }
        } else {
            return Integer.parseInt(new String(Files.readAllBytes(CACHE_PATH)));
        }
    }

    /*
    case: mod initializes
        case: index cache doesn't exist
            index = has log file ? prev index : 1
        case: index cache does exist
            index = has log file ? prev index : 1
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
                            .orElse(0), EXECUTOR
                    );
             return future.get();
        } catch (IOException | ExecutionException | InterruptedException e) {
            MineDS.LOGGER.error("[MineDS] Failed to get index from cache!");
            return 0;
        }
    }
}
