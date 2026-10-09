# ADR-012. Stage 1.5: Kubernetes(k3s) 전환과 무중단 배포

- 상태: **시기 승인 (2026-10-07), 세부는 제안.** 세부(노드 수, 상태 서비스 위치, 도구)는 이 단계를 시작하기 전에 다시 확인하고 확정한다. 이 문서의 "확인 필요"는 직접 검증하지 못한 내용이다.
- 2026-10-09: Boot 4.1.1의 probe·graceful shutdown 기본값, kubectl 내장 Kustomize, MinIO(Chainguard 이미지) 헬스 경로를 확인해 반영했다.
- 관련: [ADR-011](ADR-011-ops-practice-environment.md), [01-architecture-evolution](../02-design/01-architecture-evolution.md), NFR-REL

## 맥락
- ADR-011에서 Stage 1 동안은 compose + 자동 CD로 가고, 최종 목표를 쿠버네티스 무중단 배포로 정했다.
- 사용자 결정: **Stage 1 완료(`v1.0.0`) 후, Stage 2 시작 전**에 옮긴다.
- 이유(제안): Stage 2의 성능 측정(k6 Baseline 등)을 **최종 배포 환경에서** 하면 수치의 기준이 한 번만 정해진다. 환경을 Stage 2 중간에 바꾸면 이전 수치와 비교할 수 없게 된다.
- 제약: 호스트 RAM이 24GB다. compose VM(약 20GB)과 k3s VM을 **동시에 켤 수 없다**. 전환 후 compose VM은 끈다(스냅샷으로 보존).
- 새로 학습할 도구라 [CLAUDE.md §3](../../CLAUDE.md)에 따라 학습 비용과 대안을 적는다. 원 프로젝트에서 K3s를 써 봤다(As-Is: replicas 1, HPA·Ingress·PDB 없음, hostPath 볼륨) — 이번에는 그 결함을 고치는 것이 목표다.

## 결정 (제안 포함)
1. **시기**: Stage 1.5로 `v1.0.0` 이후, Stage 2 시작 전. Stage 2 Baseline(2-1)은 k3s 환경에서 측정한다.
2. **클러스터**: 새 VM(`myroutine-k3s`)에 **k3s 단일 노드**. compose VM은 전환 완료 후 정지(삭제하지 않고 스냅샷 유지). 노드 장애 대응(HA)은 목표가 아니다 — **"앱 배포 중 무중단"**이 목표다. 멀티 노드는 24GB 호스트에서 인프라 전체를 함께 올리기 어렵다(확인 필요: 실측).
3. **매니페스트**: plain YAML + `kubectl apply -k`(Kustomize는 kubectl 1.14부터 내장 — Kubernetes 문서 "Declarative Management of Kubernetes Objects Using Kustomize", 2026-10-09 확인). Helm·Operator는 쓰지 않는다(새 도구를 더 늘리지 않는다).
4. **상태 있는 서비스**(Postgres·Redis·Kafka·ES·MinIO): 같은 클러스터에서 단순한 StatefulSet + PVC(k3s 기본 local-path)로 시작한다. 데이터 안전성은 compose와 같은 수준(VM 백업)이다. 운영형 DB(Operator)는 범위 밖.
   - **MinIO**: 공식 `minio/minio` 이미지는 2026-09-11 Docker Hub에서 삭제됐다. compose(1-11)와 같은 `chainguard/minio`를 **같은 digest**로 쓴다(태그가 `latest`·`latest-dev`뿐이라 digest로 고정). 인자 `server /data --console-address :9001`. 이 이미지는 uid 65532(비root)로 돈다 → PVC에 쓰기 권한이 필요하다(`securityContext.fsGroup: 65532`가 필요한지 local-path에서 확인 필요). probe는 HTTP `GET /minio/health/live`(liveness)·`/minio/health/ready`(readiness), 포트 9000 — 두 경로 모두 200을 돌려주는 것을 2026-10-09 이 이미지로 확인했다.
   - presigned URL의 호스트 문제(1-11 §3)는 그대로다: `STORAGE_ENDPOINT`는 클러스터 내부 서비스 이름이 아니라 클라이언트가 닿는 주소(NodePort 또는 Ingress)여야 한다.
5. **CD**: 같은 self-hosted runner를 새 VM에 옮긴다. 배포 job이 `kubectl set image`(sha 태그) → `kubectl rollout status` → 실패 시 `kubectl rollout undo`. 러너의 kubeconfig는 **전용 네임스페이스만 다루는 ServiceAccount**로 제한한다(확인 필요: RBAC 구성).
6. **무중단 조건** (이게 갖춰져야 "무중단"이라고 말할 수 있다):

