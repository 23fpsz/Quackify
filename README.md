# SpotiDuck Compose & Native Engine 🦆🎵

A 1:1 pixel-perfect Android client recreation of Spotify's mobile experience, built with **Jetpack Compose**, **Material 3**, and a headless **Native Audio Engine** powered by Spotify Connect & Widevine L3/L1 hardware DRM.

---

## 🏛️ Architecture & Modular Design

The project is structured into fully decoupled modules adhering to strict separation of concerns:

```text
SpotiDuck-Compose/
├── ui/       # Pure Jetpack Compose UI library (Encore design system, screens, theme, zero audio/networking dependencies)
├── engine/   # Headless Spotify DRM & streaming playback engine (WebSocket dealer, Pathfinder, ExoPlayer Widevine DRM)
├── bridge/   # Reactive StateFlow adapter binding :engine state to :ui composables
└── sample/   # Complete runnable Android application integrating all modules
```

- **`:ui`**: Pure Compose presentation layer. Implements Spotify's Encore design tokens, typography, and interactive components. Can be used as a standalone UI library in any project.
- **`:engine`**: Headless backend client. Manages authentication, Spotify Connect dealer WebSocket session (`cast_audio` registration), Pathfinder GraphQL metadata queries, Akamai/Fastly CDN storage resolution, and Media3 ExoPlayer Widevine hardware decoding.
- **`:bridge`**: Clean reactive glue layer. Maps native playback states, queue management, and playlist metadata into observable StateFlows consumed by `:ui`.
- **`:sample`**: Example application demonstrating end-to-end integration and playback on physical Android devices.

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

### 🎧 Native Audio Engine (`:engine`)
- **Widevine L3/L1 Hardware DRM**: Streams encrypted MP4 AAC audio directly from Spotify's Akamai/Fastly CDN and decrypts via Google Media3 ExoPlayer and Spotify's Widevine license server.
- **Spotify Connect Handshake**: Seamlessly connects to `wss://dealer.spotify.com`, registers device endpoints, and extracts internal file manifests for arbitrary library tracks.
- **Continuous Queue Playback**: Automatic track advancement upon song completion and full skip forward/backward navigation.

---

## 🚀 Getting Started

### 1. Clone the repository
```bash
git clone https://github.com/23fpsz/SpotiDuck-Compose.git
cd SpotiDuck-Compose
```

### 2. Configure Authentication (Optional for authentic library & playback)
Create `sample/src/main/assets/sp_dc.txt` and insert your Spotify `sp_dc` cookie value. (This file is ignored by `.gitignore` to prevent credential leaks).

### 3. Build & Run
```bash
# Build debug APK
./gradlew assembleDebug

# Deploy to connected device via ADB
./gradlew installDebug
```

Or open the root folder in **Android Studio**, select the `sample` configuration, and hit **Run** (`Shift + F10`).

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
