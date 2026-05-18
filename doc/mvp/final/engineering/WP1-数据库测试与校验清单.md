# WP1 数据库测试与校验清单

> 历史归档说明：本文记录早期多租户数据库测试设计，不代表当前 validation 准出清单。当前数据库验证以 `db/validation/run_wp1_db_validation.sh`、`doc/mvp/final/engineering/当前实现基线.md`、迁移脚本和自动化测试为准；当前实现不包含 `base_tenant`，业务表不维护 `tenant_id`。

| 项目 | 内容 |
|---|---|
| 工作包 | WP1 平台基础底座 |
| 文档用途 | QA 测试设计、后端研发自测、数据库迁移评审、上线准出检查 |
| 覆盖范围 | 表结构、约束、索引、租户隔离、软删除、唯一性、状态流、敏感字段、审计日志、初始化种子数据 |
| 适用数据库 | PostgreSQL，具体字段类型以最终 DDL 为准 |
| 优先级定义 | P0 必测；P1 重点补充；P2 延后或专项 |

## 1. 测试设计原则

1. 数据库校验必须与 API 契约、权限矩阵和架构设计保持一致，不能只验证 DDL 能执行。
2. 所有业务主表必须验证 `tenant_id`、审计字段、软删除字段、唯一索引和关键外键/关联完整性。
3. 多租户、敏感字段、审计日志、角色授权、环境变量是 P0 安全门禁，必须进入 CI 或发布流水线。
4. 数据库直连校验只用于测试和迁移评审；后续 WP2/WP3/WP8/WP9/WP11 不得直接读写 WP1 表。
5. 自动化建议优先使用迁移校验脚本、SQL 断言、接口自动化后的数据库旁路核验和 Explain 计划检查。

## 2. 基础测试数据建议

| 数据类别 | 准备建议 |
|---|---|
| 租户 | 准备 `tenant_a`、`tenant_b`、`tenant_disabled`，另保留平台级 `SYSTEM_TENANT` 审计口径。 |
| 部门 | 每个租户至少 3 个部门，覆盖父子层级、同父级同名冲突、停用部门、多人负责人。 |
| 用户 | 覆盖 8 个预置角色账号、停用用户、锁定用户、无项目授权用户、跨租户同名用户。 |
| 项目 | 每租户至少 2 个项目，覆盖主项目、同租户未授权项目、归档项目、停用项目。 |
| 应用 | 每项目至少 2 个应用，覆盖 Web/API 类型、启用应用、停用应用、同项目编码冲突。 |
| 环境 | 覆盖项目公共环境、应用专属环境、PROD 环境、停用环境、同 code 不同作用域环境。 |
| 环境变量 | 普通变量 `BASE_URL`、`FEATURE_FLAG`；敏感变量 `API_TOKEN`、`ADMIN_PASSWORD`、`COOKIE_SECRET`。 |
| RBAC | 初始化 8 个预置角色、P0 权限点、角色权限、用户/部门作用域角色绑定。 |
| 审计 | 准备成功、失败、越权拒绝、敏感变量变更、授权解绑、状态流转、内部审计写入事件。 |
| 迁移数据 | 准备空库、已有部分对象库、含脏数据副本、失败中断快照、重复迁移回放环境。 |

## 3. 数据库测试点

### 3.1 表结构校验

