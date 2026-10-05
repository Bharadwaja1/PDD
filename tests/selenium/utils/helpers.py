import os
import time
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC

def wait_for_element(driver, locator, timeout=10):
    return WebDriverWait(driver, timeout).until(EC.presence_of_element_located(locator))

def wait_for_clickable(driver, locator, timeout=10):
    return WebDriverWait(driver, timeout).until(EC.element_to_be_clickable(locator))

def take_screenshot(driver, name, reports_dir="tests/selenium/reports"):
    os.makedirs(reports_dir, exist_ok=True)
    filepath = os.path.join(reports_dir, f"{name}.png")
    driver.save_screenshot(filepath)
    return filepath
