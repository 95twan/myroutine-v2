# ADR-011. 운영 환경 연습: Proxmox VM 1대 + Docker Compose, GitHub Actions self-hosted runner로 자동 배포

- 상태: 승인 (2026-10-07, 개정: 쿠버네티스를 최종 목표로 기록 / 2026-10-09: "확인 필요" 항목을 공식 문서·실행으로 확인, `workflow_run` 트리거의 안전 조건과 인프라 이미지 고정 방식 추가)
- 관련: 로드맵 [1-11](../03-roadmap/part1-foundation.md), NFR-SEC-01, [ADR-010](ADR-010-observability.md), [Git 정책 §7](../git-policy.md)

## 맥락
- 지금까지 문서에는 릴리스(태그)와 CI만 있고, **서버에 올려서 돌리는 배포**는 어디에도 없었다.
- 목적은 실서비스가 아니라 **운영 환경 연습**과 **분리된 테스트 환경**이다. 특히 Stage 2의 k6 부하 테스트는 개발 PC에서 앱·DB·k6가 자원을 나눠 쓰면 수치가 왜곡된다.
- 사용자는 Proxmox가 깔린 미니 PC(RAM 24GB, 16코어)를 갖고 있다. 리포는 **public**이다.
- **제약**: 새로 학습할 기술은 ELK, Prometheus/Grafana, k6로 한정한다([CLAUDE.md §3](../../CLAUDE.md)). Proxmox·Docker·GitHub Actions는 이미 아는 도구다. 새로 배우는 것은 **self-hosted runner(설치·설정 정도)** 하나이고 2026-10-07 사용자가 승인했다.

## 결정
1. **환경**: Proxmox에 Ubuntu Server VM 1대를 만들고 Docker Engine + compose로 앱과 인프라를 모두 올린다. VM 자원은 RAM 16GB·vCPU 8로 시작하고(호스트 RAM이 24GB이고 증설할 수 없어 8GB를 Proxmox·캐시용으로 남긴다. 2026-10-10 20GB에서 낮춤) 실제 사용량은 7-1(ELK) 때 측정해서 조정한다(확인 필요).
2. **파이프라인**:
   - CI(`ci.yml`)는 지금처럼 GitHub 호스티드 러너에서 PR·push마다 `./gradlew build`.
   - CD(`cd.yml`)는 `develop`에 push되고 CI가 통과한 뒤 `workflow_run`으로 실행된다: ① 호스티드 러너가 Docker 이미지를 빌드해 GHCR에 `{sha}` 태그로 올린다 → ② VM의 self-hosted 러너가 해당 sha 이미지를 pull해 `docker compose up -d`로 교체한다 → ③ 헬스체크가 실패하면 직전 sha로 되돌린다.
   - `workflow_dispatch`로 특정 sha를 직접 배포할 수 있다(수동 롤백 수단).
   - 배포 대상 브랜치를 `develop`으로 한 이유: `main`은 Part가 끝날 때만 갱신돼(Git 정책 §7) 배포가 7번뿐이다. 이 환경은 단계마다 합쳐진 결과를 계속 돌려보는 용도다. `main` 릴리스(태그)와 배포는 별개다.
3. **public 리포에서 self-hosted runner를 쓰는 안전 장치** (필수):
   - 배포 job은 `pull_request` 이벤트로는 절대 실행되지 않는다(`workflow_run`과 `workflow_dispatch`만). 포크 PR의 코드가 VM에서 돌 수 없어야 한다.
   - **`workflow_run`은 포크 PR로 돈 CI가 끝나도 발생한다.** 그래서 배포 job은 `github.event.workflow_run.event == 'push'`이고 `conclusion == 'success'`일 때만 실행한다(`branches: [develop]` 필터만으로는 포크의 같은 이름 브랜치를 거르지 못한다). 구현 규격은 로드맵 1-11 S7.
   - 저장소 설정 Settings → Actions → General → "Approval for running fork pull request workflows from contributors"를 "Require approval for all external contributors"로 둔다(메뉴 이름은 2026-10-09 GitHub 문서로 확인).
   - 배포 job은 GitHub Environment `ops`를 쓰고, 배포 가능한 브랜치를 `develop`·`main`으로 제한한다.
   - 러너는 전용 비root 사용자로 실행하고, VM에는 이 프로젝트 외의 중요한 데이터를 두지 않는다. (docker 그룹은 사실상 root 권한이다. VM을 격리된 용도로만 쓰는 것으로 위험을 받아들인다.)
4. **시크릿**: 운영 `.env`는 VM의 고정 경로(예: `/opt/myroutine/.env`)에 사람이 한 번 만든다. 리포와 GitHub Secrets에는 두지 않는다(NFR-SEC-01, 공격면 축소). 배포는 이 파일을 읽기만 한다. GHCR 로그인은 워크플로의 `GITHUB_TOKEN`을 쓴다. push하는 job은 `permissions: packages: write`, pull만 하는 배포 job은 `packages: read`(GitHub 문서 "Publishing Docker images"의 예시와 같다, 2026-10-09 확인).
5. **접속**: 집 네트워크(LAN) 안에서만. 앱 포트와 MinIO 포트만 LAN에 열고, DB·Redis·Kafka·ES와 actuator 관리 포트(8081)는 호스트에 publish하지 않는다(MEM-05). 외부 접속은 **보류**한다(아래).
6. **데이터**: Postgres·MinIO 등은 named volume. 백업은 Stage 1 범위 밖이고 회고에 남긴다.
   - 인프라 이미지도 고정한다: 버전 태그가 있으면 태그로(예: `pgvector/pgvector:pg17`), 태그가 `latest`뿐인 이미지(`chainguard/minio`, 2026-09-11 공식 `minio/minio` 삭제 후 사용)는 **digest**(`image@sha256:...`)로. 매일 다시 빌드되는 `latest`로 두면 롤백해도 인프라 버전이 바뀐다.
