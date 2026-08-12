# Security Policy

## Supported Versions

Security fixes are applied to `main` and the latest published release. Older releases may be unsupported.

## Reporting A Vulnerability

Do not open a public issue for a suspected vulnerability. Use GitHub's private vulnerability reporting for `BOSSincrypto/FlashPlay`:

1. Open the repository's **Security** tab.
2. Choose **Report a vulnerability**.
3. Include the affected version or commit, device and Android version, reproducible steps, impact, and relevant logs.

Remove media, credentials, URI grants, signed URL tokens, keystore data, and personal information from reports. We aim to acknowledge reports within five business days, validate impact, and coordinate a fix and advisory. Resolution time depends on severity and upstream Android or Media3 dependencies.

## Scope

FlashPlay processes user-selected local media, external Android intents, and user-provided HTTPS URLs. It has no account system, analytics service, or media backend. Vulnerabilities in Android platform codecs or Media3 may require an upstream update before a complete fix is available.