| 编号 | 优先级 | 测试目标 | 前置数据 | 操作/校验方式 | 预期结果 | 建议自动化方式 |
|---|---|---|---|---|---|---|
| DB-STRUCT-001 | P0 | 校验 WP1 核心表完整创建 | 空库完成迁移 | 查询 `information_schema.tables`，核对 `base_tenant`、`base_department`、`base_department_manager`、`base_department_member`、`iam_user`、`iam_session`、`base_project`、`base_project_department`、`base_project_member`、`base_application`、`base_environment`、`base_environment_variable`、`base_config`、`secret_provider`、`secret_reference`、`secret_local_store`、`rbac_permission`、`rbac_role`、`rbac_role_permission`、`rbac_role_binding`、`audit_log`、`audit_outbox` | 所有核心表存在，表名与数据库设计一致，无历史废弃表替代核心模型 | 迁移后 SQL 断言脚本 |
| DB-STRUCT-002 | P0 | 校验业务表强制包含 `tenant_id` | 空库完成迁移 | 检查除 `base_tenant`、全局权限字典等特殊表外的业务表字段 | `base_department`、`iam_user`、`iam_session`、`base_*`、`rbac_role`、`rbac_role_binding`、`audit_*`、`secret_local_store` 均包含 `tenant_id` | SQL 元数据扫描 |
| DB-STRUCT-003 | P0 | 校验通用审计字段和软删除字段 | 空库完成迁移 | 检查业务表是否包含 `created_at`、`created_by`、`updated_at`、`updated_by`、`deleted_at` | 业务可变更表具备通用字段；`audit_log` 只追加，可不要求 `updated_by/deleted_at` 但需有 `created_at` | SQL 元数据扫描 |
| DB-STRUCT-004 | P0 | 校验废弃单负责人字段不存在 | 空库完成迁移 | 检查 `base_department.manager_user_id`、`base_project.dept_id`、`base_application.owner_user_id` | 废弃字段不存在；负责人通过关联表或 `rbac_role_binding` 表达 | SQL 元数据扫描 |
| DB-STRUCT-005 | P0 | 校验环境作用域字段完整 | 已创建项目公共环境和应用专属环境 | 检查 `base_environment` 字段 `tenant_id`、`project_id`、`app_id`、`scope_type`、`code`、`env_type`、执行开关字段 | 项目公共环境 `app_id` 为空；应用专属环境同时保留 `project_id` 和 `app_id` | 接口造数 + SQL 断言 |
| DB-STRUCT-006 | P0 | 校验环境变量敏感字段结构 | 空库完成迁移 | 检查 `base_environment_variable` 是否包含 `value_kind`、`plain_value`、`secret_ref`、`secret_provider`、`masked_value` | 普通变量和敏感变量具备分型存储字段 | SQL 元数据扫描 |
| DB-STRUCT-007 | P0 | 校验审计日志结构 | 空库完成迁移 | 检查 `audit_log` 是否包含 `trace_id`、`actor_type`、`actor_user_id`、`actor_service`、`action`、`resource_type`、`resource_id`、`scope_type`、`scope_id`、`result`、`before_json`、`after_json`、`diff_json`、`reason`、`created_at` | 字段可支持操作人、资源、结果、变更摘要和平台级/租户级查询 | SQL 元数据扫描 |
| DB-STRUCT-008 | P1 | 校验 JSON 字段类型合理 | 空库完成迁移 | 检查 `quota_json`、`settings_json`、`health_check_json`、`config_value_json`、`condition_json`、`event_json` 是否使用 JSON/JSONB 或等价结构 | JSON 字段可被安全解析和索引扩展，不使用不可校验的大文本替代关键结构 | SQL 元数据扫描 + 研发评审 |
| DB-STRUCT-009 | P1 | 校验状态和枚举字段可约束 | 空库完成迁移 | 检查状态字段是否有 check constraint、枚举类型或应用层枚举映射清单 | 租户、用户、项目、应用、环境、角色、审计结果等枚举值有明确约束方案 | SQL 元数据扫描 + 代码扫描 |
| DB-STRUCT-010 | P2 | 校验字段注释和迁移可读性 | 空库完成迁移 | 检查核心表和关键字段 comment | 关键字段具备中文或英文注释，便于 DBA 和后续 WP 评审 | SQL 元数据扫描 |

### 3.2 约束校验

| 编号 | 优先级 | 测试目标 | 前置数据 | 操作/校验方式 | 预期结果 | 建议自动化方式 |
|---|---|---|---|---|---|---|
| DB-CONS-001 | P0 | 校验主键和非空约束 | 空库完成迁移 | 检查核心表主键；尝试插入缺少 `id`、`tenant_id`、`code`、`status` 等必填字段的数据 | 主键存在；必填字段缺失时数据库或应用层拒绝 | SQL 断言 + 负向插入 |
| DB-CONS-002 | P0 | 校验部门树不能引用跨租户父部门 | `tenant_a`、`tenant_b` 各有部门 | 通过 API 或测试 SQL 尝试将 A 部门父级设为 B 部门 | 操作失败；不形成跨租户部门树 | 接口自动化 + SQL 断言 |
| DB-CONS-003 | P0 | 校验部门树禁止自引用和循环 | 已有三层部门 | 尝试将部门父级改成自身或子部门 | 操作失败；`path` 不变；审计记录失败原因 | 接口自动化 |
| DB-CONS-004 | P0 | 校验用户必须有且仅有一个主部门 | 用户属于多个部门 | 尝试插入 0 个或 2 个 `is_primary=true` 的启用部门成员关系 | 操作失败或应用层回滚；用户启用态下仅一个主部门 | 接口自动化 + SQL 断言 |
| DB-CONS-005 | P0 | 校验项目关联部门必须同租户且有一个主责部门 | 项目和多个部门已创建 | 创建项目时不传主责部门、传多个主责部门、传跨租户部门 | 均失败；成功项目只有一个 `base_project_department.is_primary=true` | 接口自动化 + SQL 断言 |
| DB-CONS-006 | P0 | 校验项目公共环境和应用专属环境字段约束 | 已有项目和应用 | 创建 `scope_type=PROJECT` 且 `app_id` 非空；创建 `scope_type=APPLICATION` 但 `app_id` 为空 | 均失败；环境作用域与字段组合一致 | 接口自动化 |
| DB-CONS-007 | P0 | 校验角色绑定作用域合法 | 已有用户、角色、项目、应用、环境 | 尝试写入未知 `subject_type`、未知 `scope_type`、跨租户 `scope_id` | 操作失败；不产生无效授权 | 接口自动化 + SQL 断言 |
| DB-CONS-008 | P0 | 校验内置角色不可停用或删除 | 租户角色种子已初始化 | 尝试停用或软删除 `SuperAdmin`、`TenantAdmin` 等内置角色 | 操作失败；`is_system=true` 角色保持启用 | 接口自动化 |
| DB-CONS-009 | P0 | 校验审计日志只追加 | 已有审计日志 | 尝试通过业务接口修改或删除审计；迁移评审检查是否无更新型业务接口 | 业务无修改/删除入口；数据库权限或触发器策略禁止应用账号改写历史审计 | 人工评审 + SQL 权限检查 |
| DB-CONS-010 | P1 | 校验权限版本与权限变更一致 | 已有角色绑定和权限缓存 | 执行角色绑定、解绑、角色权限修改 | 用户级变更递增 `iam_user.auth_version`；角色权限变化递增 `rbac_role.version`；旧权限失效 | 接口自动化 + SQL 断言 |

