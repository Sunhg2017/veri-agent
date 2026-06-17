package com.songhg.veri.agent.reporting.api.controller;

final class ReportPermissionScopes {

    static final String REPORT_REQUEST = "@reportPermissionScopeResolver.reportRequest(#command)";
    static final String REPORT_LIST = "@reportPermissionScopeResolver.reportList(#request)";
    static final String REPORT = "@reportPermissionScopeResolver.report(#id)";
    static final String REPORT_COMPARE = "@reportPermissionScopeResolver.reportCompare(#id, #baselineReportId)";

    private ReportPermissionScopes() {
    }
}
