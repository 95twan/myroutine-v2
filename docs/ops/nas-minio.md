# 운영 MinIO를 NAS(DS720+)에서 돌리기

> 버전 0.1 · 2026-10-14
> 이 문서의 명령과 메뉴 이름은 **직접 실행해 검증한 것이 아니다.** 확인하지 못한 곳은 "확인 필요"로 남겼다.

## 0. 무엇이 바뀌나

| | 이전 | 이후 |
|---|---|---|
| MinIO 위치 | VM의 `docker-compose.ops.yml` 안 `minio` 서비스 | NAS의 Container Manager 프로젝트 |
| 데이터 | VM의 named volume `minio_data` | NAS 로컬 볼륨의 폴더(bind mount) |
| 앱 → MinIO | 같은 compose 안 (VM의 LAN IP:9000) | LAN으로 `{NAS_IP}:9000` |
| 앱 설정 | `STORAGE_ENDPOINT=http://{VM_IP}:9000` | `STORAGE_ENDPOINT=http://{NAS_IP}:9000` |

코드는 바뀌지 않는다. 앱은 MinIO를 S3 호환 스토리지로만 알고, 주소는 환경변수로 받는다. 버킷(`myroutine`)과 `products/*` 읽기 정책은 앱이 기동할 때 `BucketInitializer`가 없으면 만든다.

## 1. 준비

| 항목 | 값 |
|---|---|
| NAS | DS720+ (Intel Celeron J4125, x86_64). VM과 같은 아키텍처라 이미지 digest를 그대로 쓴다 |
| 패키지 | Container Manager (DSM 7.2 이전 이름은 Docker, 확인 필요) |
| 고정 IP | 공유기에서 NAS의 MAC에 **DHCP 예약**. 이 IP를 `{NAS_IP}`라고 부른다. presigned URL에 이 주소가 박히므로 바뀌면 안 된다 |
| 메모리 | 기본 2GB(확인 필요). MinIO 단일 노드·이미지 몇 장 수준이면 충분하다 |

## 2. 데이터 폴더

NAS 볼륨 안에 폴더를 만든다(예: `/volume1/docker/myroutine-minio/data`). 두 가지를 지킨다.

- **NAS 로컬 볼륨**이어야 한다. 다른 곳을 NFS/SMB로 마운트한 경로는 MinIO가 요구하는 파일시스템 일관성을 보장하지 못한다.
- **uid 65532가 쓸 수 있어야 한다.** `chainguard/minio` 이미지는 비root(uid 65532)로 돈다([로드맵 1-11 S3](../03-roadmap/part1-foundation.md)에서 확인). 폴더 소유자를 `65532:65532`로 바꾼다(SSH에서 `sudo chown`. 시놀로지의 ACL이 이 변경을 막는지는 확인 필요). 안 맞으면 컨테이너가 쓰기 권한 오류로 바로 종료된다.

## 3. 컨테이너 사양

Container Manager의 "프로젝트"(compose)로 만든다(메뉴 이름 확인 필요). 파일은 NAS에 두고 리포에는 넣지 않는다. 사양은 운영 compose의 `minio` 서비스와 같게 맞춘다.

| 항목 | 값 |
|---|---|
| 이미지 | `chainguard/minio@sha256:...` — **`docker/docker-compose.ops.yml`의 digest와 같은 값**. 태그(`latest`)로 두지 않는다([ADR-011 §6](../adr/ADR-011-ops-practice-environment.md)) |
| 명령 | `server /data --console-address ":9001"` |
| 환경변수 | `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` — **VM `.env`의 같은 이름 값과 동일**해야 한다. 앱이 이 값을 접속 키로 쓴다(`application.yaml`의 `access-key`·`secret-key`) |
| 포트 | 호스트 `9000` → 컨테이너 `9000`(API)만. 콘솔(9001)은 publish하지 않는다(운영 정책과 같음) |
| 볼륨 | 위 §2 폴더 → `/data` |
| 재시작 | `unless-stopped` |
| 헬스체크 | `mc ready local` (운영 compose와 같음) |

- 비밀번호는 비밀번호 관리자로 VM `.env`와 옮긴다. 채팅·이슈·리포에 붙이지 않는다.
- 호스트의 9000이 DSM이나 다른 패키지와 겹치면(확인 필요) 호스트 포트만 바꾼다. 그 경우 `STORAGE_ENDPOINT`와 `STORAGE_PUBLIC_BASE_URL`의 포트도 같이 바꾼다.
- NAS 방화벽(DSM 제어판 → 보안 → 방화벽, 확인 필요)을 켜 두었다면 LAN 대역에서 9000/tcp를 허용한다.