### 3.3 索引校验

| 编号 | 优先级 | 测试目标 | 前置数据 | 操作/校验方式 | 预期结果 | 建议自动化方式 |
|---|---|---|---|---|---|---|
| DB-IDX-001 | P0 | 校验租户编码全局唯一索引 | 空库完成迁移 | 检查 `base_tenant.uk_code(code)` 或等价唯一索引 | 租户编码全局唯一，重复创建返回 `CONFLICT` | SQL 元数据扫描 + 接口负向 |
| DB-IDX-002 | P0 | 校验租户内唯一索引 | 空库完成迁移 | 检查 `base_department(tenant_id,code)`、`iam_user(tenant_id,username)`、`base_project(tenant_id,code)`、`rbac_role(tenant_id,scope,code)` | 唯一索引包含租户或作用域，不产生跨租户冲突 | SQL 元数据扫描 |
| DB-IDX-003 | P0 | 校验父级作用域唯一索引 | 空库完成迁移 | 检查 `base_application(project_id,code)`、`base_environment_variable(env_id,key)`、`base_config(tenant_id,scope_type,scope_id,config_key)` | 同父级内唯一，跨父级允许相同编码或 key | SQL 元数据扫描 + 接口负向 |
| DB-IDX-004 | P0 | 校验环境分作用域唯一索引 | 已有项目公共环境和应用专属环境 | 检查 `base_environment` 存在项目公共环境部分唯一索引和应用专属环境部分唯一索引 | `project_id + scope_type + code where app_id is null` 唯一；`app_id + scope_type + code where app_id is not null` 唯一 | SQL 元数据扫描 |
| DB-IDX-005 | P0 | 校验关联表去重索引 | 空库完成迁移 | 检查 `base_department_manager(dept_id,user_id)`、`base_department_member(dept_id,user_id)`、`base_project_department(project_id,dept_id)`、`base_project_member(project_id,user_id)`、`rbac_role_permission(role_id,permission_id)` | 重复成员、重复负责人、重复项目部门、重复角色权限无法插入 | SQL 元数据扫描 + 接口负向 |
| DB-IDX-006 | P0 | 校验审计查询索引 | 空库完成迁移 | 检查 `audit_log(tenant_id,created_at)`、`audit_log(tenant_id,resource_type,resource_id)`、`audit_log(tenant_id,actor_user_id,created_at)` | 审计按租户时间、资源、操作者查询可命中索引 | SQL 元数据扫描 + Explain |
| DB-IDX-007 | P0 | 校验权限计算索引 | 空库完成迁移 | 检查 `rbac_role_binding(tenant_id,subject_type,subject_id)`、`rbac_role_binding(tenant_id,scope_type,scope_id)` | 用户/部门授权和资源范围授权查询可命中索引 | SQL 元数据扫描 + Explain |
| DB-IDX-008 | P1 | 校验列表分页索引 | 大数据量项目、应用、用户、环境 | 对用户列表、项目列表、应用列表、环境列表执行 Explain | 查询计划优先使用租户/父级/状态/时间相关索引，无全表扫描热点 | 性能自动化 |
| DB-IDX-009 | P1 | 校验部门树索引 | 构造 5 层以上部门树 | 对 `tenant_id + parent_id` 和 `tenant_id + path` 子树查询执行 Explain | 部门树查询命中 `idx_tenant_parent` 或 `idx_tenant_path` | 性能自动化 |
| DB-IDX-010 | P2 | 校验索引命名规范 | 空库完成迁移 | 扫描唯一索引 `uk_*`、普通索引 `idx_*`、主键 `pk_*` 命名 | 索引命名清晰，便于迁移 diff 和 DBA 审核 | SQL 元数据扫描 |

### 3.4 租户隔离校验

