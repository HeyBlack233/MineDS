package heyblack.mineds.config;

public enum ConfigOption {
    // API Settings
    URL("url", "https://api.siliconflow.cn/v1/chat/completions"),
    MODEL("model", "deepseek-ai/DeepSeek-R1"),
    API_KEY("api_key", "YOUR_API_KEY"),
    TEMPERATURE("temperature", "0.7"),
    MAX_TOKENS("max_tokens", "4096"),
    SYSTEM_MESSAGE("system_message", "你是一只傲娇猫娘"),

    // In-game general behaviour settings
    MAX_REQUEST("max_request", "1"),
    AI_NAME("ai_name", "DeepSeek"),

    // In-game advancement behaviour settings
    ADVANCEMENT_CALL("advancement_call", "false"),
    ADVANCEMENT_FILTER_ENABLED("advancement_filter_enabled", "true"),
    ADVANCEMENT_FILTER_MODE("advancement_filter_mode", AdvancementFilterMode.BLACKLIST.name),
    ADVANCEMENT_BLACKLIST("advancement_blacklist", "[]"),
    ADVANCEMENT_WHITELIST("advancement_whitelist", "[]"),
    ADVANCEMENT_PROMPT("advancement_prompt", "[Advancement Triggered]\nTitle: {title}\nDescription: {description}\nID: {id}\n\nPlease respond to this advancement."),

    // Session and multi-AI settings
    AI_PROFILES("ai_profiles", "{}"),
    COMMAND_SESSION_AI("command_session_ai", "default"),
    ADVANCEMENT_SESSION_AI("advancement_session_ai", "default"),
    SESSION_TTL_HOURS("session_ttl_hours", "24"),
    MAX_COMMAND_SESSIONS("max_command_sessions", "20"),
    MAX_ADVANCEMENT_SESSIONS("max_advancement_sessions", "10"),
    ;

    public final String id;
    public final String defaultValue;

    ConfigOption(String id, String defaultValue) {
        this.id = id;
        this.defaultValue = defaultValue;
    }
}
