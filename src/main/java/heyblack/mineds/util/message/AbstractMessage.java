package heyblack.mineds.util.message;

public abstract class AbstractMessage {
    private String role;
    private String content;

    public AbstractMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

}
