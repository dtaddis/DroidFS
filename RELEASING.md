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

Branch workflow artifacts are deliberately labelled **unsigned** and expire
after seven days. Unsigned APKs cannot be installed and must not be published
as a release. Version tags build, sign and verify all four ABI APKs, generate
SHA-256 checksums and publish them in a GitHub Release.

## Signing

The permanent PKCS#12 release keystore and its credentials are stored outside
the repository in `C:\Users\davey\DroidFS-Video-Release`. Back up that folder
securely: Android will not accept future updates signed by a different key.

GitHub Actions stores the keystore, password and alias as encrypted repository
secrets. They are made available only to tag-triggered signing steps.

Release-signing certificate SHA-256:

`E6:E2:17:40:D7:D2:09:45:54:EC:C4:A8:33:90:98:E2:7A:4E:4B:C2:74:2F:94:3C:90:61:F4:72:94:CD:5A:71`

Never commit a keystore, its passwords, or decoded signing material.
