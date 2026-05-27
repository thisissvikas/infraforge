package io.infraforge.adapters.aws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.infraforge.domain.CloudProvider;
import io.infraforge.domain.InfraRequest;
import io.infraforge.domain.RequestState;
import io.infraforge.ports.StateStorePort;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Profile({"aws", "local"})
public class DynamoDbStateStoreAdapter implements StateStorePort {

    private final DynamoDbTable<DynamoDbInfraRequestEntity> table;
    private final DynamoDbIndex<DynamoDbInfraRequestEntity> userIndex;
    private final ObjectMapper objectMapper;

    public DynamoDbStateStoreAdapter(DynamoDbEnhancedClient enhanced,
                                     @Value("${infraforge.aws.dynamodb.table-name}") String tableName,
                                     ObjectMapper objectMapper) {
        this.table = enhanced.table(tableName, TableSchema.fromBean(DynamoDbInfraRequestEntity.class));
        this.userIndex = table.index("userId-createdAt-index");
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(InfraRequest request) {
        table.putItem(toEntity(request));
    }

    @Override
    public void update(InfraRequest request) {
        table.putItem(toEntity(request));
    }

    @Override
    public Optional<InfraRequest> findById(String requestId) {
        DynamoDbInfraRequestEntity entity = table.getItem(
                Key.builder().partitionValue(requestId).build());
        return Optional.ofNullable(entity).map(this::toDomain);
    }

    @Override
    public List<InfraRequest> findByUserId(String userId) {
        return userIndex
                .query(QueryConditional.keyEqualTo(Key.builder().partitionValue(userId).build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .map(this::toDomain)
                .toList();
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private DynamoDbInfraRequestEntity toEntity(InfraRequest r) {
        DynamoDbInfraRequestEntity e = new DynamoDbInfraRequestEntity();
        e.setRequestId(r.requestId());
        e.setUserId(r.userId());
        e.setUserEmail(r.userEmail());
        e.setTeamId(r.teamId());
        e.setTargetCloud(r.targetCloud().name());
        e.setStateType(r.state().typeName());
        e.setStateMetadata(serializeStateMetadata(r.state()));
        e.setRawIntent(r.rawIntent());
        e.setGeneratedTerraform(r.generatedTerraform());
        e.setGithubPrUrl(r.githubPrUrl());
        e.setGithubBranch(r.githubBranch());
        e.setErrorMessage(r.errorMessage());
        e.setEstimatedMonthlyCostUsd(r.estimatedMonthlyCostUsd());
        e.setCreatedAt(r.createdAt().toString());
        e.setUpdatedAt(r.updatedAt().toString());
        return e;
    }

    private InfraRequest toDomain(DynamoDbInfraRequestEntity e) {
        return new InfraRequest(
                e.getRequestId(),
                e.getUserId(),
                e.getUserEmail(),
                e.getTeamId(),
                CloudProvider.fromString(e.getTargetCloud()),
                deserializeState(e.getStateType(), e.getStateMetadata()),
                e.getRawIntent(),
                e.getGeneratedTerraform(),
                e.getGithubPrUrl(),
                e.getGithubBranch(),
                e.getErrorMessage(),
                e.getEstimatedMonthlyCostUsd(),
                Instant.parse(e.getCreatedAt()),
                Instant.parse(e.getUpdatedAt())
        );
    }

    @SuppressWarnings("unchecked")
    private String serializeStateMetadata(RequestState state) {
        try {
            return switch (state) {
                case RequestState.Submitted    ignored -> "{}";
                case RequestState.PrCreated    s      -> objectMapper.writeValueAsString(
                        Map.of("prUrl", s.prUrl(), "branch", s.branch()));
                case RequestState.PlanRunning  s      -> objectMapper.writeValueAsString(
                        Map.of("checkRunId", s.checkRunId()));
                case RequestState.PlanApproved s      -> objectMapper.writeValueAsString(
                        Map.of("estimatedMonthlyCostUsd", s.estimatedMonthlyCostUsd()));
                case RequestState.Applying     s      -> objectMapper.writeValueAsString(
                        Map.of("checkRunId", s.checkRunId()));
                case RequestState.Deployed     s      -> objectMapper.writeValueAsString(
                        Map.of("deployedAt", s.deployedAt().toString()));
                case RequestState.Failed       s      -> objectMapper.writeValueAsString(
                        Map.of("reason", s.reason()));
            };
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize state metadata", e);
        }
    }

    @SuppressWarnings("unchecked")
    private RequestState deserializeState(String stateType, String metadata) {
        try {
            Map<String, Object> m = objectMapper.readValue(metadata, Map.class);
            return switch (stateType) {
                case "SUBMITTED"     -> new RequestState.Submitted();
                case "PR_CREATED"    -> new RequestState.PrCreated(
                        (String) m.get("prUrl"), (String) m.get("branch"));
                case "PLAN_RUNNING"  -> new RequestState.PlanRunning(
                        (String) m.get("checkRunId"));
                case "PLAN_APPROVED" -> new RequestState.PlanApproved(
                        ((Number) m.get("estimatedMonthlyCostUsd")).doubleValue());
                case "APPLYING"      -> new RequestState.Applying(
                        (String) m.get("checkRunId"));
                case "DEPLOYED"      -> new RequestState.Deployed(
                        Instant.parse((String) m.get("deployedAt")));
                case "FAILED"        -> new RequestState.Failed(
                        (String) m.get("reason"));
                default -> throw new IllegalArgumentException("Unknown state type: " + stateType);
            };
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize state metadata", e);
        }
    }
}