| 编号 | 优先级 | 测试目标 | 前置数据 | 操作/校验方式 | 预期结果 | 建议自动化方式 |
|---|---|---|---|---|---|---|
| DB-TENANT-001 | P0 | 校验所有列表查询带租户过滤 | `tenant_a`、`tenant_b` 均有完整数据 | 使用 `ta_a` 查询用户、部门、项目、应用、环境、审计列表 | 响应和 SQL 旁路结果均只包含 `tenant_a` 数据 | 接口自动化 + SQL 抽样 |
| DB-TENANT-002 | P0 | 校验跨租户 ID 枚举不泄露 | 已知 `tenant_b` 项目、应用、环境、审计 ID | 使用 `tenant_a` 用户访问 B 资源详情 | 返回 `403` 或统一 `404`；响应不含 B 资源名称；记录拒绝审计 | 接口自动化 |
| DB-TENANT-003 | P0 | 校验写入时 `tenant_id` 由上下文注入 | `ta_a` 已登录 | 创建部门、项目、环境时请求体伪造 `tenant_b` 的 `tenant_id` | 数据写入 `tenant_a` 或请求被拒绝；不得写入 `tenant_b` | 接口自动化 + SQL 断言 |
| DB-TENANT-004 | P0 | 校验跨租户关联被拒绝 | A 项目、B 部门/B 用户/B 应用存在 | A 租户创建项目关联 B 部门、为 B 用户绑定 A 角色、创建 A 环境绑定 B 应用 | 操作失败；无跨租户关联记录 | 接口自动化 + SQL 断言 |
| DB-TENANT-005 | P0 | 校验平台审计租户口径 | 平台级初始化、租户创建、租户停用已执行 | 查询对应 `audit_log` | 平台级审计使用 `tenant_id=SYSTEM_TENANT` 且 `scope_type=PLATFORM` | SQL 断言 |
| DB-TENANT-006 | P1 | 校验服务间委托上下文隔离 | 服务令牌和 `X-Delegated-User-Id` 可用 | 委托 A 用户读取 B 项目上下文 | 服务身份合法但委托用户越权时仍拒绝 | 接口自动化 |

### 3.5 软删除校验

| 编号 | 优先级 | 测试目标 | 前置数据 | 操作/校验方式 | 预期结果 | 建议自动化方式 |
|---|---|---|---|---|---|---|
| DB-SOFT-001 | P0 | 校验基础组织对象优先软删除 | 已有部门、用户、项目、应用、环境 | 执行删除类或移除类操作，检查记录状态和 `deleted_at` | MVP 不提供硬删除；移除成员/解绑可保留历史或软删除；历史资产归属不丢失 | 接口自动化 + SQL 断言 |
| DB-SOFT-002 | P0 | 校验逻辑删除数据默认不可见 | 已软删除或移除成员关系 | 查询列表、详情、权限计算、上下文接口 | 默认不返回 `deleted_at` 非空或已移除数据；历史审计仍可定位资源 | 接口自动化 |
| DB-SOFT-003 | P0 | 校验软删除后唯一性策略 | 已软删除部门/项目/应用或成员关系 | 创建同编码或同关联的新记录 | 按产品口径处理：若允许复用则唯一索引需排除软删除；若不允许复用则返回冲突，行为必须一致 | 接口自动化 + 迁移评审 |
| DB-SOFT-004 | P1 | 校验软删除不破坏审计引用 | 已有资源被软删除 | 查询资源相关审计详情 | 审计仍可展示资源 ID、编码摘要和变更摘要，不因资源不可见而报错 | 接口自动化 |
| DB-SOFT-005 | P2 | 校验软删除恢复策略 | 若实现恢复能力 | 恢复已软删除对象并查询上下文 | 恢复后状态、唯一性、权限和审计均一致 | 接口自动化 |

### 3.6 唯一性校验

