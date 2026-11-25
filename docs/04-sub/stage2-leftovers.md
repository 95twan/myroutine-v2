# Stage 2에서 남긴 개선

Stage 2는 S2-2 커서 조건, S2-3 커넥션 풀·예약 작업 스레드, S2-5 재고 차감 비교로 끝냈다([아키텍처 진화](../02-design/01-architecture-evolution.md)). 아래는 측정으로 필요가 보였거나 요구사항을 확인하려고 계획했지만 하지 않은 것이다. 측정 환경·시드·k6 스크립트는 [Stage 2 준비](../03-roadmap/stage2-plan.md)의 것을 그대로 쓴다.

| 단계 | 무엇 | 근거 |
|---|---|---|
| SUB-14 | outbox 릴레이 지연 | Baseline에서 이벤트 지연 p95 1.5초로 요구사항(NFR-PERF-06, 1초)을 넘었다 |
| SUB-15 | 상품 조회 캐시(옛 S2-4) | 상품 조회는 VM CPU가 한계였다. 캐시가 postgres 몫의 CPU를 얼마나 줄이는지 재 볼 수 있다 |
| SUB-16 | 남은 요구사항 측정(옛 S2-1b) | 인증 오버헤드(S-04), 정산 배치(S-05), 상품 검색(S-07)은 재지 않았다 |

## SUB-14. outbox 릴레이 지연

**현상**: 이벤트가 저장된 뒤 Kafka로 나가기까지 p50 1,019ms, p95 1,475ms였다([Baseline](../perf/s2-1a-baseline.md)). 릴레이가 이벤트를 보낸 뒤 1초를 쉬고(`OutboxRelayJob`의 `fixedDelay = 1000`), 보낼 때는 한 건씩 Kafka의 응답을 기다린다(`OutboxRelay`의 `kafkaTemplate.send(...).get(5, TimeUnit.SECONDS)`). 한 번에 약 60건을 건당 8ms씩 보냈다.

**고칠 것**
- 쉬는 시간을 줄인다.
- 묶음을 먼저 모두 보낸 뒤 응답을 한꺼번에 기다린다. `.get()`을 그냥 빼면 Kafka가 받기 전에 발송 완료로 표시되어 실패한 이벤트를 잃으므로, 응답을 받은 것만 발송 완료로 표시한다.

**확인**: S-06을 같은 조건으로 다시 재서 p95가 1초 아래인지 본다. 미발송 0건은 그대로여야 한다.

## SUB-15. 상품 조회 캐시

**현상**: 상품 조회(S-01)는 커넥션이 아니라 VM CPU(약 7/8코어)가 한계였다([S2-3](../perf/s2-3-pool-scheduling.md)). 앱과 postgres가 같은 VM의 CPU를 나눠 쓴다.

**할 것**: 상품 상세를 Redis에 캐시하고, 상품 변경 이벤트로 지운다. 동시에 같은 키가 비었을 때 DB로 몰리는 것(캐시 스탬피드)을 막는다. 목록은 커서 위치마다 키가 달라 상세부터 본다.

**확인**: 같은 S-01에서 처리량, p95, postgres CPU, 캐시 적중률을 캐시 전과 비교한다. 상세를 바꾼 뒤 옛 값이 보이는 시간도 잰다.

## SUB-16. 남은 요구사항 측정

[Stage 2 준비 3절](../03-roadmap/stage2-plan.md)의 S-04·S-05·S-07을 잰다.

| 시나리오 | 기준 |
|---|---|
| S-04 인증 오버헤드 | 헤더 있음과 없음의 p95 차이 10ms 미만 |
| S-05 정산 배치 | 구매확정 품목 100만 건을 10분 안에, 누락·중복 0건 |
| S-07 상품 검색 | p95 200ms 미만, 에러율 0.1% 미만 |

S-07은 시드 상품을 ES에도 넣어야 한다(방법은 [아키텍처 진화 Stage 2](../02-design/01-architecture-evolution.md)). S-04는 쟀고([리포트](../perf/sub-16-s04-auth.md)), S-05·S-07은 남긴다.

### S-04와 세션 캐시

S-04는 [SUB-25](session-auth.md) 앞뒤의 인증 비용을 같은 VM, 같은 데이터에서 재서 비교하고, 그 결과로 세션 앞에 Redis 캐시를 둘지 정한다([ADR-015](../adr/ADR-015-session-auth.md) 고려한 대안).

| 측정 | 앱 | 요청마다 인증이 하는 일 |
|---|---|---|
| Redis 토큰 버전 | SUB-24 머지 커밋 `d1d9519fb2de01e5c474623b339614e9b781303a` | JWT 서명 검증 + Redis `auth:token-version` GET 1번 |
| DB 세션 | SUB-25 머지 커밋 `af9df616a214686d011c65b2d182e1e0779f5212` | JWT 서명 검증 + `member.auth_session`과 `member.member` 조인 조회 1번 |

