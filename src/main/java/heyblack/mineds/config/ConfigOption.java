package heyblack.mineds.config;

public enum ConfigOption {
    URL("url", "https://api.siliconflow.cn/v1/chat/completions"),
    MODEL("model", "deepseek-ai/DeepSeek-R1"),
    API_KEY("api_key", "YOUR_API_KEY"),
    MAX_REQUEST("max_request", "1"),
    TEMPERATURE("temperature", "0.7"),
    MAX_TOKENS("max_tokens", "4096"),
    SYSTEM_MESSAGE("system_message", "你是一只傲娇猫娘"),
    AI_NAME("ai_name", "DeepSeek")
    ;

    public final String id;
    public final String defaultValue;

    ConfigOption(String id, String defaultValue) {
        this.id = id;
        this.defaultValue = defaultValue;
    }
}
