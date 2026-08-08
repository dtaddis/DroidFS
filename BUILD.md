# Introduction

DroidFS Video is an independently maintained downstream branch of
[DroidFS](https://github.com/hardcore-sushi/DroidFS). It retains the original
encrypted-volume support while adding LibVLC video playback, expanded playback
controls and optional Vulkan Smooth Slo-Mo.

The app compiles several native components: CryFS (C++), gocryptfs (Go),
FFmpeg, and the ncnn/Vulkan interpolation code. The normal build includes both
gocryptfs and CryFS support.

Please report problems specific to this branch through the
[DroidFS Video issue tracker](https://github.com/dtaddis/DroidFS-Video/issues).

# Setup

Install the required packages:

For Debian-based Linux distributions:
```
$ sudo apt-get install openjdk-17-jdk-headless build-essential nasm pkg-config git wget unzip golang-go
```

For Arch Linux and derivatives:
```
$ sudo pacman -S jdk17-openjdk base-devel nasm pkgconf git wget unzip go
```

Install [Node.js](https://nodejs.org/) 22 or newer separately. Node is used by
the PDF viewer build, and NASM is required to compile the x86_64 FFmpeg target.
Package names may differ on other distributions.

Then, you have to install the Android SDK using the `sdkmanager` [command line tool](https://developer.android.com/studio#command-line-tools-only). **You DON'T need to install Android Studio to build an Android app!** Android Studio is an infamous bloatware bundled with trackers that will be more useful for consuming your entire RAM and heating your house than for building any piece of software.

You can follow [this guide](https://developer.android.com/tools/sdkmanager) to setup `sdkmanager`, but basically it should be:
```
$ export ANDROID_HOME="<PATH>"         <-- choose any path you like as the location of the Android SDK installation
$ mkdir -p "$ANDROID_HOME/cmdline-tools"
$ unzip -d "$ANDROID_HOME/cmdline-tools" commandline-tools-*.zip
$ cd "$ANDROID_HOME/cmdline-tools"
$ mv cmdline-tools latest
```

Install the exact Android packages used by the project:
```
$ "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
    'platforms;android-37.0' \
    'build-tools;37.0.0' \
    'cmake;4.1.2' \
    'ndk;28.2.13676358'
$ export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
```
The pinned NDK is required for the current CryFS/Boost build.

# Download sources
Download DroidFS Video source code:
```
$ git clone --branch droidfs-video --recurse-submodules https://github.com/dtaddis/DroidFS-Video.git
$ cd DroidFS-Video
```

For a reproducible release build, check out the published release tag and
compare its commit SHA with the GitHub release before building. To repair or
refresh a clone's submodules, run:
```
$ git submodule update --init --recursive
```

# Build
If you know your CPU ABI, you can specify it to build scripts in order to speed up compilation time. If you don't know it, or want to build for all ABIs, just leave the field empty.

Start by compiling FFmpeg:
```
$ cd app/ffmpeg
$ ./build.sh [<ABI>]
```
## libgocryptfs
This step is only required if you want gocryptfs support.
```
$ cd app/libgocryptfs
$ ./build.sh [<ABI>]
```
## Compile APKs
Gradle builds libgocryptfs, libcryfs and the RIFE/ncnn native code by default.
The Smooth Slo-Mo model assets are also copied automatically.

The public DroidFS Video releases always include both encrypted-filesystem
implementations. For a private, reduced build without gocryptfs, run:
```
$ ./gradlew assembleRelease [-Pabi=<ABI>] -PdisableGocryptfs=true
```
For a private build without CryFS, run:
```
$ ./gradlew assembleRelease [-Pabi=<ABI>] -PdisableCryFS=true
```
To build the normal app with support for both gocryptfs and CryFS, run:
```
$ ./gradlew assembleRelease [-Pabi=<ABI>]
```

# Sign APKs
If the build succeeds, you will find the unsigned APKs in `app/build/outputs/apk/release/`. These APKs need to be signed in order to be installed on an Android device.

If you don't already have a keystore, you can create a new one by running:
```
$ keytool -genkey -keystore <output file> -alias <key alias> -keyalg EC -validity 10000
```
Then, sign the APK with Android SDK Build Tools 37.0.0:
```
$ "$ANDROID_HOME/build-tools/37.0.0/apksigner" sign --out DroidFS-signed.apk -v --ks <keystore> app/build/outputs/apk/release/<unsigned apk file>
```
Now you can install `DroidFS-signed.apk` on your device. A locally generated
key will not match the public DroidFS Video release key, so Android treats that
APK as a different signing lineage and will not install it over a public build.
