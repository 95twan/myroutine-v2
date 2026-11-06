-- 회원·배송지·가게. 변수: n_member, n_shop, password_hash(시드 회원 로그인 비밀번호 Seed1234!의 BCrypt 해시)
\timing on
set synchronous_commit = off;

-- 회원 번호 → id 대응표. 다른 엔티티는 번호로 이 표를 조인해 id를 얻는다.
create unlogged table seed.mm as
select n, ts, seed.uuid_v7(ts) id
from (select n, now() - random() * interval '730 days' ts from generate_series(1, :n_member) n) t;
alter table seed.mm add primary key (n);

-- 회원 1~n_shop이 가게 주인이다.
insert into member.member(id, email, nickname, name, phone, password_hash, status, role, version, created_at, updated_at)
select id, 'u' || n || '@seed.test', 'u' || n, '회원' || n, '010' || lpad(n::text, 8, '0'), :'password_hash', 'ACTIVE',
       case when n <= :n_shop then 'SELLER' else 'USER' end, 0, ts, ts
from seed.mm;

insert into member.member_address(id, member_id, recipient, phone, zipcode, address1, address2, is_default, version, created_at, updated_at)
select seed.uuid_v7(ts), id, '회원' || n, '010' || lpad(n::text, 8, '0'), '06236', '서울시 강남구 테헤란로 ' || (n % 500), (n % 30) || '층', true, 0, ts, ts
from seed.mm;

create unlogged table seed.sm as
select n, ts, seed.uuid_v7(ts) id, id member_id from seed.mm where n <= :n_shop;
alter table seed.sm add primary key (n);

insert into shop.shop(id, member_id, name, business_number, email, phone, address, status, version, created_at, updated_at)
select id, member_id, '가게' || n, lpad(n::text, 10, '0'), 's' || n || '@seed.test', '02' || lpad(n::text, 8, '0'), '서울시 중구 ' || n, 'ACTIVE', 0, ts, ts
from seed.sm;
