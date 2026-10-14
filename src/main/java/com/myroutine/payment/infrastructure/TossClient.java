package com.myroutine.payment.infrastructure;

import com.myroutine.common.model.Money;
import com.myroutine.payment.domain.PgResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossClient {

    private static final Set<String> UNCERTAIN_CODES = Set.of("ALREADY_PROCESSED_PAYMENT", "ALREADY_PROCESSING_REQUEST", "IDEMPOTENT_REQUEST_PROCESSING");

    private final RestClient tossRestClient;

    public PgResult confirm(UUID paymentId, String paymentKey, String pgOrderId, Money amount) {
        long start = System.nanoTime();
        PgResult result;
        try {
            result = tossRestClient.post().uri("/v1/payments/confirm")
                    .header("Idempotency-Key", paymentId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("paymentKey", paymentKey, "orderId", pgOrderId, "amount", amount.amount()))
                    .exchange((req, res) -> classify(res, r -> {
                        if ("DONE".equals(r.status())) {
                            return approved(r);
                        }
                        return new PgResult.Unknown(r.status());
                    }));
        } catch (ResourceAccessException e) {
            result = new PgResult.Unknown(e.getClass().getSimpleName());
        }
        logResult("confirm", paymentId, result, start);
        return result;
    }

    public PgResult cancel(UUID paymentId, String paymentKey, Money amount, String reason, String idempotencyKey) {
        long start = System.nanoTime();
        PgResult result;
        try {
            result = tossRestClient.post().uri("/v1/payments/{key}/cancel", paymentKey)
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cancelReason", reason, "cancelAmount", amount.amount()))
                    .exchange((req, res) -> classify(res, r -> {
                        if ("CANCELED".equals(r.status()) || "PARTIAL_CANCELED".equals(r.status())) {
                            return approved(r);
                        }
                        return new PgResult.Unknown(r.status());
                    }));
        } catch (ResourceAccessException e) {
            result = new PgResult.Unknown(e.getClass().getSimpleName());
        }
        logResult("cancel", paymentId, result, start);
        return result;
    }

    public PgResult queryByOrderId(UUID paymentId, String pgOrderId) {
        long start = System.nanoTime();
        PgResult result;
        try {
            result = tossRestClient.get().uri("/v1/payments/orders/{orderId}", pgOrderId)
                    .exchange((req, res) -> classify(res, r -> {
                        if (r.status() == null) {
                            return new PgResult.Unknown(null);
                        }
                        return switch (r.status()) {
                            case "DONE", "PARTIAL_CANCELED", "CANCELED" -> approved(r);
                            case "ABORTED", "EXPIRED" -> new PgResult.Rejected(r.status(), r.status());
                            default -> new PgResult.Unknown(r.status());
                        };
                    }));
        } catch (ResourceAccessException e) {
            result = new PgResult.Unknown(e.getClass().getSimpleName());
        }
        logResult("query", paymentId, result, start);
        return result;
    }

    private PgResult approved(TossPaymentResponse r) {
        if (r.approvedAt() == null) {
            return new PgResult.Unknown("INVALID_BODY");
        }
        return new PgResult.Approved(r.paymentKey(), r.totalAmount(), r.balanceAmount(), r.method(), r.approvedAt().toInstant());
    }

    private PgResult classify(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse res, Function<TossPaymentResponse, PgResult> onSuccess) throws IOException {
        HttpStatusCode code = res.getStatusCode();
        if (code.is2xxSuccessful()) {
            try {
                TossPaymentResponse response = res.bodyTo(TossPaymentResponse.class);
                if (response == null) {
                    return new PgResult.Unknown("INVALID_BODY");
                }
                return onSuccess.apply(response);
            } catch (RestClientException e) {
                return new PgResult.Unknown("INVALID_BODY");
            }
        } else if (code.is4xxClientError()) {
            TossErrorResponse response;
            try {
                response = res.bodyTo(TossErrorResponse.class);
            } catch (RestClientException e) {
                return new PgResult.Unknown("INVALID_BODY");
            }
            if (response == null) {
                return new PgResult.Unknown("INVALID_BODY");
            }
            if (response.code() == null) {
                return new PgResult.Unknown("HTTP_4xx");
            }
            if (UNCERTAIN_CODES.contains(response.code())) {
                return new PgResult.Unknown(response.code());
            }
            return new PgResult.Rejected(response.code(), response.message());
        } else if (code.is5xxServerError()) {
            TossErrorResponse response;
            try {
                response = res.bodyTo(TossErrorResponse.class);
            } catch (RestClientException e) {
                return new PgResult.Unknown("HTTP_5xx");
            }
            if (response == null) {
                return new PgResult.Unknown("HTTP_5xx");
            }
            if (response.code() == null) {
                return new PgResult.Unknown("HTTP_5xx");
            }
            return new PgResult.Unknown(response.code());
        }
        return new PgResult.Unknown("HTTP_" + code.value());
    }

    private void logResult(String pgMethod, UUID paymentId, PgResult result, long start) {
        String reason = switch (result) {
            case PgResult.Approved a -> "";
            case PgResult.Rejected j -> j.code();
            case PgResult.Unknown u -> u.reason();
        };
        log.info("pg {} paymentId={} result={} reason={} latencyMs={}", pgMethod, paymentId,
                result.getClass().getSimpleName(), reason, (System.nanoTime() - start) / 1_000_000);
    }
}
