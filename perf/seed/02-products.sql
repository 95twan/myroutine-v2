-- 상품·재고·입고 이력. 변수: n_shop, n_product
\timing on
set synchronous_commit = off;

-- 상품 n의 가게는 (n - 1) % n_shop + 1이고, 가게 안 순번은 (n - 1) / n_shop이다.
-- 상태를 n % 20으로 정하면 가게 수가 20의 배수일 때 한 가게 상품이 모두 같은 상태가 되므로 가게 안 순번으로 정한다.
create unlogged table seed.pm as
select n, ts, seed.uuid_v7(ts) id, (n - 1) % :n_shop + 1 shop_n,
       case ((n - 1) / :n_shop) % 20 when 0 then 'HIDDEN' when 1 then 'DISCONTINUED' else 'ON_SALE' end status,
       (array['유기농','프리미엄','데일리','저당','고단백','무첨가','순수','촉촉한','대용량','미니'])[n % 10 + 1] || ' ' ||
       (array['프로틴','비타민','샴푸','사료','커피','견과','유산균','마스크팩','세제','오메가3'])[(n / 10) % 10 + 1] || ' ' || n name,
       (1 + (n * 7919) % 99) * 1000::bigint price
from (select n, now() - random() * interval '730 days' ts from generate_series(1, :n_product) n) t;
alter table seed.pm add primary key (n);

insert into product.product(id, shop_id, name, description, category, price, status, subscribable, version, created_at, updated_at)
select p.id, s.id, p.name, p.name || ' 상세 설명', (array['FOOD','HEALTH','BEAUTY','LIVING','PET','ETC'])[p.n % 6 + 1], p.price,
       p.status, false, 0, p.ts, p.ts
from seed.pm p join seed.sm s on s.n = p.shop_n;

-- available + reserved + sold = received, 입고 이력 합 = received
insert into product.stock(product_id, available, reserved, sold, received, created_at, updated_at)
select id, 1000 - n % 500, 0, n % 500, 1000, ts, ts from seed.pm;

insert into product.stock_movement(id, product_id, type, quantity, ref_type, ref_id, created_at)
select seed.uuid_v7(ts), id, 'RECEIVE', 1000, 'PRODUCT_REGISTER', id, ts from seed.pm;
