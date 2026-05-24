package com.songhg.veri.agent.asset.application;

/**
 * Centralizes Chinese business messages for the asset module.
 *
 * <p>These constants replace inline string literals so that messages are
 * reviewable, traceable, and can be externalised to a resource bundle later.
 */
final class AssetMessages {

    private AssetMessages() {
    }

    // Resource not found
    static final String REQUIREMENT_NOT_FOUND = "需求不存在: %s";
    static final String API_NOT_FOUND = "API 不存在: %s";
    static final String PAGE_NOT_FOUND = "页面不存在: %s";
    static final String BUSINESS_FLOW_NOT_FOUND = "业务流不存在: %s";
    static final String TEST_CASE_NOT_FOUND = "测试用例不存在: %s";
    static final String TRACE_LINK_NOT_FOUND = "追踪关系不存在: %s";
    static final String VERSION_HISTORY_NOT_FOUND = "版本历史不存在: %s";
    static final String PROJECT_NOT_FOUND = "项目不存在: %s";

    // Validation
    static final String PROJECT_ID_REQUIRED = "projectId 不能为空";
    static final String TITLE_REQUIRED = "标题不能为空";
    static final String FIELD_REQUIRED = "%s 不能为空";
    static final String INVALID_FIELD = "%s 仅支持 %s";
    static final String HTTP_METHOD_REQUIRED = "HTTP 方法不能为空";
    static final String PATH_REQUIRED = "路径不能为空";
    static final String API_PATH_METHOD_CONFLICT = "已存在相同路径和 HTTP 方法的 API";
    static final String RESOURCE_CONFLICT = "关联资源不属于同一项目";
    static final String CROSS_PROJECT_LINK = "不能跨项目建立追踪关系";

    // Status transitions
    static final String INVALID_STATUS_TRANSITION = "%s 不允许从 %s 转换到 %s";
    static final String INVALID_LIFECYCLE_TRANSITION = "%s 生命周期不允许从 %s 转换到 %s";
    static final String IMPORTED_REQUIREMENT_LOCKED = "既有导入需求已进入评审或审批状态，需人工处理差异后再更新";
    static final String RESTORE_CONFLICT = "启用操作与既有%s冲突";

    // Import / Export
    static final String UNSUPPORTED_IMPORT_FORMAT = "不支持的导入格式: %s";
    static final String UNSUPPORTED_EXPORT_FORMAT = "不支持的导出格式: %s";
    static final String OPENAPI_ONLY_FOR_API = "OpenAPI %s仅支持 API 资产";
    static final String FORMAT_NOT_SUPPORTED = "format 不支持当前 assetType: %s/%s";

    // Rollback
    static final String VERSION_NOT_FOUND = "版本 %d 不存在";
    static final String NO_HISTORY = "暂无版本历史";

    // Lifecycle
    static final String ASSET_ARCHIVED = "%s 已归档: %s";
    static final String ASSET_RESTORED = "%s 已恢复: %s";
    static final String ASSET_DELETED = "%s 已删除: %s";

    // API specific
    static final String API_INVALID_HTTP_METHOD = "不支持的 HTTP 方法: %s";
    static final String API_INVALID_SOURCE = "不支持的 API 来源: %s";
}
