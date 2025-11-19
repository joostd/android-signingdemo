# Signing Demo

A simple Android app that demonstrates the use of the Android Keystore for protecting cryptographic keys.

# Install

- clone this repository

```bash
git clone git@github.com:joostd/android-signingdemo.git
cd android-signingdemo/
```

- connect an Android device (in debug mode)

- build and install

```bash
./gradlew installDebug
```

Alternatively, download the latest .apk file from
[https://github.com/joostd/android-signingdemo/releases](https://github.com/joostd/android-signingdemo/releases)

and use adb to install

```bash
wget https://github.com/joostd/android-signingdemo/releases/download/v0.0.0-alpha/app-debug.apk
adb install app-debug.apk
```

and start SigningDemo on your Android device.

# Troubleshooting

Make sure your SDK location can be found.
Either
- define a valid SDK location with an `ANDROID_HOME` environment variable
- set the `sdk.dir` path in your project's `local.properties` file
