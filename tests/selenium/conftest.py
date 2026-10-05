import os
import pytest
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from tests.selenium.utils.config import Config

def pytest_addoption(parser):
    parser.addoption("--shard", action="store", default="1/1", help="Shard index/total, e.g. 1/4")

@pytest.fixture(scope="session")
def driver_options():
    options = Options()
    if Config.HEADLESS:
        options.add_argument("--headless=new")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")
    options.add_argument("--window-size=1280,800")
    return options

@pytest.fixture(scope="session")
def driver(driver_options):
    driver = webdriver.Chrome(options=driver_options)
    driver.implicitly_wait(Config.IMPLICIT_WAIT)
    yield driver
    driver.quit()

@pytest.hookimpl(tryfirst=True, hookwrapper=True)
def pytest_runtest_makereport(item, call):
    outcome = yield
    rep = outcome.get_result()
    if rep.when == "call" and rep.failed:
        driver = item.funcargs.get("driver")
        if driver and hasattr(driver, "save_screenshot"):
            os.makedirs("tests/selenium/reports", exist_ok=True)
            screenshot_path = os.path.join("tests/selenium/reports", f"{item.name}.png")
            try:
                driver.save_screenshot(screenshot_path)
            except Exception:
                pass
