# 🎮 GameBar User Guide
### Real-time Performance Monitoring & FPS Recording

---

## 📊 What is GameBar?

GameBar is a **real-time performance overlay** that displays FPS, CPU/GPU usage, temperatures, RAM stats, and more while you use apps. It also includes **FPS Recording** with a floating recording bubble, session history, detailed graphs, and CSV/PNG export.

**Perfect for:**
- 🎮 Gaming performance testing & optimization
- 📱 App benchmarking & comparison
- 🔥 Thermal analysis & throttling detection
- ⚡ Battery drain investigation
- 📈 Performance tracking over time

---

## 🚀 Quick Start

### Step 1: Enable the GameBar Overlay
1. Open the **GameBar** app (standalone APK: app drawer; module installs may also show it in **Settings → System → GameBar**)
2. Turn **ON** the **"Enable GameBar Overlay"** switch
3. Grant overlay permission if asked

✅ You'll see the GameBar overlay on your screen.

> **📦 Module vs standalone:** This fork works both as a KernelSU/Magisk module and as a standalone APK. If the app is not exposed in **Settings → System → GameBar**, open it from the app drawer. When shared storage is unavailable, recordings automatically fall back to app-specific storage.

> **⚙️ Settings layout:** **Recording** is located below **Per-App GameBar**. It contains **Auto-record selected apps**, **Recording Apps**, **Recording bubble**, and **FPS Record**.

### Step 2: Choose How to Record

| Method | Best For | How It Works |
|--------|----------|--------------|
| **Auto-record** | Regular gaming sessions | Starts and stops automatically for selected apps |
| **Recording bubble** | Quick manual tests | Tap the floating play/stop button |
| **Both together** | Flexibility | Auto-record handles selected apps; the bubble is always available for manual control |

FPS Recording works even when the GameBar overlay is hidden. The bubble starts the FPS meter on its own, and sampling follows the overlay **Update Interval** setting.

---

## 🎯 FPS Recording

### What Gets Recorded
Each sample contains the metrics available on your device:

- FPS and frame time
- Battery, CPU, GPU, and RAM temperature
- CPU usage and per-core clock speed
- RAM usage and memory frequency
- GPU usage and clock speed
- Power draw when exposed by the device

### Set Up Automatic Recording
1. Open **GameBar** → **Recording**
2. Turn ON **Auto-record selected apps**
3. Tap **Recording Apps**
4. Search for an app, then turn ON its switch
5. Go back and open the selected app

The recording starts when the app comes to the foreground and stops when you leave it.

> **Permission tip:** Auto-recording needs **Usage Access** so GameBar can detect the foreground app.

### Record Manually With the Bubble
1. Open **GameBar** → **Recording**
2. Turn ON **Recording bubble**
3. A floating bubble appears over other apps
4. Tap the **▶ play** icon to start recording
5. The bubble turns red and shows a **⏹ stop** icon
6. Tap the stop icon to finish and save the session

You can drag the bubble anywhere; its position is remembered.

### View Saved Sessions
1. Open **GameBar** → **Recording** → **FPS Record**
2. Tap a session to open **Recording Detail**
3. Long-press a session to delete it

Sessions are kept until you delete them. Nothing is pruned automatically.

---

## 📈 Recording Detail

Recording Detail opens with a summary and a full metric dashboard.

### Summary
- **MAX / MIN / AVERAGE FPS**
- **1% LOW / 0.1% LOW / 5% LOW FPS**
- **MAX temperature** and **AVERAGE power**

### Metric Cards
Each metric shows **LATEST / AVERAGE / MIN / MAX**:
- FPS and frame time
- Battery temperature
- CPU usage, CPU frequency (including per-core), and CPU temperature
- GPU usage, GPU clock, and GPU temperature
- RAM usage, RAM frequency, and RAM temperature
- Power draw

### Graphs
- FPS and frame time
- CPU usage and temperature
- GPU usage, clock, and temperature
- RAM metrics
- Per-core CPU frequency

### Export and Share
Open the **⋮** menu in Recording Detail:

| Action | Result |
|--------|--------|
| **Export CSV** | Saves the session as a CSV file |
| **Export PNG** | Saves the dashboard as a PNG |
| **Share CSV** | Shares the CSV via the system share sheet |
| **Share PNG** | Shares the dashboard image |

