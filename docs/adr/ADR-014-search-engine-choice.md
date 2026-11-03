# ADR-014. 상품 검색 엔진으로 Elasticsearch를 쓴다

- 상태: 승인 (2026-11-02)
- 관련: [로드맵 5-2](../03-roadmap/part5-review-search.md), [ADR-010](ADR-010-observability.md), [ADR-013](ADR-013-search-hydration.md), FR-SRC-01, NFR-PERF-07, NFR-AVL-02

## 맥락
- 상품 검색은 이름과 설명에서 한국어 키워드를 찾고, 관련도순으로 정렬하고, 커서로 다음 쪽을 조회해야 한다.
- 원본 데이터는 Postgres에 있다. 검색 전용 저장소를 더하면 동기화와 운영 비용이 생기므로, Postgres로 충분한지 먼저 확인했다.
- [ADR-010](ADR-010-observability.md)의 로그 저장소도 Elasticsearch라 ES 컨테이너는 검색과 상관없이 스택에 들어온다.

## 결정
1. 상품 검색은 ES에 nori 분석기를 넣어 처리하고, 정렬은 ES의 관련도 점수(BM25)로 한다. 규격은 [로드맵 5-2](../03-roadmap/part5-review-search.md)에 있다.
2. 자동완성도 ES에서 읽는다. Postgres가 더 빨랐지만 ES도 p50 1.2ms로 충분하고, 검색 모듈이 자동완성 때문에 상품 DB 쿼리와 인덱스를 따로 두지 않아도 된다.
3. Postgres는 원본으로 남고, ES는 이벤트로 갱신하는 읽기 모델이다. 표시 값은 DB에서 읽는다([ADR-013](ADR-013-search-hydration.md)).
4. 아래 측정은 선택의 근거이고, 이력서에 쓰는 수치는 Stage 2에서 실제 API로 잰 NFR-PERF-07 결과로 한다.

## 고려한 대안
| 대안 | 측정·확인 결과 | 판단 |
|---|---|---|
| `LIKE '%단어%'` | 10만 건, 동시 20명에서 p95 1.4초 | 기각. 검색어를 한 덩어리로만 찾아 단어 순서가 바뀌면 놓치고, 관련도 점수가 없다 |
| `pg_trgm` GIN 인덱스 | 드문 단어는 ES보다 빠르다(100만 건 0.14ms). 흔한 단어는 10만 건, 동시 20명에서 p95 301ms | 기각. 맞는 행을 모두 읽고 정렬해야 해서 흔한 단어일수록 느리고, 인덱스 크기도 늘어난다 |
| Postgres 전문 검색(`tsvector`) | 쓰는 이미지(`pgvector/pgvector:pg17`)에 한국어 설정이 없다. `simple` 설정은 `멀티비타민 정`을 `멀티비타민`·`정`으로 잘라 `비타민`으로 찾지 못한다 | 기각. 형태소 분석을 하려면 이미지 교체와 확장 학습이 필요하다. 한국어 확장을 넣은 경우의 속도와 품질은 재지 않았다 |
| 상품 DB 쿼리로 자동완성(앞부분 일치, `text_pattern_ops`) | 100만 건 p50 0.08ms로 ES(1.2ms)보다 빠르다 | 결정 2의 이유로 쓰지 않는다. 속도 때문에 ES를 고른 것이 아니다 |

## 측정

### 조건
- 상품 1만·10만·100만 건을 같은 합성 데이터로 양쪽에 넣었다. 이름은 `브랜드 성분 형태`, 설명은 12개 문장 중 5개에 성분 문구를 더한 약 100자다.
- Mac의 Docker에서 컨테이너를 각각 CPU 2개, 메모리 2GB로 제한했다. ES 힙은 512MB, Postgres는 기본 설정이다.
- ES 쿼리는 5-2 규격의 `multi_match`와 정렬을 쓰고, 문서 내용 없이 `productId` 21개만 받는다. Postgres는 이름에 단어가 있는 행을 먼저 놓고 `id`로 정렬해 21개를 받는다.
- 흔한 단어는 상품의 약 10%에 걸리는 5개이고, 드문 단어는 약 20건에 걸리는 5개다.
- 동시 요청은 20개 클라이언트가 쉬지 않고 15초 동안 `비타민`을 검색한다.

