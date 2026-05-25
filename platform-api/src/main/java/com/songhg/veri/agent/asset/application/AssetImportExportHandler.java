package com.songhg.veri.agent.asset.application;

import com.songhg.veri.agent.asset.application.query.AssetExportRequest;
import com.songhg.veri.agent.asset.application.view.AssetImportItemResponse;
import java.util.Map;

interface AssetImportExportHandler {

    String assetType();

    /**
     * 先生成导入计划，dry-run 和真实导入都复用这条路径，避免校验规则分叉。
     */
    ImportPlan planImport(String projectId, Map<String, String> row, int rowNumber);

    /**
     * 执行已通过计划校验的单行导入；实现内部只处理对应资产类型的落库细节。
     */
    AssetImportItemResponse importRow(String projectId, Map<String, String> row, ImportPlan plan);

    /**
     * 按资产类型导出业务字段，入口服务只负责最终文件名和 Content-Type。
     */
    String exportAssets(AssetExportRequest request, String format);
}
