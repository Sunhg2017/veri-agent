package com.songhg.veri.agent.management.application.port;

import com.songhg.veri.agent.common.api.PageQuery;
import java.util.EnumSet;
import java.util.UUID;

/**
 * Parameter carrier for the management persistence port.
 *
 * <p>The management port no longer accepts raw key/value maps. This class keeps parameter names
 * explicit for MyBatis XML getters while constraining construction to one audited place.
 */
public final class ManagementStoreParams {

    private final Object[] values = new Object[Key.values().length];
    private final EnumSet<Key> presentKeys = EnumSet.noneOf(Key.class);

    private ManagementStoreParams() {
    }

    private ManagementStoreParams(ManagementStoreParams source) {
        System.arraycopy(source.values, 0, values, 0, values.length);
        presentKeys.addAll(source.presentKeys);
    }

    public static ManagementStoreParams empty() {
        return new ManagementStoreParams();
    }

    public static ManagementStoreParams copyOf(ManagementStoreParams source) {
        return new ManagementStoreParams(source);
    }

    public static ManagementStoreParams of(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("参数必须成对出现");
        }
        ManagementStoreParams params = empty();
        for (int index = 0; index < pairs.length; index += 2) {
            Object key = pairs[index];
            if (!(key instanceof String name)) {
                throw new IllegalArgumentException("参数名必须是字符串");
            }
            params.put(name, pairs[index + 1]);
        }
        return params;
    }

    public Object put(String key, Object value) {
        Key resolved = key(key);
        Object previous = values[resolved.ordinal()];
        values[resolved.ordinal()] = value;
        presentKeys.add(resolved);
        return previous;
    }

    public Object get(String key) {
        return value(key(key));
    }

    public boolean containsKey(String key) {
        return presentKeys.contains(key(key));
    }

    public ManagementStoreParams with(String key, Object value) {
        put(key, value);
        return this;
    }

    public ManagementStoreParams withActor(UUID actorId) {
        return with("actorId", actorId);
    }

    public ManagementStoreParams withPage(PageQuery pageQuery) {
        return with("search", pageQuery.search())
                .with("searchPattern", pageQuery.searchPattern())
                .with("limit", pageQuery.size())
                .with("offset", pageQuery.offset());
    }

    public Object getAction() {
        return value(Key.ACTION);
    }

    public Object getActor() {
        return value(Key.ACTOR);
    }

    public Object getActorId() {
        return value(Key.ACTOR_ID);
    }

    public Object getAfterJson() {
        return value(Key.AFTER_JSON);
    }

    public Object getAlgorithm() {
        return value(Key.ALGORITHM);
    }

    public Object getAllowPublicModel() {
        return value(Key.ALLOW_PUBLIC_MODEL);
    }

    public Object getApiBaseUrl() {
        return value(Key.API_BASE_URL);
    }

    public Object getAppId() {
        return value(Key.APP_ID);
    }

    public Object getAppType() {
        return value(Key.APP_TYPE);
    }

    public Object getApplication() {
        return value(Key.APPLICATION);
    }

    public Object getApplicationId() {
        return value(Key.APPLICATION_ID);
    }

    public Object getAuthTag() {
        return value(Key.AUTH_TAG);
    }

    public Object getCipherText() {
        return value(Key.CIPHER_TEXT);
    }

    public Object getCode() {
        return value(Key.CODE);
    }

    public Object getConfigKey() {
        return value(Key.CONFIG_KEY);
    }

    public Object getDefaultApiBaseUrl() {
        return value(Key.DEFAULT_API_BASE_URL);
    }

    public Object getDefaultWebUrl() {
        return value(Key.DEFAULT_WEB_URL);
    }

    public Object getDeptId() {
        return value(Key.DEPT_ID);
    }

    public Object getDepartmentId() {
        return value(Key.DEPARTMENT_ID);
    }

    public Object getDescription() {
        return value(Key.DESCRIPTION);
    }

    public Object getDisplayName() {
        return value(Key.DISPLAY_NAME);
    }

    public Object getEmail() {
        return value(Key.EMAIL);
    }

    public Object getEndTime() {
        return value(Key.END_TIME);
    }

    public Object getEndpoint() {
        return value(Key.ENDPOINT);
    }

    public Object getEnvId() {
        return value(Key.ENV_ID);
    }

    public Object getEnvType() {
        return value(Key.ENV_TYPE);
    }

    public Object getEnvironmentId() {
        return value(Key.ENVIRONMENT_ID);
    }

    public Object getExpiresAt() {
        return value(Key.EXPIRES_AT);
    }

    public Object getHealthCheckJson() {
        return value(Key.HEALTH_CHECK_JSON);
    }

    public Object getIv() {
        return value(Key.IV);
    }

    public Object getKey() {
        return value(Key.KEY);
    }

    public Object getKeyword() {
        return value(Key.KEYWORD);
    }

    public Object getLimit() {
        return value(Key.LIMIT);
    }

    public Object getMaskedValue() {
        return value(Key.MASKED_VALUE);
    }

    public Object getMasterKeyVersion() {
        return value(Key.MASTER_KEY_VERSION);
    }

    public Object getMemberType() {
        return value(Key.MEMBER_TYPE);
    }

    public Object getName() {
        return value(Key.NAME);
    }

    public Object getOffset() {
        return value(Key.OFFSET);
    }

    public Object getPasswordHash() {
        return value(Key.PASSWORD_HASH);
    }

    public Object getPath() {
        return value(Key.PATH);
    }

    public Object getPermissionCode() {
        return value(Key.PERMISSION_CODE);
    }

    public Object getPermissionCodes() {
        return value(Key.PERMISSION_CODES);
    }

    public Object getPlatformScope() {
        return value(Key.PLATFORM_SCOPE);
    }

    public Object getProjectId() {
        return value(Key.PROJECT_ID);
    }

    public Object getProjectKey() {
        return value(Key.PROJECT_KEY);
    }

    public Object getProviderCode() {
        return value(Key.PROVIDER_CODE);
    }

    public Object getProviderId() {
        return value(Key.PROVIDER_ID);
    }

    public Object getPurpose() {
        return value(Key.PURPOSE);
    }

    public Object getResourceId() {
        return value(Key.RESOURCE_ID);
    }

    public Object getResourceType() {
        return value(Key.RESOURCE_TYPE);
    }

    public Object getResult() {
        return value(Key.RESULT);
    }

    public Object getRoleCode() {
        return value(Key.ROLE_CODE);
    }

    public Object getRoleId() {
        return value(Key.ROLE_ID);
    }

    public Object getScopeId() {
        return value(Key.SCOPE_ID);
    }

    public Object getScopeType() {
        return value(Key.SCOPE_TYPE);
    }

    public Object getSearch() {
        return value(Key.SEARCH);
    }

    public Object getSearchPattern() {
        return value(Key.SEARCH_PATTERN);
    }

    public Object getSecretRef() {
        return value(Key.SECRET_REF);
    }

    public Object getSecretRefId() {
        return value(Key.SECRET_REF_ID);
    }

    public Object getSecretVersion() {
        return value(Key.SECRET_VERSION);
    }

    public Object getSensitivityLevel() {
        return value(Key.SENSITIVITY_LEVEL);
    }

    public Object getStartTime() {
        return value(Key.START_TIME);
    }

    public Object getStatus() {
        return value(Key.STATUS);
    }

    public Object getTraceId() {
        return value(Key.TRACE_ID);
    }

    public Object getUserId() {
        return value(Key.USER_ID);
    }

    public Object getUsername() {
        return value(Key.USERNAME);
    }

    public Object getValueJson() {
        return value(Key.VALUE_JSON);
    }

    public Object getVisibleProjectKeys() {
        return value(Key.VISIBLE_PROJECT_KEYS);
    }

    public Object getWebUrl() {
        return value(Key.WEB_URL);
    }

    private Object value(Key key) {
        return values[key.ordinal()];
    }

    private static Key key(String name) {
        return switch (name) {
            case "action" -> Key.ACTION;
            case "actor" -> Key.ACTOR;
            case "actorId" -> Key.ACTOR_ID;
            case "afterJson" -> Key.AFTER_JSON;
            case "algorithm" -> Key.ALGORITHM;
            case "allowPublicModel" -> Key.ALLOW_PUBLIC_MODEL;
            case "apiBaseUrl" -> Key.API_BASE_URL;
            case "appId" -> Key.APP_ID;
            case "appType" -> Key.APP_TYPE;
            case "application" -> Key.APPLICATION;
            case "applicationId" -> Key.APPLICATION_ID;
            case "authTag" -> Key.AUTH_TAG;
            case "cipherText" -> Key.CIPHER_TEXT;
            case "code" -> Key.CODE;
            case "configKey" -> Key.CONFIG_KEY;
            case "defaultApiBaseUrl" -> Key.DEFAULT_API_BASE_URL;
            case "defaultWebUrl" -> Key.DEFAULT_WEB_URL;
            case "deptId" -> Key.DEPT_ID;
            case "departmentId" -> Key.DEPARTMENT_ID;
            case "description" -> Key.DESCRIPTION;
            case "displayName" -> Key.DISPLAY_NAME;
            case "email" -> Key.EMAIL;
            case "endTime" -> Key.END_TIME;
            case "endpoint" -> Key.ENDPOINT;
            case "envId" -> Key.ENV_ID;
            case "envType" -> Key.ENV_TYPE;
            case "environmentId" -> Key.ENVIRONMENT_ID;
            case "expiresAt" -> Key.EXPIRES_AT;
            case "healthCheckJson" -> Key.HEALTH_CHECK_JSON;
            case "iv" -> Key.IV;
            case "key" -> Key.KEY;
            case "keyword" -> Key.KEYWORD;
            case "limit" -> Key.LIMIT;
            case "maskedValue" -> Key.MASKED_VALUE;
            case "masterKeyVersion" -> Key.MASTER_KEY_VERSION;
            case "memberType" -> Key.MEMBER_TYPE;
            case "name" -> Key.NAME;
            case "offset" -> Key.OFFSET;
            case "passwordHash" -> Key.PASSWORD_HASH;
            case "path" -> Key.PATH;
            case "permissionCode" -> Key.PERMISSION_CODE;
            case "permissionCodes" -> Key.PERMISSION_CODES;
            case "platformScope" -> Key.PLATFORM_SCOPE;
            case "projectId" -> Key.PROJECT_ID;
            case "projectKey" -> Key.PROJECT_KEY;
            case "providerCode" -> Key.PROVIDER_CODE;
            case "providerId" -> Key.PROVIDER_ID;
            case "purpose" -> Key.PURPOSE;
            case "resourceId" -> Key.RESOURCE_ID;
            case "resourceType" -> Key.RESOURCE_TYPE;
            case "result" -> Key.RESULT;
            case "roleCode" -> Key.ROLE_CODE;
            case "roleId" -> Key.ROLE_ID;
            case "scopeId" -> Key.SCOPE_ID;
            case "scopeType" -> Key.SCOPE_TYPE;
            case "search" -> Key.SEARCH;
            case "searchPattern" -> Key.SEARCH_PATTERN;
            case "secretRef" -> Key.SECRET_REF;
            case "secretRefId" -> Key.SECRET_REF_ID;
            case "secretVersion" -> Key.SECRET_VERSION;
            case "sensitivityLevel" -> Key.SENSITIVITY_LEVEL;
            case "startTime" -> Key.START_TIME;
            case "status" -> Key.STATUS;
            case "traceId" -> Key.TRACE_ID;
            case "userId" -> Key.USER_ID;
            case "username" -> Key.USERNAME;
            case "valueJson" -> Key.VALUE_JSON;
            case "visibleProjectKeys" -> Key.VISIBLE_PROJECT_KEYS;
            case "webUrl" -> Key.WEB_URL;
            default -> throw new IllegalArgumentException("未知管理存储参数: " + name);
        };
    }

    private enum Key {
        ACTION,
        ACTOR,
        ACTOR_ID,
        AFTER_JSON,
        ALGORITHM,
        ALLOW_PUBLIC_MODEL,
        API_BASE_URL,
        APP_ID,
        APP_TYPE,
        APPLICATION,
        APPLICATION_ID,
        AUTH_TAG,
        CIPHER_TEXT,
        CODE,
        CONFIG_KEY,
        DEFAULT_API_BASE_URL,
        DEFAULT_WEB_URL,
        DEPT_ID,
        DEPARTMENT_ID,
        DESCRIPTION,
        DISPLAY_NAME,
        EMAIL,
        END_TIME,
        ENDPOINT,
        ENV_ID,
        ENV_TYPE,
        ENVIRONMENT_ID,
        EXPIRES_AT,
        HEALTH_CHECK_JSON,
        IV,
        KEY,
        KEYWORD,
        LIMIT,
        MASKED_VALUE,
        MASTER_KEY_VERSION,
        MEMBER_TYPE,
        NAME,
        OFFSET,
        PASSWORD_HASH,
        PATH,
        PERMISSION_CODE,
        PERMISSION_CODES,
        PLATFORM_SCOPE,
        PROJECT_ID,
        PROJECT_KEY,
        PROVIDER_CODE,
        PROVIDER_ID,
        PURPOSE,
        RESOURCE_ID,
        RESOURCE_TYPE,
        RESULT,
        ROLE_CODE,
        ROLE_ID,
        SCOPE_ID,
        SCOPE_TYPE,
        SEARCH,
        SEARCH_PATTERN,
        SECRET_REF,
        SECRET_REF_ID,
        SECRET_VERSION,
        SENSITIVITY_LEVEL,
        START_TIME,
        STATUS,
        TRACE_ID,
        USER_ID,
        USERNAME,
        VALUE_JSON,
        VISIBLE_PROJECT_KEYS,
        WEB_URL
    }
}