Exported PNG files are written directly to **Pictures/GameBar/** through MediaStore.

---

## 📁 Storage

### Session Data
- **JSON (internal):** `files/fps_recordings/<id>.json` — used by Recording Detail
- **CSV:** `Documents/GameBar/FPSRecordings/{package}/<app>_<timestamp>_<id>.csv`

If GameBar cannot write to shared storage, the CSV falls back to app-specific external storage and then internal storage.

### File Names
```
<AppName>_<yyyyMMdd_HHmmss>_<short-id>.csv
```

Example:
```
BGMI_20261115_143052_a1b2c3d4.csv
```

### CSV Columns
```
DateTime, PackageName, FPS, Frame_Time, Battery_Temp, CPU_Usage, CPU_Clock,
CPU_Temp, RAM_Usage_MB, RAM_Usage_Pct, RAM_Speed_MHz, RAM_Temp,
GPU_Usage, GPU_Clock_MHz, GPU_Temp, Power_W
```

---

## 🎛️ Overlay Customization

GameBar supports runtime customization through Settings:

- **Display Options:** Toggle individual metrics (FPS, CPU, GPU, RAM, temperatures)
- **Visual Style:** Text size, colors, background transparency, corner radius, padding, item spacing
- **Position:** Choose from predefined positions or enable draggable mode
- **Update Interval:** 500ms, 1s, 2s, or 5s — also controls FPS recording sampling
- **FPS Method:** New (SurfaceFlinger) or legacy (sysfs) measurement
- **Split Mode:** Side-by-side or stacked layout

### Per-App Overlay
Configure GameBar to activate automatically for specific apps:
1. Open **GameBar** → **Per-App GameBar** → **Configure Apps**
2. Select apps from the list
3. GameBar auto-enables when those apps are in the foreground

This list is separate from **Recording Apps**, so you can record a different set of apps.

---

## 👆 Gesture Controls

- **Single-tap overlay:** Switch between full and minimal formats
- **Long-press overlay:** Open GameBar settings
- **Drag overlay:** Move it when draggable mode is enabled

The recording bubble has its own controls:
- **Tap:** Start or stop recording
- **Drag:** Move the bubble; the position is saved

---

## ❓ Troubleshooting

### ❌ Auto-recording does not start
**Check:**
1. **Usage Access** is granted to GameBar
2. The app is enabled under **Recording → Recording Apps**
3. **Auto-record selected apps** is ON
4. The app is actually in the foreground

### ❌ Recording bubble is missing
**Check:**
1. **Recording bubble** is ON under **GameBar → Recording**
2. Overlay permission is granted
3. The app was not force-stopped after enabling the bubble

### ❌ No sessions in FPS Record
**Check:**
1. Recording was running long enough to collect at least one sample
2. You tapped the **stop** icon to finish the session
3. Auto-recording was enabled for the app you used

### ❌ FPS shows N/A while recording
- Verify the FPS method under **FPS Measurement Method**
- Some apps or protected surfaces do not expose frame data to SurfaceFlinger
- Try the legacy sysfs method if your device exposes a measured FPS node

### ❌ CSV or PNG export fails
1. Install a file manager or document provider
2. Check available storage space
3. For PNG, make sure the Pictures/GameBar location is writable
4. Try **Share CSV** or **Share PNG** instead of exporting directly

### ❌ Temperatures or GPU values show N/A
- The device may not expose the required sysfs/hardware nodes
- Configure hardware paths in your device overlay (`config.xml`)
- Check node permissions with `adb shell cat <path>`

---

## 💡 Pro Tips

### 🎮 For Gamers
1. Enable **Auto-record** for your main games
2. Record a few sessions to establish average performance
3. After game updates, compare new sessions with older ones
4. Share the CSV or PNG when reporting lag

### 🔥 For Thermal Testing
1. Keep all temperature metrics enabled in the overlay
2. Record a long session under load
3. Look for FPS drops that line up with rising temperatures
4. Compare sessions in different environments

### ⚡ For Battery Investigation
1. Record a session with the overlay hidden
2. Record another session with the overlay visible
3. Compare power draw and temperature
4. Check which apps use more CPU/GPU over time

### 📈 For Reviews and Comparisons
- Use the same **Update Interval** for every comparison
- Let each session run at least 5 minutes
- Close background apps for cleaner results
- Compare 1% low and 0.1% low FPS, not just average FPS

---

## 🎯 Quick Reference

| I Want To... | Steps |
|--------------|-------|
| Record every BGMI session automatically | **Recording** → enable **Auto-record selected apps** → **Recording Apps** → select BGMI |
| Start a quick manual recording | Enable **Recording bubble** → tap **▶** → use the app → tap **⏹** |
| View recorded sessions | **Recording** → **FPS Record** → tap a session |
| Delete a session | **FPS Record** → long-press the session → confirm |
| Export data | Open **Recording Detail** → **⋮** → **Export CSV** |
| Share a dashboard image | Open **Recording Detail** → **⋮** → **Share PNG** |
| Compare performance after an update | Record before the update, then record again after it |

---

## ✨ Summary

**The easiest workflow:**
1. ✅ Open **GameBar** → **Recording**
2. ✅ Turn ON **Auto-record selected apps**
3. ✅ Open **Recording Apps** and select your games
4. ✅ Turn ON **Recording bubble** if you want manual control
5. ✅ Play normally, then open **FPS Record** to review results

**Key points:**
- ▶ **Green bubble** — idle, tap to start recording
- ⏹ **Red bubble** — recording, tap to stop and save
- 📊 **FPS Record** — sessions, summary cards, graphs, and export
- 📁 **Storage** — `Documents/GameBar/FPSRecordings/{package}/`
- 🖼️ **PNG export** — saved to `Pictures/GameBar/`
- 🗑️ Sessions stay until you delete them manually

---

**GameBar 12**
