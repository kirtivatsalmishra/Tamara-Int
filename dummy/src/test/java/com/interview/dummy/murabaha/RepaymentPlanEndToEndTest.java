package com.interview.dummy.murabaha;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.dummy.murabaha.api.dto.CreatePlanRequest;
import com.interview.dummy.murabaha.infrastructure.persistence.RepaymentPlanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(RepaymentPlanEndToEndTest.FixedClockConfig.class)
class RepaymentPlanEndToEndTest {

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-05-06T08:00:00Z"), ZoneId.of("Asia/Riyadh"));
        }
    }

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper json;
    @Autowired private RepaymentPlanRepository repository;

    private MockMvc mvc;

    private MockMvc mvc() {
        if (mvc == null) {
            mvc = MockMvcBuilders.webAppContextSetup(context).build();
        }
        return mvc;
    }

    @Test
    void createsPlanWithSpecWorkedExampleAndReadsItBack() throws Exception {
        CreatePlanRequest req = new CreatePlanRequest(
                "CUST-7421", new BigDecimal("5000.00"), "gold", 6, "SAVE10",
                LocalDate.parse("2026-05-06"), null);

        MvcResult created = mvc().perform(post("/api/murabaha/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appliedMarginPercent").value(7.20))
                .andExpect(jsonPath("$.totalProfit").value(360.00))
                .andExpect(jsonPath("$.totalPayable").value(5360.00))
                .andExpect(jsonPath("$.baseInstallmentAmount").value(893.33))
                .andExpect(jsonPath("$.currencyCode").value("SAR"))
                .andExpect(jsonPath("$.schedule[5].amount").value(893.35))
                .andExpect(jsonPath("$.schedule[0].dueDate").value("2026-06-06"))
                .andExpect(jsonPath("$.schedule[5].dueDate").value("2026-11-06"))
                .andExpect(jsonPath("$.contractSummary").value(org.hamcrest.Matchers.containsString("SAR 5000.00")))
                .andReturn();

        JsonNode body = json.readTree(created.getResponse().getContentAsString());
        String planId = body.get("planId").asText();

        mvc().perform(get("/api/murabaha/plans/" + planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(planId))
                .andExpect(jsonPath("$.totalPayable").value(5360.00));
    }

    @Test
    void duplicateIdempotencyKeyYieldsSamePlanAndSingleRow() throws Exception {
        CreatePlanRequest req = new CreatePlanRequest(
                "CUST-IDEM", new BigDecimal("3000.00"), "metals", 4, null,
                LocalDate.parse("2026-05-06"), null);
        long before = repository.count();

        String first = mvc().perform(post("/api/murabaha/plans")
                        .header("Idempotency-Key", "key-e2e-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String second = mvc().perform(post("/api/murabaha/plans")
                        .header("Idempotency-Key", "key-e2e-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(repository.count()).isEqualTo(before + 1);
    }

    @Test
    void duplicateIdempotencyKeyDifferentBodyReturns409() throws Exception {
        CreatePlanRequest req1 = new CreatePlanRequest(
                "CUST-CONF", new BigDecimal("1000.00"), "gold", 3, null,
                LocalDate.parse("2026-05-06"), null);
        CreatePlanRequest req2 = new CreatePlanRequest(
                "CUST-CONF", new BigDecimal("9999.00"), "gold", 3, null,
                LocalDate.parse("2026-05-06"), null);

        mvc().perform(post("/api/murabaha/plans")
                        .header("Idempotency-Key", "key-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        mvc().perform(post("/api/murabaha/plans")
                        .header("Idempotency-Key", "key-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("idempotency-key-conflict")));
    }

    @Test
    void nonSarCurrencyEndToEnd() throws Exception {
        CreatePlanRequest req = new CreatePlanRequest(
                "CUST-KWD", new BigDecimal("100.000"), "metals", 3, null,
                LocalDate.parse("2026-05-06"), "KWD");
        mvc().perform(post("/api/murabaha/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currencyCode").value("KWD"))
                .andExpect(jsonPath("$.contractSummary").value(org.hamcrest.Matchers.containsString("KWD")));
    }

    @Test
    void unknownCurrencyReturns400() throws Exception {
        CreatePlanRequest req = new CreatePlanRequest(
                "CUST", new BigDecimal("100.00"), "gold", 3, null,
                LocalDate.parse("2026-05-06"), "XYZ");
        mvc().perform(post("/api/murabaha/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("unknown-currency")));
    }
}
