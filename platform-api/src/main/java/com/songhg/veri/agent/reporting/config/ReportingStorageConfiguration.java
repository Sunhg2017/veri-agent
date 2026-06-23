package com.songhg.veri.agent.reporting.config;

import com.songhg.veri.agent.common.storage.LocalOpaqueFileStorage;
import com.songhg.veri.agent.common.storage.OpaqueFileStorage;
import com.songhg.veri.agent.common.storage.PlatformStorageProperties;
import com.songhg.veri.agent.reporting.application.ReportExportFileStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReportingStorageConfiguration {

    @Bean("reportExportFileStorage")
    public OpaqueFileStorage reportExportFileStorage(PlatformStorageProperties storageProperties) {
        return new LocalOpaqueFileStorage("reports", storageProperties.namespaceRoot("reports"));
    }

    @Bean
    public ReportExportFileStorage reportExportContentStorage(OpaqueFileStorage reportExportFileStorage) {
        return new ReportExportFileStorage(reportExportFileStorage);
    }
}