| 조건 | 내용 |
|---|---|
| 복제본 | 앱 replicas ≥ 2 |
| 롤링 전략 | `maxUnavailable: 0`, `maxSurge: 1` |
| readiness probe | 앱이 요청을 받을 준비가 된 뒤에만 트래픽을 받는다. Boot의 probe 그룹 `/actuator/health/readiness`·`/actuator/health/liveness`를 관리 포트 8081에서 쓴다 — Boot 4.1.1은 `management.endpoint.health.probes.enabled` 기본값이 `true`라 설정 없이 열린다(2026-10-09 jar 메타데이터로 확인). 메인 포트(8080)에도 `/readyz`·`/livez`를 열려면 `management.endpoint.health.probes.add-additional-paths=true`(기본 false) |
| 종료 | `server.shutdown=graceful`은 Boot 4.1.1 기본값이다(따로 켜지 않는다). 단계별 최대 대기 `spring.lifecycle.timeout-per-shutdown-phase`(기본 30s)보다 `terminationGracePeriodSeconds`를 크게, 종료 신호 전 짧은 지연(`preStop`)으로 서비스 엔드포인트에서 빠질 시간을 준다 |
| PDB | PodDisruptionBudget으로 동시 중단 수를 제한 |
| DB 스키마 | 롤링 중에는 **구버전·신버전이 같은 DB를 동시에 쓴다.** ADR-011 §7의 "파괴적 마이그레이션은 여러 번의 배포로"가 필수 규칙이 된다 |
| 다중 인스턴스 안전 | 앱이 2개 이상일 때 스케줄 잡(Postgres advisory lock, 2-5)과 Outbox 발행(Part 3)이 중복 실행되지 않는다. **이 단계가 그 가정을 처음 실제로 검증한다** |

7. **검증**: k6 부하를 건 상태에서 롤아웃. 지표는 5xx 개수, p99 스파이크, 롤백 소요 시간. 비교 대상은 compose 배포(컨테이너 교체 중 단절 시간).

## 단계 개요 (착수 전에 로드맵 문서로 구체화한다)

| 단계 | 내용 | 완료 확인(요지) |
|---|---|---|
| K-1 | k3s VM 구성, Postgres·MinIO 매니페스트, 앱을 수동으로 `kubectl apply` | 앱이 k3s에서 Part 1 기능을 수행 |
| K-2 | probe·graceful shutdown·롤링(replicas 2) | **k6 부하 중 롤아웃 → 5xx 0건** |
| K-3 | CD를 kubectl로 교체, 자동 롤백, RBAC 제한 | 실패 배포가 자동 `rollout undo`, 러너 권한이 네임스페이스로 제한됨 |
| K-4 | Redis·Kafka·ES·관측 스택 이전, compose VM 정지 | 전체 기능·E2E 시나리오(7-4) 통과 |
| K-5 | 파드 강제 종료 등 장애 주입, 리소스 requests/limits, 비교 리포트·회고 | compose 대비 배포 중 에러율·단절 시간 수치 |

- 릴리스 태그: 앱 코드가 아니라 배포 방식이 바뀌므로 PATCH(`v1.0.1`)를 제안한다(확정은 착수 전).
- Part 3~6(Kafka, Redis, ES)이 compose 기준으로 먼저 만들어지므로 K-4에서 매니페스트로 옮긴다. 이때 compose의 서비스 정의가 그대로 번역 원본이 된다.

## 고려한 대안
| 대안 | 결정 |
|---|---|
| Stage 1 중간(예: Part 3)에 전환 | Kafka·Redis 등 인프라가 늘어나는 시점과 겹쳐 기능 개발이 느려진다 |
| Stage 2 중에 전환 | 측정 환경이 바뀌어 전후 수치를 비교할 수 없게 된다 |
| Stage 3(MSA)와 함께 전환 | 서비스 분리와 클러스터 도입을 한 번에 겪으면 문제 원인이 섞인다. 다만 K8s는 MSA에서 가장 가치가 크므로 **Stage 3에서는 이미 클러스터가 있는 상태**로 시작할 수 있어 유리하다 |
| compose에서 blue-green(수동) | 도구는 안 늘지만 트래픽 전환(프록시)을 직접 만들어야 하고 이력서 가치가 낮다 |
| 매니지드 클러스터(클라우드) | 비용, 로컬 환경 연습이라는 목적과 맞지 않음 |
| Helm | 템플릿 문법이라는 학습 비용이 추가된다. plain YAML + Kustomize로 시작한다 |

## 결과
- Stage 2의 모든 측정이 k3s 단일 노드 환경에서 이뤄진다. 측정 리포트에 환경(리소스, 복제 수)을 명시한다.
- 한계(숨기지 않는다): 단일 노드라 **노드 장애에는 무방비**다. "무중단"은 앱 롤링 배포에 한정한다. 이 한계는 면접에서 먼저 말할 수 있어야 한다.
- 새 학습 부담: k3s 설치·운영, Deployment/Service/Probe/PDB/RBAC, Kustomize. 원 프로젝트 경험이 있어 처음부터는 아니다.
