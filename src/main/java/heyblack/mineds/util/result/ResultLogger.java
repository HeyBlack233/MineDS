package heyblack.mineds.util.result;

import heyblack.mineds.MineDS;
import heyblack.mineds.initializer.MineDSClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Handles logging API call results to disk.
 * Context management has been moved to the Session system.
 */
public class ResultLogger {
    private static final String PREFIX = "MineDS_";
    private static final String SUFFIX = ".json";
    private static final Pattern PATTERN = Pattern.compile(PREFIX + "(\\d+)" + SUFFIX);
    private static final Path CACHE_PATH = MineDS.LOG_PATH.resolve(".index");

    public static void log(ApiCallResult result) {
        try {
            int i = getOrCreateIndex() + 1;
            String fileName = String.format("%s%d%s", PREFIX, i, SUFFIX);
            MineDS.LOGGER.info("[MineDS] Logging API call to {}", fileName);
            Files.write(MineDS.LOG_PATH.resolve(fileName), MineDS.GSON.toJson(result).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            Files.write(CACHE_PATH, String.valueOf(i).getBytes(),
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        } catch (IOException e) {
            MineDS.LOGGER.error("[MineDS] Failed to log API call!", e);
            try {
                MinecraftClient.getInstance().player.sendMessage(MineDSClient.getChatPrefix().append(new LiteralText("Failed to log API call!").formatted(Formatting.RED)), false);
            } catch (NullPointerException n) {}
        }
    }

    public static int getOrCreateIndex() throws IOException {
        if (!Files.exists(CACHE_PATH)) {
            int i;
            try (Stream<Path> files = Files.list(MineDS.LOG_PATH)) {
                i = files.findAny().isPresent() ? findMaxIndex() : 0;
                Files.write(CACHE_PATH, String.valueOf(i).getBytes());
                return i;
            }
        } else {
            return Integer.parseInt(new String(Files.readAllBytes(CACHE_PATH), StandardCharsets.UTF_8));
        }
    }

    public static void initializeCacheOnStartup() throws IOException {
        int i = 0;
        try (Stream<Path> files = Files.list(MineDS.LOG_PATH)) {
            if (files.findAny().isPresent()) i = findMaxIndex(); else i = 1;
            Files.write(CACHE_PATH, String.valueOf(i).getBytes());
        }
    }

    private static int findMaxIndex() throws IOException {
        try (Stream<Path> files = Files.list(MineDS.LOG_PATH)) {
            return files.map(Path::getFileName).map(Path::toString).map(PATTERN::matcher)
                    .filter(Matcher::matches).map(m -> Integer.parseInt(m.group(1)))
                    .max(Comparator.naturalOrder()).orElse(0);
        }
    }
}
