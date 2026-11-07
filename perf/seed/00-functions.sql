-- 시드 작업 공간과 UUIDv7 함수. 09-cleanup.sql이 스키마째 지운다.
\timing on
create schema if not exists seed;

-- gen_random_uuid()(v4)의 앞 48비트를 ts의 밀리초로 덮고, 버전 비트 52·53을 켜서 0100(4)을 0111(7)로 만든다.
-- Postgres 17에는 uuidv7()이 없다. 앱의 UUIDv7과 같은 인덱스 지역성을 내려고 쓴다.
create or replace function seed.uuid_v7(ts timestamptz) returns uuid language sql volatile as $$
  select encode(set_bit(set_bit(overlay(uuid_send(gen_random_uuid())
         placing substring(int8send((extract(epoch from ts) * 1000)::bigint) from 3) from 1 for 6), 52, 1), 53, 1), 'hex')::uuid
$$;
