# GitHub-only Build & Release (No Local Build Needed)

If your internet is slow on your PC, you can build and release fully on GitHub Actions.

## 1) Push your code

Push your `rust-public` branch to GitHub.

## 2) Configure repository secrets

Go to: **GitHub repo -> Settings -> Secrets and variables -> Actions -> New repository secret**

Create these secrets:

- `ANDROID_KEYSTORE_BASE64` (base64 of your `.jks` keystore)
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

### Create `ANDROID_KEYSTORE_BASE64`

On Linux/macOS:

```bash
base64 -w 0 release.jks
```

On Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks"))
```

Copy output and paste into `ANDROID_KEYSTORE_BASE64`.

## 3) Run CI debug build (optional)

- Open **Actions -> Android CI**
- Trigger by pushing a commit or opening a PR
- Download `app-debug-apk-splits` artifact

## 4) Run manual release workflow

- Open **Actions -> Manual Release**
- Click **Run workflow**
- Example inputs:
  - `tag_name`: `v1.0.0`
  - `release_name`: `MasterHttpRelayVPN Android v1.0.0`
  - `make_latest`: `true`

The workflow will:

1. Build Rust JNI bridge (`librust_jni_bridge.so`) for Android ABIs
2. Build tun2socks AAR
3. Build signed release APK splits + universal APK
4. Create GitHub Release and upload all artifacts

## 5) Output artifacts in Release

- `*-universal-release.apk`
- ABI split APKs (`arm64-v8a`, `armeabi-v7a`, `x86_64`)
- `*-tun2socks.aar`
- `*-librust_jni_bridge.so` files for each ABI

## Notes

- No local Android Studio/Gradle build is required.
- GitHub-hosted runners download all dependencies in the cloud.
