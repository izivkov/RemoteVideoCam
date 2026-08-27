# Release Notes - v42.0

## 🛠 Build System Modernization
- **AGP 9.3.2 Upgrade**: The project is now powered by the latest Android Gradle Plugin, ensuring faster builds and better compatibility with modern Android tools.
- **Gradle 9.5.1**: Updated to the newest Gradle version for improved performance and stability.
- **Built-in Kotlin Support**: Migrated to AGP's built-in Kotlin support, simplifying our build configuration and reducing plugin overhead.
- **Android 15 Ready**: Targeted Android 15 (API 37) to leverage the latest platform features and security enhancements.
- **Dependency Refresh**: Updated core libraries including Kotlin 2.4.10 and Jetpack Compose 1.12.0 for a smoother development and user experience.

# Release Notes - v4.5

## 🌟 Major Updates
- **STUN-less Connection Architecture**: We've completely removed the dependency on external STUN servers for local network connections. This significantly improves privacy and ensures reliable video streaming in offline or restricted network environments.
- **Modernized UI Engine**: The entire application has been migrated to Jetpack Compose with Material 3. Enjoy a sleek, premium interface with glassmorphism effects, smooth animations, and a fully responsive design.
- **WiFi Direct & Local Discovery**: Enhanced local IP discovery logic allows for faster and more consistent pairing between the Camera and Display devices on the same network.

## 🚀 Performance & Stability
- **Optimized WebRTC Signaling**: Refactored the internal handshake process to prevent race conditions and ensure faster connection establishment.
- **Improved Reconnection Logic**: The app now recovers more gracefully from transient network drops or app restarts.
- **Efficient Resource Management**: Reduced CPU and battery usage during long streaming sessions through better thread handling and native library optimizations.

## 🛠 Fixes & Tweaks
- Fixed a regression where the video stream would occasionally freeze after switching cameras.
- Improved audio/video synchronization for a more natural viewing experience.
- Added haptic feedback for a more tactile user experience.
- Updated internal libraries and build tools to the latest versions.
