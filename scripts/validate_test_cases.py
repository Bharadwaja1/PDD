#!/usr/bin/env python3
"""
Validation script for MoodTunes test case definitions.
Verifies that each suite contains exactly 300 unique, valid test cases:
  - SEL-001 -> SEL-300
  - APP-001 -> APP-300
  - LOAD-001 -> LOAD-300
  - SEC-001 -> SEC-300
"""

import sys
import os
import re

try:
    import yaml
except ImportError:
    yaml = None


def parse_yaml_fallback(filepath):
    """Fallback YAML parser for simple list-of-dict test case YAML files when PyYAML is not installed."""
    test_cases = []
    current_case = None
    with open(filepath, 'r', encoding='utf-8') as f:
        for line in f:
            stripped = line.strip()
            if not stripped or stripped.startswith('#') or stripped == 'test_cases:':
                continue
            if stripped.startswith('- id:') or stripped.startswith('- "id":'):
                if current_case:
                    test_cases.append(current_case)
                current_case = {}
                parts = stripped.split(':', 1)
                val = parts[1].strip().strip('"\'')
                current_case['id'] = val
            elif current_case and ':' in stripped:
                key, val = stripped.split(':', 1)
                key = key.strip().strip('"\'')
                val = val.strip().strip('"\'')
                current_case[key] = val
        if current_case:
            test_cases.append(current_case)
    return {'test_cases': test_cases}


def load_yaml_file(filepath):
    if not os.path.exists(filepath):
        raise FileNotFoundError(f"File not found: {filepath}")

    if yaml is not None:
        with open(filepath, 'r', encoding='utf-8') as f:
            data = yaml.safe_load(f)
            return data
    else:
        return parse_yaml_fallback(filepath)


REQUIRED_FIELDS = {'id', 'category', 'name', 'description', 'priority', 'expected_result'}

SUITES = [
    {
        'file': 'tests/selenium/test_cases/test_cases.yml',
        'prefix': 'SEL',
        'expected_count': 300
    },
    {
        'file': 'tests/appium/test_cases/test_cases.yml',
        'prefix': 'APP',
        'expected_count': 300
    },
    {
        'file': 'tests/load/test_cases/test_cases.yml',
        'prefix': 'LOAD',
        'expected_count': 300
    },
    {
        'file': 'tests/security/test_cases/test_cases.yml',
        'prefix': 'SEC',
        'expected_count': 300
    }
]


def validate_suite(suite):
    filepath = suite['file']
    prefix = suite['prefix']
    expected_count = suite['expected_count']
    errors = []

    print(f"Validating {filepath}...")

    try:
        data = load_yaml_file(filepath)
    except Exception as e:
        print(f"  [FAIL] Error loading file: {e}")
        return False, [str(e)]

    if not isinstance(data, dict) or 'test_cases' not in data:
        msg = f"Root key 'test_cases' missing in {filepath}"
        print(f"  [FAIL] {msg}")
        return False, [msg]

    test_cases = data['test_cases']
    actual_count = len(test_cases)

    if actual_count != expected_count:
        errors.append(f"Expected exactly {expected_count} test cases, found {actual_count}")

    seen_ids = set()
    expected_ids = {f"{prefix}-{i:03d}" for i in range(1, expected_count + 1)}

    for idx, tc in enumerate(test_cases, start=1):
        tc_id = tc.get('id')

        # Check required fields
        missing_fields = REQUIRED_FIELDS - set(tc.keys())
        if missing_fields:
            errors.append(f"Test case #{idx} ({tc_id}) missing fields: {', '.join(missing_fields)}")

        if not tc_id:
            errors.append(f"Test case #{idx} missing 'id'")
            continue

        # Check for duplicates
        if tc_id in seen_ids:
            errors.append(f"Duplicate test ID: {tc_id}")
        seen_ids.add(tc_id)

        # Check pattern
        expected_pattern = rf"^{prefix}-\d{{3}}$"
        if not re.match(expected_pattern, tc_id):
            errors.append(f"Invalid ID format: {tc_id} (expected pattern: {prefix}-XXX)")

    # Check missing IDs from range
    missing_ids = expected_ids - seen_ids
    if missing_ids:
        errors.append(f"Missing expected IDs ({len(missing_ids)}): {sorted(list(missing_ids))[:5]}...")

    if errors:
        print(f"  [FAIL] Found {len(errors)} error(s):")
        for err in errors[:10]:
            print(f"    - {err}")
        if len(errors) > 10:
            print(f"    ... and {len(errors) - 10} more error(s)")
        return False, errors
    else:
        print(f"  [OK] Validated {actual_count} test cases ({prefix}-001 -> {prefix}-{expected_count:03d}) successfully!")
        return True, []


def main():
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    os.chdir(repo_root)

    print("=" * 60)
    print(" MoodTunes Test Case Definition Validator")
    print("=" * 60)

    total_valid = True
    total_cases = 0

    for suite in SUITES:
        success, errors = validate_suite(suite)
        if success:
            total_cases += suite['expected_count']
        else:
            total_valid = False

    print("=" * 60)
    if total_valid:
        print(f"SUCCESS: All 4 test suites validated! Total test cases: {total_cases}")
        sys.exit(0)
    else:
        print("FAILURE: Validation failed for one or more test suites.")
        sys.exit(1)


if __name__ == '__main__':
    main()
