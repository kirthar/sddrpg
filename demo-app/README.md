# demo-app

Compose Multiplatform demo application (Android + web/wasmJs + desktop) consuming
`core` and `content` — implemented by spec 009 (`specs/009-compose-demo-app/`).

- `BattleController` (commonMain): event-driven battle session controller — the UI
  observes `uiState` and calls `submit`; automatic turns advance internally.
- `ui/BattleScreen.kt` (commonMain): the single shared battle screen.
- Entry points: `MainActivity` (Android), `Main.kt` (desktop window), `Main.kt` +
  `index.html` (wasmJs browser).

```bash
./gradlew :demo-app:desktopTest                # kotest suite (UI-free, deterministic)
./gradlew :demo-app:assembleDebug              # installable debug APK
./gradlew :demo-app:wasmJsBrowserDistribution  # static web build (GitHub Pages ready)
./gradlew :demo-app:run                        # desktop dev app
```
