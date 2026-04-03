package heyblack.mineds.config;

public enum ConfigOption {
    // API Settings
    URL("url", "https://api.siliconflow.cn/v1/chat/completions", null, null),
    MODEL("model", "deepseek-ai/DeepSeek-R1", null, null),
    API_KEY("api_key", "YOUR_API_KEY", null, null),
    TEMPERATURE("temperature", "0.7", 0.0f, 2.0f),
    MAX_TOKENS("max_tokens", "4096", 1, 8192),
    SYSTEM_MESSAGE("system_message", "你是一只傲娇猫娘", null, null),

    // In-game general behaviour settings
    MAX_REQUEST("max_request", "1", 1, 10),
    AI_NAME("ai_name", "DeepSeek", null, null),

    // In-game advancement behaviour settings
    ADVANCEMENT_CALL("advancement_call", "true", null, null),
    ;

    public final String id;
    public final String defaultValue;
    public final Number minValue;
    public final Number maxValue;

    ConfigOption(String id, String defaultValue, Number minValue, Number maxValue) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    /**
     * 检查配置值是否在有效范围内。
     *
     * @param value 配置值
     * @return 如果有效或无范围限制则返回 true
     */
    public boolean isValid(String value) {
        if (value == null)
            return false;

        if (minValue != null && maxValue != null) {
            try {
                if (minValue instanceof Integer) {
                    int num = Integer.parseInt(value);
                    return num >= (Integer) minValue && num <= (Integer) maxValue;
                } else if (minValue instanceof Float) {
                    float num = Float.parseFloat(value);
                    return num >= (Float) minValue && num <= (Float) maxValue;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }
}
