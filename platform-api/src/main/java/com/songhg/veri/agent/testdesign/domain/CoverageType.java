package com.songhg.veri.agent.testdesign.domain;

import java.util.Set;

/**
 * WP5 候选用例覆盖类型
 *
 * <p>这些值同时受数据库 check 约束保护，新增类型时需要同步迁移脚本、
 * 质量门禁、前端筛选项和 OpenAPI 契约测试。</p>
 */
public enum CoverageType {
    /** 核心冒烟路径，验证功能是否具备最小可用闭环 */
    SMOKE,
    /** 常规功能路径，覆盖主要业务规则和正常输入 */
    FUNCTIONAL,
    /** 异常路径，覆盖错误输入、外部失败和失败提示 */
    EXCEPTION,
    /** 边界路径，覆盖数量、长度、状态边界等临界条件 */
    BOUNDARY,
    /** 权限路径，覆盖角色、资源 scope 和越权场景 */
    PERMISSION,
    /** 回归路径，覆盖历史缺陷或高频改动区域 */
    REGRESSION;

    /**
     * 返回所有数据库和接口允许的覆盖类型代码
     */
    public static Set<String> codes() {
        return Set.of(
                SMOKE.name(),
                FUNCTIONAL.name(),
                EXCEPTION.name(),
                BOUNDARY.name(),
                PERMISSION.name(),
                REGRESSION.name()
        );
    }
}
