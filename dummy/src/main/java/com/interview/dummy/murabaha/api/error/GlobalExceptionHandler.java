package com.interview.dummy.murabaha.api.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates internal exceptions into RFC 7807 {@code application/problem+json}
 * payloads. The {@code type} field uses an opaque slug rooted at
 * {@code https://api.tamara.test/problems/} so clients can switch on the slug
 * without parsing free-form messages.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final String PROBLEM_BASE = "https://api.tamara.test/problems/";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> {
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("field", fe.getField());
                    entry.put("message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage());
                    return entry;
                })
                .toList();
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "validation-failed",
                "Request validation failed",
                "One or more fields failed validation.",
                request);
        body.setProperty("errors", fieldErrors);
        return respond(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadable(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "validation-failed",
                "Malformed JSON request",
                "Request body could not be parsed.",
                request);
        return respond(body);
    }

    @ExceptionHandler(InvalidPromoCodeException.class)
    public ResponseEntity<ProblemDetail> handleInvalidPromo(InvalidPromoCodeException ex,
                                                            HttpServletRequest request) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "invalid-promo-code",
                "Invalid promo code", ex.getMessage(), request);
        body.setProperty("promoCode", ex.getPromoCode());
        return respond(body);
    }

    @ExceptionHandler(UnknownCurrencyException.class)
    public ResponseEntity<ProblemDetail> handleUnknownCurrency(UnknownCurrencyException ex,
                                                               HttpServletRequest request) {
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "unknown-currency",
                "Unknown currency code", ex.getMessage(), request);
        body.setProperty("currencyCode", ex.getCurrencyCode());
        return respond(body);
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ProblemDetail> handleIdempotencyConflict(IdempotencyKeyConflictException ex,
                                                                   HttpServletRequest request) {
        ProblemDetail body = problem(HttpStatus.CONFLICT, "idempotency-key-conflict",
                "Idempotency key conflict", ex.getMessage(), request);
        body.setProperty("idempotencyKey", ex.getIdempotencyKey());
        return respond(body);
    }

    @ExceptionHandler(PlanNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(PlanNotFoundException ex,
                                                        HttpServletRequest request) {
        ProblemDetail body = problem(HttpStatus.NOT_FOUND, "plan-not-found",
                "Plan not found", ex.getMessage(), request);
        body.setProperty("planId", ex.getPlanId().toString());
        return respond(body);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomain(DomainException ex,
                                                      HttpServletRequest request) {
        ProblemDetail body = problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "Internal error",
                "An internal invariant was violated.",
                request);
        return respond(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleAny(Exception ex, HttpServletRequest request) {
        ProblemDetail body = problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
                "Internal error",
                "An unexpected error occurred.",
                request);
        return respond(body);
    }

    private static ProblemDetail problem(HttpStatus status, String slug, String title,
                                         String detail, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(PROBLEM_BASE + slug));
        pd.setTitle(title);
        if (request != null) {
            pd.setInstance(URI.create(request.getRequestURI()));
        }
        return pd;
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail body) {
        return ResponseEntity.status(body.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(body);
    }
}
