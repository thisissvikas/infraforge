package io.infraforge.api;

import io.infraforge.api.dto.InfraRequestDto;
import io.infraforge.api.dto.SubmitRequestDto;
import io.infraforge.domain.InfraRequest;
import io.infraforge.domain.WorkflowEvent;
import io.infraforge.ports.MessageQueuePort;
import io.infraforge.ports.StateStorePort;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Internal API consumed only by the Chat Agent via service-key authentication.
 * Not reachable from the public internet (enforced at the network / ALB layer in prod).
 *
 * <p>Tool call mappings:
 * <ul>
 *   <li>{@code submit_request()}    → {@code POST /internal/requests}</li>
 *   <li>{@code get_request_status()} → {@code GET /internal/requests/{id}}</li>
 *   <li>{@code check_budget()}       → {@code GET /internal/budget}</li>
 *   <li>{@code get_team_policies()}  → {@code GET /internal/policies}</li>
 *   <li>{@code validate_architecture()} → {@code POST /internal/validate}</li>
 * </ul>
 * Policy and budget endpoints are stubs here — Phase 3 wires them to Bedrock KB
 * and Cost Explorer respectively.</p>
 */
@RestController
@RequestMapping("/internal")
public class InternalController {

    private final StateStorePort   stateStore;
    private final MessageQueuePort queue;
    private final String           workflowQueueUrl;

    public InternalController(StateStorePort stateStore,
                               MessageQueuePort queue,
                               @Value("${infraforge.aws.sqs.workflow-queue-url:}") String workflowQueueUrl) {
        this.stateStore       = stateStore;
        this.queue            = queue;
        this.workflowQueueUrl = workflowQueueUrl;
    }

    // ── submit_request() ──────────────────────────────────────────────────────

    @PostMapping("/requests")
    public ResponseEntity<Map<String, String>> submitRequest(
            @RequestBody @Valid SubmitRequestDto body) {

        InfraRequest request = InfraRequest.create(
                body.userId(), body.userEmail(), body.teamId(), body.rawIntent());

        if (body.generatedTerraform() != null) {
            request = request.withTerraform(body.generatedTerraform());
        }

        stateStore.save(request);
        queue.publish(workflowQueueUrl, WorkflowEvent.submitted(request.requestId()));

        return ResponseEntity.ok(Map.of(
                "requestId", request.requestId(),
                "state",     request.state().typeName()
        ));
    }

    // ── get_request_status() ─────────────────────────────────────────────────

    @GetMapping("/requests/{requestId}")
    public ResponseEntity<InfraRequestDto> getRequestStatus(@PathVariable String requestId) {
        return stateStore.findById(requestId)
                .map(InfraRequestDto::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── get_team_policies() — Phase 3: wire to Bedrock KB ────────────────────

    @GetMapping("/policies")
    public ResponseEntity<Map<String, Object>> getTeamPolicies(
            @RequestParam String teamId) {
        // TODO Phase 3: retrieve from Bedrock Knowledge Base filtered by teamId
        return ResponseEntity.ok(Map.of(
                "teamId", teamId,
                "policies", java.util.List.of(),
                "approvedModules", java.util.List.of("ecs-service", "rds-postgres", "vpc", "s3-bucket")
        ));
    }

    // ── check_budget() — Phase 3: wire to Cost Explorer ─────────────────────

    @GetMapping("/budget")
    public ResponseEntity<Map<String, Object>> checkBudget(
            @RequestParam String teamId) {
        // TODO Phase 3: query AWS Cost Explorer for team's actual spend vs. ceiling
        return ResponseEntity.ok(Map.of(
                "teamId",          teamId,
                "ceilingUsd",      1000.0,
                "currentSpendUsd", 0.0,
                "headroomUsd",     1000.0
        ));
    }

    // ── validate_architecture() — Phase 3: wire to OPA server ────────────────

    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateArchitecture(
            @RequestBody Map<String, Object> request) {
        // TODO Phase 3: POST terraform plan JSON to OPA server at localhost:8181
        return ResponseEntity.ok(Map.of(
                "passed", true,
                "violations", java.util.List.of()
        ));
    }
}
