# Cyphr

Cyphr is a privacy layer for messaging. It encrypts message content locally on your device before you send it through third-party platforms such as Instagram, Discord, or any other messenger.

Two Cyphr users exchange public keys (via QR code or text) and then each can encrypt plaintext for the other. The carrier platform sees only ciphertext.

> **Cyphr is experimental and has not been audited. Do not rely on it for sensitive communications.**

## Features

- Encrypt text for individual contacts using HPKE (X25519) + AES-256-GCM
- Decrypt and inspect received payloads
- Multi-profile support with key rotation
- Contact management with fingerprint verification
- Biometric app unlock
- Replay protection with per-contact send/receive counters
- Encrypted on-device storage
- Custom keyboard IME (8 language layouts)
- Accessibility service for on-screen decryption overlay
- QR code scanning and QR code generation for key exchange
- No internet permission, no accounts, no analytics, no ads, no tracking

## Screenshots

<!-- TODO: add screenshots -->

## Download

- **GitHub Releases**: [latest release](https://github.com/NekDevs/cyphr-android/releases)
- **F-Droid**: coming soon

## Building

Requires Android SDK 36. Set `sdk.dir` in `local.properties`.

```sh
./gradlew assembleDebug        # debug APK (unsigned)
./gradlew assembleRelease      # release APK (signed — requires keystore + env vars)
./gradlew test                 # run unit tests
./gradlew lint                 # run lint
```

### Signing a release

A keystore at `app/release.keystore` is required for release builds. Generate one:

```sh
keytool -genkey -v -keystore app/release.keystore -alias cyphr \
  -keyalg RSA -keysize 2048 -validity 10000
```

Set passwords via environment variables (or leave unset for unsigned debug-only builds):

```sh
export CYPHR_KEY_PASSWORD=<your-key-password>
export CYPHR_STORE_PASSWORD=<your-store-password>
./gradlew assembleRelease
```

## Distribution

| Channel | Signing | Notes |
|---------|---------|-------|
| GitHub Releases | Developer key | APK/AAB signed by maintainer |
| F-Droid | F-Droid key | Built from source by F-Droid infrastructure |

Install from only **one** channel — the signing keys differ, so cross-channel updates will fail.

### F-Droid

Metadata for F-Droid submission is at `app/fdroid/metadata.yml`.
Submit it to [fdroiddata](https://gitlab.com/fdroid/fdroiddata) to get listed.

## License

MIT
