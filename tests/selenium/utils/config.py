import os

class Config:
    BASE_URL = os.getenv("BASE_URL", "http://localhost:8080")
    TEST_USERNAME = os.getenv("TEST_USERNAME", "testuser@moodtunes.app")
    TEST_PASSWORD = os.getenv("TEST_PASSWORD", "TestPass123!")
    HEADLESS = os.getenv("HEADLESS", "true").lower() == "true"
    IMPLICIT_WAIT = int(os.getenv("IMPLICIT_WAIT", "5"))
    EXPLICIT_WAIT = int(os.getenv("EXPLICIT_WAIT", "10"))
