import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 100);
const AUTH = __ENV.AUTH === 'true';
const JSON_HEADERS = { 'Content-Type': 'application/json' };
const productIds = new SharedArray('productIds', () => JSON.parse(open('./data/product-ids.json')));

export const options = {
  setupTimeout: '5m',
  scenarios: {
    read: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.RAMP || '1m', target: VUS },
        { duration: __ENV.HOLD || '5m', target: VUS },
      ],
    },
  },
  thresholds: {
    'http_req_failed{name:detail}': ['rate<0.001'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

function login(i) {
  return ['POST', `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: `k6-co-${i}@seed.test`, password: 'Seed1234!' }),
    { headers: JSON_HEADERS, tags: { name: 'setup' } }];
}

export function setup() {
  if (!AUTH) {
    return { tokens: [] };
  }
  const tokens = [];
  for (let start = 1; start <= VUS; start += 50) {
    const logins = http.batch(Array.from({ length: Math.min(50, VUS - start + 1) }, (_, k) => login(start + k)));
    logins.forEach((r, k) => {
      if (r.status !== 200) {
        throw new Error(`login k6-co-${start + k} failed: ${r.status}`);
      }
      tokens.push(r.json('accessToken'));
    });
  }
  const me = http.batch(tokens.map((t) => ['GET', `${BASE_URL}/api/members/me`, null,
    { headers: { Authorization: `Bearer ${t}` }, tags: { name: 'setup' } }]));
  me.forEach((r, i) => {
    if (r.status !== 200) {
      throw new Error(`token ${i + 1} not authenticated: ${r.status}`);
    }
  });
  return { tokens };
}

export default function (data) {
  const id = productIds[Math.floor(Math.random() * productIds.length)];
  const params = { tags: { name: 'detail' } };
  if (AUTH) {
    params.headers = { Authorization: `Bearer ${data.tokens[__VU - 1]}` };
  }
  const res = http.get(`${BASE_URL}/api/products/${id}`, params);
  check(res, { 'detail 200': (r) => r.status === 200 });
}
