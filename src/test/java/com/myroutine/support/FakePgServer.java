package com.myroutine.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class FakePgServer {

    private static final String PAYMENTS_PATH = "/v1/payments/";
    private static final String CONFIRM_PATH = "/v1/payments/confirm";
    private static final String ORDERS_PATH = "/v1/payments/orders/";
    private static final String CANCEL_SUFFIX = "/cancel";

    private final HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Behavior> behaviors = new ConcurrentHashMap<>();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final Map<String, ApprovedPayment> approved = new ConcurrentHashMap<>();
    private final Map<String, StoredResponse> idempotent = new ConcurrentHashMap<>();

    private FakePgServer(HttpServer server) {
        this.server = server;
    }

    public static FakePgServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            FakePgServer fake = new FakePgServer(server);
            server.createContext("/", fake::handle);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.start();
            return fake;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public void on(String pathPrefix, Behavior behavior) {
        behaviors.put(pathPrefix, behavior);
    }

    public List<RecordedRequest> requests(String pathPrefix) {
        return requests.stream()
                .filter(r -> r.path().startsWith(pathPrefix))
                .toList();
    }

    public void reset() {
        behaviors.clear();
        requests.clear();
        approved.clear();
        idempotent.clear();
    }

    public void markApproved(String pgOrderId, String paymentKey, long amount, Instant approvedAt) {
        approved.put(pgOrderId, new ApprovedPayment(paymentKey, pgOrderId, amount, approvedAt, 0));
    }

    private void handle(HttpExchange exchange) throws IOException {
        // 1. 요청을 읽어 기록한다
        String path = exchange.getRequestURI().getPath();
        Map<String, String> headers = exchange.getRequestHeaders().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toLowerCase(), e -> e.getValue().get(0)));
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new RecordedRequest(exchange.getRequestMethod(), path, headers, requestBody));

        // 2. 이 경로에 등록된 동작을 찾는다 (가장 긴 접두어, 없으면 진짜 Toss처럼 처리하는 기본 동작)
        Behavior behavior = behaviors.entrySet().stream()
                .filter(e -> path.startsWith(e.getKey()))
                .max(Comparator.comparingInt(e -> e.getKey().length()))
                .map(Map.Entry::getValue)
                .orElse(Behavior.approve());

        // 3. 연결 끊기: 응답 없이 닫는다
        if (behavior.dropConnection()) {
            exchange.close();
            return;
        }

        // 4. 응답을 정한다
        int status = behavior.status();
        String responseBody = behavior.body();

        if (behavior.defaultHandling()) {
            String idempotencyKey = headers.get("idempotency-key");
            StoredResponse response;

            if (idempotencyKey != null && idempotent.containsKey(idempotencyKey)) {
                response = idempotent.get(idempotencyKey);
            } else if (path.startsWith(ORDERS_PATH)) {
                ApprovedPayment payment = approved.get(path.substring(ORDERS_PATH.length()));
                response = payment == null ? notFound() : ok(payment);
            } else if (path.equals(CONFIRM_PATH)) {
                Map<String, Object> request = mapper.readValue(requestBody, Map.class);
                ApprovedPayment payment = new ApprovedPayment(
                        (String) request.get("paymentKey"),
                        (String) request.get("orderId"),
                        ((Number) request.get("amount")).longValue(),
                        Instant.now(),
                        0
                );
                approved.put(payment.orderId(), payment);
                response = ok(payment);
                if (idempotencyKey != null) {
                    idempotent.put(idempotencyKey, response);
                }
            } else if (path.startsWith(PAYMENTS_PATH) && path.endsWith(CANCEL_SUFFIX)) {
                String paymentKey = path.substring(PAYMENTS_PATH.length(), path.length() - CANCEL_SUFFIX.length());
                ApprovedPayment payment = approved.values().stream()
                        .filter(p -> p.paymentKey().equals(paymentKey))
                        .findFirst()
                        .orElse(null);
                if (payment == null) {
                    response = notFound();
                } else {
                    long cancelAmount = ((Number) mapper.readValue(requestBody, Map.class).get("cancelAmount")).longValue();
                    ApprovedPayment updated = new ApprovedPayment(
                            payment.paymentKey(),
                            payment.orderId(),
                            payment.amount(),
                            payment.approvedAt(),
                            payment.canceledAmount() + cancelAmount
                    );
                    approved.put(updated.orderId(), updated);
                    response = ok(updated);
                    if (idempotencyKey != null) {
                        idempotent.put(idempotencyKey, response);
                    }
                }
            } else {
                response = new StoredResponse(404, "{\"code\":\"NOT_FOUND\",\"message\":\"unknown path\"}");
            }

            status = response.status();
            responseBody = response.body();
        }

        // 5. 처리한 뒤에 기다린다 (PG는 승인했는데 응답만 늦는 상황)
        if (behavior.delayMillis() > 0) {
            try {
                Thread.sleep(behavior.delayMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 6. 응답한다
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    private StoredResponse ok(ApprovedPayment payment) {
        String status = payment.canceledAmount() == 0 ? "DONE"
                : payment.canceledAmount() == payment.amount() ? "CANCELED" : "PARTIAL_CANCELED";
        Map<String, Object> json = Map.of(
                "paymentKey", payment.paymentKey(),
                "orderId", payment.orderId(),
                "status", status,
                "totalAmount", payment.amount(),
                "balanceAmount", payment.amount() - payment.canceledAmount(),
                "method", "카드",
                "approvedAt", OffsetDateTime.ofInstant(payment.approvedAt(), ZoneOffset.ofHours(9))
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        );
        return new StoredResponse(200, mapper.writeValueAsString(json));
    }

    private StoredResponse notFound() {
        return new StoredResponse(404, "{\"code\":\"NOT_FOUND_PAYMENT\",\"message\":\"결제를 찾을 수 없습니다\"}");
    }

    private record ApprovedPayment(String paymentKey, String orderId, long amount, Instant approvedAt,
                                   long canceledAmount) {
    }

    private record StoredResponse(int status, String body) {
    }
}