- 두 커밋은 인증 말고는 코드가 같다. 지금 운영 VM의 데이터에서 `workflow_dispatch`로 차례로 배포해 잰다. SUB-24 앱은 SUB-25가 남긴 `member.token_version` 컬럼으로 그대로 뜨고, 이미 적용된 SUB-25 마이그레이션은 Flyway 기본값(`ignoreMigrationPatterns` = `*:future`, `flyway-core` 12.4.0 `FlywayModel`)이 무시한다.
- 캐시를 붙인 SUB-25에서 캐시가 맞는 요청은 Redis GET 1번이라 SUB-24와 같은 모양이다. 그래서 두 앱의 인증 비용 차이가 캐시로 얻을 수 있는 몫의 상한이다.
- 앱마다 배포 직후 헤더 없음과 있음으로 30초씩 데운 뒤, 헤더 없음과 있음을 번갈아 두 번씩 재고(없음, 있음, 없음, 있음), 헤더 있음과 없음의 p95 차이를 인증 비용으로 본다. 두 분포의 p95 차이이지 요청별 비용의 p95는 아니다. 같은 조건 두 번의 p95가 인증 비용만큼 흔들리면 한 쌍을 더 잰다.
- 토큰은 `setup()`에서 k6 회원 `k6-co-1`~`k6-co-100`으로 로그인해 VU마다 하나씩 쓴다. 토큰 하나를 모두가 쓰면 같은 세션 행만 읽어 실제보다 유리하게 나온다. 로그인과 토큰 확인(`GET /api/members/me` 200)이 하나라도 실패하면 측정 전에 멈춘다.
- 운영 DB의 시드 회원 비밀번호는 공개하면서 무작위 해시로 바꿔 두었다. 측정하는 동안만 `k6-co-*` 100명을 `Seed1234!`로 돌리고, 끝나면 다른 시드 회원과 같은 무작위 해시로 되돌린다.
- DB 세션 측정에서는 postgres 컨테이너 CPU와 커넥션 풀 대기(`hikaricp_connections_pending`)도 본다. 세션 조회가 DB와 풀을 쓰기 때문이다.
- 캐시는 CPU 한계보다 아래의 부하에서 DB 세션의 인증 비용이 기준(p95 차이 10ms 미만)을 넘거나, 헤더 있음에서 커넥션 풀 대기가 생길 때 실험한다. 한계 근처에서는 어떤 일이든 더해지면 꼬리가 길어지므로, 100 VU에서 기준을 넘으면 부하를 낮춰(30 VU) 다시 잰다.
- 결과는 [S-04 리포트](../perf/sub-16-s04-auth.md)다. 보통 부하(30 VU)에서 p95 차이 1.1ms, CPU 한계 근처(100 VU)에서 12~14ms였고 커넥션 풀 대기는 없어 캐시를 더하지 않았다.

#### k6 스크립트

`perf/k6/s04-auth-overhead.js`. `GET /api/products/{id}`(공개 API)를 100 VU로 1분 램프업 뒤 5분 유지한다. `AUTH=true`면 `Authorization` 헤더를 붙여, 공개 API라도 인증 필터가 토큰을 검증하고 세션을 조회한다. 가짜 HTTP 서버로 돌려 토큰 있음에서 모든 상세 요청에 헤더가 붙고, 로그인이 실패하면 측정 전에 멈추는 것을 확인했다.

```js
import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 100);
const AUTH = __ENV.AUTH === 'true';
const JSON_HEADERS = { 'Content-Type': 'application/json' };
const productIds = new SharedArray('productIds', () => JSON.parse(open('./data/product-ids.json')));

export const options = {
  setupTimeout: '5m',
  scenarios: {
    read: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.RAMP || '1m', target: VUS },
        { duration: __ENV.HOLD || '5m', target: VUS },
      ],
    },
  },
  thresholds: {
    'http_req_failed{name:detail}': ['rate<0.001'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

function login(i) {
  return ['POST', `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: `k6-co-${i}@seed.test`, password: 'Seed1234!' }),
    { headers: JSON_HEADERS, tags: { name: 'setup' } }];
}

