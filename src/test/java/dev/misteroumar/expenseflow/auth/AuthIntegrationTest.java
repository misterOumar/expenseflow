package dev.misteroumar.expenseflow.auth;

import com.jayway.jsonpath.JsonPath;
import dev.misteroumar.expenseflow.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    @DisplayName("le jeton obtenu au login donne accès à l'API")
    void loginThenCallApi() throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "Alice@ExpenseFlow.dev", "password": "password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(86400))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mvc.perform(get("/api/reports").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("401 identique pour un mauvais mot de passe et un email inconnu")
    void rejectsBadCredentials() throws Exception {
        for (String email : new String[]{"alice@expenseflow.dev", "inconnu@expenseflow.dev"}) {
            mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email": "%s", "password": "mauvais"}
                                    """.formatted(email)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Email ou mot de passe incorrect."));
        }
    }

    @Test
    @DisplayName("401 en ProblemDetail sans jeton")
    void rejectsMissingToken() throws Exception {
        mvc.perform(get("/api/reports"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.title").value("Authentification requise"));
    }

    @Test
    @DisplayName("401 en ProblemDetail avec un jeton falsifié")
    void rejectsForgedToken() throws Exception {
        mvc.perform(get("/api/reports").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