### 요청 한 건의 지연 (p50, ms)
| 검색 | 방식 | 1만 건 | 10만 건 | 100만 건 |
|---|---|---|---|---|
| 흔한 단어 | ES | 2.49 | 1.42 | 3.59 |
| 흔한 단어 | `LIKE` | 3.59 | 16.06 | 198.38 |
| 흔한 단어 | `pg_trgm` | 0.75 | 6.96 | 92.61 |
| 드문 단어 | ES | 1.55 | 0.90 | 1.33 |
| 드문 단어 | `LIKE` | 3.57 | 34.53 | 203.53 |
| 드문 단어 | `pg_trgm` | 0.17 | 0.14 | 0.14 |
| 앞부분 일치 | ES | 0.98 | 0.78 | 1.20 |
| 앞부분 일치 | Postgres B-tree | 0.09 | 0.09 | 0.08 |

정렬 없이 `LIMIT 21`만 하면 `LIKE`도 흔한 단어를 약 0.2ms에 찾는다. 앞쪽에서 21건을 찾으면 멈추기 때문이다. 관련도순으로 정렬하려면 이 방식을 쓸 수 없다.

### 동시 20개 클라이언트
| 상품 수 | ES | `pg_trgm` | `LIKE` |
|---|---|---|---|
| 1만 건 | 초당 2,188건, p95 70ms | 초당 1,141건, p95 88ms | 초당 254건, p95 180ms |
| 10만 건 | 초당 2,668건, p95 56ms | 초당 124건, p95 301ms | 초당 25.5건, p95 1.4초 |
| 100만 건 | 초당 701건, p95 78ms | 초당 10.8건, p95 3.3초 | 초당 3.1건, p95 12.3초 |

### 용량
| 항목 | ES | Postgres |
|---|---|---|
| 메모리, 데이터 없을 때 | 880MB | 25MB |
| 메모리, 100만 건 측정 후 | 985MB | 234MB |
| 디스크, 1만 건 | 3MB | 4MB (인덱스 포함 7MB) |
| 디스크, 10만 건 | 32MB | 41MB (인덱스 포함 59MB) |
| 디스크, 100만 건 | 309MB | 413MB (인덱스 포함 557MB) |

100만 건 ES 디스크의 필드별 크기는 `_source` 120.5MB, `productId` 66.9MB, `_id` 38.2MB, `description` 32.8MB, `name.autocomplete` 30.8MB, `name` 5.9MB다.

ES 쓰기는 묶음 색인이 초당 약 15,000건, consumer처럼 external version으로 한 건씩 쓰면 초당 788건이다(클라이언트 1개, 100만 건 인덱스에 3,000건).