export function setup() {
  if (!AUTH) {
    return { tokens: [] };
  }
  const tokens = [];
  for (let start = 1; start <= VUS; start += 50) {
    const logins = http.batch(Array.from({ length: Math.min(50, VUS - start + 1) }, (_, k) => login(start + k)));
    logins.forEach((r, k) => {
      if (r.status !== 200) {
        throw new Error(`login k6-co-${start + k} failed: ${r.status}`);
      }
      tokens.push(r.json('accessToken'));
    });
  }
  const me = http.batch(tokens.map((t) => ['GET', `${BASE_URL}/api/members/me`, null,
    { headers: { Authorization: `Bearer ${t}` }, tags: { name: 'setup' } }]));
  me.forEach((r, i) => {
    if (r.status !== 200) {
      throw new Error(`token ${i + 1} not authenticated: ${r.status}`);
    }
  });
  return { tokens };
}

export default function (data) {
  const id = productIds[Math.floor(Math.random() * productIds.length)];
  const params = { tags: { name: 'detail' } };
  if (AUTH) {
    params.headers = { Authorization: `Bearer ${data.tokens[__VU - 1]}` };
  }
  const res = http.get(`${BASE_URL}/api/products/${id}`, params);
  check(res, { 'detail 200': (r) => r.status === 200 });
}
```

#### 측정 순서

**VM 창**(runner로):

```bash
sudo -iu runner
cd ~/actions-runner/_work/myroutine-v2/myroutine-v2
PG='docker exec -i myroutine-ops-postgres-1 sh -c'
```

**1. k6 회원 비밀번호를 잠시 되돌린다.** 해시는 `Seed1234!`를 앱과 같은 `BCryptPasswordEncoder`로 만든 값이다.

```bash
$PG 'psql -U "$POSTGRES_USER" -d my_routine -c "update member.member set password_hash = '"'"'\$2a\$10\$NYEi/FKT7iPXXjlQqDGSGu3pDkF7x0VVsNMdtckDIdTd7Zv.LV4Iq'"'"' where email like '"'"'k6-co-%@seed.test'"'"'"'
```

**2. 개발 PC 창**(리포 루트):

```bash
ulimit -n 10000
cd perf/k6
BASE=http://192.168.10.13:8080
mark() { echo "$1 $(date '+%Y-%m-%d %H:%M:%S')" | tee -a results/sub-16-s04-times.txt; }
round() {
  k6 run -e BASE_URL=$BASE -e VUS=10 -e RAMP=0s -e HOLD=30s s04-auth-overhead.js
  k6 run -e BASE_URL=$BASE -e AUTH=true -e VUS=10 -e RAMP=0s -e HOLD=30s s04-auth-overhead.js
  for i in 1 2; do
    mark "$1-anon-$i-start"; k6 run -e BASE_URL=$BASE --summary-export results/sub-16-s04-$1-anon-$i.json s04-auth-overhead.js | tee results/sub-16-s04-$1-anon-$i.txt; mark "$1-anon-$i-end"
    mark "$1-auth-$i-start"; k6 run -e BASE_URL=$BASE -e AUTH=true --summary-export results/sub-16-s04-$1-auth-$i.json s04-auth-overhead.js | tee results/sub-16-s04-$1-auth-$i.txt; mark "$1-auth-$i-end"
  done
}
```

먼저 토큰 경로가 도는지 짧게 확인한다: `k6 run -e BASE_URL=$BASE -e AUTH=true -e VUS=2 -e RAMP=0s -e HOLD=10s s04-auth-overhead.js`가 에러 없이 끝나야 한다.

**3. SUB-24 앱을 배포하고 잰다.**

```bash
gh workflow run cd.yml -f sha=d1d9519fb2de01e5c474623b339614e9b781303a
```

CD가 끝나면 VM에서 `cat /opt/myroutine/deployed-sha`가 `d1d9519...`인지 보고, 개발 PC에서 `round token`(약 27분).

**4. SUB-25 앱을 배포하고 잰다.**

```bash
gh workflow run cd.yml -f sha=af9df616a214686d011c65b2d182e1e0779f5212
```

배포된 sha를 확인하고 `round session`. 이 구간에서는 Grafana "성능 측정" 대시보드의 컨테이너 CPU(postgres)와 "서비스 개요"의 HikariCP 대기를 `results/sub-16-s04-times.txt`의 시각으로 맞춰 남긴다.

**5. k6 회원 비밀번호를 무작위 해시로 되돌린다.** 다른 시드 회원(`u1@seed.test`)의 해시를 그대로 쓴다.

```bash
$PG 'psql -U "$POSTGRES_USER" -d my_routine -c "update member.member set password_hash = (select password_hash from member.member where email = '"'"'u1@seed.test'"'"') where email like '"'"'k6-co-%@seed.test'"'"'"'
```

마지막에 배포된 앱은 develop과 같은 SUB-25다. 결과는 `docs/perf/sub-16-s04-auth.md` 리포트로 남긴다.
