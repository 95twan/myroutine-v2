package com.myroutine.support;

import com.myroutine.member.domain.OAuthProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class FakeOAuthServer {

    private static final String TOKEN_SUFFIX = "/token";
    private static final String USERINFO_SUFFIX = "/userinfo";

    private final HttpServer server;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<OAuthProvider, GivenUser> users = new ConcurrentHashMap<>();
    private final Map<OAuthProvider, Behavior> behaviors = new ConcurrentHashMap<>();
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();

    private FakeOAuthServer(HttpServer server) {
        this.server = server;
    }

    public static FakeOAuthServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            FakeOAuthServer fake = new FakeOAuthServer(server);
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

    public void givenUser(OAuthProvider provider, String providerUserId, String email) {
        users.put(provider, new GivenUser(providerUserId, email));
    }

    public void failWith(OAuthProvider provider, Behavior behavior) {
        behaviors.put(provider, behavior);
    }

    public List<RecordedRequest> requests(String pathPrefix) {
        return requests.stream()
                .filter(r -> r.path().startsWith(pathPrefix))
                .toList();
    }

    public void reset() {
        users.clear();
        behaviors.clear();
        requests.clear();
    }

    private void handle(HttpExchange exchange) throws IOException {
        // 1. 요청을 읽어 기록한다
        String path = exchange.getRequestURI().getPath();
        Map<String, String> headers = exchange.getRequestHeaders().entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toLowerCase(), e -> e.getValue().get(0)));
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new RecordedRequest(exchange.getRequestMethod(), path, headers, requestBody));

        // 2. 경로의 제공자(/kakao/token → KAKAO)와 그 제공자에 등록된 동작을 찾는다
        OAuthProvider provider = providerOf(path);
        Behavior behavior = provider == null ? Behavior.approve() : behaviors.getOrDefault(provider, Behavior.approve());

        // 3. 연결 끊기: 응답 없이 닫는다
        if (behavior.dropConnection()) {
            exchange.close();
            return;
        }

        // 4. 응답을 정한다
        int status = behavior.status();
        String responseBody = behavior.body();

        if (behavior.defaultHandling()) {
            if (provider != null && path.endsWith(TOKEN_SUFFIX)) {
                status = 200;
                responseBody = mapper.writeValueAsString(Map.of("access_token", "fake-access-token", "token_type", "bearer"));
            } else if (provider != null && path.endsWith(USERINFO_SUFFIX) && users.containsKey(provider)) {
                status = 200;
                responseBody = mapper.writeValueAsString(userInfoBody(provider, users.get(provider)));
            } else {
                status = 404;
                responseBody = "{\"error\":\"not_found\"}";
            }
        }

        // 5. 처리한 뒤에 기다린다
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

    private OAuthProvider providerOf(String path) {
        String[] segments = path.split("/");
        if (segments.length < 2) {
            return null;
        }
        try {
            return OAuthProvider.valueOf(segments[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // 제공자마다 다른 실제 응답 모양. 이메일을 안 주는 사용자는 이메일 필드를 아예 내리지 않는다
    private Map<String, Object> userInfoBody(OAuthProvider provider, GivenUser user) {
        Map<String, Object> body = new LinkedHashMap<>();
        switch (provider) {
            case KAKAO -> {
                // Kakao의 id는 JSON 숫자다
                body.put("id", user.providerUserId().matches("\\d{1,18}") ? (Object) Long.parseLong(user.providerUserId()) : user.providerUserId());
                Map<String, Object> account = new LinkedHashMap<>();
                if (user.email() != null) {
                    account.put("email", user.email());
                }
                body.put("kakao_account", account);
            }
            case GOOGLE -> {
                body.put("sub", user.providerUserId());
                if (user.email() != null) {
                    body.put("email", user.email());
                }
            }
            case NAVER -> {
                body.put("resultcode", "00");
                body.put("message", "success");
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("id", user.providerUserId());
                if (user.email() != null) {
                    response.put("email", user.email());
                }
                body.put("response", response);
            }
        }
        return body;
    }

    private record GivenUser(String providerUserId, String email) {
    }
}
