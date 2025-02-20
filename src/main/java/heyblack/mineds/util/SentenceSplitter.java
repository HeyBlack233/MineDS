package heyblack.mineds.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SentenceSplitter {
    private static final Pattern SPLIT_PATTERN = Pattern.compile(Pattern.quote("<->"));
    private final StringBuilder buffer = new StringBuilder();

    public List<String> processChunk(String chunk) {
        buffer.append(chunk);
        List<String> completeSentences = new ArrayList<>();

        Matcher matcher = SPLIT_PATTERN.matcher(buffer);
        int lastEndPos = -1;

        while (matcher.find()) {
            lastEndPos = matcher.end();
        }

        if (lastEndPos != -1) {
            String fullContent = buffer.substring(0, lastEndPos);
            String[] sentences = fullContent.split(Pattern.quote("<->"));
            for (String sentence : sentences) {
                if (!sentence.isEmpty()) {
                    completeSentences.add(sentence);
                }
            }

            buffer.delete(0, lastEndPos);
        }

        return completeSentences;
    }

    public String getRemaining() {
        return buffer.toString();
    }
}