| 编号 | 优先级 | 测试目标 | 前置数据 | 操作/校验方式 | 预期结果 | 建议自动化方式 |
|---|---|---|---|---|---|---|
| DB-UNIQ-001 | P0 | 校验租户编码全局唯一 | 已有 `tenant_a` | 再次创建 `code=tenant_a` 的租户 | 返回 `CONFLICT`；不新增租户和管理员 | 接口自动化 |
| DB-UNIQ-002 | P0 | 校验部门编码租户内唯一 | A/B 租户各有部门 | A 租户重复创建同 `code` 部门；B 租户创建同 `code` 部门 | A 租户冲突；B 租户允许 | 接口自动化 |
| DB-UNIQ-003 | P0 | 校验同父级部门名称唯一 | 同一父部门下已有部门 | 同父级创建同名部门；不同父级创建同名部门 | 同父级冲突；不同父级按产品口径允许 | 接口自动化 |
| DB-UNIQ-004 | P0 | 校验用户账号租户内唯一 | A/B 租户各有账号 | A 租户重复创建同 `username`；B 租户创建同 `username` | A 租户冲突；B 租户允许 | 接口自动化 |
| DB-UNIQ-005 | P0 | 校验项目编码租户内唯一 | A 租户已有项目 | 重复创建同 `code` 项目；B 租户创建同 `code` 项目 | A 租户冲突；B 租户允许 | 接口自动化 |
| DB-UNIQ-006 | P0 | 校验应用编码项目内唯一 | 同项目已有 `app_a_web` | 同项目重复创建同 `code`；其他项目创建同 `code` | 同项目冲突；其他项目允许 | 接口自动化 |
| DB-UNIQ-007 | P0 | 校验环境编码按作用域唯一 | 项目公共环境和应用专属环境存在 | 同项目公共环境重复 `code`；同应用专属环境重复 `code`；不同应用同 `code` | 同作用域冲突；不同应用允许；项目公共与应用专属互不误判 | 接口自动化 |
| DB-UNIQ-008 | P0 | 校验环境变量 key 单环境唯一 | 环境已有 `BASE_URL` | `MERGE` 或 `REPLACE` 重复提交同 key | 不产生重复行；覆盖或校验失败行为符合契约 | 接口自动化 + SQL 断言 |
| DB-UNIQ-009 | P0 | 校验角色绑定不重复 | 用户已绑定某角色到同一作用域 | 重复提交同角色绑定 | 返回 `CONFLICT` 或幂等成功，但 `rbac_role_binding` 不重复 | 接口自动化 + SQL 断言 |
| DB-UNIQ-010 | P1 | 校验幂等键唯一性 | 创建接口支持 `Idempotency-Key` | 同一租户/用户/路径/幂等键重复提交相同和不同 payload | 相同 payload 返回首次结果并标记重放；不同 payload 返回 `CONFLICT` | 接口自动化 |

### 3.7 状态流数据校验

| 编号 | 优先级 | 测试目标 | 前置数据 | 操作/校验方式 | 预期结果 | 建议自动化方式 |
|---|---|---|---|---|---|---|
| DB-STATE-001 | P0 | 校验租户启停状态流 | 启用租户有活跃用户会话 | 停用租户，再尝试登录和访问 API | 租户状态为 `DISABLED`；普通用户会话撤销；`SuperAdmin` 可查看和恢复 | 接口自动化 + SQL 断言 |
| DB-STATE-002 | P0 | 校验用户状态流和 `auth_version` | 启用用户有活跃会话 | 停用、锁定、重新启用用户 | 停用/锁定后会话撤销且 `auth_version` 递增；非启用用户不可登录 | 接口自动化 + SQL 断言 |
| DB-STATE-003 | P0 | 校验项目归档/停用只读规则 | 进行中项目 | 归档或停用后新增成员、应用、环境 | 状态更新成功；新增资源失败；历史数据只读可查 | 接口自动化 |
| DB-STATE-004 | P0 | 校验应用停用阻断新专属环境 | 启用应用 | 停用应用后创建应用专属环境 | 创建失败；历史环境仍可只读查询 | 接口自动化 |
| DB-STATE-005 | P0 | 校验环境停用不可使用 | 启用环境已授权 Tester | 停用环境后调用 `/use-check` | 返回不可用；不会作为后续执行目标 | 接口自动化 |
| DB-STATE-006 | P0 | 校验角色停用不参与权限计算 | 非内置角色或测试角色存在 | 停用角色后用旧 Token 访问原授权资源 | 权限失效；`rbac_role.version` 更新，受影响用户权限缓存失效 | 接口自动化 + SQL 断言 |
| DB-STATE-007 | P1 | 校验重复状态设置审计 `no_change` | 已停用环境 | 再次设置为 `DISABLED` | 可返回 OK 或冲突，但审计中若成功应标记 `no_change=true` | 接口自动化 |
| DB-STATE-008 | P1 | 校验 PROD 环境默认执行开关 | 创建 `env_type=PROD` 环境 | 查询 `allow_api_execution`、`allow_ui_execution`、`allow_e2e_execution` | PROD 默认关闭自动执行开关，开启需记录原因和审计 | 接口自动化 + SQL 断言 |

### 3.8 敏感字段校验

