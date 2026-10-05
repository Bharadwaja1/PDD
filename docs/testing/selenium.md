# Selenium E2E Testing Framework

## Overview
The Selenium testing suite covers **300 test cases (SEL-001 -> SEL-300)** for MoodTunes web UI and core web application behavior.

## Directory Structure
```text
tests/selenium/
├── pages/              # Page Object Model classes
├── fixtures/           # Pytest fixtures and driver setups
├── test_cases/         # Test runner and test_cases.yml (SEL-001 -> SEL-300)
├── data/               # Test data generators
├── utils/              # Config, explicit wait helpers, screenshot capturers
└── reports/            # Generated HTML, JUnit XML, and failure screenshots
```

## Local Execution
1. Install dependencies:
   ```bash
   pip install -r tests/selenium/requirements.txt
   ```
2. Run test suite:
   ```bash
   pytest tests/selenium/test_cases/test_runner.py -n auto --html=tests/selenium/reports/report.html
   ```

## GitHub Actions Execution
- Workflow: `.github/workflows/selenium-tests.yml`
- Triggers: `push`, `pull_request`, `workflow_dispatch`
- Sharding: Parallel execution across 4 matrix shards (`matrix.shard: [1, 2, 3, 4]`).
- Artifacts: Uploaded as `selenium-results-shard-1`, `selenium-results-shard-2`, etc.
