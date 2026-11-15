SKIPUNZIP=0

ui_print "*****************************************"
ui_print "*            GameBar (Fork)             *"
ui_print "*   Performance Overlay for Android     *"
ui_print "*       KernelSU / Magisk Module        *"
ui_print "*                                       *"
ui_print "* Author: Bryan16                       *"
ui_print "* (https://github.com/Bryan16-ly)       *"
ui_print "*                                       *"
ui_print "* Original: Pong-Development / kenway   *"
ui_print "* (https://github.com/Pong-Development) *"
ui_print "*****************************************"

if [ "$API" -lt 33 ]; then
  abort "! GameBar requires Android 13+ (API 33+). Detected API: $API"
fi

ui_print "- Configuring GameBar KernelSU system modifications..."
ui_print "- Note: GameBar APK is installed separately via Package Manager."

# Set permissions for module files
set_perm "$MODPATH/service.sh" 0 0 0755 u:object_r:system_file:s0
set_perm "$MODPATH/module.prop" 0 0 0644 u:object_r:system_file:s0
set_perm "$MODPATH/sepolicy.rule" 0 0 0644 u:object_r:system_file:s0

chmod 0755 "$MODPATH/service.sh" 2>/dev/null || true
chmod 0644 "$MODPATH/module.prop" "$MODPATH/sepolicy.rule" 2>/dev/null || true
chcon u:object_r:system_file:s0 "$MODPATH/service.sh" "$MODPATH/module.prop" "$MODPATH/sepolicy.rule" 2>/dev/null || true

ui_print "- Setup completed successfully!"
ui_print "- Please reboot your device to activate GameBar system support."