| 编号 | 优先级 | 测试目标 | 前置数据 | 操作/校验方式 | 预期结果 | 建议自动化方式 |
|---|---|---|---|---|---|---|
| DB-SECRET-001 | P0 | 校验密码只存哈希 | 创建本地用户 | SQL 检查 `iam_user.password_hash` 和审计详情 | 不存在明文密码；哈希不可逆；审计不包含密码明文 | 接口自动化 + SQL 扫描 |
| DB-SECRET-002 | P0 | 校验敏感变量不写 `plain_value` | 创建 `API_TOKEN=real-token-value` | 查询 `base_environment_variable` | `value_kind` 为 `SECRET` 或 `SECRET_REF`；`plain_value` 为空；存在 `secret_ref`、`masked_value` | 接口自动化 + SQL 断言 |
| DB-SECRET-003 | P0 | 校验 `secret_local_store` 不保存明文 | 本地加密 SecretProvider 启用 | 搜索 `secret_local_store.cipher_text`、`masked_value` 等字段 | 不出现 `real-token-value`、密码、Cookie 明文；密文字段非空 | SQL 敏感词扫描 |
| DB-SECRET-004 | P0 | 校验 `settings_json` 不混入敏感值 | 创建环境鉴权配置和敏感配置 | 扫描 `base_tenant.settings_json`、`base_project.settings_json`、`base_application.settings_json`、`base_environment.settings_json`、`base_config.config_value_json` | 敏感配置只保存引用、掩码或加密摘要，不保存明文 | SQL 敏感词扫描 |
| DB-SECRET-005 | P0 | 校验审计 JSON 脱敏 | 修改敏感变量和鉴权配置 | 查询 `audit_log.before_json`、`after_json`、`diff_json` | 只包含字段名、`changed=true`、`masked_value`、`secret_ref_summary`、`secret_provider`、`secret_version` | 接口自动化 + SQL 扫描 |
| DB-SECRET-006 | P0 | 校验疑似敏感变量不能按 PLAIN 保存 | 提交 `ADMIN_PASSWORD`、`API_TOKEN` 且 `value_kind=PLAIN` | 调用环境变量保存接口 | 返回 `SECRET_POLICY_VIOLATION`；数据库不落明文 | 接口自动化 |
| DB-SECRET-007 | P0 | 校验密钥服务失败主业务回滚 | 模拟 SecretProvider 保存失败 | 保存敏感变量 | 返回 `SECRET_PROVIDER_ERROR`；不新增 `base_environment_variable` 或 `secret_local_store` 半成品 | 接口自动化 + 故障注入 |
| DB-SECRET-008 | P1 | 校验导出和应用日志无敏感明文 | 有敏感变量变更审计 | 导出审计或检查应用日志 | 文件和日志不含真实 token、密码、Cookie | 人工专项 + 敏感词扫描 |

### 3.9 审计日志校验

| 编号 | 优先级 | 测试目标 | 前置数据 | 操作/校验方式 | 预期结果 | 建议自动化方式 |
|---|---|---|---|---|---|---|
| DB-AUDIT-001 | P0 | 校验 P0 成功写操作均记录审计 | 执行租户、部门、用户、项目、应用、环境、角色、配置写操作 | 查询 `audit_log` | 每类成功写操作均有审计；包含 `tenant_id`、`trace_id`、操作者、资源、动作、结果、变更摘要 | 接口自动化 + SQL 断言 |
| DB-AUDIT-002 | P0 | 校验越权拒绝写审计 | 执行跨租户 ID 枚举和同租户越权 | 查询 `audit_log` | 生成 `ACCESS_DENIED` 或等价拒绝审计；业务数据未变化 | 接口自动化 |
| DB-AUDIT-003 | P0 | 校验审计租户范围查询 | A/B 租户均有审计 | A 租户审计员查询审计列表和详情 | 只返回 A 租户授权范围审计；B 审计不可见 | 接口自动化 |
| DB-AUDIT-004 | P0 | 校验审计日志不可篡改 | 已有审计日志 | 使用应用数据库账号尝试更新或删除 `audit_log` | 操作被权限、触发器或审计策略拒绝；若 DBA 账号可改，需记录为运维特权风险 | SQL 权限检查 + 人工评审 |
| DB-AUDIT-005 | P0 | 校验审计 outbox 补偿 | 模拟审计写入失败 | 执行业务写操作后恢复审计任务 | 主业务按设计成功；`audit_outbox` 写入待补偿事件；重试后补齐审计 | 故障注入 + SQL 断言 |
| DB-AUDIT-006 | P1 | 校验审计默认近 7 天查询 | 审计数据跨多个时间段 | 不传时间范围查询审计 | 默认只查近 7 天；大范围查询按权限和性能规则限制 | 接口自动化 |
| DB-AUDIT-007 | P1 | 校验审计导出自身被审计 | 开启审计导出 | 发起导出任务 | 生成 `AUDIT_EXPORT` 审计；导出条件、结果、文件摘要脱敏记录 | 接口自动化 |

### 3.10 初始化种子数据校验

