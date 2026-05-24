package com.songhg.veri.agent.asset.application.query;

public class AssetExportRequest extends AssetListRequest {

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
