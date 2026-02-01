# Release Notes - v3.5

## Key Features & Improvements
- **Modern UI Overhaul**: Fully rewritten complying with Material 3 Design principles using Jetpack Compose for a sleek, modern, and responsive user experience.
- **Robust Connection Strategy**: Implemented a new "Robust Connection" system that automatically attempts multiple connection methods (Local Network, Wi-Fi Aware, Wi-Fi Direct) in parallel to ensure a reliable link between devices.
- **Privacy First**: Removed reliance on external STUN servers. All WebRTC negotiation now happens locally, ensuring your data never leaves your local network.
- **Simplified Experience**: 
  - Implementation of auto-connection on startup.
  - Removal of Picture-in-Picture mode for better stability.
  - Consolidated connection logic for better maintainability and performance.
- **Performance**: Significant improvements in video stream reliability and reconnection handling.