7. **스키마와 롤백**: 롤백은 이미지만 되돌린다(Flyway는 되돌리지 않는다). 따라서 **이전 버전 앱이 새 스키마에서 동작해야** 한다. 컬럼 삭제·이름 변경 같은 파괴적 마이그레이션은 "추가 → 코드 전환 → 삭제"의 두 번 이상의 배포로 나눈다.

## 고려한 대안
| 대안 | 결정 |
|---|---|
| 로컬 compose만(배포 없음) | 단순하지만 부하 테스트에서 부하 생성기와 앱이 자원을 공유해 수치가 왜곡된다. 운영 환경 연습 목적도 못 채운다 |
| Actions → SSH | VM이 집 네트워크 안이라 GitHub 호스티드 러너가 접근하려면 포트 개방이나 터널이 필요하다 |
| Actions → Tailscale → SSH | 도메인 없이 되지만 Tailscale이라는 또 다른 도구를 배워야 한다 |
| VM이 이미지를 당겨가기(cron, Watchtower) | 포트가 필요 없고 단순하지만, 배포 성공·실패가 Actions에 남지 않고 반영이 폴링 주기만큼 늦다 |
| **self-hosted runner (채택)** | VM에서 GitHub로 나가는 연결만 쓴다. 포트·도메인이 필요 없고, 배포 결과가 Actions에 기록된다. 대가는 public 리포에서의 위 안전 장치 |
| 처음부터 Kubernetes(k3s) | Stage 1 기능을 만드는 동안 클러스터 운영 부담이 겹친다. 기능이 쌓인 뒤 옮기는 쪽이 비교(compose → k8s)가 쉽다 → **최종 목표로 두고 시기는 뒤에서 정한다**(아래 "진화 경로") |

## 진화 경로: 최종 목표는 Kubernetes + 무중단 배포
- 2026-10-07 사용자가 밝힌 최종 목표다. 원 프로젝트에서 K3s를 써 봤다(As-Is: 매니페스트가 replicas 1, HPA·Ingress·PDB 없음 — 그 결함을 고치는 것이 이번 목표의 일부).
- **지금(1-11)은 compose + 자동 CD**로 시작한다. 그래야 파이프라인과 앱의 운영 특성(헬스체크, 환경변수 주입, graceful shutdown)을 먼저 익히고, 쿠버네티스로 옮길 때 "무엇이 좋아졌나"를 비교할 수 있다.
- 옮기기 쉽게 **1-11에서 미리 지키는 것**: 이미지는 sha 태그, 설정은 전부 환경변수, 헬스체크 경로·포트 고정(readiness/liveness로 재사용), 앱의 종료 시 요청 마무리(`server.shutdown=graceful` — Boot 4.1.1에서는 기본값이라 따로 켜지 않는다. 단계별 최대 대기는 `spring.lifecycle.timeout-per-shutdown-phase`, 기본 30s. 2026-10-09 jar의 설정 메타데이터로 확인).
- **시기와 세부는 [ADR-012](ADR-012-kubernetes-zero-downtime.md)**: Stage 1 완료 후, Stage 2 시작 전(2026-10-07 결정). 노드 수·상태 서비스 위치·무중단 조건·단계(K-1~K-5)는 거기서 다룬다.

## 보류: 외부 접속
- Cloudflare Tunnel로 도메인을 연결하는 방안을 검토했으나 **2026-10-07 보류**했다(고정 주소에는 Cloudflare에 등록한 도메인이 필요하다. Synology DDNS 주소는 Cloudflare에 추가할 수 없다).
- 대안으로 Synology DDNS + 포트포워딩은 집 포트를 직접 열어야 해서 선택하지 않았다.
- 영향: ① 프론트·외부 서비스가 쓰는 주소(CORS 허용 오리진, 이미지 공개 URL, Toss 리다이렉트 URL)를 코드에 고정하지 않고 **환경변수**로 받는다. ② Toss 웹훅처럼 외부에서 앱에 들어와야 하는 흐름은 LAN에서 검증할 수 없다 → Part 2 시작 전에 이 ADR을 다시 연다.

## 결과
- 모든 단계가 머지되면 VM에 자동 반영되고, `v0.1.0`부터 "배포해서 돌려본 버전"이 된다.
- Stage 2 부하 테스트는 개발 PC의 k6 → VM의 앱 구조로 한다(부하 생성기 분리).
- 한계: 단일 VM이라 가용성·무중단 배포는 목표가 아니다(배포 중 수 초 단절 허용). 백업·모니터링 알림 연동은 Part 7에서 다룬다.
- 프론트엔드는 별도 리포에서 AI로 작성한다. 이 리포는 API 명세가 계약이다.
