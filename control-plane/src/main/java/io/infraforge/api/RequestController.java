package io.infraforge.api;

import io.infraforge.api.dto.InfraRequestDto;
import io.infraforge.auth.AuthenticatedUser;
import io.infraforge.ports.StateStorePort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Developer-facing API — returns request history and status.
 * Requires a valid JWT ({@code Authorization: Bearer <token>}).
 */
@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final StateStorePort stateStore;

    public RequestController(StateStorePort stateStore) {
        this.stateStore = stateStore;
    }

    /** List all requests for the authenticated user, most recent first. */
    @GetMapping
    public List<InfraRequestDto> listRequests(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return stateStore.findByUserId(principal.userId())
                .stream()
                .map(InfraRequestDto::from)
                .toList();
    }

    /** Get a single request by ID — only if it belongs to the authenticated user. */
    @GetMapping("/{requestId}")
    public ResponseEntity<InfraRequestDto> getRequest(
            @PathVariable String requestId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return stateStore.findById(requestId)
                .filter(r -> r.userId().equals(principal.userId()))
                .map(InfraRequestDto::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
