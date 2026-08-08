# Release process

`droidfs-video` is the default downstream branch for the independently
maintained DroidFS Video edition. It periodically incorporates upstream
DroidFS changes while retaining the additional video support and playback
features documented in the README.

## Automated builds

Every push to `droidfs-video` builds an ARM64 APK. The **Build APKs** workflow
can also be started from the repository's **Actions** page; manual runs build
all four supported Android ABIs. Version tags beginning with `v` build every
ABI and publish a release after every build succeeds.

The workflow uses Java 17, Go 1.22, Node.js 22, NASM, Android platform and
Build Tools 37.0.0, CMake 4.1.2, and NDK 28.2.13676358. Keep these versions in
sync with `BUILD.md` and the Gradle configuration.

The root submodules use the original author's GitHub mirrors because GitHub's
hosted runners cannot connect reliably to `forge.chapril.org`. FFmpeg uses its
official GitHub mirror because its canonical server rejects shallow retrieval
of the pinned commit. All pinned submodule commit IDs are unchanged.

Branch and manual workflow artifacts are deliberately labelled **unsigned**
and expire after seven days. Unsigned APKs cannot be installed and must not be
published as a release. Version tags build, sign and verify all four ABI APKs,
generate signature sidecars and SHA-256 checksums, and publish them in a GitHub
Release.
Both gocryptfs and CryFS must remain enabled for public releases; never use the
`disableGocryptfs` or `disableCryFS` properties for a release build.

## Publishing a version

1. Update `versionCode` and `versionName` in `app/build.gradle`, and update any
   version-specific release notes.
2. Push `droidfs-video` and wait for its ARM64 build to pass.
3. Create and push a `v`-prefixed tag pointing at that tested commit.
4. Confirm that all four signed APK jobs pass and that the GitHub Release
   contains the APKs, signature sidecars and checksum files.
5. Verify at least one downloaded APK against its checksum and the certificate
   fingerprint below.

## Signing

The permanent PKCS#12 release keystore and its credentials are stored outside
the repository and backed up securely. Android will not accept future updates
signed by a different key.

GitHub Actions stores the keystore, password and alias as encrypted repository
secrets. They are made available only to tag-triggered signing steps.

Release-signing certificate SHA-256:

`E6:E2:17:40:D7:D2:09:45:54:EC:C4:A8:33:90:98:E2:7A:4E:4B:C2:74:2F:94:3C:90:61:F4:72:94:CD:5A:71`

Never commit a keystore, its passwords, or decoded signing material.
