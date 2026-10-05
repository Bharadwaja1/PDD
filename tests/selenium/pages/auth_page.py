from selenium.webdriver.common.by import By
from tests.selenium.pages.base_page import BasePage

class AuthPage(BasePage):
    EMAIL_INPUT = (By.XPATH, "//input[@type='email' or contains(@label, 'Email')]")
    PASSWORD_INPUT = (By.XPATH, "//input[@type='password' or contains(@label, 'Password')]")
    SUBMIT_BUTTON = (By.XPATH, "//button[contains(text(), 'Log In') or contains(text(), 'Create Account')]")
    TOGGLE_SIGNUP_LINK = (By.XPATH, "//*[contains(text(), 'Create account') or contains(text(), 'Log in')]")

    def login(self, email, password):
        self.send_keys(self.EMAIL_INPUT, email)
        self.send_keys(self.PASSWORD_INPUT, password)
        self.click(self.SUBMIT_BUTTON)
