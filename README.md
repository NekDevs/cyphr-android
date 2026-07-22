# Cyphr

**Private messages, anywhere.**

Cyphr encrypts your message on your Android device before you send it through platforms such as Discord, Instagram, Telegram, or any other messenger.

The platform only receives encrypted text. The person you are messaging can decrypt it with Cyphr.

> [!WARNING]
> **Cyphr is experimental and has not been independently audited.**
> Do not use it for highly sensitive, emergency, or critical communications.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android-3DDC84.svg)](https://www.android.com/)

---

## How it works

Cyphr lets two people exchange public keys, then encrypt messages for one another.

```text
Write your message in Cyphr
        ↓
Cyphr encrypts it on your device
        ↓
Copy and send the encrypted text in any messenger
        ↓
Your contact copies it into Cyphr
        ↓
Cyphr decrypts the message on their device
```

Cyphr works without accounts, phone numbers, servers, or an internet connection. You only need a messaging platform to deliver the encrypted text to the other person.

---

## Features

- Encrypt messages for individual contacts
- Exchange keys with QR codes or copyable text
- Verify contact fingerprints
- Create multiple profiles and rotate keys
- Protect against replayed messages
- Store Cyphr data encrypted on your device
- Lock the app with biometrics
- Copy, paste, encrypt, and decrypt messages easily
- Custom keyboard with eight language layouts
- On-screen decryption overlay
- No account required
- No ads, analytics, tracking, or telemetry
- No internet permission

---

## Getting started

### 1. Install Cyphr

Download the latest Android APK from [GitHub Releases](https://github.com/NekDevs/cyphr-android/releases).

> [!IMPORTANT]
> Only download Cyphr from the official GitHub Releases page or F-Droid once it becomes available.

### 2. Create your profile

Open Cyphr and create a profile. Your encryption keys are generated and stored locally on your device.

### 3. Add a contact

Ask your contact to open Cyphr and share their public key with you.

You can exchange keys by:

- Scanning a QR code
- Sending a public-key text string through a messenger
- Copying and pasting a public key directly

### 4. Verify the fingerprint

After adding a contact, compare the displayed fingerprint through a separate trusted method, such as in person or over a voice call.

> [!WARNING]
> Do not skip fingerprint verification for important conversations. If an attacker replaces a public key, they may be able to read messages intended for your contact.

### 5. Send an encrypted message

Choose your contact in Cyphr, write your message, and encrypt it.

Copy the encrypted result and paste it into Discord, Instagram, Telegram, SMS, email, or another messaging platform.

### 6. Read a received message

When another Cyphr user sends you an encrypted message:

1. Copy the encrypted text.
2. Open Cyphr.
3. Select the relevant contact.
4. Paste and decrypt the message.

---

## What Cyphr protects

Cyphr encrypts the **content** of your messages before they leave your device.

This can help protect your message text from:

- The platform used to send the message
- People who access the chat history without your Cyphr keys
- Content inspection of the encrypted message itself

---

## What Cyphr does not hide

Cyphr does not make you anonymous and cannot hide metadata collected by the platform you use.

The carrier platform may still know:

- Who you are communicating with
- When you send messages
- How often you communicate
- The approximate size of a message
- Your account name, device details, IP address, or location information

Cyphr also cannot protect messages if your device, your contact’s device, or either private key is compromised.

---

## Safety notes

- Cyphr is new, experimental software and has **not been independently audited**.
- Verify fingerprints before using Cyphr for anything important.
- Never share your private key or any recovery data.
- Protect your phone with a strong device lock.
- Be cautious when granting keyboard and accessibility permissions.
- Screenshots, screen recording, copied clipboard content, and compromised devices can expose decrypted messages.
- Cyphr is not a replacement for established end-to-end encrypted messengers.

---

## Screenshots

> Screenshots coming soon.

<!--
<p align="center">
  <img src="docs/screenshots/home.png" width="220" alt="Cyphr home screen">
  <img src="docs/screenshots/encrypt.png" width="220" alt="Encrypting a message in Cyphr">
  <img src="docs/screenshots/contacts.png" width="220" alt="Cyphr contacts">
</p>
-->

---

## Updates

Cyphr can be downloaded from [GitHub Releases](https://github.com/NekDevs/cyphr-android/releases).

F-Droid support is planned.

> [!NOTE]
> Install Cyphr from one source only. GitHub Releases and F-Droid builds use different signing keys, so Android cannot update an installation across sources.

---

## Privacy

Cyphr is designed to work locally on your device.

- No user account
- No phone number
- No ads
- No analytics
- No telemetry
- No tracking SDKs
- No remote server required for encryption or decryption
- No internet permission

Your encryption keys and Cyphr data stay on your device unless you choose to export, copy, or back them up.

---

## License

Cyphr is released under the [MIT License](LICENSE).