### 측정의 한계
- **디스크**: 설명 문장이 반복되어 압축이 잘 된다. 실제 설명이면 ES가 더 커질 수 있어 "ES가 Postgres보다 작다"는 결론을 내지 않는다.
- **전체 응답 시간**: ES 수치는 검색만 잰 값이다. 실제 API는 DB에서 항목을 채우는 시간이 더해진다.
- **환경**: 운영 VM이 아니다. 동시 요청의 긴 지연에는 CPU 제한의 영향이 섞여 있을 수 있다. ES는 컨테이너 밖에서 HTTP로 호출했고, `psql`과 `pgbench`는 Postgres 컨테이너 안에서 돌아 CPU 제한을 같이 쓴다.
- **ES 쿼리 옵션**: 1만·10만 건 ES 값과 앞부분 일치 값에는 5-2 규격에 없는 `track_total_hits: false`가 들어갔다. 100만 건의 흔한 단어·드문 단어·동시 요청은 규격대로 다시 쟀다. 이 옵션이 있을 때 100만 건 흔한 단어의 p50은 2.6~3.4ms다.
- **자동완성 설정**: 측정의 `min_gram`은 1이고 현재 규격은 2다. 100만 건에서 `name.autocomplete` 크기는 21.03MB와 20.97MB로 차이가 0.3%였다. 표의 앞부분 일치 값에는 현재 규격의 `collapse`도 없다. `collapse`와 `name.keyword`를 넣으면 100만 건에서 디스크가 8.9MB 늘고, 맞는 상품이 많은 짧은 검색어(최대 약 90만 건)의 p50이 1.50ms에서 3.57ms로, p95가 4.98ms에서 17.69ms로 늘었다. 현재 규격은 공백을 지우는 `char_filter`와 `name.keyword` 두 번째 정렬 키도 쓴다. 공백을 지워도 조각 수는 늘지 않지만, 이 설정의 크기와 지연은 측정하지 않았다(확인 필요).
- **품사 필터**: 측정은 nori 기본 품사 필터로 했고, 현재 규격은 감탄사(`IC`)와 일반 부사(`MAG`)를 버리지 않는다. 색인되는 단어가 늘어나므로 표의 검색 지연과 디스크가 달라질 수 있지만 측정하지 않았다(확인 필요).
- **로그 공유**: [ADR-010](ADR-010-observability.md)대로 로그와 클러스터를 함께 쓰면 힙과 CPU를 나눠 쓴다. 이번 측정에는 로그가 없다.
- **검색 품질**: 합성 데이터에서는 `비타민`의 결과 건수가 ES와 `LIKE`에서 같았다. 실제 문장에서 순위 품질을 비교한 결과는 아직 없다.
- **뺀 값**: Postgres 인덱스 생성 시간은 1만 건 91초, 100만 건 324초로 건수와 비례하지 않아 믿을 수 없어 뺐다.
- **재지 않은 것**: 100만 건을 넘는 규모, 장시간 운영.

## 결과
- 상품 수가 1만 건이면 속도만으로는 ES가 필요 없다. 차이는 동시 요청이 있을 때 10만 건쯤부터 벌어진다. 이 프로젝트에서 ES를 쓰는 주된 이유는 관련도 정렬과 한국어 분석이다.
- 메모리를 약 1GB 쓴다. ES는 로그 저장소로도 필요해서 검색 때문에 컨테이너가 늘지는 않는다.
- 반영 지연이 생긴다. 상품을 바꾼 뒤 이벤트 전달과 refresh(기본 1초)를 거쳐야 검색 대상과 순서에 반영된다.
- 이벤트 consumer와 인덱스 관리 코드가 늘어난다(로드맵 5-2). 무중단 재색인은 서브 SUB-7로 미뤘다.
- ES가 멈춰도 검색만 실패하도록 격리한다(NFR-AVL-02, `SearchUnavailableTest`).

## 면접 연결
- "왜 Postgres로 안 했나?" → 흔한 단어를 관련도순으로 정렬할 때 맞는 행을 모두 읽어야 해서 느렸고, 쓰는 이미지에 한국어 분석 설정도 없었다.
- "상품이 1만 건이어도 ES가 필요한가?" → 속도만 보면 필요 없다. 측정해 보니 차이는 10만 건쯤부터 벌어졌고, 관련도 정렬과 한국어 분석 때문에 골랐다.
- "ES가 항상 빠른가?" → 아니다. 드문 단어와 앞부분 일치는 Postgres가 빨랐다.

## 부록: 측정 스크립트
Python 3.11 표준 라이브러리, Docker, 빌드해 둔 `myroutine-elasticsearch:9.4.8`, `pgvector/pgvector:pg17`이 필요하다. 임시 컨테이너 `es-bench`, `pg-bench`만 쓰고 끝나면 지운다. 두 파일을 같은 폴더에 `bench.py`, `check.py`로 저장해 `python3 -I bench.py`로 돌린다. `check.py`는 `python3 check.py`로 돌린다(`bench`를 가져오려면 현재 폴더가 경로에 있어야 한다).

아래 `bench.py`는 100만 건을 잴 때의 그대로다. 1만·10만 건은 `SIZES = [10_000, 100_000, 1_000_000]`으로 돌렸고, 그때 Postgres 컨테이너에는 `--shm-size=1g`이 없었다. 이 옵션이 없으면 100만 건의 인덱스 측정에서 `pgbench`가 실패했고, 옵션을 넣으니 통과했다. 실패 원인을 직접 확인하지는 않았다.

