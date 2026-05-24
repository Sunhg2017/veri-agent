package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.domain.AssetReviewStatus;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.util.Map;
import java.util.Set;

/**
 * Asset import/export format constants and validation logic.
 *
 * <p>Extracted from {@link AssetImportExportService} to isolate format
 * definitions and cross-format validation from orchestration logic.
 */
final class AssetFormatValidator {

    static final String ASSET_REQUIREMENT = "REQUIREMENT";
    static final String ASSET_API = "API";
    static final String ASSET_TEST_CASE = "TEST_CASE";
    static final String FORMAT_CSV = "CSV";
    static final String FORMAT_JSON = "JSON";
    static final String FORMAT_OPENAPI = "OPENAPI";
    static final String SOURCE_IMPORT = "IMPORT";
    static final String STATUS_ACTIVE = "ACTIVE";

    static final Set<String> REVIEW_STATUSES = AssetReviewStatus.codes();
    static final Set<String> PRIORITIES = Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
    static final Set<String> API_STATUSES = Set.of(STATUS_ACTIVE, "DEPRECATED", "REMOVED");
    static final Set<String> API_SOURCES = Set.of(FORMAT_OPENAPI, "MANUAL", SOURCE_IMPORT);
    static final Set<String> API_HTTP_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    static final Set<String> IMPORT_EXPORT_FORMATS = Set.of(FORMAT_CSV, FORMAT_JSON, FORMAT_OPENAPI);
    static final Map<String, Set<String>> FORMATS_BY_ASSET_TYPE = Map.of(
            ASSET_REQUIREMENT, Set.of(FORMAT_CSV, FORMAT_JSON),
            ASSET_API, IMPORT_EXPORT_FORMATS,
            ASSET_TEST_CASE, Set.of(FORMAT_CSV, FORMAT_JSON)
    );
    static final Set<String> IMPORT_EXPORT_ASSET_TYPES = FORMATS_BY_ASSET_TYPE.keySet();

    private AssetFormatValidator() {
    }

    static String normalizeAssetType(String rawValue) {
        return valueIn(rawValue, null, IMPORT_EXPORT_ASSET_TYPES, "assetType");
    }

    static String normalizeFormat(String assetType, String rawValue, String operationName) {
        String format = valueIn(rawValue, FORMAT_CSV, IMPORT_EXPORT_FORMATS, "format");
        if (!FORMATS_BY_ASSET_TYPE.getOrDefault(assetType, Set.of()).contains(format)) {
            if (FORMAT_OPENAPI.equals(format)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "OpenAPI " + operationName + "仅支持 API 资产");
            }
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "format 不支持当前 assetType: " + format + "/" + assetType);
        }
        return format;
    }

    private static String valueIn(String value, String fallbackValue, Set<String> allowed, String fieldName) {
        if (value == null || value.isBlank()) {
            if (fallbackValue != null) {
                return fallbackValue;
            }
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 不能为空");
        }
        String trimmed = value.trim().toUpperCase();
        if (!allowed.contains(trimmed)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    fieldName + " 仅支持 " + String.join("/", allowed));
        }
        return trimmed;
    }
}