| 编号 | 优先级 | 测试目标 | 前置数据 | 操作/校验方式 | 预期结果 | 建议自动化方式 |
|---|---|---|---|---|---|---|
| DB-SEED-001 | P0 | 校验 P0 权限点完整初始化 | 空库迁移和种子执行完成 | 查询 `rbac_permission.code` | 包含 tenant、department、user、project、application、environment、role、audit、config、secret 的 P0 权限点；编码与契约一致 | SQL 断言 |
| DB-SEED-002 | P0 | 校验 8 个预置角色完整初始化 | 创建首个租户或新租户 | 查询 `rbac_role` | `SuperAdmin`、`TenantAdmin`、`DepartmentManager`、`ProjectOwner`、`AppOwner`、`Tester`、`Developer`、`Auditor` 存在，编码冻结，`is_system=true` | SQL 断言 |
| DB-SEED-003 | P0 | 校验预置角色权限映射正确 | 种子完成 | 抽查 `rbac_role_permission` 与权限矩阵 | 角色默认权限不超过矩阵范围；敏感值明文权限不存在 | SQL 断言 + 权限矩阵用例 |
| DB-SEED-004 | P0 | 校验首个 `SuperAdmin` 初始化幂等 | 系统未初始化后执行一次初始化 | 再次调用初始化接口或重复执行种子 | 不创建第二个超级管理员；返回 `CONFLICT` 或跳过；审计可追踪 | 接口自动化 |
| DB-SEED-005 | P0 | 校验新租户初始化租户管理员和角色 | `SuperAdmin` 创建新租户和管理员 | 查询租户、管理员用户、角色、角色绑定 | 新租户启用；管理员为 `TenantAdmin`；角色绑定作用域为 Tenant | 接口自动化 + SQL 断言 |
| DB-SEED-006 | P1 | 校验种子数据重复执行幂等 | 已执行种子 | 重复执行 seed/migration | 权限、角色、角色权限不重复；版本表状态一致 | 迁移流水线测试 |
| DB-SEED-007 | P1 | 校验种子变更兼容已有租户 | 已有租户和自定义数据 | 新增权限点或角色权限后执行升级种子 | 内置角色按升级规则补齐；不覆盖业务自定义数据 | 升级环境自动化 |

## 4. 数据库迁移测试建议

| 编号 | 优先级 | 测试目标 | 前置数据 | 操作/校验方式 | 预期结果 | 建议自动化方式 |
|---|---|---|---|---|---|---|
| DB-MIG-001 | P0 | 空库迁移 | 全新 PostgreSQL 空库 | 从 0 执行全部迁移和种子 | 迁移成功；核心表、索引、约束、种子数据完整；应用可启动 | CI 迁移任务 |
| DB-MIG-002 | P0 | 重复迁移 | 已完成迁移的数据库 | 再次执行迁移命令 | 无重复建表、重复索引、重复种子；版本表无异常 | CI 迁移任务 |
| DB-MIG-003 | P0 | 失败恢复 | 在中间迁移脚本故障注入失败 | 修复脚本后重新执行迁移 | 数据库无半成品或可自动修复；重新执行成功；失败记录可追踪 | 故障注入 |
| DB-MIG-004 | P0 | 回滚验证 | 支持回滚脚本或快照恢复 | 执行升级后回滚到上一版本 | 表结构、数据、版本表恢复一致；无法自动回滚的脚本需明确备份恢复步骤 | 预发演练 |
| DB-MIG-005 | P0 | 种子数据幂等 | 已有权限、角色、租户数据 | 重复执行 seed | 预置权限和角色不重复；业务租户、自定义数据不被覆盖 | SQL 断言 |
| DB-MIG-006 | P1 | 脏数据迁移前置检查 | 构造缺失租户、重复编码、跨租户关联、明文敏感值数据 | 执行 precheck 脚本 | 输出阻断项和修复建议；高危脏数据不得继续升级 | 迁移前检查脚本 |
| DB-MIG-007 | P1 | 大表迁移耗时评估 | 构造百万级审计、十万级用户/项目关联 | 执行新增索引、字段、回填脚本 | 迁移耗时、锁表时间、回填批次满足发布窗口 | 压测环境演练 |
| DB-MIG-008 | P1 | 迁移版本顺序校验 | 多版本迁移脚本 | 检查版本号、checksum、依赖顺序 | 版本递增、checksum 固定、禁止跳版本遗漏 | CI 静态检查 |
| DB-MIG-009 | P2 | 多环境一致性校验 | 开发、测试、预发库 | 对比 schema diff | 非预期差异为 0；环境特定配置不进入 DDL 差异 | Schema diff 工具 |

## 5. 安全测试建议

