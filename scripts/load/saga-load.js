// Открытая модель нагрузки: заказы подаются с постоянной частотой RATE в секунду,
// независимо от того, успевает ли система. Конец саги k6 не ждёт — его считают по базе заказов.
//
// Переменные окружения:
//   BASE_URL   http://gateway:8081
//   RATE       заказов в секунду
//   DURATION   длительность ступени, например 60s
//   SCENARIO   happy | mixed | hot
//   ITEM_FROM, ITEM_TO  диапазон id товаров (по умолчанию 1001–1100)
//   FAIL_SHARE доля заказов с отказом оплаты для mixed (по умолчанию 0.2)
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://gateway:8081';
const RATE = parseInt(__ENV.RATE || '20', 10);
const DURATION = __ENV.DURATION || '60s';
const SCENARIO = __ENV.SCENARIO || 'happy';
const ITEM_FROM = parseInt(__ENV.ITEM_FROM || '1001', 10);
const ITEM_TO = parseInt(__ENV.ITEM_TO || '1100', 10);
const FAIL_SHARE = parseFloat(__ENV.FAIL_SHARE || '0.2');
const FAIL_USER = 9999;

export const options = {
  discardResponseBodies: true,
  scenarios: {
    orders: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.max(20, RATE),
      maxVUs: Math.max(200, RATE * 4),
    },
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

function pickItem() {
  if (SCENARIO === 'hot') return ITEM_FROM;
  return ITEM_FROM + Math.floor(Math.random() * (ITEM_TO - ITEM_FROM + 1));
}

function pickUser() {
  if (SCENARIO === 'mixed' && Math.random() < FAIL_SHARE) return FAIL_USER;
  return 101 + Math.floor(Math.random() * 1000);
}

export default function () {
  const body = JSON.stringify({
    userId: pickUser(),
    orderItems: [{ itemId: pickItem(), amount: 1, cost: 100.0 }],
    cost: 100.0,
  });
  const res = http.post(`${BASE_URL}/order`, body, {
    headers: { 'Content-Type': 'application/json' },
    timeout: '10s',
  });
  check(res, { 'POST 2xx': (r) => r.status >= 200 && r.status < 300 });
}
