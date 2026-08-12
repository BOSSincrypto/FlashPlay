# Release Signing

Android updates must use the same signing key for the lifetime of the application ID. Generate and back up this key once; losing it prevents ordinary updates to existing installations.

## Generate A Production Key

Run this on a trusted offline or encrypted machine and choose strong unique passwords:

```powershell
keytool -genkeypair -v -keystore flashplay-release.jks -alias flashplay -keyalg RSA -keysize 4096 -validity 10000
```

Create at least two encrypted backups in separate locations. Never commit or send the keystore through chat, issues, pull requests, build logs, or artifacts.

## Configure GitHub

Encode the keystore locally:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("flashplay-release.jks")) | Set-Clipboard
```

Add these secrets to the protected GitHub environment named `release`:

- `ANDROID_KEYSTORE_B64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Optionally add repository secret `RELEASE_PLEASE_TOKEN` using a fine-grained token or GitHub App token with repository contents and pull-request permissions. This lets workflows run automatically on Release Please pull requests.

## Publish Or Rebuild

Release Please creates a release after its version pull request is merged. If the tag already exists but signing was configured later, open **Actions > Release > Run workflow** and enter the existing tag, for example `v0.2.0`.

The workflow verifies the APK signature and publishes APK, AAB, R8 mapping, CycloneDX SBOM, checksums, and GitHub attestations. If any signing secret is missing, it exits successfully without producing misleading unsigned release assets and records setup instructions in the run summary.
