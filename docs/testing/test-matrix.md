# MoodTunes Master Test Matrix

Total Test Cases Across Framework: **1,200**

| Suite | Category | Range | Test Count | Workflow |
| :--- | :--- | :--- | :---: | :--- |
| **Selenium** | Web UI & Core Features | SEL-001 -> SEL-300 | **300** | `.github/workflows/selenium-tests.yml` |
| **Appium** | Android Mobile E2E | APP-001 -> APP-300 | **300** | `.github/workflows/appium-tests.yml` |
| **k6 Load** | Load & Performance Scenarios | LOAD-001 -> LOAD-300 | **300** | `.github/workflows/load-tests.yml` |
| **Security** | Vulnerability & Security Checks | SEC-001 -> SEC-300 | **300** | `.github/workflows/vulnerability-tests.yml` |
| **TOTAL** | | | **1,200** | |

## Validation
Run the validation script anytime to verify test case counts and formatting:
```bash
python scripts/validate_test_cases.py
```
