package com.songhg.veri.agent.asset.api.request;

import com.songhg.veri.agent.asset.application.query.AssetListRequest;
import io.swagger.v3.oas.annotations.media.Schema;

public class AssetExportRequest extends AssetListRequest {

    @Schema(description = "资产类型")
    private String assetType;
    private String format;

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
}
