package ru.maltsev.primemarketbackend.deposit.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ru.maltsev.primemarketbackend.security.user.UserPrincipal;
import ru.maltsev.primemarketbackend.support.AbstractPostgresIntegrationTest;
import ru.maltsev.primemarketbackend.user.domain.Permission;
import ru.maltsev.primemarketbackend.user.domain.Role;
import ru.maltsev.primemarketbackend.user.domain.User;
import ru.maltsev.primemarketbackend.user.repository.UserRepository;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class DepositAdminSecurityIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void supportSeedUserHasSupportRoleAndAllCurrentPermissions() {
        User support = loadUser("sup1@123.123");

        assertThat(support.getRoles())
            .extracting(Role::getCode)
            .contains("SUPPORT");
        assertThat(support.getPermissions())
            .extracting(Permission::getCode)
            .contains("DEPOSIT_APPROVE", "BACKOFFICE_ACCESS");
    }

    @Test
    void adminDepositRequestsRequireDepositApprovePermissionOnAdminAndBackofficeAliases() throws Exception {
        User support = loadUser("sup1@123.123");
        User regularUser = loadUser("user1@123.123");

        mockMvc.perform(get("/api/admin/deposit-requests").with(auth(regularUser)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/backoffice/deposit-requests").with(auth(regularUser)))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/deposit-requests").with(auth(support)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/backoffice/deposit-requests").with(auth(support)))
            .andExpect(status().isOk());
    }

    private User loadUser(String email) {
        return userRepository.findWithRolesByEmailIgnoreCase(email)
            .orElseThrow(() -> new IllegalStateException("User not found: " + email));
    }

    private RequestPostProcessor auth(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        return authentication(new UsernamePasswordAuthenticationToken(
            principal,
            null,
            principal.getAuthorities()
        ));
    }
}