<details>
<summary><code>bench.py</code>: ES, LIKE, pg_trgm 비교</summary>

```python
"""ES vs Postgres 검색 비교. 임시 컨테이너(es-bench, pg-bench)만 쓰고 끝나면 지운다."""
import http.client, json, multiprocessing as mp, os, random, re, statistics, subprocess, sys, time, uuid

ES_PORT, PG_PORT = 19202, 15433
SIZES = [1_000_000]
HERE = os.path.dirname(os.path.abspath(__file__))
RESULT = os.path.join(HERE, "results_1m.json")

COMMON = ["비타민", "콜라겐", "유산균", "오메가", "루테인"]
RARE = ["아스타잔틴", "쿼세틴", "베르베린", "실리마린", "크릴오일"]
BRANDS = [f"브랜드{c}{d}" for c in "가나다라마바사아자차" for d in "하늘숲강별달빛산들꽃"]
INGREDIENTS = COMMON + ["마그네슘", "칼슘", "아연", "철분", "엽산", "홍삼", "녹차", "석류", "레몬", "허브",
                        "알로에", "히알루론", "세라마이드", "레티놀", "시카", "티트리", "쌀겨", "귀리", "꿀", "보리"]
FORMS = ["정", "캡슐", "분말", "젤리", "크림", "앰플", "세럼", "로션", "마스크팩", "스틱"]
PHRASES = ["매일 간편하게 섭취할 수 있는 제품입니다", "피부 보습에 도움을 주는 성분을 담았습니다", "온 가족이 함께 즐길 수 있습니다",
           "엄격한 품질 관리를 거쳐 생산되었습니다", "휴대하기 편한 개별 포장으로 구성했습니다", "아침 식사 후 물과 함께 드시면 좋습니다",
           "자극이 적은 원료를 사용했습니다", "꾸준한 관리를 원하는 분께 권합니다", "국내 공장에서 위생적으로 제조했습니다",
           "산뜻한 사용감으로 끈적임이 없습니다", "선물용으로도 인기가 많은 상품입니다", "서늘하고 건조한 곳에 보관해 주세요"]
CATS = ["HEALTH", "BEAUTY", "FOOD", "ETC"]


def run(*a, **k):
    p = subprocess.run(a, capture_output=True, text=True, **k)
    if p.returncode:
        raise RuntimeError(f"rc={p.returncode} {a[:6]} ERR={p.stderr[-1500:]}")
    return p.stdout


def docs(n):
    r = random.Random(42)
    rare_at = {r.randrange(n): RARE[i % 5] for i in range(100)}  # 단어마다 약 20건
    for i in range(n):
        name = f"{r.choice(BRANDS)} {r.choice(INGREDIENTS)} {r.choice(FORMS)}"
        parts = r.sample(PHRASES, 5)
        for w in COMMON:
            if r.random() < 0.08:
                parts.append(f"{w} 함유")
        if i in rare_at:
            parts.append(f"{rare_at[i]} 함유")
        s = r.random()
        yield dict(productId=str(uuid.UUID(int=r.getrandbits(128), version=4)), name=name,
                   description=" ".join(parts),
                   status="ON_SALE" if s < .9 else ("HIDDEN" if s < .97 else "DISCONTINUED"),
                   category=r.choice(CATS), price=r.randrange(1000, 100000), shopId="s" + str(r.randrange(500)))


class Es:
    def __init__(self):
        self.c = http.client.HTTPConnection("127.0.0.1", ES_PORT, timeout=300)

    def req(self, m, p, body=None, ndjson=False):
        d = None if body is None else (body if isinstance(body, str) else json.dumps(body)).encode("utf-8")
        self.c.request(m, p, d, {"Content-Type": "application/x-ndjson" if ndjson else "application/json"})
        r = self.c.getresponse()
        t = r.read()
        if r.status >= 400:
            raise RuntimeError(f"{m} {p} {r.status} {t[:400]}")
        return json.loads(t) if t else None


INDEX_BODY = {
    "settings": {"number_of_shards": 1, "number_of_replicas": 1, "analysis": {
        "tokenizer": {"korean_tokenizer": {"type": "nori_tokenizer", "decompound_mode": "mixed"},
                      "autocomplete_tokenizer": {"type": "edge_ngram", "min_gram": 1, "max_gram": 20}},
        "analyzer": {"korean": {"type": "custom", "tokenizer": "korean_tokenizer", "filter": ["nori_part_of_speech", "lowercase"]},
                     "autocomplete": {"type": "custom", "tokenizer": "autocomplete_tokenizer", "filter": ["lowercase"]},
                     "autocomplete_search": {"type": "custom", "tokenizer": "keyword", "filter": ["trim", "lowercase"]}}}},
    "mappings": {"properties": {
        "name": {"type": "text", "analyzer": "korean", "fields": {
            "autocomplete": {"type": "text", "analyzer": "autocomplete", "search_analyzer": "autocomplete_search"}}},
        "description": {"type": "text", "analyzer": "korean"},
        "productId": {"type": "keyword"}, "shopId": {"type": "keyword"}, "category": {"type": "keyword"},
        "status": {"type": "keyword"}, "price": {"type": "long"}}}}


def search_body(q):
    return {"query": {"bool": {"must": {"multi_match": {"query": q, "fields": ["name^3", "description"]}},
                               "filter": [{"term": {"status": "ON_SALE"}}]}},
            "sort": [{"_score": "desc"}, {"productId": "asc"}], "size": 21, "_source": ["productId"], "track_total_hits": False}


def auto_body(q):
    return {"query": {"bool": {"must": {"match": {"name.autocomplete": q}}, "filter": [{"term": {"status": "ON_SALE"}}]}},
            "size": 10, "_source": ["name"], "track_total_hits": False}


def stats(xs):
    xs = sorted(xs)
    return dict(n=len(xs), p50=round(statistics.median(xs), 2), p95=round(xs[int(len(xs) * .95) - 1], 2), max=round(xs[-1], 2))


def es_latency(index, words, body, n):
    e, out = Es(), []
    for i in range(n + 10):
        t = time.perf_counter()
        e.req("POST", f"/{index}/_search", body(words[i % len(words)]))
        if i >= 10:
            out.append((time.perf_counter() - t) * 1000)
    return stats(out)


def es_worker(a):
    index, word, secs = a
    e, out, end = Es(), [], time.time() + secs
    while time.time() < end:
        t = time.perf_counter()
        e.req("POST", f"/{index}/_search", search_body(word))
        out.append((time.perf_counter() - t) * 1000)
    return out


def es_concurrent(index, word, clients=20, secs=15):
    with mp.Pool(clients) as p:
        all_ = [x for l in p.map(es_worker, [(index, word, secs)] * clients) for x in l]
    return dict(clients=clients, tps=round(len(all_) / secs), **stats(all_))


def psql(sql):
    return subprocess.run(["docker", "exec", "-i", "pg-bench", "psql", "-U", "postgres", "-qAt", "-f", "-"], input=sql,
                          check=True, capture_output=True, text=True).stdout


def pg_latency(fn, words, n):
    script = "\\timing on\n\\o /dev/null\n" + "".join(fn(words[i % len(words)]) + ";\n" for i in range(n + 3))
    ts = [float(x) for x in re.findall(r"Time: ([\d.]+) ms", psql(script))]
    return stats(ts[3:])


def pg_concurrent(sql, clients=20, secs=15):
    p = "/tmp/b.sql"
    subprocess.run(["docker", "exec", "-i", "pg-bench", "sh", "-c", f"cat > {p}; rm -f /tmp/log.*"], input=sql + ";\n", check=True, text=True)
    run("docker", "exec", "pg-bench", "pgbench", "-U", "postgres", "-n", "-c", str(clients), "-j", "4", "-T", str(secs), "-f", p, "-l", "--log-prefix=/tmp/log", "postgres")
    log = run("docker", "exec", "pg-bench", "sh", "-c", "cat /tmp/log.*")
    ls = [int(l.split()[2]) / 1000 for l in log.splitlines()]
    return dict(clients=clients, tps=round(len(ls) / secs, 1), **stats(ls))


def mem():
    out = run("docker", "stats", "--no-stream", "--format", "{{.Name}} {{.MemUsage}}", "es-bench", "pg-bench")
    return dict(l.split(" ", 1) for l in out.strip().splitlines())


def like(w, sort):
    cond = f"status='ON_SALE' AND (name LIKE '%{w}%' OR description LIKE '%{w}%')"
    if sort:
        return f"SELECT id FROM p{{n}} WHERE {cond} ORDER BY CASE WHEN name LIKE '%{w}%' THEN 0 ELSE 1 END, id LIMIT 21"
    return f"SELECT id FROM p{{n}} WHERE {cond} LIMIT 21"


def save(res):
    with open(RESULT, "w") as f:
        json.dump(res, f, ensure_ascii=False, indent=1)


def main():
    res = {"sizes": {}}
    limits = ["--cpus=2", "--memory=2g"]
    run("docker", "run", "--rm", "-d", "--name", "es-bench", *limits, "-p", f"127.0.0.1:{ES_PORT}:9200", "-e", "discovery.type=single-node",
        "-e", "xpack.security.enabled=false", "-e", "ES_JAVA_OPTS=-Xms512m -Xmx512m", "myroutine-elasticsearch:9.4.8")
    run("docker", "run", "--rm", "-d", "--name", "pg-bench", *limits, "--shm-size=1g", "-e", "POSTGRES_HOST_AUTH_METHOD=trust", "pgvector/pgvector:pg17")
    for _ in range(120):
        try:
            Es().req("GET", "/_cluster/health?wait_for_status=yellow&timeout=1s")
            if subprocess.run(["docker", "exec", "pg-bench", "pg_isready", "-U", "postgres"], capture_output=True).returncode == 0:
                break
        except Exception:
            pass
        time.sleep(1)
    time.sleep(15)
    psql("CREATE EXTENSION pg_trgm;")
    res["idle_mem"] = mem()
    e = Es()
    res["tokens"] = {w: [t["token"] for t in e.req("POST", "/_analyze", {"tokenizer": {"type": "nori_tokenizer", "decompound_mode": "mixed"}, "text": w})["tokens"]] for w in COMMON + RARE}
    print("idle", res["idle_mem"], flush=True)

    for n in SIZES:
        r = res["sizes"][str(n)] = {}
        idx = f"products-{n}"
        e = Es()
        e.req("PUT", f"/{idx}", INDEX_BODY)
        t0, buf, copy = time.time(), [], []
        pg_tab = f"p{n}"
        psql(f"CREATE TABLE {pg_tab}(id uuid PRIMARY KEY, name text, description text, status text, category text, price bigint, shop_id text);")
        pg = subprocess.Popen(["docker", "exec", "-i", "pg-bench", "psql", "-U", "postgres", "-qc", f"COPY {pg_tab} FROM STDIN"], stdin=subprocess.PIPE, text=True)
        es_secs = 0.0
        def flush():
            nonlocal es_secs
            t = time.time()
            ans = e.req("POST", f"/{idx}/_bulk", "".join(buf), ndjson=True)
            if ans["errors"]:
                raise RuntimeError("bulk errors")
            es_secs += time.time() - t
            buf.clear()
        for d in docs(n):
            buf.append(json.dumps({"index": {"_id": d["productId"]}}) + "\n" + json.dumps(d, ensure_ascii=False) + "\n")
            pg.stdin.write("\t".join([d["productId"], d["name"], d["description"], d["status"], d["category"], str(d["price"]), d["shopId"]]) + "\n")
            if len(buf) == 2000:
                flush()
        if buf:
            flush()
        pg.stdin.close(); pg.wait()
        e.req("POST", f"/{idx}/_refresh")
        e.req("POST", f"/{idx}/_flush")
        psql(f"ANALYZE {pg_tab};")
        r["es_bulk_docs_per_sec"] = round(n / es_secs)
        time.sleep(5)
        print(n, "loaded", round(time.time() - t0), "s", flush=True)

        sub = lambda s: s.replace("{n}", str(n))
        r["es_store"] = e.req("GET", f"/_cat/indices/{idx}?format=json&bytes=mb&h=store.size,docs.count")
        r["pg_table_mb"] = int(psql(f"SELECT pg_total_relation_size('{pg_tab}')/1048576;"))
        r["hits"] = {w: {"es": e.req("POST", f"/{idx}/_count", {"query": search_body(w)["query"]})["count"],
                         "pg": int(psql(sub(f"SELECT count(*) FROM p{{n}} WHERE status='ON_SALE' AND (name LIKE '%{w}%' OR description LIKE '%{w}%');")))} for w in ["비타민", "아스타잔틴"]}
        prefixes = [f"{b} {i[0]}" for b, i in zip(BRANDS[:5], INGREDIENTS[:5])]

        r["es"] = {"common": es_latency(idx, COMMON, search_body, 300), "rare": es_latency(idx, RARE, search_body, 300),
                   "two_words": es_latency(idx, ["비타민 크림", "콜라겐 젤리", "유산균 캡슐", "오메가 정", "루테인 스틱"], search_body, 300),
                   "autocomplete": es_latency(idx, prefixes, auto_body, 300), "concurrent": es_concurrent(idx, "비타민")}
        print(n, "es done", flush=True)
        save(res)

        reps = 200 if n <= 10_000 else (50 if n <= 100_000 else 15)
        r["pg_seq"] = {"common_nosort": pg_latency(lambda w: sub(like(w, False)), COMMON, reps),
                       "common_sorted": pg_latency(lambda w: sub(like(w, True)), COMMON, reps),
                       "rare": pg_latency(lambda w: sub(like(w, False)), RARE, reps),
                       "prefix": pg_latency(lambda w: sub(f"SELECT name FROM p{{n}} WHERE status='ON_SALE' AND name LIKE '{w}%' LIMIT 10"), prefixes, reps),
                       "concurrent": pg_concurrent(sub(like("비타민", True)))}
        r["pg_seq_plan"] = psql("EXPLAIN " + sub(like("비타민", True)) + ";").splitlines()[:8]
        print(n, "pg seq done", flush=True)
        save(res)

        t = time.time()
        psql(sub("CREATE INDEX t{n}_name ON p{n} USING gin(name gin_trgm_ops); CREATE INDEX t{n}_desc ON p{n} USING gin(description gin_trgm_ops); CREATE INDEX t{n}_pre ON p{n}(name text_pattern_ops); ANALYZE p{n};"))
        r["pg_index_secs"] = round(time.time() - t, 1)
        r["pg_table_with_index_mb"] = int(psql(f"SELECT pg_total_relation_size('{pg_tab}')/1048576;"))
        r["pg_trgm"] = {"common_sorted": pg_latency(lambda w: sub(like(w, True)), COMMON, reps),
                        "rare": pg_latency(lambda w: sub(like(w, False)), RARE, 200),
                        "prefix": pg_latency(lambda w: sub(f"SELECT name FROM p{{n}} WHERE status='ON_SALE' AND name LIKE '{w}%' LIMIT 10"), prefixes, 200),
                        "concurrent": pg_concurrent(sub(like("비타민", True)))}
        r["pg_trgm_plan"] = {"common": psql("EXPLAIN " + sub(like("비타민", True)) + ";").splitlines()[:8],
                             "rare": psql("EXPLAIN " + sub(like("아스타잔틴", False)) + ";").splitlines()[:6]}
        r["mem_after"] = mem()
        r["es_heap"] = e.req("GET", "/_nodes/stats/jvm")["nodes"].popitem()[1]["jvm"]["mem"]["heap_used_in_bytes"] // 1048576
        if n == SIZES[-1] or n == 100_000:
            du = e.req("POST", f"/{idx}/_disk_usage?run_expensive_tasks=true")[idx]["fields"]
            r["es_fields_mb"] = {k: round(v["total_in_bytes"] / 1048576, 1) for k, v in du.items() if v["total_in_bytes"] > 1048576 * 0.1}
        save(res)
        print(n, "done", json.dumps(r, ensure_ascii=False)[:300], flush=True)


if __name__ == "__main__":
    try:
        main()
    finally:
        subprocess.run(["docker", "rm", "-f", "es-bench", "pg-bench"], capture_output=True)
        print("cleaned", flush=True)
```

