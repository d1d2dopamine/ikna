# Signing: nothing to do

The signing key is already in the repository: `ikna.keystore` at the project
root. The password, alias and key password are hard-coded in
`app/build.gradle.kts`:

```
keystore : ikna.keystore   (PKCS12)
alias    : ikna
password : iknafixedkey
validity : 100 years
key      : RSA 4096
```

You do not generate anything. You do not configure repository secrets. Clone,
push, let Actions build, install the APK. Every later APK is signed by the same
key, so Android installs it over the previous one and the review history
survives.

## Why the key is committed instead of hidden

Gradle otherwise creates a random `~/.android/debug.keystore` on every CI
runner. A different signature means Android refuses the update with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, and the only fix is uninstalling, which
wipes the database. A fixed key is the only way to get painless updates.

Storing that key in a public repository is a deliberate trade-off, not an
oversight. Here is exactly what it means.

What the key does **not** protect:

- It is not an account, not a token, not a password to any service.
- It gives nobody access to your data. Reviews never leave the phone; exports
  go to `Documents/ikna/` on local storage.

What it does mean:

- Anyone can build an APK that Android treats as the same app as yours. That
  only matters if you install an APK from somewhere other than your own
  Actions artefacts. So install only from your own repository.
- The key cannot be used to push anything to your phone. Android does not
  update apps by itself from an unknown source; installation is always a
  manual step you perform.

## If this ever goes on Google Play

Do not upload an APK signed with this key. Google Play binds a package name to
the first signing key forever, and a public key cannot be trusted. Before any
store upload, generate a private key, keep it out of the repository, and change
the signing config. Until then, this setup is strictly better than fighting
secrets for a personal app.

## Verifying the build is signed as expected

Every CI run prints the signer of the produced APK in the `Report signer` step.
The fingerprint of the committed key is:

```
SHA256: 7A:B1:A6:52:81:57:F0:0C:76:D0:B1:E2:AE:AB:3B:F8:44:78:6C:E4:C2:53:D3:CB:E3:CD:5E:50:F5:AD:BD:17
```

If a build ever prints a different fingerprint, the keystore was lost from the
repository and the build fell back to a random debug key. In that case restore
`ikna.keystore` from history rather than installing the APK.

## Rotating the key (only if you want to)

This breaks updates once: the app must be uninstalled and reinstalled, so
export your reviews from the debug screen first.

```sh
keytool -genkeypair -v \
  -keystore ikna.keystore -storetype PKCS12 \
  -alias ikna -keyalg RSA -keysize 4096 -validity 36500 \
  -storepass iknafixedkey -keypass iknafixedkey \
  -dname "CN=Ikna, OU=Ikna, O=Ikna, L=Yekaterinburg, C=RU"
```
