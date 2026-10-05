import os
import pytest
from tests.appium.utils.config import AppiumConfig

def pytest_addoption(parser):
    parser.addoption("--shard", action="store", default="1/1", help="Shard index/total, e.g. 1/2")

@pytest.fixture(scope="session")
def appium_capabilities():
    capabilities = {
        "platformName": "Android",
        "appium:automationName": "UiAutomator2",
        "appium:deviceName": AppiumConfig.ANDROID_DEVICE_NAME,
        "appium:app": os.path.abspath(AppiumConfig.ANDROID_APP_PATH),
        "appium:appPackage": AppiumConfig.APP_PACKAGE,
        "appium:appActivity": AppiumConfig.APP_ACTIVITY,
        "appium:noReset": False,
        "appium:fullReset": False
    }
    return capabilities

@pytest.fixture(scope="session")
def driver(appium_capabilities):
    from appium import webdriver
    from appium.options.android import UiAutomator2Options

    options = UiAutomator2Options()
    options.load_capabilities(appium_capabilities)
    driver = webdriver.Remote(AppiumConfig.APPIUM_SERVER_URL, options=options)
    yield driver
    driver.quit()

@pytest.hookimpl(tryfirst=True, hookwrapper=True)
def pytest_runtest_makereport(item, call):
    outcome = yield
    rep = outcome.get_result()
    if rep.when == "call" and rep.failed:
        driver = item.funcargs.get("driver")
        if driver and hasattr(driver, "save_screenshot"):
            os.makedirs("tests/appium/reports", exist_ok=True)
            screenshot_path = os.path.join("tests/appium/reports", f"{item.name}.png")
            try:
                driver.save_screenshot(screenshot_path)
            except Exception:
                pass
