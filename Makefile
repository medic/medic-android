ADB = ${ANDROID_HOME}/platform-tools/adb
GRADLE = ./gradlew
GRADLE_OPTS = --daemon --parallel
flavor = UnbrandedWebview
abi = x86

ifdef ComSpec	 # Windows
  # Use `/` for all paths, except `.\`
  ADB := $(subst \,/,${ADB})
  GRADLE := $(subst /,\,${GRADLE})
endif

default: deploy logs
branded: clean-apks assemble-all deploy-all logs
branded-debug: clean-apks assemble-all-debug deploy-all logs
xwalk: deploy-xwalk logs

logs:
	${ADB} logcat MedicMobile:V AndroidRuntime:E '*:S' | tee android.log

deploy:
	${GRADLE} ${GRADLE_OPTS} install${flavor}Debug
deploy-xwalk:
	${GRADLE} ${GRADLE_OPTS} installUnbrandedXwalkDebug
deploy-all:
	find build/outputs/apk -name \*-debug.apk | \
		xargs -n1 ${ADB} install -r

clean:
	${GRADLE} clean
clean-apks:
	rm -rf build/outputs/apk/

assemble:
	${GRADLE} ${GRADLE_OPTS} assemble${flavor}
assemble-all:
	${GRADLE} ${GRADLE_OPTS} assembleRelease
assemble-all-debug:
	${GRADLE} ${GRADLE_OPTS} assembleDebug

bundle:
	${GRADLE} ${GRADLE_OPTS} bundle${flavor}Release
bundle-all:
	${GRADLE} ${GRADLE_OPTS} bundleRelease

uninstall-all:
	${GRADLE} uninstallAll

url-tester:
	DISABLE_APP_URL_VALIDATION=true ${GRADLE} ${GRADLE_OPTS} install${flavor}Debug

uninstall:
	adb uninstall org.medicmobile.webapp.mobile

lint:
	${GRADLE} ${GRADLE_OPTS} androidCheck lint${flavor}Debug
test: lint
	${GRADLE} ${GRADLE_OPTS} test
test-ui:
	${GRADLE} connectedUnbrandedWebviewDebugAndroidTest -Pabi=${abi} --stacktrace
test-ui-gamma:
	${GRADLE} connectedMedicmobilegammaWebviewDebugAndroidTest -Pabi=${abi} --stacktrace
