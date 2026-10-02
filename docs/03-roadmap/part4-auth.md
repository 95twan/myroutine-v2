# Part 4. 인증 완성

> **끝나면**: refresh 토큰으로 로그인이 유지되고, 탈취된 토큰을 감지하며, 이메일 인증·OAuth 로그인·관리자 제재·로그인 실패 잠금이 동작한다.
> **인프라 추가**: **Redis**
> **릴리스**: `v0.4.0`
> Part 4 시작 전에 이 문서를 다시 다듬는다.

| 단계 | 제목 | 크기 |
|---|---|---|
| 4-1 | Redis · refresh 토큰 회전 · 로그아웃 | M |
| 4-2 | 이메일 인증 · 비밀번호 변경·재설정 | M |
| 4-3 | OAuth 로그인 (Kakao · Google · Naver) | L |
| 4-4 | 즉시 차단(tokenVersion) · 관리자 제재 · 로그인 실패 잠금 | M |

---

## 4-1. Redis · refresh 토큰 회전 · 로그아웃 (M)

**목표**: Access 토큰을 15분으로 줄이고, refresh 토큰으로 갱신한다. refresh는 쓸 때마다 바뀌고, 이미 쓴 refresh가 다시 오면 탈취로 보고 그 기기의 세션을 모두 끊는다.

**왜 지금**: 1-4에서 남겨둔 한계(로그아웃 불가, 1시간 만료)를 해결한다. 서버가 상태(세션)를 가져야 하므로 Redis가 등장한다.

**새로 등장**

| 개념·도구 | 한 줄 설명 | 더 읽을 곳 |
|---|---|---|
| Redis | 만료 시간(TTL)이 있는 빠른 저장소. 세션·인증코드·잠금 횟수에 쓴다 | |
| refresh 회전 | refresh를 쓰면 새 refresh를 주고 이전 것은 무효 | [ADR-006](../adr/ADR-006-auth.md) |
| 재사용 탐지 | 무효가 된 refresh가 다시 오면 누군가 훔친 것 → 같은 family 전체 폐기 | [시퀀스 §9](../02-design/06-sequences.md) |

**할 일**
1. docker-compose·테스트에 Redis 추가
2. 세션 저장: `auth:session:{sessionId}` → memberId, familyId, refresh 해시, 기기 정보, TTL 7일
3. 로그인·가입 응답에 refresh 추가, `POST /api/auth/refresh`, `POST /api/auth/logout`(`?all=true`)
4. 동시 갱신 정책 OPEN-02(권장: 재사용으로 판단)

**완료 확인**
- [ ] refresh로 갱신 → 새 access + 새 refresh, 이전 refresh 무효
- [ ] 이전 refresh 재사용 → 401 `REFRESH_REUSED`, 같은 family 세션 모두 폐기, 다른 기기 세션은 유지
- [ ] 로그아웃 → 해당 refresh 사용 불가

**리뷰 때 물어볼 것**
- refresh를 원문이 아니라 해시로 저장하는 이유는?
- JWT는 무상태인데 왜 Redis를 쓰나? (원 프로젝트 면접 질문 1.2)

---

## 4-2. 이메일 인증 · 비밀번호 변경·재설정 (M)

**목표**: 이메일 가입 회원은 인증코드로 이메일을 인증해야 주문·결제·가게 개설을 할 수 있다. 비밀번호 변경과 재설정을 지원한다.

**할 일**
1. `POST /api/auth/email-verifications`(발송 60초 간격, 시간당 5회), `/confirm`(5회 실패 시 무효) → `email_verified_at`
2. 미인증 회원 차단: `@RequireVerifiedEmail` 같은 표시로 체크아웃·결제·충전·가게 개설에 403 `EMAIL_NOT_VERIFIED`
3. `PATCH /api/members/me/password`(현재 비밀번호 확인, 다른 세션 모두 폐기), 재설정(코드 발송 → 코드 + 새 비밀번호)
4. 메일 발송은 3-5의 `MailSender` 포트 재사용(트랜잭션 밖, 즉시 발송)
5. 기존 개발 데이터 회원은 마이그레이션으로 인증 처리하거나 테스트 데이터에서 정리

