package com.songhg.veri.agent.asset.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.songhg.veri.agent.common.error.BusinessException;
import com.songhg.veri.agent.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON serialization helpers for the asset import/export module.
 *
 * <p>Extracted from {@link AssetImportExportService} so JSON parsing,
 * object-to-string conversion, and import-row deserialization live
 * in a single place with consistent error handling.
 */
final class AssetJsonCodec {

    private final ObjectMapper objectMapper;

    AssetJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    JsonNode parseJson(String content) {
        if (!org.springframework.util.StringUtils.hasText(content)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(content);
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }

    String toJsonString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "资产导入导出序列化失败");
        }
    }

    List<Map<String, String>> parseImportRows(String content) {
        try {
            List<Map<String, String>> rows = new ArrayList<>();
            JsonNode root = objectMapper.readTree(content);
            if (root == null || !root.isArray()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "JSON 导入内容必须是数组");
            }
            for (JsonNode element : root) {
                Map<String, String> row = new LinkedHashMap<>();
                element.fields().forEachRemaining(entry -> {
                    row.put(entry.getKey(), entry.getValue().isTextual()
                            ? entry.getValue().asText() : entry.getValue().toString());
                });
                rows.add(row);
            }
            return rows;
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "JSON 导入内容解析失败: " + e.getMessage());
        }
    }
}
