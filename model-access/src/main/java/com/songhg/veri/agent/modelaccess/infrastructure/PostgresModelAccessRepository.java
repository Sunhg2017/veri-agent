package com.songhg.veri.agent.modelaccess.infrastructure;

import com.songhg.veri.agent.modelaccess.api.InvocationSummaryResponse;
import com.songhg.veri.agent.modelaccess.application.InvocationQuery;
import com.songhg.veri.agent.modelaccess.application.ModelAccessRepository;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.domain.InvocationStatus;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.PromptStatus;
import com.songhg.veri.agent.modelaccess.domain.PromptTemplate;
import com.songhg.veri.agent.modelaccess.domain.ProviderStatus;
import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("db")
public class PostgresModelAccessRepository implements ModelAccessRepository {

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public PostgresModelAccessRepository(
            JdbcTemplate jdbcTemplate,
            NamedParameterJdbcTemplate namedParameterJdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    @Override
    public List<ModelProviderConfig> providers() {
        return jdbcTemplate.query("""
                select id, name, provider_type, base_url, api_key_ref, status, priority, timeout_ms,
                       input_cost_per_1k_tokens, output_cost_per_1k_tokens, created_at, updated_at
                from ma_model_provider
                order by priority asc, created_at desc
                """, this::mapProvider);
    }

    @Override
    public Optional<ModelProviderConfig> provider(UUID id) {
        return jdbcTemplate.query("""
                select id, name, provider_type, base_url, api_key_ref, status, priority, timeout_ms,
                       input_cost_per_1k_tokens, output_cost_per_1k_tokens, created_at, updated_at
                from ma_model_provider
                where id = ?
                """, this::mapProvider, id).stream().findFirst();
    }

    @Override
    public ModelProviderConfig saveProvider(ModelProviderConfig provider) {
        jdbcTemplate.update("""
                insert into ma_model_provider (
                    id, name, provider_type, base_url, api_key_ref, status, priority, timeout_ms,
                    input_cost_per_1k_tokens, output_cost_per_1k_tokens, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update set
                    name = excluded.name,
                    provider_type = excluded.provider_type,
                    base_url = excluded.base_url,
                    api_key_ref = excluded.api_key_ref,
                    status = excluded.status,
                    priority = excluded.priority,
                    timeout_ms = excluded.timeout_ms,
                    input_cost_per_1k_tokens = excluded.input_cost_per_1k_tokens,
                    output_cost_per_1k_tokens = excluded.output_cost_per_1k_tokens,
                    updated_at = excluded.updated_at
                """,
                provider.id(),
                provider.name(),
                provider.providerType().name(),
                provider.baseUrl(),
                provider.apiKeyRef(),
                provider.status().name(),
                provider.priority(),
                provider.timeoutMs(),
                provider.inputCostPer1kTokens(),
                provider.outputCostPer1kTokens(),
                Timestamp.from(provider.createdAt()),
                Timestamp.from(provider.updatedAt())
        );
        return provider;
    }

    @Override
    public List<PromptTemplate> prompts(String promptKey) {
        if (promptKey == null) {
            return jdbcTemplate.query("""
                    select id, prompt_key, name, version, content, status, change_note, created_at, updated_at
                    from ma_prompt_template
                    order by prompt_key asc, version desc
                    """, this::mapPrompt);
        }
        return jdbcTemplate.query("""
                select id, prompt_key, name, version, content, status, change_note, created_at, updated_at
                from ma_prompt_template
                where prompt_key = ?
                order by version desc
                """, this::mapPrompt, promptKey);
    }

    @Override
    public Optional<PromptTemplate> prompt(UUID id) {
        return jdbcTemplate.query("""
                select id, prompt_key, name, version, content, status, change_note, created_at, updated_at
                from ma_prompt_template
                where id = ?
                """, this::mapPrompt, id).stream().findFirst();
    }

    @Override
    public Optional<PromptTemplate> activePrompt(String promptKey) {
        return jdbcTemplate.query("""
                select id, prompt_key, name, version, content, status, change_note, created_at, updated_at
                from ma_prompt_template
                where prompt_key = ? and status = 'ACTIVE'
                order by version desc
                limit 1
                """, this::mapPrompt, promptKey).stream().findFirst();
    }

    @Override
    public PromptTemplate savePrompt(PromptTemplate prompt) {
        jdbcTemplate.update("""
                insert into ma_prompt_template (
                    id, prompt_key, name, version, content, status, change_note, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (id) do update set
                    name = excluded.name,
                    content = excluded.content,
                    status = excluded.status,
                    change_note = excluded.change_note,
                    updated_at = excluded.updated_at
                """,
                prompt.id(),
                prompt.promptKey(),
                prompt.name(),
                prompt.version(),
                prompt.content(),
                prompt.status().name(),
                prompt.changeNote(),
                Timestamp.from(prompt.createdAt()),
                Timestamp.from(prompt.updatedAt())
        );
        return prompt;
    }

    @Override
    public void deactivateActivePrompts(String promptKey) {
        jdbcTemplate.update("""
                update ma_prompt_template
                set status = 'ARCHIVED', updated_at = now()
                where prompt_key = ? and status = 'ACTIVE'
                """, promptKey);
    }

    @Override
    public InvocationRecord saveInvocation(InvocationRecord record) {
        jdbcTemplate.update("""
                insert into ma_invocation_log (
                    id, project_id, application_id, environment_id, prompt_key, prompt_version,
                    sensitivity_level, provider_id, provider_name, model_name, status, fallback_used, prompt_digest,
                    request_preview, response_preview, input_tokens, output_tokens, total_cost,
                    error_code, error_message, latency_ms, actor_service, delegated_user_id, created_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                record.id(),
                record.projectId(),
                record.applicationId(),
                record.environmentId(),
                record.promptKey(),
                record.promptVersion(),
                record.sensitivityLevel(),
                record.providerId(),
                record.providerName(),
                record.modelName(),
                record.status().name(),
                record.fallbackUsed(),
                record.promptDigest(),
                record.requestPreview(),
                record.responsePreview(),
                record.inputTokens(),
                record.outputTokens(),
                record.totalCost(),
                record.errorCode(),
                record.errorMessage(),
                record.latencyMs(),
                record.actorService(),
                record.delegatedUserId(),
                Timestamp.from(record.createdAt())
        );
        return record;
    }

    @Override
    public List<InvocationRecord> invocations(InvocationQuery query) {
        QueryParts parts = queryParts(query);
        return namedParameterJdbcTemplate.query("""
                select id, project_id, application_id, environment_id, prompt_key, prompt_version,
                       sensitivity_level, provider_id, provider_name, model_name, status, fallback_used, prompt_digest,
                       request_preview, response_preview, input_tokens, output_tokens, total_cost,
                       error_code, error_message, latency_ms, actor_service, delegated_user_id, created_at
                from ma_invocation_log
                %s
                order by created_at desc
                limit :size offset :offset
                """.formatted(parts.whereClause()),
                parts.params()
                        .addValue("size", query.size())
                        .addValue("offset", query.offset()),
                this::mapInvocation
        );
    }

    @Override
    public long countInvocations(InvocationQuery query) {
        QueryParts parts = queryParts(query);
        Long count = namedParameterJdbcTemplate.queryForObject(
                "select count(*) from ma_invocation_log " + parts.whereClause(),
                parts.params(),
                Long.class
        );
        return count == null ? 0 : count;
    }

    @Override
    public InvocationSummaryResponse invocationSummary(InvocationQuery query) {
        QueryParts parts = queryParts(query);
        return namedParameterJdbcTemplate.queryForObject("""
                select
                    count(*) as total,
                    count(*) filter (where status = 'SUCCEEDED') as succeeded,
                    count(*) filter (where status = 'FAILED') as failed,
                    count(*) filter (where status = 'BLOCKED') as blocked,
                    coalesce(sum(input_tokens), 0) as input_tokens,
                    coalesce(sum(output_tokens), 0) as output_tokens,
                    coalesce(sum(total_cost), 0) as total_cost
                from ma_invocation_log
                %s
                """.formatted(parts.whereClause()),
                parts.params(),
                (rs, rowNum) -> new InvocationSummaryResponse(
                        rs.getLong("total"),
                        rs.getLong("succeeded"),
                        rs.getLong("failed"),
                        rs.getLong("blocked"),
                        rs.getLong("input_tokens"),
                        rs.getLong("output_tokens"),
                        rs.getBigDecimal("total_cost")
                )
        );
    }

    private ModelProviderConfig mapProvider(ResultSet rs, int rowNum) throws SQLException {
        return new ModelProviderConfig(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                ProviderType.valueOf(rs.getString("provider_type")),
                rs.getString("base_url"),
                rs.getString("api_key_ref"),
                ProviderStatus.valueOf(rs.getString("status")),
                rs.getInt("priority"),
                rs.getInt("timeout_ms"),
                rs.getBigDecimal("input_cost_per_1k_tokens"),
                rs.getBigDecimal("output_cost_per_1k_tokens"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private PromptTemplate mapPrompt(ResultSet rs, int rowNum) throws SQLException {
        return new PromptTemplate(
                rs.getObject("id", UUID.class),
                rs.getString("prompt_key"),
                rs.getString("name"),
                rs.getInt("version"),
                rs.getString("content"),
                PromptStatus.valueOf(rs.getString("status")),
                rs.getString("change_note"),
                instant(rs, "created_at"),
                instant(rs, "updated_at")
        );
    }

    private InvocationRecord mapInvocation(ResultSet rs, int rowNum) throws SQLException {
        return new InvocationRecord(
                rs.getObject("id", UUID.class),
                rs.getString("project_id"),
                rs.getString("application_id"),
                rs.getString("environment_id"),
                rs.getString("sensitivity_level"),
                rs.getString("prompt_key"),
                intOrNull(rs, "prompt_version"),
                rs.getObject("provider_id", UUID.class),
                rs.getString("provider_name"),
                rs.getString("model_name"),
                InvocationStatus.valueOf(rs.getString("status")),
                rs.getBoolean("fallback_used"),
                rs.getString("prompt_digest"),
                rs.getString("request_preview"),
                rs.getString("response_preview"),
                rs.getInt("input_tokens"),
                rs.getInt("output_tokens"),
                rs.getBigDecimal("total_cost"),
                rs.getString("error_code"),
                rs.getString("error_message"),
                rs.getLong("latency_ms"),
                rs.getString("actor_service"),
                rs.getString("delegated_user_id"),
                instant(rs, "created_at")
        );
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private Integer intOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private QueryParts queryParts(InvocationQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder();
        addEquals(where, params, "project_id", "projectId", query.projectId());
        addEquals(where, params, "application_id", "applicationId", query.applicationId());
        addEquals(where, params, "sensitivity_level", "sensitivityLevel", query.sensitivityLevel());
        addEquals(where, params, "provider_id", "providerId", query.providerId());
        addEquals(where, params, "actor_service", "actorService", query.actorService());
        if (query.status() != null) {
            addEquals(where, params, "status", "status", query.status().name());
        }
        if (query.startTime() != null) {
            addCondition(where, "created_at >= :startTime");
            params.addValue("startTime", Timestamp.from(query.startTime()));
        }
        if (query.endTime() != null) {
            addCondition(where, "created_at < :endTime");
            params.addValue("endTime", Timestamp.from(query.endTime()));
        }
        return new QueryParts(where.isEmpty() ? "" : "where " + where, params);
    }

    private void addEquals(
            StringBuilder where,
            MapSqlParameterSource params,
            String column,
            String parameter,
            Object value
    ) {
        if (value == null) {
            return;
        }
        addCondition(where, column + " = :" + parameter);
        params.addValue(parameter, value);
    }

    private void addCondition(StringBuilder where, String condition) {
        if (!where.isEmpty()) {
            where.append(" and ");
        }
        where.append(condition);
    }

    private record QueryParts(String whereClause, MapSqlParameterSource params) {
    }
}
