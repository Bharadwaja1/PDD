from selenium.webdriver.common.by import By
from tests.selenium.pages.base_page import BasePage

class HomePage(BasePage):
    HEADER_TITLE = (By.XPATH, "//*[contains(text(), 'MoodTunes')]")
    PROFILE_ICON = (By.XPATH, "//*[@aria-label='Open profile' or contains(@content-desc, 'profile')]")
    HAPPY_MOOD_CARD = (By.XPATH, "//*[contains(text(), 'Happy')]")
    CALM_MOOD_CARD = (By.XPATH, "//*[contains(text(), 'Calm')]")

    def select_mood(self, mood_name):
        locator = (By.XPATH, f"//*[contains(text(), '{mood_name}')]")
        self.click(locator)
