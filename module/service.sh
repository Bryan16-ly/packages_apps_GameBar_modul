#!/system/bin/sh
# GameBar KernelSU / Magisk Background Service
# Handles hardware node access permissions and legitimate system permission grants.

MODDIR=${0%/*}

# Wait until Android has fully booted
until [ "$(getprop sys.boot_completed)" = "1" ]; do
  sleep 2
done

# Grant permissions and appops via legitimate Android system mechanisms.
# Only grants development and runtime permissions, plus standard appops.
for pkg in com.android.gamebar.fork com.android.gamebar; do
  if pm list packages | grep -E "^package:${pkg}$" >/dev/null 2>&1; then
    # Development permissions: explicitly grantable via pm grant for apps that declare them
    pm grant "$pkg" android.permission.ACCESS_FPS_COUNTER 2>/dev/null || true
    pm grant "$pkg" android.permission.WRITE_SECURE_SETTINGS 2>/dev/null || true

    # Storage permissions: required for writing CSV log files to /sdcard
    pm grant "$pkg" android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
    pm grant "$pkg" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true

    # Runtime permissions: grantable via pm grant
    pm grant "$pkg" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

    # AppOps: legitimate system mechanism for overlay display and usage stats
    appops set "$pkg" SYSTEM_ALERT_WINDOW allow 2>/dev/null || true
    appops set "$pkg" GET_USAGE_STATS allow 2>/dev/null || true

    # AppOps: allow storage access (needed on Android 13+ for /sdcard writes)
    appops set "$pkg" READ_MEDIA_IMAGES allow 2>/dev/null || true
    appops set "$pkg" MANAGE_EXTERNAL_STORAGE allow 2>/dev/null || true
  fi
done

# Hardware sysfs access:
# Set specific monitoring nodes to read-only (0644).
# NEVER modify system directories recursively using chmod 0666.

# Thermal zones (CPU/Battery/SoC temperatures)
for tz in /sys/class/thermal/thermal_zone*; do
  [ -f "$tz/temp" ] && chmod 0644 "$tz/temp" 2>/dev/null
  [ -f "$tz/type" ] && chmod 0644 "$tz/type" 2>/dev/null
done

# Qualcomm Adreno GPU (kgsl) metrics - read-only
if [ -d /sys/class/kgsl/kgsl-3d0 ]; then
  chmod 0644 /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage 2>/dev/null
  chmod 0644 /sys/class/kgsl/kgsl-3d0/gpuclk 2>/dev/null
  chmod 0644 /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq 2>/dev/null
  chmod 0644 /sys/class/kgsl/kgsl-3d0/temp 2>/dev/null
fi

# Mali / MediaTek / Exynos GPU metrics - target specific files only, NO recursive chmod
for node in /sys/class/misc/mali0/device/gpu_loading \
            /sys/class/misc/mali0/device/gpu_freq \
            /sys/class/misc/mali0/device/utilization \
            /sys/devices/platform/*.mali/power/runtime_status \
            /sys/kernel/gpu/gpu_busy \
            /sys/kernel/gpu/gpu_clock; do
  [ -f "$node" ] && chmod 0644 "$node" 2>/dev/null
done

# RAM frequency paths - read-only
for ram in /sys/devices/system/cpu/bus_dcvs/DDR/cur_freq \
           /sys/class/devfreq/*ddr*/cur_freq \
           /sys/class/devfreq/*llcc*/cur_freq; do
  [ -f "$ram" ] && chmod 0644 "$ram" 2>/dev/null
done

# CPU scaling frequency & stats - read-only
chmod 0644 /proc/stat 2>/dev/null
for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq; do
  [ -f "$cpu" ] && chmod 0644 "$cpu" 2>/dev/null
done

# Display measured FPS (DRM / SDE) - read-only
for drm in /sys/class/drm/sde-crtc-*/measured_fps \
           /sys/class/graphics/fb0/measured_fps; do
  [ -f "$drm" ] && chmod 0644 "$drm" 2>/dev/null
done

exit 0
