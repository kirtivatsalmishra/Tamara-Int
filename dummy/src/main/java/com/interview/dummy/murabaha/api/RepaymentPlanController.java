package com.interview.dummy.murabaha.api;

import com.interview.dummy.murabaha.api.dto.CreatePlanRequest;
import com.interview.dummy.murabaha.api.dto.RepaymentPlanResponse;
import com.interview.dummy.murabaha.application.RepaymentPlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Murabaha repayment plan REST surface.
 *
 * <p>The {@code Idempotency-Key} header is optional. When present, two POSTs
 * with the same value and an equivalent body return the same {@code planId}
 * and body; a same-key/different-body collision yields {@code 409}.
 */
@RestController
@RequestMapping("/api/murabaha/plans")
public class RepaymentPlanController {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final RepaymentPlanService service;
    private final RepaymentPlanResponseMapper responseMapper;

    public RepaymentPlanController(RepaymentPlanService service,
                                   RepaymentPlanResponseMapper responseMapper) {
        this.service = service;
        this.responseMapper = responseMapper;
    }

    @PostMapping
    public ResponseEntity<RepaymentPlanResponse> create(
            @Valid @RequestBody CreatePlanRequest request,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey) {
        RepaymentPlanResponse body = responseMapper.toResponse(service.create(request, idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/{planId}")
    public RepaymentPlanResponse get(@PathVariable UUID planId) {
        return responseMapper.toResponse(service.get(planId));
    }
}
