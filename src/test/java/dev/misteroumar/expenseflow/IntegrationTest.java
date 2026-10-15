package dev.misteroumar.expenseflow;

import com.jayway.jsonpath.JsonPath;
import dev.misteroumar.expenseflow.user.User;
import dev.misteroumar.expenseflow.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base des tests d'intégration : application complète, PostgreSQL Testcontainers et comptes
 * de démonstration de la migration V3. Les notes et justificatifs sont vidés avant chaque test.
 * L'envoi d'emails est simulé par un mock de {@link JavaMailSender}.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "expenseflow.receipts.directory=${java.io.tmpdir}/expenseflow-test-receipts"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    protected static final String ALICE = "alice@expenseflow.dev";
    protected static final String BOB = "bob@expenseflow.dev";
    protected static final String CHLOE = "chloe@expenseflow.dev";
    protected static final String MANAGER = "manager@expenseflow.dev";
    protected static final String ACCOUNTANT = "compta@expenseflow.dev";

    protected static final byte[] PDF = "%PDF-1.4\n%test\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @MockitoBean
    protected JavaMailSender mailSender;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("DELETE FROM status_history");
        jdbc.execute("DELETE FROM expense_lines");
        jdbc.execute("DELETE FROM expense_reports");
        jdbc.execute("DELETE FROM receipts");
    }

    /**
     * Authentifie la requête avec un JWT dont le {@code sub} est l'id de l'utilisateur.
     */
    protected RequestPostProcessor as(String email) {
        return jwt().jwt(token -> token.subject(user(email).getId().toString()));
    }

    protected User user(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow();
    }

    protected long createDraft(String email, int month) throws Exception {
        String body = mvc.perform(post("/api/reports").with(as(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Note de test", "periodMonth": %d, "periodYear": 2026}
                                """.formatted(month)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    protected void addLine(String email, long reportId, boolean withReceipt) throws Exception {
        String receiptId = withReceipt ? String.valueOf(uploadReceipt(email)) : "null";
        mvc.perform(post("/api/reports/{id}/lines", reportId).with(as(email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"spentOn": "2026-01-15", "category": "MEAL", "description": "Repas client",
                                 "amountHt": 20.00, "vatAmount": 2.00, "receiptId": %s}
                                """.formatted(receiptId)))
                .andExpect(status().isCreated());
    }

    protected long submittedReport(String email, int month) throws Exception {
        long id = createDraft(email, month);
        addLine(email, id, true);
        transition(email, id, "submit");
        return id;
    }

    protected long approvedReport(String email, int month) throws Exception {
        long id = submittedReport(email, month);
        transition(MANAGER, id, "approve");
        return id;
    }

    protected void transition(String email, long reportId, String action) throws Exception {
        mvc.perform(post("/api/reports/{id}/" + action, reportId).with(as(email)))
                .andExpect(status().isOk());
    }

    protected long uploadReceipt(String email) throws Exception {
        String body = mvc.perform(multipart("/api/receipts")
                        .file(new MockMultipartFile("file", "ticket.pdf", "application/pdf", PDF))
                        .with(as(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
