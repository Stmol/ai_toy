# AI Toy

An Android prototype for local voice interaction.

## Features

- Record audio with push-to-talk.
- Convert speech to text on the device.
- Write a prompt and get a local text response.
- Use voice input for a prompt.
- Play a response as speech.
- Manage runtime files in the app.
- Show an animated eyes demo.

## Requirements

- Android Studio with an Android SDK.
- Android API 29 or newer.
- A physical Android device is recommended for microphone features.

## Build

```bash
./gradlew :app:assembleDebug
```

To build and install the debug app on a connected device:

```bash
make install
```

## Runtime files

The app needs runtime files for speech and text features. The repository does not include these files. Use the `Models` screen to install them.

## Status

This project is an early prototype. It is not a production release.
