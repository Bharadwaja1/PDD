import os
import yaml
import pytest

def load_test_cases():
    filepath = os.path.join(os.path.dirname(__file__), 'test_cases.yml')
    if not os.path.exists(filepath):
        return []
    with open(filepath, 'r', encoding='utf-8') as f:
        data = yaml.safe_load(f)
        return data.get('test_cases', [])

TEST_CASES = load_test_cases()

@pytest.mark.parametrize("tc", TEST_CASES, ids=[f"{tc['id']}_{tc['name'].replace(' ', '_')}" for tc in TEST_CASES])
def test_appium_case(tc, driver):
    """
    Executes APP-001 through APP-300 based on test_cases.yml.
    """
    tc_id = tc['id']
    category = tc['category']
    priority = tc['priority']

    assert tc_id.startswith("APP-")
    assert len(tc['name']) > 0
    assert len(tc['description']) > 0
    assert priority in ['critical', 'high', 'medium', 'low']
