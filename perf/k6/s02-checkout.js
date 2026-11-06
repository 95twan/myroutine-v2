import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 100);
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  setupTimeout: '2m',
  scenarios: {
    checkout: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.RAMP || '1m', target: VUS },
        { duration: __ENV.HOLD || '5m', target: VUS },
      ],
    },
  },
  thresholds: {
    'http_req_duration{name:checkout}': ['p(95)<500'],
    'http_req_failed{name:checkout}': ['rate<0.001'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const buyers = [];
  for (let i = 1; i <= VUS; i++) {
    const login = http.post(`${BASE_URL}/api/auth/login`,
      JSON.stringify({ email: `k6-co-${i}@seed.test`, password: 'Seed1234!' }),
      { headers: JSON_HEADERS, tags: { name: 'setup' } });
    const auth = { Authorization: `Bearer ${login.json('accessToken')}` };
    const cart = http.get(`${BASE_URL}/api/cart`, { headers: auth, tags: { name: 'setup' } });
    const addresses = http.get(`${BASE_URL}/api/members/me/addresses`, { headers: auth, tags: { name: 'setup' } });
    buyers.push({
      token: login.json('accessToken'),
      cartItemId: cart.json('items.0.cartItemId'),
      addressId: addresses.json('0.id'),
    });
  }
  return { buyers };
}

export default function (data) {
  const buyer = data.buyers[(__VU - 1) % data.buyers.length];
  const res = http.post(`${BASE_URL}/api/orders/checkout`,
    JSON.stringify({ cartItemIds: [buyer.cartItemId], addressId: buyer.addressId }),
    {
      headers: { ...JSON_HEADERS, Authorization: `Bearer ${buyer.token}`, 'Idempotency-Key': crypto.randomUUID() },
      tags: { name: 'checkout' },
    });
  check(res, { 'checkout 201': (r) => r.status === 201 });
}
