package com.songhg.veri.agent.modelaccess.application;

/**
 * Centralizes Chinese business messages for the model-access module.
 */
final class ModelAccessMessages {

    private ModelAccessMessages() {
    }

    static final String PROVIDER_NOT_FOUND = "模型供应商不存在: %s";
    static final String PROMPT_NOT_FOUND = "Prompt 版本不存在: %s";
    static final String ACTIVE_PROMPT_NOT_FOUND = "未找到可用的 Prompt 活跃版本";
    static final String PROVIDER_ADAPTER_NOT_CONFIGURED = "模型供应商适配器未配置";
    static final String PROVIDER_CIRCUIT_OPEN = "模型供应商熔断中";
    static final String NO_AVAILABLE_PROVIDER = "没有符合策略的可用模型供应商";
    static final String INVOCATION_FAILED = "模型调用失败，invocationId=%s";
    static final String PROMPT_EXCEEDS_LIMIT = "Prompt 超出 WP2 最大长度限制";
    static final String INVALID_SENSITIVITY_LEVEL = "sensitivityLevel 仅支持 PUBLIC/INTERNAL/CONFIDENTIAL/RESTRICTED";
    static final String PROVIDER_NAME_REQUIRED = "模型供应商名称不能为空";
    static final String PROVIDER_NAME_CONFLICT = "模型供应商名称已存在";
    static final String NEGATIVE_COST = "模型供应商 token 成本不能为负数";
    static final String OPENAI_BASE_URL_REQUIRED = "OpenAI-compatible 供应商必须配置 baseUrl";
    static final String OPENAI_INVALID_BASE_URL = "OpenAI-compatible 供应商 baseUrl 格式无效";
    static final String OPENAI_API_KEY_REQUIRED = "OpenAI-compatible 供应商 apiKeyRef 必须使用 env:VARIABLE_NAME 或 secret:// 引用";
    static final String PROVIDER_UNAVAILABLE = "模型供应商调用失败";
    static final String HIGH_RISK_PROMPT_NEEDS_APPROVAL = "高风险 Prompt 需审批通过后才能激活";
    static final String LOW_RISK_NO_REVIEW = "低风险 Prompt 不需要审批";
    static final String ACTIVE_PROMPT_NO_REVIEW = "已激活 Prompt 不能重新审批";
    static final String ROUTING_GROUP_REQUIRED = "%s 不能为空";
    static final String ROUTING_GROUP_TOO_LONG = "%s 长度不能超过 %d";
    static final String ROUTING_GROUP_INVALID_CHARS = "%s 仅支持字母、数字、点、下划线、冒号和短横线";
}
