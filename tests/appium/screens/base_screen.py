class BaseScreen:
    def __init__(self, driver):
        self.driver = driver

    def find_element(self, by, value):
        return self.driver.find_element(by, value)

    def click(self, by, value):
        self.find_element(by, value).click()

    def send_keys(self, by, value, text):
        elem = self.find_element(by, value)
        elem.clear()
        elem.send_keys(text)
