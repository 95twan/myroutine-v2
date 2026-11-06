import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 300);
const MAX_PAGES = Number(__ENV.MAX_PAGES || 10);
const CATEGORIES = ['FOOD', 'HEALTH', 'BEAUTY', 'LIVING', 'PET', 'ETC'];
const productIds = new SharedArray('productIds', () => JSON.parse(open('./data/product-ids.json')));

export const options = {
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
    http_req_duration: ['p(95)<200'],
    http_req_failed: ['rate<0.001'],
    'http_req_duration{name:list}': ['p(95)<200'],
    'http_req_duration{name:detail}': ['p(95)<200'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

let cursor = null;
let category = null;
let page = 0;

export default function () {
  if (Math.random() < 0.3) {
    if (cursor === null || page === MAX_PAGES) {
      category = CATEGORIES[Math.floor(Math.random() * CATEGORIES.length)];
      cursor = null;
      page = 0;
    }
    const query = `category=${category}&size=20` + (cursor ? `&cursor=${encodeURIComponent(cursor)}` : '');
    const res = http.get(`${BASE_URL}/api/products?${query}`, { tags: { name: 'list' } });
    check(res, { 'list 200': (r) => r.status === 200 });
    cursor = res.status === 200 ? res.json('nextCursor') : null;
    page += 1;
  } else {
    const id = productIds[Math.floor(Math.random() * productIds.length)];
    const res = http.get(`${BASE_URL}/api/products/${id}`, { tags: { name: 'detail' } });
    check(res, { 'detail 200': (r) => r.status === 200 });
  }
}
