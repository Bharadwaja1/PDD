# Appium Mobile Testing Framework

## Overview
The Appium mobile testing suite covers **300 test cases (APP-001 -> APP-300)** for MoodTunes Android app using Appium 2 and UiAutomator2.

## Directory Structure
```text
tests/appium/
├── screens/            # Screen Object Model classes
├── fixtures/           # Pytest & Appium driver fixtures
├── test_cases/         # Test runner and test_cases.yml (APP-001 -> APP-300)
├── data/               # Mobile test data
├── utils/              # Mobile gestures, scrolling, screenshot utilities
└── reports/            # Appium logs, device reports, failure screenshots
```

## Local Execution
1. Start Appium Server:
   ```bash
   appium driver install uiautomator2
   appium
   ```
2. Build Android Debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
3. Run test suite:
   ```bash
   pip install -r tests/appium/requirements.txt
   pytest tests/appium/test_cases/test_runner.py --html=tests/appium/reports/report.html
   ```

## GitHub Actions Execution
- Workflow: `.github/workflows/appium-tests.yml`
- Triggers: `workflow_dispatch`, `pull_request` (path-filtered for `app/**` and `tests/appium/**`)
- Sharding: Parallel execution across 2 matrix shards (`matrix.shard: [1, 2]`).
- Artifacts: Uploaded as `appium-results-shard-1`, `appium-results-shard-2`.
