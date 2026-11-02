# SpotiDuck Compose & Native Engine 🦆🎵

A 1:1 pixel-perfect Android client recreation of Spotify's mobile experience, built with **Jetpack Compose**, **Material 3**, and a headless **Native Audio Engine** powered by Spotify Connect & Widevine L3/L1 hardware DRM.

---

## 🏛️ Architecture & Modular Design

The project is structured into fully decoupled modules adhering to strict separation of concerns:

```text
SpotiDuck-Compose/
├── ui/             # Pure Jetpack Compose UI library (Encore design system, screens, theme)
├── app/            # Runnable Android application, wires the shell to the backend
├── krespot-model/  # Shared data models
├── krespot-core/   # net/, auth/, webview/, log/
├── krespot-api/    # Stateless per-call surfaces
├── krespot-library/ # Home, search, library, playlist, album, artist, podcasts
├── krespot-connect/ # Dealer, registration, cluster, commands, state reports
└── krespot-player/ # The engine: fMP4, CENC, Widevine, focus, seek
```

- **`:ui`**: Pure Compose presentation layer. Implements Spotify's Encore design tokens, typography, and interactive components. Reaches the data layer only through `Krespot`.
- **`:app`**: The application. Owns `PlaybackBridge`, which reconciles the Connect cluster, the server commands and the engine into one `ComposePlaybackState`.
- **`:krespot-connect`**: The Connect plane. Manages the dealer WebSocket, device registration, the cluster, the command family and the state reports.
- **`:krespot-player`**: The playback plane. Owns the fMP4 parser, `MediaCodec`, Widevine and the audio sink. No Media3.

---

## 📱 Features

### 🎨 Visual & UI Capabilities (`:ui`)
- **Home Screen**:
  - Filter chips (*All, Music, Podcasts, Audiobooks*)
  - Header actions (*User Profile drawer, What's New feed, Listening History, Settings*)
  - 8-tile Quick Access grid with dynamic playlist thumbnails and accent tints
  - Horizontal recommendation carousels (*Made for you, Popular artists, Jump back in*)
  - Artist picks and promotional cards
- **Search Screen**:
  - Interactive search bar with instant query dispatch
  - Search filter chips (*Top, Songs, Artists, Playlists, Albums, Podcasts*)
  - Signature Spotify "Top Result" card and browse genre categories
- **Your Library**:
  - Live playlist and artist retrieval from authenticated account
  - Filter chips (*Playlists, Podcasts, Albums, Artists, Downloaded*)
  - List view and 2-column Grid view toggle with sort order sheet
- **Playlist & Album Screen**:
  - Sticky glassmorphic top navigation bar on scroll
  - Gradient hero header displaying live playlist artwork and metadata
  - Encore action buttons (Like heart, download indicator, 3-dot options, shuffle & play)
  - Interactive tracklist with active playing indicators
- **Player Experience**:
  - **Mini Player**: 56dp floating bottom bar with track thumbnail, title, artist, Spotify Native Widevine indicator, and play/pause controls
  - **Full Now Playing Screen**: Swipe/tap to expand, ambient gradient backdrop, smooth seeking scrubber, repeat/shuffle controls, device picker, and time-synced lyrics card

### 🎧 Native Audio Engine (`:krespot-player`)
- **Widevine L1 Hardware DRM**: Streams encrypted fMP4 AAC audio directly from Spotify's CDN and decrypts through `MediaDrm` and `MediaCodec`. One session per pssh, no Media3.
- **Spotify Connect Handshake**: Connects to the dealer WebSocket, registers the device on both services, and reads the cluster for the real queue and the device roster.
- **Continuous Queue Playback**: Automatic track advancement on the server's new state machine, with forward and backward skip.

---

## 🚀 Getting Started

### 1. Clone the repository
```bash
git clone https://github.com/23fpsz/SpotiDuck-Compose.git
cd SpotiDuck-Compose
```

### 2. Configure Authentication
The session comes from the login WebView. No asset file is needed.

### 3. Build & Run
```bash
# Build debug APK
./gradlew assembleDebug

# Deploy to connected device via ADB
./gradlew installDebug
```

Or open the root folder in **Android Studio**, select the `app` configuration, and hit **Run** (`Shift + F10`).

---

## 🤝 Contributing

Contributions to improve UI fidelity, add animations, or expand engine capabilities are welcomed!

1. Fork the repo.
2. Create your feature branch (`git checkout -b feature/awesome-feature`).
3. Commit your changes (`git commit -m 'feat: add awesome feature'`).
4. Push to the branch (`git push origin feature/awesome-feature`).
5. Open a Pull Request!

---

## 📄 License
This project is for educational and research purposes. Spotify is a registered trademark of Spotify AB.
