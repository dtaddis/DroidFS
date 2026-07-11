# DroidFS Video

An unofficial, independently maintained edition of
[DroidFS](https://github.com/hardcore-sushi/DroidFS) focused on capable,
private video playback inside encrypted volumes.

This project is not produced, endorsed or supported by the original DroidFS
developer. DroidFS was created by Hardcore Sushi; this fork preserves the
original attribution and AGPL-3.0 licence while publishing its modifications
and build source.

[Download the latest release](https://github.com/dtaddis/DroidFS-Video/releases/latest)

## Video edition improvements

- LibVLC playback with broader container and codec support
- User-configurable file-extension handling
- Fit, fill, horizontal and vertical video sizing
- Repeat, playlist and A/B repeat controls
- Swipe brightness, volume and scrubbing gestures
- Secure range-based playback on Android 8.0 and newer
- Explicit safety checks for legacy Android versions and large text files

Like upstream DroidFS, the app supports encrypted gocryptfs and CryFS volumes
through its own internal file explorer instead of mounting them.

<p align="center">
<img src="https://forge.chapril.org/hardcoresushi/DroidFS/raw/branch/master/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" height="500">
<img src="https://forge.chapril.org/hardcoresushi/DroidFS/raw/branch/master/fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" height="500">
<img src="https://forge.chapril.org/hardcoresushi/DroidFS/raw/branch/master/fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" height="500">
</p>

# Support and attribution

Report problems specific to this edition through its
[GitHub issue tracker](https://github.com/dtaddis/DroidFS-Video/issues).
Upstream DroidFS is maintained by Hardcore Sushi, whose work can be supported
through the [original donation information](https://forge.chapril.org/hardcoresushi/DroidFS/src/branch/master/DONATE.txt).

# Disclaimer
DroidFS is provided "as is", without any warranty of any kind.
It shouldn't be considered as an absolute safe way to store files.
DroidFS cannot protect you from screen recording apps, keyloggers, apk backdooring, compromised root accesses, memory dumps etc.
Do not use this app with volumes containing sensitive data unless you know exactly what you are doing.

# Features
- Compatible with original encrypted volume implementations
- Internal support for video, audio, images, text and PDF files
- Built-in camera to take on-the-fly encrypted photos and videos
- Unlocking volumes using fingerprint authentication
- Volume auto-locking when the app goes in background

For planned features, see [TODO.md](https://forge.chapril.org/hardcoresushi/DroidFS/src/branch/master/TODO.md).

# Unsafe features
Some available features are considered risky and are therefore disabled by default. It is strongly recommended that you read the following documentation if you wish to activate one of these options.

<ul>
  <li><b>Allow screenshots:</b>

  Disable the secure flag of DroidFS activities. This will allow you to take screenshots from the app, but will also allow other apps to record the screen while using DroidFS.

  Note: apps with root access don't care about this flag: they can take screenshots or record the screen of any app without any permissions.</li>
  <li><b>Allow exporting files:</b>

  Decrypt and write file to disk (external storage). Any app with storage permissions could access exported files.</li>
  <li><b>Allow sharing files via the android share menu⁽¹⁾:</b>

  Decrypt and share file with other apps. These apps could save and send the files thus shared.</li>
  <li><b>Allow saving password hash using fingerprint:</b>

  Generate an AES-256 GCM key in the Android Keystore (protected by fingerprint authentication), then use it to encrypt the volume password hash and store it to the DroidFS internal storage. This require Android v6.0+. If your device is not encrypted, extracting the encryption key with physical access may be possible.</li>
  <li><b>Disable volume auto-locking:</b> (previously called <i>"Keep volumes open when the app goes in background"</i>)

  Don't close open volumes when you leave the app. Anyone going back to the application could have access to open volumes. Cryptographic secrets are kept in memory for an undefined amount of time.</li>
  <li><b>Keep volumes open:</b>
  (Different from the old <i>"Keep volumes open when the app goes in background"</i>. Yes it's confusing, sorry)

  Keep the app running as a [foreground service](https://developer.android.com/develop/background-work/services/foreground-services) to maintain volumes open, even when the app is removed from recent tasks.

  This avoid the app from being killed by the system during file operations or while accessing exposed volumes, but this mean cryptographic secrets stay in memory for an undefined amount of time.</li>
  <li><b>Allow opening files with other applications⁽¹⁾:</b>

  Decrypt and open file using external apps. These apps could save and send the files thus opened.</li>
  <li><b>Expose open volumes⁽¹⁾:</b>

  Allow open volumes to be browsed in the system file explorer (<a href="https://developer.android.com/guide/topics/providers/document-provider">DocumentProvider</a> API). Encrypted files can then be selected from other applications, potentially with permanent access. This feature requires <i>"Disable volume auto-locking"</i>, and works more reliably when <i>"Keep volumes open"</i> is also enabled.</li>
  <li><b>Grant write access:</b>

  Files opened with another applications can be modified by them. This applies to both previous unsafe features.</li>
</ul>

⁽¹⁾: These features can work in two ways: temporarily writing the plain file to disk (DroidFS internal storage) or sharing it via memory. By default, DroidFS will choose to keep the file only in memory as it's more secure, but will fallback to disk export if the file is too large to be held in memory. This behavior can be changed with the *"Export method"* parameter in the settings. Please note that some applications require the file to be stored on disk, and therefore do not work with memory-exported files.

# Download

Installable APKs are published in this repository's
[Releases section](https://github.com/dtaddis/DroidFS-Video/releases). Each
release contains per-ABI APKs and SHA-256 checksums.

Android release-signing certificate:

`E6:E2:17:40:D7:D2:09:45:54:EC:C4:A8:33:90:98:E2:7A:4E:4B:C2:74:2F:94:3C:90:61:F4:72:94:CD:5A:71`

Verify a downloaded APK with Android SDK Build Tools:

```
apksigner verify --verbose --print-certs <APK file>
```

The official DroidFS package remains available from
[F-Droid](https://f-droid.org/packages/sushi.hardcore.droidfs). DroidFS Video
uses a separate Android application ID and signing key, so both apps can be
installed together. It is not an update for the official package.

# Permissions
DroidFS needs some permissions for certain features. However, you are free to deny them if you do not wish to use these features.

- **Read & write access to shared storage**: Required to access volumes located on shared storage.
- **Biometric/Fingerprint hardware**: Required to encrypt/decrypt password hashes using a fingerprint protected key.
- **Camera**: Required to take encrypted photos or videos directly from the app.
- **Record audio**: Required if you want sound on video recorded with DroidFS.
- **Notifications**: Used to report file operations progress and notify about volumes kept open.

# Limitations
DroidFS works as a wrapper around modified versions of the original encrypted container implementations ([libgocryptfs](https://forge.chapril.org/hardcoresushi/libgocryptfs) and [libcryfs](https://forge.chapril.org/hardcoresushi/libcryfs)). These programs were designed to run on standard x86 Linux systems: they access the underlying file system with file paths and syscalls. However, on Android, you can't access files from other applications using file paths. Instead, one has to use the [ContentProvider](https://developer.android.com/guide/topics/providers/content-providers) API. Obviously, neither Gocryptfs nor CryFS support this API. As a result, DroidFS cannot open volumes provided by other applications (such as cloud storage clients). If you want to synchronize your volumes on a cloud, the cloud application must synchronize the encrypted directory from disk.

Due to Android's storage restrictions, encrypted volumes located on SD cards
must be placed under `/Android/data/io.github.dtaddis.droidfsvideo/` if you
want DroidFS Video to modify them. Existing volumes in the official app's
private or app-specific storage are not migrated automatically; back them up
and reopen or copy them deliberately.

# Building from source
You can follow the instructions in [BUILD.md](BUILD.md) to build DroidFS from source.

# Third party code
Thanks to these open source projects that DroidFS uses:

### Modified code:
- Encrypted filesystems (to protect your data):
    - [libgocryptfs](https://forge.chapril.org/hardcoresushi/libgocryptfs) (forked from [gocryptfs](https://github.com/rfjakob/gocryptfs))
    - [libcryfs](https://forge.chapril.org/hardcoresushi/libcryfs) (forked from [CryFS](https://github.com/cryfs/cryfs))
- [libpdfviewer](https://forge.chapril.org/hardcoresushi/libpdfviewer) (forked from [PdfViewer](https://github.com/GrapheneOS/PdfViewer)) to open PDF files
- [DoubleTapPlayerView](https://github.com/vkay94/DoubleTapPlayerView) to add double-click controls to the video player
### Borrowed code:
- [MaterialFiles](https://github.com/zhanghai/MaterialFiles) for Kotlin natural sorting implementation
### Libraries:
- [Glide](https://github.com/bumptech/glide) to display pictures
- [ExoPlayer](https://github.com/google/ExoPlayer) to play media files
- [libVLC](https://www.videolan.org/vlc/libvlc.html) for expanded video playback support
