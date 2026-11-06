import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const BUYERS = Number(__ENV.BUYERS || 1000);
const JSON_HEADERS = { 'Content-Type': 'application/json' };
const succeeded = new Counter('checkout_succeeded');
const outOfStock = new Counter('checkout_out_of_stock');

http.setResponseCallback(http.expectedStatuses(200, 201, 422));

export const options = {
  setupTimeout: '10m',
  scenarios: {
    rush: { executor: 'per-vu-iterations', vus: BUYERS, iterations: 1, maxDuration: '2m' },
  },
  thresholds: {
    checkout_succeeded: ['count==100'],
    'http_req_duration{name:checkout}': [],
    'http_req_failed{name:checkout}': ['rate==0'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

function login(i) {
  return ['POST', `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: `k6-hot-${i}@seed.test`, password: 'Seed1234!' }),
    { headers: JSON_HEADERS, tags: { name: 'setup' } }];
}

export function setup() {
  const buyers = [];
  for (let start = 1; start <= BUYERS; start += 50) {
    const logins = http.batch(Array.from({ length: Math.min(50, BUYERS - start + 1) }, (_, k) => login(start + k)));
    const tokens = logins.map((r) => r.json('accessToken'));
    const carts = http.batch(tokens.map((t) => ['GET', `${BASE_URL}/api/cart`, null, { headers: { Authorization: `Bearer ${t}` }, tags: { name: 'setup' } }]));
    const addresses = http.batch(tokens.map((t) => ['GET', `${BASE_URL}/api/members/me/addresses`, null, { headers: { Authorization: `Bearer ${t}` }, tags: { name: 'setup' } }]));
    tokens.forEach((token, k) => buyers.push({
      token,
      cartItemId: carts[k].json('items.0.cartItemId'),
      addressId: addresses[k].json('0.id'),
    }));
  }
  return { buyers };
}

export default function (data) {
  const buyer = data.buyers[__VU - 1];
  const res = http.post(`${BASE_URL}/api/orders/checkout`,
    JSON.stringify({ cartItemIds: [buyer.cartItemId], addressId: buyer.addressId }),
    {
      headers: { ...JSON_HEADERS, Authorization: `Bearer ${buyer.token}`, 'Idempotency-Key': crypto.randomUUID() },
      tags: { name: 'checkout' },
    });
  if (res.status !== 201 && res.status !== 422) console.log(`status=${res.status} error=${res.error} code=${res.error_code}`);
  if (res.status === 201) succeeded.add(1);
  if (res.status === 422 && res.json('code') === 'OUT_OF_STOCK') outOfStock.add(1);
  check(res, { '201 or OUT_OF_STOCK': (r) => r.status === 201 || (r.status === 422 && r.json('code') === 'OUT_OF_STOCK') });
}
