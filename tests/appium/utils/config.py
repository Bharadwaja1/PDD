import os

class AppiumConfig:
    APPIUM_SERVER_URL = os.getenv("APPIUM_SERVER_URL", "http://127.0.0.1:4723")
    ANDROID_APP_PATH = os.getenv("ANDROID_APP_PATH", "app/build/outputs/apk/debug/app-debug.apk")
    ANDROID_DEVICE_NAME = os.getenv("ANDROID_DEVICE_NAME", "emulator-5554")
    PLATFORM_VERSION = os.getenv("PLATFORM_VERSION", "14.0")
    APP_PACKAGE = "com.moodtunes.app"
    APP_ACTIVITY = ".MainActivity"
