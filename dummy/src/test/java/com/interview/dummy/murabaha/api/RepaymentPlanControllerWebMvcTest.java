package com.interview.dummy.murabaha.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interview.dummy.murabaha.api.dto.CreatePlanRequest;
import com.interview.dummy.murabaha.api.error.GlobalExceptionHandler;
import com.interview.dummy.murabaha.api.error.IdempotencyKeyConflictException;
import com.interview.dummy.murabaha.api.error.InvalidPromoCodeException;
import com.interview.dummy.murabaha.api.error.PlanNotFoundException;
import com.interview.dummy.murabaha.api.error.UnknownCurrencyException;
import com.interview.dummy.murabaha.application.RepaymentPlanService;
import com.interview.dummy.murabaha.application.RepaymentPlanService.PlanWithSummary;
import com.interview.dummy.murabaha.domain.model.Installment;
import com.interview.dummy.murabaha.domain.model.Money;
import com.interview.dummy.murabaha.domain.model.RepaymentPlan;
import com.interview.dummy.murabaha.infrastructure.config.JacksonConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RepaymentPlanController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class, RepaymentPlanResponseMapper.class})
class RepaymentPlanControllerWebMvcTest {

    private static final Currency SAR = Currency.getInstance("SAR");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @MockitoBean private RepaymentPlanService service;

    @BeforeEach
    void resetMock() { /* mock fresh per test via @MockitoBean */ }

    @Test
    void postCreatesPlanAndReturns201() throws Exception {
        CreatePlanRequest req = new CreatePlanRequest(
                "CUST", new BigDecimal("100.00"), "gold", 3, null,
                LocalDate.parse("2026-05-06"), null);
        when(service.create(any(), eq(null))).thenReturn(samplePlanWithSummary());

        mvc.perform(MockMvcRequestBuilders.post("/api/murabaha/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value("CUST"))
                .andExpect(jsonPath("$.currencyCode").value("SAR"))
                .andExpect(jsonPath("$.schedule.length()").value(3));
    }

    @Test
    void postWithIdempotencyKeyHeaderForwardsToService() throws Exception {
        CreatePlanRequest req = new CreatePlanRequest(
                "CUST", new BigDecimal("100.00"), "gold", 3, null,
                LocalDate.parse("2026-05-06"), null);
        PlanWithSummary plan = samplePlanWithSummary();
        when(service.create(any(), eq("abc-123"))).thenReturn(plan);

        mvc.perform(MockMvcRequestBuilders.post("/api/murabaha/plans")
                        .header("Idempotency-Key", "abc-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planId").value(plan.plan().id().toString()));
    }

    @Test
    void postReturns409OnIdempotencyConflict() throws Exception {
        CreatePlanRequest req = new CreatePlanRequest(
                "CUST", new BigDecimal("100.00"), "gold", 3, null,
                LocalDate.parse("2026-05-06"), null);
        when(service.create(any(), eq("dup")))
                .thenThrow(new IdempotencyKeyConflictException("dup"));

        mvc.perform(MockMvcRequestBuilders.post("/api/murabaha/plans")
                        .header("Idempotency-Key", "dup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("idempotency-key-conflict")))
                .andExpect(jsonPath("$.idempotencyKey").value("dup"));
    }

    @Test
    void postReturns400OnUnknownCurrency() throws Exception {
        CreatePlanRequest req = new CreatePlanRequest(
                "CUST", new BigDecimal("100.00"), "gold", 3, null,
                LocalDate.parse("2026-05-06"), "XYZ");
        when(service.create(any(), any())).thenThrow(new UnknownCurrencyException("XYZ"));

        mvc.perform(MockMvcRequestBuilders.post("/api/murabaha/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("unknown-currency")))
                .andExpect(jsonPath("$.currencyCode").value("XYZ"));
    }

    @Test
    void postReturns400OnNegativeCommodityCost() throws Exception {
        String body = """
                {"customerId":"C","commodityCost":"-1.00","commodityCategory":"gold","installments":3}
                """;
        mvc.perform(MockMvcRequestBuilders.post("/api/murabaha/plans")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("validation-failed")));
    }

    @Test
    void postReturns400OnInstallmentsAboveMax() throws Exception {
        String body = """
                {"customerId":"C","commodityCost":"100.00","commodityCategory":"gold","installments":13}
                """;
        mvc.perform(MockMvcRequestBuilders.post("/api/murabaha/plans")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postReturns400OnInstallmentsBelowMin() throws Exception {
        String body = """
                {"customerId":"C","commodityCost":"100.00","commodityCategory":"gold","installments":1}
                """;
        mvc.perform(MockMvcRequestBuilders.post("/api/murabaha/plans")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postReturns400OnInvalidPromoCode() throws Exception {
        CreatePlanRequest req = new CreatePlanRequest(
                "C", new BigDecimal("100.00"), "gold", 3, "BOGUS",
                LocalDate.parse("2026-05-06"), null);
        when(service.create(any(), any())).thenThrow(new InvalidPromoCodeException("BOGUS"));

        mvc.perform(MockMvcRequestBuilders.post("/api/murabaha/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("invalid-promo-code")))
                .andExpect(jsonPath("$.promoCode").value("BOGUS"));
    }

    @Test
    void postReturns400OnMalformedJson() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/murabaha/plans")
                        .contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReturnsPlanWhenPresent() throws Exception {
        PlanWithSummary plan = samplePlanWithSummary();
        when(service.get(plan.plan().id())).thenReturn(plan);

        mvc.perform(MockMvcRequestBuilders.get("/api/murabaha/plans/" + plan.plan().id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(plan.plan().customerId()));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenThrow(new PlanNotFoundException(id));

        mvc.perform(MockMvcRequestBuilders.get("/api/murabaha/plans/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("plan-not-found")))
                .andExpect(jsonPath("$.planId").value(id.toString()));
    }

    @Test
    void datesAreSerializedAsIsoStrings() throws Exception {
        PlanWithSummary plan = samplePlanWithSummary();
        when(service.create(any(), any())).thenReturn(plan);
        CreatePlanRequest req = new CreatePlanRequest(
                "CUST", new BigDecimal("100.00"), "gold", 3, null,
                LocalDate.parse("2026-05-06"), null);

        mvc.perform(MockMvcRequestBuilders.post("/api/murabaha/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schedule[0].dueDate").value("2026-06-06"));
    }

    private PlanWithSummary samplePlanWithSummary() {
        Money cost = Money.of(new BigDecimal("100.00"), SAR);
        Money profit = Money.of(new BigDecimal("10.00"), SAR);
        Money payable = Money.of(new BigDecimal("110.00"), SAR);
        List<Installment> schedule = List.of(
                new Installment(1, LocalDate.parse("2026-06-06"), Money.of(new BigDecimal("36.66"), SAR)),
                new Installment(2, LocalDate.parse("2026-07-06"), Money.of(new BigDecimal("36.66"), SAR)),
                new Installment(3, LocalDate.parse("2026-08-06"), Money.of(new BigDecimal("36.68"), SAR))
        );
        RepaymentPlan plan = new RepaymentPlan(
                UUID.randomUUID(), "CUST", cost, new BigDecimal("10.00"),
                profit, payable, schedule, LocalDate.parse("2026-05-06"),
                Instant.parse("2026-05-06T08:00:00Z"), new BigDecimal("5.00"));
        return new PlanWithSummary(plan, "summary");
    }
}
