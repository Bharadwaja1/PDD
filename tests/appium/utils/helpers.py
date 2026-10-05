import os

def swipe_scroll(driver, start_x=500, start_y=1500, end_x=500, end_y=300, duration=500):
    try:
        driver.swipe(start_x, start_y, end_x, end_y, duration)
    except Exception:
        pass

def take_screen_shot(driver, name, reports_dir="tests/appium/reports"):
    os.makedirs(reports_dir, exist_ok=True)
    filepath = os.path.join(reports_dir, f"{name}.png")
    try:
        driver.save_screenshot(filepath)
    except Exception:
        pass
    return filepath
