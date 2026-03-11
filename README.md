# Koreal Network Monitor

Koreal is an Android application built to monitor device network traffic natively. By utilizing the Android `VpnService` and `NetworkStatsManager` APIs, it provides real-time insights into active network connections, packet destinations, and overall data usage on a per-app basis.

## Features

*   **Real-time Packet Interception**: Uses a local, non-routing `VpnService` to capture IPv4 network packets directly from the device's network interfaces.
*   **App Attribution & Socket Tracking**: Accurately maps intercepted packets (Source IP/Port, Destination IP/Port) back to the specific application that generated them.
    *   On Android 10+ (API 29+), it utilizes `ConnectivityManager.getConnectionOwnerUid()` for secure, system-level socket tracking.
    *   On Android 9 and older, it includes a robust fallback parser that reads the underlying Linux kernel connection tables (`/proc/net/tcp` and `/proc/net/udp`) to map active ports to their owning UIDs.
*   **Packet Directionality**: Displays clear visual indicators distinguishing between outbound and inbound packets.
*   **Live Traffic Filtering**: Includes a real-time search interface allowing users to filter the intercepted packet stream to focus exclusively on a single application's traffic (e.g., "WhatsApp").
*   **Data Usage Statistics**: Integrates `NetworkStatsManager` to track and display total byte consumption for installed applications.

## Architecture & Tech Stack

The application strictly adheres to the Keep It Simple, Stupid (KISS) principle and is built using modern Android development standards:

*   **Language**: Kotlin
*   **Architecture**: MVVM (Model-View-ViewModel)
*   **UI Framework**: Jetpack Compose (Material 3)
*   **Asynchronous Programming**: Kotlin Coroutines and StateFlows
*   **Dependency Injection**: Koin
*   **Packaging Frameworks**: Gradle Kotlin DSL

## Limitations

*   **Deep Packet Inspection (DPI)**: This application acts as a metadata monitor. It does not perform Deep Packet Inspection to decrypt HTTPS/TLS packet payloads into human-readable text. Doing so would require a Man-in-the-Middle (MitM) architecture, a custom Certificate Authority, and root access to bypass modern Certificate Pinning.
*   **VPN Routing**: The MVP implements a local "black hole" VPN. Packets are intercepted, analyzed, and logged, but they are not currently forwarded to the actual internet. Active processes may lose connectivity while the monitor is explicitly active.

## Setup & Installation

1. Clone the repository.
2. Open the project in Android Studio.
3. Sync Gradle files.
4. Build and deploy to an Android emulator or a physical device running Android Nougat (API 24) or higher.

Upon first launch, the application will request necessary permissions, including `PACKAGE_USAGE_STATS` for data monitoring and user authorization to establish the VPN interface.
