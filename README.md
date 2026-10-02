# MyRoutin v2

판매자가 가게를 열어 상품을 팔고, 구매자는 여러 가게의 상품을 **카드(Toss) 결제**로 한 번에 구매하거나 **정기 구독**하는 오픈마켓.

팀 프로젝트(MSA 8개 서비스)를 분석해 **모듈러 모놀리스로 재설계**하고, 측정 기반 성능 개선을 거쳐 MSA로 전환하는 과정을 기록하는 개인 프로젝트다.

> 현재 단계: **Stage 1 · Part 1** — [로드맵](docs/03-roadmap/README.md)

## 기술 스택
Java 25 · Spring Boot 4.1 · Spring Data JPA (Hibernate 7) · PostgreSQL 17 (pgvector) · Flyway · Testcontainers · Gradle 9
(이후 단계에서 Kafka, Redis, Elasticsearch, ELK, Prometheus/Grafana, k6가 추가된다)

## 로컬 실행

**필요한 것**: Docker, JDK 25 (Gradle toolchain이 자동으로 찾는다)

```bash
# 1. 환경변수 파일 준비 (값은 로컬용으로 채운다. .env.local은 커밋하지 않는다)
cp .env.example .env.local

# 2. 인프라 실행 (Postgres)
docker compose --env-file .env.local -f docker/docker-compose.yml up -d

# 3. 앱 실행 (local 프로필이 .env.local을 읽는다)
./gradlew bootRun --args='--spring.profiles.active=local'

# 4. 헬스체크 (관리 포트 8081)
curl -s localhost:8081/actuator/health      # {"status":"UP", ...}
```

앱이 시작될 때 Flyway가 `src/main/resources/db/migration` 아래 마이그레이션을 자동으로 적용한다.

**테스트**: Docker가 실행 중이면 Testcontainers가 테스트용 Postgres를 자동으로 띄운다.
```bash
./gradlew test
```

**로컬 DB 초기화** (마이그레이션을 처음부터 다시 적용하고 싶을 때, 로컬 데이터가 모두 지워진다)
```bash
docker compose --env-file .env.local -f docker/docker-compose.yml down -v
```

## 문서
| 문서 | 내용 |
|---|---|
| [00-as-is](docs/00-as-is/README.md) | 원 팀 프로젝트 분석과 결함 54건 (재설계의 근거) |
| [01-requirements](docs/01-requirements/README.md) | 요구사항, 정책, 정합성 불변식 |
| [02-design](docs/02-design/README.md) | 아키텍처 진화, ERD, 상태머신, 이벤트, 시퀀스, API |
| [adr](docs/adr/) | 설계 결정 기록 |
| [development-guide](docs/development-guide.md) | 코딩 규칙과 리뷰 기준 |
| [git-policy](docs/git-policy.md) | 브랜치·커밋·PR 규칙 |
| [03-roadmap](docs/03-roadmap/README.md) | 단계별 구현 로드맵 |
