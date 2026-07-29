# xPoint - Advanced Location Spoofer

**xPoint** is a lightweight and powerful Xposed module designed to spoof your device's GPS coordinates at the system level. It hooks into the Android Location framework and Geocoder API to ensure that applications receive the faked coordinates.

## Features

- **System-level Spoofing**: Hooks directly into `android.location.Location` and `android.location.Geocoder`.
- **Persistent Settings**: Saves your fake coordinates across reboots using a custom ContentProvider.
- **Easy to Use**: Simple UI to set and update coordinates.

## Requirements

- **Android Version**: Android 8.0 (Oreo) to Android 14+
- **Rooted**: **LSPosed** (Recommended), EdXposed, or ZygiskNext.
- **Non-Root**: **LSPatch**, **NPatch**, or VirtualXposed/TaiChi.

## Installation

### For LSPosed (Rooted)
1. Install the xPoint APK.
2. Enable the module in the LSPosed Manager.
3. Select the target apps in the "Scope" settings.
4. Reboot or force stop the target apps.

### For LSPatch / NPatch (Non-Root)
1. Install the xPoint APK.
2. Use LSPatch/NPatch to patch your target APK (e.g., a browser or social media app).
3. Install and run the patched APK.
4. Set the location in the xPoint app.

## How it Works

xPoint uses two main components:
1. **MainActivity**: A simple interface to input latitude and longitude, stored via `SharedPreferences`.
2. **LocationProvider**: A `ContentProvider` that allows the Xposed module (running in other app processes) to read the stored coordinates.
3. **XPointModule**: The core Xposed module that hooks into `Location.getLatitude()`, `Location.getLongitude()`, and `Geocoder.getFromLocation()` to return the fake data.

## Development

Built with:
- Kotlin
- Jetpack Libraries (AppCompat, Material Design)
- Xposed Bridge API

## License

This project is licensed under the **GNU General Public License v3.0**. See the [LICENSE](LICENSE) file for the full text.

---

*Disclaimer: This tool is intended for development and testing purposes only. Use it responsibly and at your own risk.*
