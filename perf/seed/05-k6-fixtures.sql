-- k6 전용 데이터. 변수: password_hash(시드 회원 로그인 비밀번호 Seed1234!의 BCrypt 해시)
-- S-02: 회원 k6-co-{1..100}이 각자 다른 상품 'k6 체크아웃 상품 {i}'(재고 10만) 1개를 장바구니에 둔다.
-- S-03: 회원 k6-hot-{1..1000}이 모두 'k6 인기 상품'(재고 100) 1개를 장바구니에 둔다.
\timing on
set synchronous_commit = off;

create unlogged table seed.k6p as
select seed.uuid_v7(now()) id, s.id shop_id, x.name, x.stock
from (select i shop_n, 'k6 체크아웃 상품 ' || i name, 100000 stock from generate_series(1, 100) i
      union all select 1, 'k6 인기 상품', 100) x
join seed.sm s on s.n = x.shop_n;

insert into product.product(id, shop_id, name, description, category, price, status, subscribable, version, created_at, updated_at)
select id, shop_id, name, name, 'FOOD', 10000, 'ON_SALE', false, 0, now(), now() from seed.k6p;

insert into product.stock(product_id, available, reserved, sold, received, created_at, updated_at)
select id, stock, 0, 0, stock, now(), now() from seed.k6p;

insert into product.stock_movement(id, product_id, type, quantity, ref_type, ref_id, created_at)
select seed.uuid_v7(now()), id, 'RECEIVE', stock, 'PRODUCT_REGISTER', id, now() from seed.k6p;

create unlogged table seed.k6m as
select seed.uuid_v7(now()) id, 'k6-co-' || i email_local, 'k6 체크아웃 상품 ' || i product_name from generate_series(1, 100) i
union all
select seed.uuid_v7(now()), 'k6-hot-' || i, 'k6 인기 상품' from generate_series(1, 1000) i;

insert into member.member(id, email, nickname, name, password_hash, status, role, version, created_at, updated_at)
select id, email_local || '@seed.test', email_local, email_local, :'password_hash', 'ACTIVE', 'USER', 0, now(), now() from seed.k6m;

insert into member.member_address(id, member_id, recipient, phone, zipcode, address1, is_default, version, created_at, updated_at)
select seed.uuid_v7(now()), id, email_local, '01000000000', '06236', '서울시 강남구', true, 0, now(), now() from seed.k6m;

insert into orders.cart(id, member_id, created_at, updated_at)
select seed.uuid_v7(now()), id, now(), now() from seed.k6m;

insert into orders.cart_item(id, cart_id, product_id, quantity, version, created_at, updated_at)
select seed.uuid_v7(now()), c.id, p.id, 1, 0, now(), now()
from seed.k6m m join orders.cart c on c.member_id = m.id join seed.k6p p on p.name = m.product_name;

analyze;
