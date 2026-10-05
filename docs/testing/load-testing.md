# k6 Load & Performance Testing Framework

## Overview
The k6 performance suite covers **300 load scenarios (LOAD-001 -> LOAD-300)** categorized across 6 execution profiles.

## Test Profiles
- `smoke`: Minimal 2 VUs, 15s run for fast CI feedback
- `load`: Ramping 0 to 20 VUs, 2m run for normal traffic baseline
- `stress`: Ramping 0 to 100 VUs, 2m run for peak load testing
- `spike`: Instant surge 0 to 150 VUs in 10s
- `soak`: Extended 15 VUs duration test for leak detection
- `api`: Fast 10 VUs, 30s benchmark of key REST endpoints

## Directory Structure
```text
tests/load/
├── scenarios/          # k6 test scripts (k6_load_test.js)
├── test_cases/         # test_cases.yml (LOAD-001 -> LOAD-300)
├── data/               # User payloads & search keywords
└── reports/            # Exported JSON metrics and HTML summaries
```

## Local Execution
```bash
k6 run -e PROFILE=smoke tests/load/scenarios/k6_load_test.js
```

## GitHub Actions Execution
- Workflow: `.github/workflows/load-tests.yml`
- Triggers: `workflow_dispatch` (with `profile` parameter), `pull_request` (runs `smoke` profile)
- Artifacts: Uploaded as `k6-test-results`.