| 编号 | 优先级 | 测试目标 | 前置数据 | 操作/校验方式 | 预期结果 | 建议自动化方式 |
|---|---|---|---|---|---|---|
| DB-SEC-001 | P0 | 敏感值不明文 | 已保存密码、Token、Cookie、API Key、敏感环境变量 | 对业务表、审计表、导出文件、应用日志做敏感词扫描 | 不出现明文；只出现哈希、密文、掩码或引用摘要 | SQL + 文件敏感词扫描 |
| DB-SEC-002 | P0 | 跨租户 ID 枚举 | 已知其他租户资源 ID | 使用低权限用户枚举项目、应用、环境、用户、审计 ID | 返回 `403` 或统一 `404`；不泄露资源存在性；记录拒绝审计 | 接口自动化 |
| DB-SEC-003 | P0 | 逻辑删除不可见 | 已软删除部门、成员、项目关系或角色绑定 | 查询列表、详情、上下文、权限计算接口 | 默认不可见，不参与权限计算；审计历史可查 | 接口自动化 |
| DB-SEC-004 | P0 | 审计不可篡改 | 已有关键审计 | 使用应用账号尝试更新/删除；检查数据库权限和触发器策略 | 应用账号不能改写历史审计；DBA 特权操作需另有数据库审计或变更流程 | SQL 权限检查 |
| DB-SEC-005 | P0 | 密钥引用租户和作用域隔离 | A/B 租户各有 secret_ref | A 租户提交 B 租户 `secret_ref` | 请求失败；不保存跨租户引用 | 接口自动化 |
| DB-SEC-006 | P1 | URL 中疑似凭证脱敏 | `web_url`、`api_base_url`、`repo_url` 带 token 参数 | 保存并查询审计、导出 | URL 中疑似凭证参数脱敏或保存被拒绝 | 接口自动化 |
| DB-SEC-007 | P1 | 数据库账号最小权限 | 应用账号、迁移账号、只读测试账号已配置 | 检查各账号 grant | 应用账号无 DDL 和审计改写权限；迁移账号仅发布时使用；只读账号不可写 | DBA 检查脚本 |

## 6. 性能测试建议

| 编号 | 优先级 | 测试目标 | 前置数据 | 操作/校验方式 | 预期结果 | 建议自动化方式 |
|---|---|---|---|---|---|---|
| DB-PERF-001 | P0 | 核心查询索引命中 | 构造多租户、多项目、多应用、多环境数据 | 对用户、项目、应用、环境、角色绑定、审计核心查询执行 `EXPLAIN ANALYZE` | 查询计划命中租户/父级/作用域索引，无高成本全表扫描 | 性能 SQL 套件 |
| DB-PERF-002 | P0 | 列表分页性能 | 每租户至少 10 万用户、1 万项目/应用/环境或等比例压测数据 | 测试 page 1、深分页、关键词筛选、状态筛选 | 默认分页响应满足 SLA；最大 `page_size=100`；深分页有明确限制或优化策略 | 接口压测 + Explain |
| DB-PERF-003 | P0 | 部门树查询性能 | 构造 5 至 8 层部门树和大量成员 | 查询部门树、子树、成员数 | `path` 或 `parent_id` 索引命中；不因成员统计导致 N+1 查询 | 接口压测 + SQL 观测 |
| DB-PERF-004 | P0 | 权限计算查询性能 | 用户拥有多部门、多项目、多应用、多环境角色绑定 | 调用 `/auth/me`、`/users/{id}/permissions`、上下文切换 | 角色绑定、角色权限、作用域查询命中索引；缓存失效后首查可接受 | 接口压测 |
| DB-PERF-005 | P0 | 审计查询性能 | 每租户百万级审计日志 | 按近 7 天、资源、操作者、结果筛选查询 | 命中 `tenant_id + created_at`、资源、操作者索引；大范围查询受限 | 性能 SQL 套件 |
| DB-PERF-006 | P1 | 环境变量读取性能 | 单环境多变量，多应用多环境 | 查询环境详情和运行上下文 | 普通变量和敏感引用批量读取，无逐条查询 Secret 元数据问题 | 接口压测 |
| DB-PERF-007 | P1 | 迁移索引创建耗时 | 大表压测库 | 执行新增索引迁移 | 锁表时间、耗时、磁盘增长可控；必要时使用并发建索引策略 | 预发迁移演练 |
| DB-PERF-008 | P2 | 审计归档预留评估 | 超过 180 天审计数据 | 测试按时间分区或归档策略预案 | 不影响 MVP 查询；后续生命周期任务有扩展空间 | DBA 专项 |

## 7. 准出门槛

| 门槛 | 准出要求 |
|---|---|
| P0 表结构 | 核心表、字段、索引、约束全部通过；无废弃字段进入最终库。 |
| P0 租户隔离 | 跨租户查询、写入、关联、ID 枚举全部被阻断，拒绝审计可查。 |
| P0 敏感字段 | 数据库、审计、导出、日志均无密码、Token、Cookie、API Key 明文。 |
| P0 审计 | 成功写操作、失败写操作、越权拒绝、授权解绑、状态流转均可追踪。 |
| P0 迁移 | 空库迁移、重复迁移、失败恢复、种子幂等通过。 |
| P0 性能 | 核心列表、部门树、权限计算、审计查询命中预期索引，无明显全表扫描风险。 |
| 缺陷准出 | P0 阻塞缺陷为 0；P1 缺陷有明确规避或排期；P2 纳入后续回归池。 |
