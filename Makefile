SHELL := /bin/sh

ADB ?= adb
GRADLEW := ./gradlew
GRADLE_APK := app/build/outputs/apk/debug/app-debug.apk
DIST_DIR := dist
APK := $(DIST_DIR)/app-debug.apk
APP_ID := com.example.yasin
MAIN_ACTIVITY := com.example.aitoy.app.MainActivity

.PHONY: install build

install: build
	@DEVICE_ID=`$(ADB) devices | awk '$$2 == "device" { print $$1; exit }'`; \
	if [ -z "$$DEVICE_ID" ]; then \
		echo "No adb device in 'device' state found."; \
		exit 1; \
	fi; \
	echo "Installing $(APK) to $$DEVICE_ID"; \
	$(ADB) -s "$$DEVICE_ID" install -r "$(APK)"; \
	echo "Starting $(APP_ID)/$(MAIN_ACTIVITY) on $$DEVICE_ID"; \
	$(ADB) -s "$$DEVICE_ID" shell am start -n "$(APP_ID)/$(MAIN_ACTIVITY)"

build:
	$(GRADLEW) :app:assembleDebug
	@mkdir -p "$(DIST_DIR)"
	@cp "$(GRADLE_APK)" "$(APK)"
	@echo "APK ready: $(APK)"
