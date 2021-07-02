CHT Android App
========================

The cht-android application is a thin wrapper to load the [CHT Core Framework](https://github.com/medic/cht-core/) web application in a webview. This allows the application to be hardcoded to a specific CHT deployment and have a partner specific logo and display name. This app also provides some deeper integration with other android apps and native phone functions that are otherwise unavailable to webapps.


# APKs

For compatibility with a wide range of devices the build script produces multiple APKs. The two variables are the instruction set used by the device's CPU, and the supported Android version. When publishing to the Google Play Store upload all APKs and it will automatically choose the right one for the target device. However, when sideloading the application it is essential to pick the correct APK or the application may crash.

To help you pick which APK to install you can find information about the version of Android and the CPU in the About section of the phone's settings menu.

The APKs are named as follows: `cht-android-{version}-{brand}-{rendering-engine}-{instruction-set}-release.apk`

| Rendering engine | Instruction set | Android version | Notes |
|------------------|-----------------|-----------------|--|
| `webview`        | `arm64-v8a`     | 10+             |  Preferred. Use this APK if possible. |
| `webview`        | `armeabi-v7a`   | 10+             | Built but not compatible with any devices. Ignore this APK. |
| `xwalk`          | `arm64-v8a`     | 4.4 - 9         |  |
| `xwalk`          | `armeabi-v7a`   | 4.4 - 9         |  |

# Release notes

## 0.8.0

### Changes

- [improvement] [#163](https://github.com/medic/cht-android/issues/163) New connection errors UX:
  - The improvements only apply to _Webview_ flavors.
  - It also applies when the app migrates to Webview from a XWalk installation.
- [improvement] [#134](https://github.com/medic/cht-android/issues/134) New UX of Crosswalk to Webview migration:
  - Add splash screen while the data is migrated.
  - Fix bug that caused redirect to the login page after migrate.
- [improvement] Remove unused `READ_EXTERNAL_STORAGE` from the `cmmb_kenya` and `surveillance_covid19_kenya` flavors

You can see more about these changes in the release notes for [CHT Core v3.11.0](https://github.com/medic/cht-core/blob/master/release-notes/docs/3.11.0.md)

## 0.7.3

### Changes

- [improvement] [cht-android#148](https://github.com/medic/cht-android/issues/148): Remove storage access for most flavors that don't use the permission
- [improvement] New flavors added, and "livinggoods_innovation_ke" removed
- [improvement] Add new translations for the prominent disclosure for location access: Tagalog (tel), Illonggo (hil), and Bisaya (ceb), and fixed translation string for the disclosure in Nepal (ne)

### Notes

A new flavor `unbranded_test` was added with the storage permission removed for testing, while `unbranded` flavor keeps the permission from the global manifest.

## 0.7.0

### Changes

- [feature] [cht-android#136](https://github.com/medic/cht-android/issues/136): Add UI for prominent disclosure when requesting location permissions.

### Notes

The text used in the new location permission request is in the Android wrapper app itself (`cht-android`), and translated differently than [CHT Core labels](https://docs.communityhealthtoolkit.org/core/overview/translations/). Any additions or modifications to translations in `cht-android` are done in the `strings.xml` files according to the [Android localization framework](https://developer.android.com/guide/topics/resources/localization).


## 0.6.0

### Upgrade notes

This release is largely intended to unblock the publishing of new apps onto the Google Play Store in a manner compatible with Android 10+. This release includes a difficult upgrade experience which may cause operational challenges in the field for apps with users on Android 10+. In particular:

1. When loading the application after upgrade you will need an internet connection in order to cache the application code.
2. The migration can take a few minutes to complete so after upgrade the user may be shown a login screen. If this happens, restart the application periodically until the user is logged in and the app loads as per usual.

We are planning improvements to [make the migration more user friendly](https://github.com/medic/cht-android/issues/134) in a future release.

Users on Android 9 and below do not need to be migrated and will be unaffected by this change. Because of this it is recommended that projects upgrade to v0.6.0 version of their app before issuing Android 10+ devices, if possible.

Earlier releases are no longer accepted by the Google Play Store.

### Changes

- [improvement] [cht-android#106](https://github.com/medic/cht-android/issues/106): Update target SDK version to 29 for Play Store compliance

## 0.5.0

### Upgrade notes

This release changes the way in which location data is collected to better align with Play Store policies. Now the information is gathered only when filling in a form as opposed to as soon as the app is loaded.

*Note*: This breaks backwards compatibility with older versions of the CHT Core Framework which may mean that location data is no longer collected at all. It is recommended you upgrade to CHT v3.9.2 or later before upgrading the android app.

### Changes

- [feature] [cht-core#6380](https://github.com/medic/cht-core/issues/6380): Adds intent so opening deployment URLs will prompt to load in app
- [improvement] [cht-android#111](https://github.com/medic/cht-android/issues/111): Compliance with Play Store developer policies for PII collection disclosure
- [bug] [cht-core#6648](https://github.com/medic/cht-core/issues/6648): Blank screen when launching external apps from CHT Android app

# Development

1. Install Android SDK
2. Clone the repository
3. Plug in your phone. Check it's detected with `adb devices`
4. Execute `make` to build and install the app in the phone. For Xwalk development, execute `make xwalk`.

See the [makefile](./Makefile) for more command line options.

## Debug Xwalk with Android Studio

1. Select a Xwalk flavor in `Build` > `Select Build Variant`.
2. Disable Android Jetifier: The Xwalk flavor uses the old Android Support Library (android.support.*).
    * Navigate to `Settings` or `Preferences` > `Build, Execution, Deployment` > `Compiler`.
    * Add `-Pandroid.enableJetifier=false` in the `Command-line Options` field (remember to remove this when debugging with Webview).
    * Click `OK` save changes.
3. Click the debug button in the toolbar.

# Testing - Instrumentation Tests (UI Tests)

1. Install Android SDK
2. Clone the repository
3. Plug in your phone. Check it's detected with `adb devices`
4. Execute: `./gradlew connected[Flavor]WebviewDebugAndroidTest` . Eg `./gradlew connectedUnbrandedWebviewDebugAndroidTest` or `./gradlew connectedMedicmobilegammaWebviewDebugAndroidTest`. At the moment we have tests only in these 2 flavors: unbranded and medicmobilegamma. To avoid failures running the tests, previous versions of the app should be uninstalled first, otherwise an `InstallException: INSTALL_FAILED_VERSION_DOWNGRADE` can make the tests to fail, and Android needs to have English as default language.

## Connecting to the server locally
Refer to the [cht-core Developer Guide](https://github.com/medic/cht-core/blob/master/DEVELOPMENT.md#testing-locally-with-devices).

# Branding

## Building branded apps

To build and deploy APKs for all configured brands:

	make branded

## Adding new brands

To add a new brand:

1. add `productFlavors { <new_brand> { ... } }` in `build.gradle`
1. add icons, strings etc. in `src/<new_brand>`
1. to enable automated deployments, add the `new_brand` to `.github/workflows/publish.yml`

# Releasing

## Alpha for release testing

1. Make sure all issues for this release have passed AT and been merged into `master`
2. Create a git tag starting with `v` and ending with the alpha version, e.g. `v1.2.3-alpha.1` and push the tag to GitHub.
3. Creating this tag will trigger [GitHub Action](https://github.com/medic/cht-android/actions) to build, sign, and properly version the build. The release-ready APKs are available for side-loading from [GitHub Releases](https://github.com/medic/cht-android/releases).
4. Announce the release in #quality-assurance

## Final for users

1. Create a git tag starting with `v`, e.g. `v1.2.3` and push the tag to GitHub. 
2. The exact same process as Step 3 above.
3. Publish the unbranded, demo, simprints, and gamma flavors to the Play Store.
4. Announce the release on the [CHT forum](https://forum.communityhealthtoolkit.org), under the "Product - Releases" category.
5. Each flavor is then individually released to users via "Release Management" in the Google Play Console. Once a flavor has been tested and is ready to go live, click Release to Production

# Copyright

Copyright 2013-2021 Medic Mobile, Inc. <hello@medic.org>

# License

The software is provided under AGPL-3.0. Contributions to this project are accepted under the same license.
