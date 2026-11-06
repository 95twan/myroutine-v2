-- 정산 대상 품목과 지난 달들의 정산. 변수 없음
\timing on
set synchronous_commit = off;

-- 구매확정 품목마다 1건. 없으면 InvariantChecker의 정산 누락 검사에 걸린다.
insert into settlement.settlement_item(id, order_line_id, shop_id, order_id, product_id, amount, confirmed_at, created_at, updated_at, shop_order_id, product_name, quantity)
select seed.uuid_v7(ol.confirmed_at), ol.id, so.shop_id, so.order_id, ol.product_id, ol.line_amount, ol.confirmed_at, ol.confirmed_at, ol.confirmed_at, so.id, ol.product_name, ol.quantity
from orders.order_line ol join orders.shop_order so on so.id = ol.shop_order_id
where ol.status = 'CONFIRMED';

-- 지난달보다 앞선 달은 정산을 끝낸 상태(PAID)로 둔다. 지난달과 이번 달은 정산 전이다(S-05가 지난달을 정산한다).
-- 달 경계는 정산 잡과 같이 Asia/Seoul 기준이다.
create unlogged table seed.stl as
select seed.uuid_v7(period_start::timestamp at time zone 'Asia/Seoul' + interval '1 month 4 days') id, shop_id, period_start, gross, cnt
from (select shop_id, (date_trunc('month', confirmed_at at time zone 'Asia/Seoul'))::date period_start, sum(amount) gross, count(*) cnt
      from settlement.settlement_item
      where confirmed_at < (date_trunc('month', now() at time zone 'Asia/Seoul') - interval '1 month') at time zone 'Asia/Seoul'
      group by 1, 2) t;

insert into settlement.settlement(id, shop_id, period_start, period_end, gross_amount, fee_amount, net_amount, fee_rate_bp, item_count, status, payout_attempts, paid_at, version, created_at, updated_at)
select id, shop_id, period_start, (period_start + interval '1 month - 1 day')::date, gross, gross * 500 / 10000, gross - gross * 500 / 10000, 500, cnt, 'PAID', 1,
       period_start::timestamp at time zone 'Asia/Seoul' + interval '1 month 4 days', 0,
       period_start::timestamp at time zone 'Asia/Seoul' + interval '1 month 4 days',
       period_start::timestamp at time zone 'Asia/Seoul' + interval '1 month 4 days'
from seed.stl;

update settlement.settlement_item i set settlement_id = s.id, updated_at = s.created_at
from settlement.settlement s
where s.shop_id = i.shop_id
  and i.confirmed_at >= s.period_start::timestamp at time zone 'Asia/Seoul'
  and i.confirmed_at < (s.period_start + interval '1 month')::timestamp at time zone 'Asia/Seoul';
