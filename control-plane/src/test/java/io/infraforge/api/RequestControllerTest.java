package io.infraforge.api;

import io.infraforge.auth.AuthenticatedUser;
import io.infraforge.auth.JwtService;
import io.infraforge.domain.InfraRequest;
import io.infraforge.domain.User;
import io.infraforge.ports.StateStorePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired StateStorePort stateStore;

    @Test
    void listRequests_returnsOnlyUserRequests() throws Exception {
        User user = new User("u-001", "alice", "alice@test.com");
        String token = jwtService.issue(user);

        InfraRequest req = InfraRequest.create("u-001", "alice@test.com", "team-a", "S3 bucket");
        stateStore.save(req);

        mockMvc.perform(get("/api/requests")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestId").value(req.requestId()))
                .andExpect(jsonPath("$[0].state").value("SUBMITTED"));
    }

    @Test
    void listRequests_returns401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/requests"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getRequest_returns404ForOtherUsersRequest() throws Exception {
        User alice = new User("u-alice", "alice", "alice@test.com");
        User bob   = new User("u-bob",   "bob",   "bob@test.com");

        InfraRequest alicesReq = InfraRequest.create("u-alice", "alice@test.com", "team-a", "private");
        stateStore.save(alicesReq);

        String bobToken = jwtService.issue(bob);
        mockMvc.perform(get("/api/requests/" + alicesReq.requestId())
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNotFound());
    }
}
