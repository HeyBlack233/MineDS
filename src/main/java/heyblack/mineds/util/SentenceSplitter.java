package heyblack.mineds.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SentenceSplitter {
    private static final Pattern END_PUNCTUATION = Pattern.compile(
            "(?<![\\ud800-\\udbff])[。！？；）](?![\\udc00-\\udfff])"
    );
    private final StringBuilder buffer = new StringBuilder();

    public List<String> processChunk(String chunk) {
        buffer.append(chunk);
        List<String> completeSentences = new ArrayList<>();

        int lastEndPos = -1;
        Matcher matcher = END_PUNCTUATION.matcher(buffer);

        while (matcher.find()) {
            lastEndPos = matcher.end();
        }

        if (lastEndPos != -1) {
            String fullContent = buffer.substring(0, lastEndPos);
            String[] sentences = fullContent.split("(?<=[。！？；）])");

            Collections.addAll(completeSentences, sentences);

            buffer.delete(0, lastEndPos);
        }

        return completeSentences;
    }

    public String getRemaining() {
        return buffer.toString();
    }
}
