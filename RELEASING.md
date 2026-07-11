# Release process

This branch contains the independently maintained DroidFS Video edition. The
`video-player-improvements` branch remains limited to the changes proposed to
upstream DroidFS.

## Automated builds

Every push to `droidfs-video` builds an ARM64 APK. The **Build APKs** workflow
can also be started from the repository's **Actions** page once this is the
default branch; manual runs build all four supported Android ABIs. Version tags
beginning with `v` also build every ABI.

The root submodules use the original author's GitHub mirrors because GitHub's
hosted runners cannot connect reliably to `forge.chapril.org`. FFmpeg uses its
official GitHub mirror because its canonical server rejects shallow retrieval
of the pinned commit. All pinned submodule commit IDs are unchanged.

Workflow artifacts are deliberately labelled **unsigned** and expire after
seven days. Unsigned APKs cannot be installed and must not be published as a
release. The workflow also generates SHA-256 checksum files for the APKs.

## Signing

Create one permanent release keystore and keep it outside this repository.
Back it up securely: Android will not accept future updates signed by a
different key.

Until automated signing is configured with GitHub Actions secrets, sign each
APK locally using the Android SDK's `apksigner`, verify it, regenerate the
SHA-256 checksums, and upload the signed APKs and checksums to a GitHub Release.

Never commit a keystore, its passwords, or decoded signing material.