</details>

<details>
<summary><code>check.py</code>: 5-2 규격 쿼리 재측정과 한 건씩 쓰기</summary>

```python
import json, multiprocessing as mp, subprocess, sys, time, uuid
sys.path.insert(0, ".")
import bench as b
def body(q):
    x = b.search_body(q); x.pop("track_total_hits"); return x
def worker(_):
    e, out, end = b.Es(), [], time.time() + 15
    while time.time() < end:
        t = time.perf_counter(); e.req("POST", "/d/_search", body("비타민")); out.append((time.perf_counter() - t) * 1000)
    return out
if __name__ == "__main__":
    try:
        b.run("docker","run","--rm","-d","--name","es-bench","--cpus=2","--memory=2g","-p",f"127.0.0.1:{b.ES_PORT}:9200","-e","discovery.type=single-node","-e","xpack.security.enabled=false","-e","ES_JAVA_OPTS=-Xms512m -Xmx512m","myroutine-elasticsearch:9.4.8")
        for _ in range(120):
            try:
                b.Es().req("GET","/_cluster/health?wait_for_status=yellow&timeout=1s"); break
            except Exception: time.sleep(1)
        time.sleep(10)
        e = b.Es(); e.req("PUT", "/d", b.INDEX_BODY); buf = []
        for d in b.docs(1_000_000):
            buf.append(json.dumps({"index":{"_id":d["productId"]}})+"\n"+json.dumps(d,ensure_ascii=False)+"\n")
            if len(buf)==2000: e.req("POST","/d/_bulk","".join(buf),ndjson=True); buf.clear()
        e.req("POST","/d/_refresh"); time.sleep(5)
        print("default common", b.es_latency("d", b.COMMON, body, 300), flush=True)
        print("false   common", b.es_latency("d", b.COMMON, b.search_body, 300), flush=True)
        print("default two", b.es_latency("d", ["비타민 크림","콜라겐 젤리","유산균 캡슐","오메가 정","루테인 스틱"], body, 300), flush=True)
        print("default rare", b.es_latency("d", b.RARE, body, 300), flush=True)
        with mp.Pool(20) as p:
            a=[x for l in p.map(worker, range(20)) for x in l]
        print("default concurrent tps", round(len(a)/15), b.stats(a), flush=True)
        ds=[d for _,d in zip(range(3000), b.docs(3000))]
        t=time.time()
        for i,d in enumerate(ds):
            e.req("PUT", f"/d/_doc/{uuid.uuid4()}?version=1&version_type=external", d)
        print("single PUT external docs/s", round(len(ds)/(time.time()-t)), flush=True)
    finally:
        subprocess.run(["docker","rm","-f","es-bench"],capture_output=True); print("cleaned")
```

</details>

<details>
<summary>Postgres 전문 검색 확인</summary>

```bash
docker run --rm -d --name pg-fts-check -e POSTGRES_HOST_AUTH_METHOD=trust pgvector/pgvector:pg17
docker exec pg-fts-check psql -U postgres -At \
  -c "SELECT string_agg(cfgname,',') FROM pg_ts_config" \
  -c "SELECT count(*), coalesce(string_agg(name,','),'-') FROM pg_available_extensions WHERE name ~* 'groonga|bigm|textsearch_ko|mecab|korean|rum|zhparser'" \
  -c "SELECT to_tsvector('simple','멀티비타민 정')" \
  -c "SELECT to_tsvector('simple','멀티비타민 정') @@ plainto_tsquery('simple','비타민') AS 부분단어, to_tsvector('simple','비타민 정') @@ plainto_tsquery('simple','비타민') AS 공백단어"
docker rm -f pg-fts-check
```

결과: 설정 목록에 한국어가 없고, 찾은 확장은 0개, `'멀티비타민':1 '정':2`, 부분단어 `f`, 공백단어 `t`.

</details>
