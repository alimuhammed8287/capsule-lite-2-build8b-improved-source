Capsule Lite Build 8 — follow-up charging/theme fixes

Changes in this pass:
- Decoupled theme selection from blur. Black, frosted/translucent, and accent-outline themes now retain their own background/stroke choices even when blur is enabled.
- Replaced the broken-arc charging indicator with a continuous scalloped perimeter whose wave phase travels around the edge, based on the supplied charging reference.
- Added a second small animated charging ring for the collapsed pill; the expanded charging layout retains its larger ring.
- Replaced the first expanded charging metric (elapsed chronometer) with an ETA label when Android provides a charge-time estimate.
- Added an ACTION_BATTERY_CHANGED `current_now` fallback for devices where BatteryManager's current property returns no value.

Important limitations:
- Actual charging current/amps/watts and ETA depend on what the phone's Android build exposes. Some manufacturers block current readings or do not provide a time-to-full estimate; those values will still show as unavailable on such devices.
- Android overlay blur is OEM/version dependent. This pass prevents blur preference from overriding the selected theme, but it cannot guarantee true background blur on devices that disallow blur for overlay windows.
- The project was not compiled or device-tested in this environment. Please build and test before treating these changes as verified.
