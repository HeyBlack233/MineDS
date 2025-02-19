package heyblack.mineds.util.message;

public class AssistantMessage extends AbstractMessage {
    private final String reasoning_content;

    public AssistantMessage(String content, String reasoningContent) {
        super("assistant", content);
        this.reasoning_content = reasoningContent;
    }

    public String getReasoning_content() {
        return reasoning_content;
    }
}
