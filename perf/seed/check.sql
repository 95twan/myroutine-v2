-- 시드 전후 확인. 시드 루프(0*.sql)에 들어가지 않는다.
-- 불변식은 src/test/java/com/myroutine/support/InvariantChecker.java의 쿼리를 옮긴 것이다. 위반 건수가 모두 0이어야 한다.
\pset footer off

-- 시드 전에만 본다: 시드의 닉네임(u1~u1000000)·이메일(@seed.test)·사업자번호(0000000001~0000010000)와 겹치는 기존 행.
-- 0이 아니면 시드가 유일 제약 오류로 멈춘다.
select 'nickname·email 충돌' as conflict, count(*) from member.member where nickname ~ '^u[0-9]+$' or email like '%@seed.test'
union all
select 'business_number 충돌', count(*) from shop.shop where business_number ~ '^0{5}[0-9]{5}$';

select '주문 총액이 품목 금액 합과 다른 주문' as invariant, count(*) as violations from (
SELECT o.id
FROM orders.orders o
JOIN orders.shop_order so ON so.order_id = o.id
JOIN orders.order_line ol ON ol.shop_order_id = so.id
GROUP BY o.id, o.total_amount
HAVING o.total_amount <> SUM(ol.line_amount)
) v
union all
select '결제 금액이 총액과 다른 PAID 주문' as invariant, count(*) as violations from (
SELECT o.id
FROM orders.orders o
JOIN payment.payment p ON p.order_id = o.id
WHERE o.status = 'PAID' AND p.amount <> o.total_amount
) v
union all
select '승인된 결제가 반영되지 않은 주문' as invariant, count(*) as violations from (
SELECT o.id
FROM payment.payment p
JOIN orders.orders o ON o.id = p.order_id
WHERE p.status IN ('APPROVED', 'PARTIAL_CANCELLED', 'CANCELLED')
  AND o.status <> 'PAID'
  AND NOT (p.status = 'CANCELLED' AND o.status = 'PAYMENT_FAILED')
) v
union all
select '재고 등식이 깨진 상품' as invariant, count(*) as violations from (
SELECT s.product_id
FROM product.stock s
LEFT JOIN (SELECT product_id, SUM(quantity) q FROM product.stock_movement WHERE type IN ('RECEIVE', 'ADJUST') GROUP BY product_id) m ON m.product_id = s.product_id
LEFT JOIN (SELECT product_id, SUM(quantity) q FROM product.stock_reservation WHERE status = 'HELD' GROUP BY product_id) r ON r.product_id = s.product_id
WHERE s.available + s.reserved + s.sold <> s.received
   OR s.available < 0 OR s.reserved < 0 OR s.sold < 0
   OR s.received <> COALESCE(m.q, 0)
   OR s.reserved <> COALESCE(r.q, 0)
) v
union all
select '환불액이 품목 금액을 넘은 품목' as invariant, count(*) as violations from (
SELECT id FROM orders.order_line WHERE refunded_amount > line_amount
) v
union all
select '취소액이 승인액을 넘은 결제의 주문' as invariant, count(*) as violations from (
SELECT order_id FROM payment.payment WHERE cancelled_amount > amount
) v
union all
select '완료된 환불 합이 총액을 넘은 주문' as invariant, count(*) as violations from (
SELECT o.id
FROM orders.orders o
JOIN orders.refund r ON r.order_id = o.id
WHERE r.status = 'COMPLETED'
GROUP BY o.id, o.total_amount
HAVING SUM(r.amount) > o.total_amount
) v
union all
select '정산 항목이 없는 구매확정 품목' as invariant, count(*) as violations from (
SELECT ol.id
FROM orders.order_line ol
LEFT JOIN settlement.settlement_item si ON si.order_line_id = ol.id
WHERE ol.status = 'CONFIRMED' AND si.id IS NULL
) v;

-- 스케줄 잡이 처리할 주문. 시드는 0건이므로 시드 뒤 값은 시드 전 값과 같아야 한다.
select 'PENDING_PAYMENT·PAYMENT_IN_PROGRESS 주문' as job_target, count(*) from orders.orders where status in ('PENDING_PAYMENT', 'PAYMENT_IN_PROGRESS')
union all
select 'DELIVERED 가게주문', count(*) from orders.shop_order where status = 'DELIVERED';

-- 테이블별 건수. 시드 전 값을 리포트에 적는다.
select 'member.member' as table_name, count(*) from member.member
union all select 'shop.shop', count(*) from shop.shop
union all select 'product.product', count(*) from product.product
union all select 'orders.orders', count(*) from orders.orders
union all select 'orders.shop_order', count(*) from orders.shop_order
union all select 'orders.order_line', count(*) from orders.order_line
union all select 'payment.payment', count(*) from payment.payment
union all select 'settlement.settlement_item', count(*) from settlement.settlement_item
union all select 'settlement.settlement', count(*) from settlement.settlement;

select pg_size_pretty(pg_database_size(current_database())) as db_size;