**완료 확인**
- [ ] 60초 내 재발송 → 429, 시간당 6번째 → 429
- [ ] 확인 5회 실패 → 맞는 코드도 거부
- [ ] 미인증 회원 체크아웃 → 403
- [ ] 비밀번호 변경 → 다른 기기 refresh 무효

**리뷰 때 물어볼 것**
- 인증코드를 비교할 때 타이밍 공격은?
- 비밀번호 재설정 요청에서 "가입되지 않은 이메일"이라고 알려주면 무엇이 문제인가?

---

## 4-3. OAuth 로그인 (L)

**목표**: Kakao·Google·Naver로 가입·로그인한다. 신규 회원은 임시 토큰 → 이메일 인증 → 가입 완료. 로그인한 회원은 소셜 계정을 연결할 수 있다.

**할 일**
1. `social_account` 마이그레이션(`uk_social_account_provider_user`)
2. 제공자별 전략(인가 URL, code 교환, 사용자 정보) — 응답 차이를 전략 안에서 흡수
3. `GET /api/auth/oauth/{provider}/authorize-url`, `POST .../login`(기존 → 토큰, 신규 → 임시 토큰 10분), `POST /api/auth/oauth/signup`
4. 계정 연결 `POST /api/members/me/social-accounts/{provider}` — 같은 이메일이어도 자동 연결하지 않는다(POL-19)
5. 테스트: `FakeHttpServer`로 제공자 토큰·사용자정보 엔드포인트 흉내

**완료 확인**
- [ ] 신규 소셜 로그인 → 임시 토큰 → 이메일 인증 → 가입 → 토큰
- [ ] 기존 연결된 소셜 계정 → 바로 토큰
- [ ] 같은 이메일의 이메일 가입 계정이 있어도 자동 연결되지 않음
- [ ] 제공자 타임아웃 → 정해진 에러

**리뷰 때 물어볼 것**
- OAuth `state` 파라미터는 무엇을 막나?
- 이메일이 같으면 자동으로 연결하면 어떤 보안 문제가 생기나?

---

## 4-4. 즉시 차단(tokenVersion) · 관리자 제재 · 로그인 실패 잠금 (M)

**목표**: 제재·탈퇴·전체 로그아웃 시 기존 Access 토큰이 몇 초 안에 막힌다. 관리자가 회원을 제재·해제한다. 비밀번호를 5번 틀리면 15분 잠긴다.

**할 일**
1. `token_version` 증가 + Redis `auth:token-version:{memberId}`, 인증 필터에서 비교(로컬 캐시 5초)
2. 제재·탈퇴(3-6)·전체 로그아웃·비밀번호 변경 시 버전 증가
3. `/admin/api/members` 검색, `/ban`, `/unban` (ADMIN만), BANNED는 문의 외 403
4. 로그인 실패 횟수 `auth:login-fail:{email}`, 5회 → 423 `ACCOUNT_LOCKED`
5. Redis 장애 시 정책: 로컬 캐시 값으로 허용, 캐시가 없으면 거부
6. 개발용 API(`/dev/**`)가 local 외 프로필에서 없는지 다시 확인

**완료 확인**
- [ ] 제재 → 기존 access가 5초 안에 401 `TOKEN_REVOKED`
- [ ] 일반 회원이 관리자 API → 403
- [ ] 5회 실패 → 잠금, 15분 후 해제 (`Clock`)
- [ ] Redis 중단 시 정책대로 동작

**리뷰 때 물어볼 것**
- 블랙리스트 방식(토큰마다 저장)과 tokenVersion 방식의 차이는?
- 매 요청 Redis를 조회하는 비용은? (Stage 2에서 측정, 원 프로젝트 인가 캐시 실험과 비교)

---

## Part 4 완료
- [ ] `v0.4.0` 릴리스, Part 5 문서 다듬기
