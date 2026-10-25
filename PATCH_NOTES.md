Capsule Lite Build 8 — targeted improvement pass

Changes made in this source pass:
- Changed blur from blur-behind (which can blur the entire screen) to window-background blur, for a localized overlay effect where supported by the device/OS.
- Increased music transport controls from 40dp to 48dp.
- Added a right-aligned spacer for the compact music visualizer.
- Made notification body text multiline (up to 15 lines) and calculated expanded height from the body length, bounded by available screen height.
- Intercepted downward notification swipes as an expand gesture so they don't dispatch a user-configured dismiss action; horizontal swipe-to-dismiss remains supported.
- Added Fast / Medium / Slow liquid spring animation presets in Look settings and reduced the snapshot transition blur to reduce animation work.
- Improved charging current lookup across common Android power-supply nodes and tightened charging text to reduce clipping.

Important:
- The requested charging “snake” animation has NOT been redesigned yet because the reference image/video has not been attached. Please share it so the motion and ring path can be matched accurately.
- This environment did not have an Android SDK/Gradle installation, so the project could not be compiled or device-tested here. The included GitHub Actions workflow can build it after pushing the source to GitHub.
