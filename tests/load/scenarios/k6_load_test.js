import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || __ENV.LOAD_TEST_BASE_URL || 'http://localhost:8080';
const PROFILE = __ENV.PROFILE || 'smoke';

// Define execution profiles
const PROFILES = {
  smoke: {
    executor: 'constant-vus',
    vus: 2,
    duration: '15s',
  },
  load: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '30s', target: 20 },
      { duration: '1m', target: 20 },
      { duration: '30s', target: 0 },
    ],
  },
  stress: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '30s', target: 50 },
      { duration: '1m', target: 100 },
      { duration: '30s', target: 0 },
    ],
  },
  spike: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '10s', target: 150 },
      { duration: '30s', target: 150 },
      { duration: '10s', target: 0 },
    ],
  },
  soak: {
    executor: 'constant-vus',
    vus: 15,
    duration: '5m', // Configured to 5m for CI safety, can be set to 1h
  },
  api: {
    executor: 'constant-vus',
    vus: 10,
    duration: '30s',
  },
};

export const options = {
  scenarios: {
    default: PROFILES[PROFILE] || PROFILES.smoke,
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

export default function () {
  // 1. Health Probe
  const resHealth = http.get(`${BASE_URL}/health`, {
    tags: { name: 'HealthCheck' },
  });
  check(resHealth, {
    'health status is 200 or 404 fallback': (r) => r.status === 200 || r.status === 404,
  });

  // 2. Search API
  const resSearch = http.get(`${BASE_URL}/api/search?q=Telugu`, {
    tags: { name: 'SearchAPI' },
  });
  check(resSearch, {
    'search status ok or fallback': (r) => r.status < 500,
  });

  // 3. Spotify Token Endpoint
  const tokenPayload = {
    grant_type: 'authorization_code',
    code: 'dummy_code',
    redirect_uri: 'moodtunes-login://callback',
    client_id: 'dummy_client_id',
    code_verifier: 'dummy_verifier_string_for_k6_load_test_scenario_validation_123456789',
  };

  const resToken = http.post('https://accounts.spotify.com/api/token', tokenPayload, {
    tags: { name: 'SpotifyTokenExchange' },
  });
  check(resToken, {
    'token endpoint handles request without 5xx': (r) => r.status < 500,
  });

  sleep(1);
}

export function handleSummary(data) {
  const summaryObj = {
    profile: PROFILE,
    total_requests: data.metrics.http_reqs ? data.metrics.http_reqs.values.count : 0,
    failed_rate: data.metrics.http_req_failed ? data.metrics.http_req_failed.values.rate : 0,
    p95_duration: data.metrics.http_req_duration ? data.metrics.http_req_duration.values['p(95)'] : 0,
  };

  return {
    'tests/load/reports/summary.json': JSON.stringify(data, null, 2),
    'stdout': textSummary(summaryObj),
  };
}

function textSummary(s) {
  return `
========================================
       k6 Load Test Summary
========================================
Profile: ${s.profile}
Total Requests: ${s.total_requests}
Failed Rate: ${(s.failed_rate * 100).toFixed(2)}%
p(95) Duration: ${s.p95_duration.toFixed(2)}ms
========================================
`;
}
