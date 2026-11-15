# GameBar - Real-time Performance Overlay for Android

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com)
[![API](https://img.shields.io/badge/API-33%2B-brightgreen.svg)](https://android-arsenal.com/api?level=33)

GameBar is a comprehensive real-time performance monitoring overlay for Android devices. It provides detailed system metrics including FPS, CPU/GPU usage, temperatures, and memory statistics with a customizable floating overlay.

## Features

- **Real-time FPS Monitoring** - Track frame rates with multiple measurement methods
- **CPU Metrics** - Usage percentage, per-core frequencies, and temperature
- **GPU Metrics** - Usage, clock speed, and temperature
- **Memory Stats** - RAM usage, speed, and temperature
- **Battery Temperature** - Monitor device thermal status
- **Customizable Overlay** - Adjustable position, size, colors, and transparency
- **Per-App Configuration** - Auto-enable GameBar for specific applications
- **FPS Recording** - Session-based FPS logs with summary cards, graphs, and CSV/PNG export
- **Recording Bubble** - Floating play/stop bubble for manual recording, plus auto-record for selected apps
- **Gesture Controls** - Single-tap format toggle, long-press actions, and drag to move
- **Device-Specific Overlays** - Easy hardware path configuration per device

## Screenshots

### GameBar Settings & Customization
<p align="center">
  <img src="readme_resources/settings_06.png" width="200" />
  <img src="readme_resources/settings_07.png" width="200" />
  <img src="readme_resources/settings_08.png" width="200" />
</p>
<p align="center">
  <img src="readme_resources/settings_09.png" width="200" />
  <img src="readme_resources/settings_10.png" width="200" />
  <img src="readme_resources/settings_13.png" width="200" />
</p>

### In-Game Overlay
<p align="center">
  <img src="readme_resources/ingame_01.png" width="250" />
  <img src="readme_resources/ingame_02.png" width="250" />
  <img src="readme_resources/ingame_03.png" width="250" />
</p>
<p align="center">
  <img src="readme_resources/ingame_04.png" width="250" />
  <img src="readme_resources/ingame_05.png" width="250" />
  <img src="readme_resources/ingame_06.png" width="250" />
</p>

### FPS Recording & Bubble
<p align="center">
  <img src="readme_resources/fps_recording.svg" width="720" alt="FPS recording flow: bubble, saved session, and FPS Record detail" />
</p>
<p align="center">
  <img src="readme_resources/recording_bubble.svg" width="720" alt="Recording bubble play and stop states" />
</p>

## Requirements

- Android 13 (API 33) or higher
- System-level permissions (privileged app)
- LineageOS or AOSP-based ROM

## Building

### Prerequisites

- AOSP/LineageOS build environment
- Android SDK Platform 33+
- Soong build system

### Integration into Device Tree

1. **Clone the repository** into your ROM source:
   ```bash
   git clone https://github.com/kenway214/packages_apps_GameBar.git /packages/apps/GameBar -b main
   ```

2. **Include in device makefile**:
   
   Add to your `device.mk`:
   ```makefile
   # GameBar Performance Overlay
   $(call inherit-product, packages/apps/GameBar/gamebar.mk)
   ```

3. **Create device-specific overlay** (IMPORTANT):
   
   Create the overlay directory structure:
   ```bash
   mkdir -p device/<vendor>/<device>/overlay/packages/apps/GameBar/res/values
   ```
   
   Create `device/<vendor>/<device>/overlay/packages/apps/GameBar/res/values/config.xml`:
   
   **Example overlay configuration:** [View config.xml example](LINK_HERE)

4. **Configure hardware paths**:
   
   Edit your device overlay `config.xml`:
   
   ```xml
   <resources>
       <!-- Find your device's thermal zones -->
       <!-- Run: adb shell "ls /sys/class/thermal/thermal_zone*/type" -->
       
       <!-- GPU temperature path -->
       <string name="config_gpu_temp_path">/sys/class/kgsl/kgsl-3d0/temp</string>
       
       <!-- RAM temperature path -->
       <string name="config_ram_temp_path">/sys/class/thermal/thermal_zone78/temp</string>
       
       <!-- Adjust dividers if needed (usually 1000 for millidegrees, 10 for decidegrees) -->
       <integer name="config_cpu_temp_divider">1000</integer>
   </resources>
   ```

5. **Customize init.rc** (if needed):
   
   Edit `packages/apps/GameBar/init/init.gamebar.rc` to match your device's hardware paths.
   Ensure permissions are set for all sysfs nodes used by GameBar.

6. **Build**:
   ```bash
   # Clean build (recommended for first build)
   m clean
   m GameBar
   
   # Or build entire ROM
   brunch <device>
   ```

## Finding Device-Specific Paths

Use these ADB commands to find the correct paths for your device:

```bash
# Find CPU thermal zones
adb shell "for i in /sys/class/thermal/thermal_zone*/type; do echo \$i: \$(cat \$i); done" | grep -i cpu

# Find GPU thermal zones  
adb shell "for i in /sys/class/thermal/thermal_zone*/type; do echo \$i: \$(cat \$i); done" | grep -i gpu

# Find RAM/DDR thermal zones
adb shell "for i in /sys/class/thermal/thermal_zone*/type; do echo \$i: \$(cat \$i); done" | grep -i ddr

# Check GPU paths
adb shell "ls -la /sys/class/kgsl/kgsl-3d0/"

# Check FPS path
adb shell "cat /sys/class/drm/sde-crtc-0/measured_fps"

# Check RAM frequency path
adb shell "cat /sys/devices/system/cpu/bus_dcvs/DDR/cur_freq"
```

## Configuration

### Overlay Customization

GameBar supports runtime customization through Settings:

- **Display Options**: Toggle individual metrics (FPS, CPU, GPU, RAM, temperatures)
- **Visual Style**: Adjust text size, colors, background transparency, corner radius
- **Position**: Choose from 9 predefined positions or enable draggable mode
- **Update Interval**: Set refresh rate (500ms - 5000ms)
- **FPS Method**: Choose between new (SurfaceFlinger) or legacy (sysfs) measurement

### Per-App Auto-Enable

Configure GameBar to automatically activate for specific apps:

1. Open GameBar Settings
2. Navigate to "Per-App GameBar" → "Configure Apps"
3. Select apps from the list
4. GameBar will auto-enable when those apps are in foreground

## Usage

### Quick Settings Tile

1. Add GameBar tile to Quick Settings
2. Tap to toggle overlay on/off
3. Long-press tile to open settings

### Gesture Controls

- **Single-tap**: Switch between full and minimal overlay formats
- **Long-press**: Open GameBar settings
- **Drag**: Move overlay (when draggable mode enabled)

### FPS Recording

GameBar records complete FPS sessions with the metrics needed for performance analysis:

- **Auto-record selected apps**: Start a session automatically when a configured app is in the foreground
- **Recording bubble**: Tap the floating play button to start; it turns into a stop button while recording
- **FPS Record**: Browse saved sessions, open the detail dashboard, and compare FPS, CPU, GPU, RAM, temperature, and power data
- **Export**: Export or share each session as CSV, and export or share the dashboard as PNG

All sessions are saved under `Documents/GameBar/FPSRecordings/{package}/`, with an app-specific fallback when shared storage is unavailable. Sessions are kept until you delete them manually.

## SELinux Policy

GameBar includes SELinux policies for:
- Access to sysfs nodes (thermal, kgsl, drm)
- Overlay window permissions
- System service interactions
- File provider for CSV and PNG sharing

Policies are automatically included via `sepolicy/SEPolicy.mk`.

## Permissions

Required permissions (granted automatically as system app):
- `SYSTEM_ALERT_WINDOW` - Overlay display
- `PACKAGE_USAGE_STATS` - Foreground app detection for auto-recording
- `WRITE_EXTERNAL_STORAGE` - Recording CSV storage
- `ACCESS_SURFACE_FLINGER` - FPS measurement
- `WRITE_SECURE_SETTINGS` - Configuration persistence

## Troubleshooting

### Overlay not showing
- Check overlay permission in Settings → Apps → GameBar
- Verify SELinux is not blocking (check `adb logcat | grep avc`)
- Ensure init.rc permissions are applied (`adb shell ls -l /sys/class/...`)

### Metrics showing "N/A"
- Verify sysfs paths in overlay config.xml match your device
- Check file permissions: `adb shell cat /sys/class/thermal/thermal_zone*/temp`
- Review logcat for file access errors

### Temperature values incorrect
- Adjust divider values in config.xml
- Most devices use 1000 (millidegrees) or 10 (decidegrees)
- Test: `adb shell cat <temp_path>` and divide manually

### Recording issues
- Make sure Usage Access is granted for automatic recording
- Check that the target app is enabled under **Recording → Recording Apps**
- For manual recording, enable **Recording bubble** and tap the play button
- Recordings are saved even when the overlay is hidden; the bubble can run independently

### Build errors
- Ensure `org.lineageos.settings.resources` is available in your ROM
- Check SettingsLib is included in build
- Verify all resource files are present in res/ directory

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test on your device
5. Submit a pull request

## License

```
Copyright (C) 2025 kenway214

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Credits

- **Fork Maintainer & Module Packaging**: [Bryan16](https://github.com/Bryan16-ly)
- **Original Project**: [Pong-Development/packages_apps_GameBar](https://github.com/Pong-Development/packages_apps_GameBar) & [kenway214](https://github.com/kenway214)
- Built for LineageOS, AOSP, and systemless root (KernelSU / Magisk / APatch)
- [ColorPicker](https://github.com/jaredrummler/ColorPicker)

## Support

- **Fork Repository**: [https://github.com/Bryan16-ly](https://github.com/Bryan16-ly)
- **Original Issues**: [Pong-Development GitHub Issues](https://github.com/Pong-Development/packages_apps_GameBar/issues)

---

**Note**: Starting from the latest refactor, the **GameBar APK is installed independently** via Android Package Manager (`bash install_apk.sh`), while the **KernelSU / Magisk / APatch module** (`out/GameBar-Module-v1.0.zip`) provides SELinux rules and hardware sysfs node access. If the root module is disabled or removed, the GameBar application remains installed and functional without getting uninstalled or crashing. Package name is `com.android.gamebar.fork` for safe coexistence with ROM-bundled versions.