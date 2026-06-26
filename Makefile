ANDROID_JAVA_HOME ?= /Applications/Android Studio.app/Contents/jbr/Contents/Home

.PHONY: android-build android-deploy android-install android-launch android-wait gateway-dev gateway-start

android-build:
	cd android && JAVA_HOME="$(ANDROID_JAVA_HOME)" ./gradlew assembleDebug

android-deploy: android-build android-wait android-install android-launch

android-wait:
	adb wait-for-device

android-install:
	adb install -r android/app/build/outputs/apk/debug/app-debug.apk

android-launch:
	adb shell monkey -p com.hermes.voiceremote -c android.intent.category.LAUNCHER 1

gateway-dev:
	cd gateway && bun run dev

gateway-start:
	cd gateway && bun run start
