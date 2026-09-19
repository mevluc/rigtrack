# 🎥 RigTrack — Camera Tracking for Android

> Turn your ARCore-compatible Android phone into a portable camera-tracking companion for Blender and VFX workflows.

![Version](https://img.shields.io/badge/version-1.3.1-20C997?style=for-the-badge)
![Platform](https://img.shields.io/badge/platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![ARCore](https://img.shields.io/badge/tracking-ARCore-4285F4?style=for-the-badge)
![Status](https://img.shields.io/badge/status-Experimental-FFB020?style=for-the-badge)

---

## ✨ What is RigTrack?

**RigTrack** is an experimental Android application designed to record real-world camera movement using **Google ARCore**, optional physical markers, and the motion sensors available on your phone.

The application is intended for filmmakers, VFX artists, Blender users, virtual-production enthusiasts, and developers who want to experiment with affordable camera tracking without requiring a dedicated commercial tracking system.

Mount your phone securely to a film camera, record the movement, and export the resulting camera trajectory for post-production.

---

## 🚀 Main Features

### 📱 ARCore Camera Tracking

RigTrack records the phone camera’s position and orientation using ARCore while preserving the original timestamps and tracking states.

Captured information includes:

- Camera position and rotation
- ARCore tracking state
- Tracking interruptions
- Camera intrinsics
- Gyroscope and accelerometer samples
- Original nanosecond timestamps
- Tracking-quality diagnostics

---

### 🏷️ Marker-Assisted World Lock

RigTrack supports physical markers for improving world-space stability and recovering a known stage reference.

Supported marker systems include:

- AprilTag
- ArUco
- Marker maps with multiple fixed markers
- Marker-based relocalization
- Confidence-weighted marker observations
- Multi-marker robust pose estimation
- Moved-marker detection
- Reprojection-error filtering

Marker observations are evaluated before they are allowed to affect the camera track. Detecting a marker does not automatically mean that it will be used for correction.

---

### 🧭 Smooth World-Lock Corrections

Marker corrections are applied as a separate transform instead of modifying the raw ARCore movement directly.

The world-lock system includes:

- Translation dead zones
- Rotation dead zones
- Relock confirmation frames
- Confidence gating
- Translation and rotation outlier rejection
- Multi-marker consensus
- Gradual correction blending
- Independent translation and rotation blend times
- Protection against single-frame marker snaps

When a marker returns after being temporarily lost, RigTrack waits for multiple consistent observations before applying a correction.

This helps reduce sudden camera jumps during relocalization.

---

## 🎚️ Three Tracking Layers

RigTrack keeps the camera trajectory in three separate layers:

| Track | Description |
|---|---|
| 🟠 **RAW** | Original ARCore anchor-relative motion. Never overwritten or filtered. |
| 🔵 **FUSED** | RAW motion with real-time marker/world-lock corrections. |
| 🟢 **REFINED** | Offline-processed FUSED track with jitter reduction, outlier handling, and short-gap repair. |

RAW and FUSED tracks are always preserved. Refinement creates a separate result and never destructively replaces the original tracking data.

```text
RAW
 │
 ├── Real-time marker/world-lock correction
 │
 ▼
FUSED
 │
 ├── Outlier detection
 ├── Short-gap repair
 ├── Translation smoothing
 └── Quaternion-aware rotation smoothing
 │
 ▼
REFINED
```

---

## ✨ Offline Tracking Refinement

After recording, RigTrack can generate a separate refined trajectory.

The refinement pipeline includes:

- Robust isolated-pose outlier detection
- Short internal tracking-gap repair
- Timestamp-based translation interpolation
- Quaternion SLERP rotation interpolation
- Quaternion sign-continuity handling
- Symmetric, low-phase smoothing
- Camera-speed-adaptive smoothing strength
- Preservation of fast pans and intentional handheld movement

Available refinement presets:

- **Off**
- **Low** — default
- **Medium**
- **High**

The purpose of refinement is to reduce solver noise and relock jitter without making the camera motion feel artificially static.

---

## 📊 Synthetic Refinement Result

The current **Low** preset produced the following result on the included synthetic validation trajectory:

| Metric | Before | After | Improvement |
|---|---:|---:|---:|
| Translation jitter | 1.079 mm | 0.871 mm | ≈ 19.3% |
| Rotation residual | 0.0561° | 0.0456° | ≈ 18.7% |

Maximum trajectory deviation:

- **3.17 mm** translation
- **0.193°** rotation

The centered refinement filter preserved motion timing without introducing a conventional causal-filter delay.

> These values come from a synthetic validation trajectory and should not be interpreted as guaranteed real-world tracking accuracy.

---

## 🎬 Two-Camera Workflow

RigTrack supports a physical two-camera setup:

### 📱 Reference Camera

Represents the phone’s physical camera.

It can contain:

- Phone camera pose
- Phone camera intrinsics
- Reference video
- Reference-video timing
- RAW, FUSED, and REFINED phone trajectories

### 🎥 Film Camera

Represents the actual production camera mounted to the phone rig.

It uses:

- Configurable phone-to-camera translation
- Configurable phone-to-camera rotation
- Film-camera sensor dimensions
- Focal length
- Resolution
- Rational frame rate

The refined Film Camera track is derived from the refined Reference Camera pose using the fixed rig transform, preserving the rigid relationship between the two cameras.

---

## 🎞️ Reference Video

RigTrack can record an optional phone-camera reference video alongside the tracking session.

The reference video can help with:

- Visual synchronization
- Comparing the reconstructed camera against the original view
- Checking marker alignment
- Reviewing tracking loss
- Diagnosing projection or timing problems

Available recording options may include:

- Off
- 720p
- 1080p

Reference video can be optionally included in the exported tracking package.

---

## 📦 Export Format

RigTrack exports tracking sessions as a `.vfxtrack` package.

The package may include:

```text
metadata.json
calibration.json
rig.json
marker_map.json

ar_pose.csv
phone_camera_fused.csv
phone_camera_refined.csv

film_camera_raw.csv
film_camera_fused.csv
film_camera_refined.csv

blender_camera_raw.csv
blender_camera.csv
blender_camera_refined.csv

blender_reference_camera_raw.csv
blender_reference_camera.csv
blender_reference_camera_refined.csv

imu.csv
markers.csv
intrinsics.csv
events.csv
diagnostics.json

reference_video.mp4
README.txt
```

The `.vfxtrack` file is a ZIP-based container, but it uses its own extension to keep the tracking package easy to identify.

---

## 🧊 Blender Workflow

RigTrack tracking packages support three camera-track choices:

- **REFINED** — recommended and selected by default
- **FUSED**
- **RAW**

Fallback behavior:

```text
REFINED unavailable
        ↓
Use FUSED
        ↓
FUSED unavailable
        ↓
Use RAW
```

The Blender workflow can reconstruct:

- Reference Camera
- Film Camera
- Tracking origin
- Marker Empty objects
- Camera animation
- Film-camera projection settings
- Reference-video timing
- Rational frame rates such as 23.976, 29.97, and 59.94 FPS

---

## 📈 Diagnostics

RigTrack records real measurements for reviewing the quality of a take.

Available diagnostics include:

- ARCore frame rate
- Marker detector frame rate
- Submitted marker frames
- Processed marker frames
- Dropped marker frames
- Marker-processing time
- IMU sampling rate
- Tracking-loss duration
- Longest tracking interruption
- World-lock corrections
- Marker relocks
- Rejected marker observations
- Marker rejection reasons
- Repaired tracking gaps
- Unrepaired long gaps
- RAW, FUSED, and REFINED jitter
- Maximum refined-track deviation
- Refinement-processing time
- Track Quality score

The Track Quality score is a diagnostic indicator. It is not a calibrated millimetre-accuracy guarantee.

---

## 🌍 Languages

The application interface currently supports:

- 🇬🇧 English
- 🇹🇷 Turkish

---

## 📲 Installation

1. Download **`RigTrack-1.3.1-debug.apk`** from the Assets section below.
2. Transfer the APK to your Android device if necessary.
3. Allow installation from unknown sources for your browser or file manager.
4. Open the APK.
5. Complete the installation.
6. Grant the required camera and sensor permissions.

> Android may display a security warning because this APK is installed outside the Google Play Store.

---

## ✅ System Requirements

- Android 7.0 or later
- An ARCore-compatible Android device
- Rear camera access
- Gyroscope
- Accelerometer
- Sufficient free storage for tracking sessions and optional reference video

For the best results:

- Use a modern ARCore-supported device.
- Securely attach the phone to the camera rig.
- Avoid loose or flexible mounting.
- Use good lighting.
- Avoid excessive motion blur.
- Keep mapped markers fixed.
- Use accurately printed and measured markers.
- Calibrate the phone camera when possible.

---

## 🎯 Suggested Workflow

```text
1. Mount the phone securely
2. Configure the film camera
3. Enter the measured rig offset
4. Select or create a marker map
5. Set or relocalize the tracking origin
6. Confirm stable ARCore tracking
7. Start recording
8. Add synchronization cues
9. Perform the camera movement
10. Stop and review diagnostics
11. Export the .vfxtrack package
12. Import the selected track into Blender
13. Verify scale, timing, lens, and alignment
```

---

## ⚠️ Experimental Software

RigTrack is currently an experimental development project.

Please keep the following limitations in mind:

- It is not a replacement for a professional optical tracking system.
- Tracking quality depends heavily on the Android device and environment.
- ARCore behavior can vary between phone models.
- Fast motion and motion blur may reduce tracking quality.
- Reflective, dark, repetitive, or textureless environments may cause drift.
- Poorly measured rig offsets will produce incorrect Film Camera movement.
- Marker maps are only reliable when markers remain completely stationary.
- Reference-video synchronization is not genlock or hardware timecode.
- Long tracking gaps are intentionally left invalid instead of generating invented movement.
- The APK is a debug/development build and is not distributed through Google Play.

---

## 🧪 Validation Status

The current build passed:

- ✅ Android debug build
- ✅ 37 JVM tests
- ✅ 14 Android API 35 emulator tests
- ✅ Android lint with zero errors
- ✅ RAW/FUSED/REFINED container validation
- ✅ ZIP CRC and metadata validation
- ✅ Rational FPS and timestamp validation
- ✅ Blender 5.1 camera-import regression
- ✅ REFINED → FUSED fallback
- ✅ REFINED → RAW fallback
- ✅ Two-camera rigid-transform validation
- ✅ Marker Empty import validation
- ✅ Reference-video timing validation

### Physical Device Limitation

**Physical ARCore live tracking validation was not performed because no compatible physical device was attached during the final automated validation.**

The passing emulator tests do not replace physical testing of:

- Live multi-marker tracking
- Real ARCore camera movement
- Marker-overlay performance
- Device temperature
- Sustained recording behavior
- Real-world trajectory accuracy

---

## 📥 Download

### RigTrack 1.3.1

Download the attached APK:

```text
RigTrack-1.3.1-debug.apk
```

SHA-256:

```text
1C986A280EADFCA82F64D557CB36F4C552E0EB9867EFB8498B7C88CB086B9092
```

---

## 🔐 Safety Check

After downloading, you can verify the APK with SHA-256.

### Windows PowerShell

```powershell
Get-FileHash .\RigTrack-1.3.1-debug.apk -Algorithm SHA256
```

### Linux

```bash
sha256sum RigTrack-1.3.1-debug.apk
```

### macOS

```bash
shasum -a 256 RigTrack-1.3.1-debug.apk
```

The result should match:

```text
1C986A280EADFCA82F64D557CB36F4C552E0EB9867EFB8498B7C88CB086B9092
```

---

## 💡 Recommended Before Production Use

Before using a take in a real production:

- Record a short test movement.
- Verify camera direction and scale.
- Check the Film Camera rig offset.
- Compare RAW, FUSED, and REFINED tracks.
- Review tracking-loss and jitter diagnostics.
- Confirm focal length and sensor dimensions.
- Validate synchronization against the film footage.
- Inspect the final camera animation in Blender.

---

## 🛠️ Version Information

| Item | Value |
|---|---|
| Application | RigTrack |
| Version | 1.3.1 |
| Build type | Debug / Experimental |
| Platform | Android |
| Tracking | Google ARCore |
| Recommended output | REFINED |
| Package | `RigTrack-1.3.1-debug.apk` |

---

## 📜 Disclaimer

RigTrack is provided for development, testing, education, and experimental production workflows.

Always preserve the original footage and RAW tracking data. Carefully verify the exported camera movement before using it in a production scene.

**Use at your own risk.**
