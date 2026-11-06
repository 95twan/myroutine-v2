import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JSON_HEADERS = { 'Content-Type': 'application/json' };
const products = new SharedArray('sellerProducts', () => JSON.parse(open('./data/seller-products.json')));

export const options = {
  scenarios: {
    update: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 20),
      timeUnit: '1s',
      duration: __ENV.DURATION || '5m',
      preAllocatedVUs: 20,
      maxVUs: 50,
    },
  },
  thresholds: {
    'http_req_duration{name:update}': [],
    'http_req_failed{name:update}': ['rate<0.001'],
    dropped_iterations: ['count==0'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const login = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: __ENV.SELLER_EMAIL || 'u1@seed.test', password: 'Seed1234!' }),
    { headers: JSON_HEADERS, tags: { name: 'setup' } });
  return { token: login.json('accessToken'), startedAt: new Date().toISOString() };
}

export default function (data) {
  const p = products[Math.floor(Math.random() * products.length)];
  const res = http.patch(`${BASE_URL}/api/shops/${p.shopId}/products/${p.productId}`,
    JSON.stringify({ price: 1000 * (1 + Math.floor(Math.random() * 99)) }),
    { headers: { ...JSON_HEADERS, Authorization: `Bearer ${data.token}` }, tags: { name: 'update' } });
  check(res, { 'update 200': (r) => r.status === 200 });
}

export function teardown(data) {
  console.log(`started_at=${data.startedAt} finished_at=${new Date().toISOString()}`);
}
