-- 주문·가게주문·품목·결제. 변수: n_member, n_shop, n_product, n_order
-- 스케줄 잡이 처리하는 상태(결제 대기·결제 중 주문, 배송완료 가게주문)는 넣지 않는다.
\timing on
set synchronous_commit = off;

-- 주문 골격: PAID 85%, CANCELLED·PAYMENT_FAILED·EXPIRED 각 5%
create unlogged table seed.om as
select n, ts, seed.uuid_v7(ts) id, 1 + (n * 2654435761 % :n_member) member_n,
       case n % 20 when 0 then 'CANCELLED' when 1 then 'PAYMENT_FAILED' when 2 then 'EXPIRED' else 'PAID' end status
from (select n, now() - interval '1 hour' - random() * interval '730 days' ts from generate_series(1, :n_order) n) t;
alter table seed.om add primary key (n);

-- 가게주문 골격: 주문당 1~2개. j가 달라도 7919 % n_shop ≠ 0이면 가게가 겹치지 않는다(UK(order_id, shop_id)).
-- PAID 주문은 최근 10일이면 PAID, 아니면 PAID 15%·SHIPPED 5%·COMPLETED 80%
create unlogged table seed.som as
select o.n order_n, j, seed.uuid_v7(o.ts) id, (o.n * 31 + j * 7919) % :n_shop + 1 shop_n,
       case when o.status <> 'PAID' then 'CANCELLED'
            when o.ts > now() - interval '10 days' then 'PAID'
            when (o.n + j) % 20 < 3 then 'PAID'
            when (o.n + j) % 20 = 3 then 'SHIPPED'
            else 'COMPLETED' end status,
       o.ts
from seed.om o cross join lateral generate_series(1, 1 + o.n % 2) j;

-- 품목 골격: 가게주문당 1~2개(평균 5/3). 상품은 그 가게의 상품(shop_n + n_shop × t)에서 고른다.
create unlogged table seed.olm as
select so.order_n, so.id shop_order_id, so.status so_status, so.ts, l,
       so.shop_n + :n_shop * ((so.order_n * 13 + l * 101) % (:n_product / :n_shop)) product_n,
       1 + (so.order_n + l) % 3 quantity
from seed.som so cross join lateral generate_series(1, case (so.order_n + so.j) % 3 when 0 then 1 else 2 end) l;

-- FK 때문에 orders를 품목보다 먼저 넣어야 해서 합계를 미리 구한다.
create unlogged table seed.ototal as
select l.order_n, sum(p.price * l.quantity) total from seed.olm l join seed.pm p on p.n = l.product_n group by l.order_n;
alter table seed.ototal add primary key (order_n);

insert into orders.orders(id, member_id, type, status, total_amount, shipping_address, expires_at, paid_at, failure_reason, version, created_at, updated_at)
select o.id, m.id, 'NORMAL', o.status, t.total,
       jsonb_build_object('recipient', '회원' || m.n, 'phone', '010' || lpad(m.n::text, 8, '0'), 'zipcode', '06236', 'address1', '서울시 강남구 테헤란로', 'address2', null),
       o.ts + interval '15 minutes', case when o.status = 'PAID' then o.ts + interval '1 minute' end,
       case when o.status = 'PAYMENT_FAILED' then 'REJECT_CARD_PAYMENT' end, 0, o.ts, o.ts
from seed.om o join seed.mm m on m.n = o.member_n join seed.ototal t on t.order_n = o.n;

insert into orders.shop_order(id, order_id, shop_id, shop_name, status, shipped_at, delivered_at, completed_at, version, created_at, updated_at)
select so.id, o.id, s.id, '가게' || s.n, so.status,
       case when so.status in ('SHIPPED', 'COMPLETED') then so.ts + interval '2 days' end,
       case when so.status = 'COMPLETED' then so.ts + interval '3 days' end,
       case when so.status = 'COMPLETED' then so.ts + interval '10 days' end, 0, so.ts, so.ts
from seed.som so join seed.om o on o.n = so.order_n join seed.sm s on s.n = so.shop_n;

insert into orders.order_line(id, shop_order_id, product_id, product_name, unit_price, quantity, line_amount, status, confirmed_at, version, created_at, updated_at)
select seed.uuid_v7(l.ts), l.shop_order_id, p.id, p.name, p.price, l.quantity, p.price * l.quantity,
       case l.so_status when 'COMPLETED' then 'CONFIRMED' else l.so_status end,
       case when l.so_status = 'COMPLETED' then l.ts + interval '10 days' end, 0, l.ts, l.ts
from seed.olm l join seed.pm p on p.n = l.product_n;

-- 취소·만료 주문은 결제 행이 없다.
insert into payment.payment(id, order_id, member_id, amount, pg_order_id, payment_key, method, status, failure_code, approved_at, version, created_at, updated_at)
select seed.uuid_v7(o.ts), o.id, m.id, t.total, 'seed-' || o.n, 'seed-pk-' || o.n,
       case when o.status = 'PAID' then '카드' end,
       case when o.status = 'PAID' then 'APPROVED' else 'FAILED' end,
       case when o.status = 'PAYMENT_FAILED' then 'REJECT_CARD_PAYMENT' end,
       case when o.status = 'PAID' then o.ts + interval '1 minute' end, 0, o.ts, o.ts
from seed.om o join seed.mm m on m.n = o.member_n join seed.ototal t on t.order_n = o.n
where o.status in ('PAID', 'PAYMENT_FAILED');