## 4. 기동 확인

```bash
curl -fsS -o /dev/null -w '%{http_code}\n' http://{NAS_IP}:9000/minio/health/live   # 200
```

- 맥(개발 PC)과 **VM 안에서 각각** 확인한다. VM에서 안 되면 라우팅·방화벽 문제다.
- `/minio/health/live`가 200을 돌려주는 것은 이 이미지로 확인했다([ADR-012](../adr/ADR-012-kubernetes-zero-downtime.md) §4).

## 5. 전환 순서 (순서가 중요하다)

1. NAS에서 MinIO를 띄우고 §4를 통과시킨다.
2. VM의 `/opt/myroutine/.env`에서 `STORAGE_ENDPOINT`, `STORAGE_PUBLIC_BASE_URL`의 `{VM_IP}`를 `{NAS_IP}`로 바꾼다. `MINIO_ROOT_*`는 그대로 둔다(NAS와 같아야 한다). 실행 중인 앱은 컨테이너를 다시 만들 때까지 이전 값을 쓴다.
3. 리포의 `docker/docker-compose.ops.yml`에서 `minio` 서비스, `volumes.minio_data`, `app.depends_on.minio`를 삭제한다(사용자가 작성). PR로 `develop`에 합치면 CD가 새 compose로 배포한다. **2번보다 먼저 하면 앱이 VM의 MinIO가 없는 채로 옛 주소를 보고 기동에 실패한다.**
4. 배포가 끝나면 VM의 남은 것을 정리한다.
   ```bash
   docker ps -a | grep minio                     # 고아 컨테이너가 남았는지
   docker rm -f <컨테이너>                        # 있으면 삭제
   docker volume rm myroutine-ops_minio_data     # 이전 이미지 데이터(새로 시작하기로 했으므로 삭제)
   sudo ufw status numbered                      # 9000 규칙 2개의 번호 확인 후
   sudo ufw delete <번호>                        # 192.168.10.0/24, 172.16.0.0/12 두 규칙 삭제
   ```
5. 확인: 앱이 기동하는지(`/actuator/health`), 상품 이미지 presigned URL을 발급받아 호스트가 `{NAS_IP}:9000`인지, 그 URL로 PUT → 공개 URL로 GET이 되는지([로드맵 1-11](../03-roadmap/part1-foundation.md)의 업로드 확인 절차와 같다).

> ⚠ 운영 DB에 이전 테스트로 등록한 상품 이미지 키가 남아 있으면, NAS에는 그 객체가 없어 이미지 URL이 404가 된다. 새로 시작하기로 했으므로 테스트 데이터를 지우거나 이미지를 다시 올린다.

## 6. 되돌리기

VM `.env`의 두 값을 `{VM_IP}`로 되돌리고, `docker-compose.ops.yml`의 이전 커밋(minio 포함)을 다시 배포한다. 데이터는 NAS와 VM이 따로 있으므로 어느 쪽으로 가든 이미지는 새로 올려야 한다.

## 7. 한계와 알려진 점

- 앱이 VM과 NAS **두 장비에 의존**한다. NAS 재부팅·DSM 업데이트 중에는 상품 이미지 업로드가 실패한다. 앱 기동 때 `BucketInitializer`가 NAS에 접속하므로, NAS가 꺼진 채로 앱이 재시작하면 앱도 못 뜬다. 연습 환경이라 허용한다.
- HDD 절전이 켜져 있으면 첫 요청이 느릴 수 있다(확인 필요: DSM 하드디스크 최대 절전 설정).
- 백업: VM 백업(vzdump)에는 더 이상 이미지 데이터가 들어 있지 않다. NAS 쪽은 스냅샷(Btrfs일 때 Snapshot Replication 등, 확인 필요)이나 Hyper Backup으로 따로 건다. Stage 1에서는 범위 밖이다([ADR-011 §6](../adr/ADR-011-ops-practice-environment.md)).
- 부하 테스트(Stage 2)에서 이미지 업로드를 시나리오에 넣는다면 NAS의 디스크·네트워크가 병목이 될 수 있다. 측정 때 이 구성임을 결과에 적는다.
