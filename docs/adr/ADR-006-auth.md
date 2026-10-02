# ADR-006. 인증·인가: 역할 클레임 JWT + tokenVersion 즉시 무효화 + 소유권 기반 인가

- 상태: 승인 (2026-10-02)
- 관련: [06-sequences.md §9](../02-design/06-sequences.md), NFR-SEC, NFR-AVL-01

## 맥락
- As-Is: JWT에는 memberId만 담고, 게이트웨이가 매 요청 member-service에 authorize를 동기 호출했다(MEM-08). 가게 개설·폐업으로 역할이 바뀌어도 토큰을 재발급할 필요가 없다는 장점이 있었지만, 대가로 매 요청 네트워크 홉이 생기고 member가 SPOF가 됐다. 엔드포인트 캐시로 RPS +16% 개선한 실험이 있다(본인 작업, [실험 결과](../references/experiments/authorization-endpoint-cache-performance.md)).
- 모놀리스에서는 게이트웨이↔member 홉이 사라지지만, "역할 변경을 언제 반영할 것인가"와 "토큰을 즉시 무효화할 수 있는가"는 그대로 남는다.

## 결정
1. Access JWT(15분)에 `memberId, role, tokenVersion`을 담는다(역할은 회원당 하나: USER·SELLER·ADMIN). 서명 키는 환경변수로 주입한다(MEM-01).
2. **판매자 기능은 역할이 아닌 소유권으로 인가**한다: `shop.memberId == 요청자`. SELLER 역할은 UI 노출과 판매자 메뉴 진입용이다. 그래서 역할 반영이 최종적 일관성이어도(가게 개설 직후 토큰에 SELLER가 없어도) 기능이 막히지 않는다.
3. 즉시 무효화가 필요한 경우(제재, 탈퇴, 전체 로그아웃)에는 `member.token_version`을 증가시키고 Redis에 반영한다. 인증 필터는 토큰의 tv를 매 요청 Redis 값과 비교한다. (2026-10-02: 로컬 캐시 5초는 선택 사항으로 돌렸다 — Stage 2에서 측정 후 결정)
4. Refresh는 Redis 세션으로 기기별 관리하고, 사용할 때마다 교체한다. 이미 사용된 refresh가 다시 오면 같은 family 전체를 폐기한다(탈취 탐지).
5. 관리자 경로(`/admin/api/**`)는 ADMIN 역할로 인가한다. 역할 변경 시에는 tokenVersion 증가로 즉시 반영한다.

## 고려한 대안
| 대안 | 기각 이유 |
|---|---|
| memberId만 담고 매 요청 DB·캐시에서 역할 조회 (As-Is 방식) | 모놀리스에선 홉은 없지만 매 요청 조회 비용. Stage 3에서 다시 SPOF 문제로 돌아감 |
| 역할 변경 시 강제 재로그인 | 가게 개설 직후 재로그인은 나쁜 경험 |
| Access 토큰 블랙리스트 | 토큰 단위 관리라 키가 많아짐. 회원 단위 버전 하나로 충분 |
| 완전 무상태(Redis 조회 없음) | 제재·탈퇴를 최대 15분간 반영하지 못함 (FR-MEM-04 위반) |

## 결과
- 매 요청 비용 = 서명 검증 + Redis 조회 1회. Stage 2에서 As-Is 방식(매 요청 인가 조회)과 비교 측정한다(NFR-PERF-04).
- Redis 장애 시 정책은 Stage 1에서 두지 않는다(인증 요청 실패). 로컬 캐시 + 허용/거부 정책은 [제안]으로 남긴다.
- Stage 3 게이트웨이 도입 시 1~3번을 게이트웨이로 옮기면 된다. 서비스는 소유권 검증만 한다.

## 면접 연결 ([면접 질문](../references/notes/interview-questions.md) 1.2, 1.3, 2.4)
- "JWT에 권한을 넣지 않으면 매 요청 member를 호출하나요?" → 원 프로젝트 방식과 캐시 실험, v2에서 바꾼 이유
- "토큰 탈취 시 대응은?" → refresh 회전 + 재사용 탐지
- "블랙리스트 방식과 비교하면?" → 회원 단위 tokenVersion
