# Signing

Lemon Native ships one fixed signing key: every APK of the lemon app, built by CI or on any machine, is signed with the same certificate, so an update installs over the last build without an uninstall and a bug report's signer line always names the same key.

## The key

| | |
|---|---|
| Keystore | `android/app/lemon-sign.jks` (PKCS12), committed to this repository |
| Alias | `lemon-native` |
| Store and key password | `lemon-native` |
| Key | RSA 4096, self-signed, valid through October 2126 |
| Certificate SHA-256 | `132d441035e050412a857a86a199b1f72e06d6047a4b816687b3985a36808b71` |

The key lives in the repository by choice, not as a secret. Its job is build identity — an update installs over the last build only when the signature matches — and it cannot do that if each machine generates its own. It is not a tamper guard: anyone can clone the repository and build a same-signed APK, which is exactly the point for a public app distributed from source. No personal data sits in the certificate (`CN=Lemon Native, O=Lemon Native, C=BR`).

## How it is wired

`android/app/build.gradle` defines `signingConfigs.lemon` (the table above) and picks it for every build type of the lemon app:

- `-PnxApp=lemon` (what CI's `build-apk.yml` runs): the release build type — the one the `apk` task assembles — and the debug build type both sign with `signingConfigs.lemon`.
- `-PnxRelease=true -PnxApp=lemon`: the same fixed key, and the build skips the `NX_SIGN_STORE` / `NX_SIGN_ALIAS` / `NX_SIGN_PASS` requirement the other release paths have, because the key needs no environment.
- The upstream apps keep their signing: the test apps and `-PnxApp=peanuts` still use the debug keystore, and `-PnxLive=true` / `-PnxRelease=true -PnxApp=peanuts` still require the release key from the environment (`NX_SIGN_*`), which never lives in this repository.

## Verify a build

```
apksigner verify --print-certs <apk>        # SHA-256 must be the fingerprint above
keytool -list -v -keystore android/app/lemon-sign.jks -storepass lemon-native
```

The launcher's official-build stamp (`Signer.OFFICIAL`, described in [android/README.md](../android/README.md) under "Official-build stamp") still names the upstream v0.1.0 release certificate, so a lemon release build stamps `· unofficial build` — honest, since it is not that key. Giving the lemon app its own official mark, with the fingerprint above, is a deliberate later change, not part of the fixed-signature setup.

## Releases are manual

CI (`build-apk.yml`) builds the APK, uploads it as a 1-hour artifact and deletes it; it never creates a GitHub release, a tag or any other publication. A release is published only when the maintainer asks for it, by hand, and its APK comes from a build signed with the key above.

## Rotating

Replace `lemon-sign.jks`, update the alias and password in `signingConfigs.lemon` and the fingerprint in the table. Builds installed before a rotation no longer update in place: they need an uninstall first, so rotate only with a reason.
