<div align="center">
  <img src="art/quackifyart.png" alt="Quackify" width="480" />
  <p>Quackify is the continuation of SpotiDuck, a native Android music client built with Jetpack Compose and Material 3. It offers a familiar streaming experience, inspired by modern player apps, without needing a WebView.</p>
  <p>
    <a href="https://github.com/23fpsz/Quackify/stargazers"><img src="https://img.shields.io/github/stars/23fpsz/Quackify?style=flat-square&color=yellow" alt="Stars" /></a>
    <a href="https://github.com/23fpsz/Quackify/releases"><img src="https://img.shields.io/github/downloads/23fpsz/Quackify/total?style=flat-square&color=green" alt="Downloads" /></a>
    <a href="https://deepwiki.com/23fpsz/Quackify"><img src="https://img.shields.io/badge/Ask_DeepWiki-6E56CF?style=flat-square" alt="Ask DeepWiki" /></a>
    <a href="https://discord.gg/NNXDGZEDFs"><img src="https://img.shields.io/badge/Discord-join-5865F2?style=flat-square&logo=discord&logoColor=white" alt="Discord" /></a>
  </p>
</div>

> [!WARNING]
> **BETA Notice** — This project is still under continuous development. Not everything may work exactly as expected yet. Any help is welcome, as long as you know what you are doing.

## Features

- Browse the home screen with filter chips
- Search for songs, artists, playlists, and podcasts
- View your library's playlists and albums
- Play playlist and album tracks with the mini player
- Expand to the full Now Playing screen with seek controls and lyrics
- Podcasts with chapters, playback speed, and episode video

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/23fpsz/Quackify.git
cd Quackify
```

### 2. Build & run

Quackify has build flavors, so pick one when building:

```bash
# standard flavor (com.quackify.app)
./gradlew installStandardDebug

# or build without installing
./gradlew assembleStandardDebug
```

Other flavors: `installSpotifyDebug` and `installAppleDebug` (cloned island variants).

Or open Android Studio, select the **app** configuration with the **standardDebug** build variant, and hit **Run** (`Shift + F10`).

## Releases

Official builds published by the devs are on the [Releases page](https://github.com/23fpsz/Quackify/releases/latest) — grab the latest one for a tested, stable experience.

Automatic builds are produced on every commit/push by the devs: the [Build Quackify](https://github.com/23fpsz/Quackify/actions/workflows/build.yml) workflow compiles the app and attaches the APKs to the run artifacts. These are bleeding-edge builds straight from the source — they may contain unfinished work, so use them at your own risk.

## Contributing

1. Fork the repo
2. Create your feature branch (`git checkout -b feature/awesome-feature`)
3. Commit your changes (`git commit -m 'feat: add awesome feature'`)
4. Push to the branch (`git push origin feature/awesome-feature`)
5. Open a pull request

## About

Quackify is a community driven project built with passion by:

- **Main dev:** [23fpsz](https://github.com/23fpsz)
- **Lead dev backend:** [xzyyysh](https://github.com/xzyyysh)
- **Backend & UI dev:** [AldySan](https://github.com/AldySan)

## License

This project is licensed under the GNU General Public License v3.0 (GPL-3.0) - see the [LICENSE](LICENSE) file for details.

## Disclaimer

This project is not affiliated with, endorsed by, or sponsored by Spotify AB or any other streaming service. All product names, trademarks, and registered trademarks mentioned or referenced are the property of their respective owners. Quackify is an independent, community-driven client intended for educational and personal use. Users are responsible for complying with the terms of service of any service they connect to. No proprietary assets or credentials are distributed with this project.
