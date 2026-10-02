### 1. 서비스 간 호출(API) 무분별 허용

**[문제점]**

현재 MSA 구조에서 각 서비스는 Eureka(Discovery)를 통해 다른 서비스에 직접 REST 호출(Feign 등)을 수행할 수 있다.
이때 아무 서비스나 민감한 내부 API(회원 정보 수정, 권한 변경, 예치금 변경 등)를 호출할 수 있는 위험이 존재한다.

**[해결책]**

1.  서비스 간 식별을 위한 “커스텀 내부 인증 헤더” 적용
2.  서비스 간 비밀키(Shared Secret) 또는 서명(Signature) 기반 인증

### 2. 내부 api는 swagger 공개 해야되나?

1. 메소드에 @Operation(hidden = true) 사용
2. 컨트롤러에 @Hidden
3. Swagger 그룹에서 특정 패키지/경로 제외

   ```
    @Bean
   public OpenAPI customOpenAPI() {
    return new OpenAPI()
            .paths(filterInternalPaths());
   }
   private Paths filterInternalPaths(Paths paths) {
    Paths filtered = new Paths();
    paths.forEach((key, value) -> {
        if (!key.startsWith("/internal")) { // 내부 API 제외
            filtered.addPathItem(key, value);
        }
    });
    return filtered;
   }
   ```

### 3. 메일 인증 코드 발송 api의 호출 제한

**[문제점]**
악의적인 사용자가 같은 임시토큰 + 같은 이메일로 수십 번, 수백 번 인증코드 발송 요청을 보낼 수 있다.

**[해결책]**

1. Redis 기반 Rate Limiting을 사용해 같은 이메일로 요청을 시간당 1~3회로 제한
2. 발송 간 최소 시간 간격 제한으로 레디스에 해당 이메일이 남아 있으면 재전송을 막음

### 4. 인증 코드 확인 횟수 제한

### 6. 이메일 전송 비동기로

지금은 간단하게 @Async로 해결했지만 요청이 많아지면 쓰레도 소모가 많아진다.

### 7. Feign 실패 시 보상 트랜잭션

### 8. 토큰 블랙리스트 로그아웃, 탈퇴, 가게 생성, 삭제 시 기존 토큰 처리

### 9. 레디스 백업

### 10. 캐싱?

### 11. Circuit Breaker

### 12. apigateway를 통한 요청인지 service에서 체크를 해야 하나

### 14. discovery 유지

### 15. validation request and entity level

### 16. 가게 생성/삭제 시 Member Role 변경에 대한 멱등성 및 동시성 문제 발생

### 17. 실패 재처리 필요하면 spring.kafka.listener.ack-mode: manual + 재시도 정책(DLT/RetryTemplate) 설계

### 18. 트랜잭션 안에서 외부 서비스 호출

### 19. Reader에서 lastSummary까지 같이 읽기

### 20. Partitioner 로 llm 병렬

### 21. 리뷰 어뷰징 방지 및 필터링

### 22. 상품 검색시 상품에 검색어 저장

### 23. kafka concurrency 설정

### 24. deleteMyShop

실패 시나리오 대응표 (현재 구조 기준)

- 동시 삭제(같은 memberId 여러 상점) → SELLER 권한 제거 누락 가능 → memberId 기준 pg_advisory_xact_lock로 직렬화 + 역할 제거 전 재조회
- 동시 삭제(같은 shopId) → 삭제 이벤트 중복 발행 가능 → UPDATE ... WHERE deleted_at IS NULL로 idempotent 삭제 + 업데이트 row count가 0이면 이벤트 미발행
- 이벤트 발행 실패(네트워크/Kafka 다운) → downstream 정합성 누락 → Outbox 패턴 또는 재시도 큐 적용
- 트랜잭션 실패(삭제는 롤백, 이벤트는 발행됨) → 유령 이벤트 가능 → @TransactionalEventListener(AfterCommit)로만 발행(현재 ShopDeletedEventHandler는 OK)
- Kafka 지연/중단 → downstream 지연 정합성 → eventual consistency 허용 명시 + 소비자 재처리/모니터링
- 재시도(클라이언트/서버) → 중복 삭제/중복 이벤트 → 위 idempotent 업데이트 + 멱등 이벤트 키 사용
