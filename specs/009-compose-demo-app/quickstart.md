# Quickstart: Validating the Compose Demo App

**Feature**: 009-compose-demo-app

## Prerequisites

- JDK 21, Node.js (both already present in this environment).
- Android SDK with `platforms;android-36` + `build-tools;36.0.0` — installed at
  `/opt/android-sdk` in this session; point `local.properties` at it
  (`sdk.dir=/opt/android-sdk`, uncommitted/gitignored).
- Network access to Google Maven (`dl.google.com`) and Maven Central for the new
  Compose/AGP artifacts (verified reachable through this environment's proxy).
- Binaryen (`wasm-opt`) on the PATH for the *production* web build in this
  environment (`npm install -g binaryen` — the Kotlin toolchain's own GitHub
  download is blocked here; see research R9). Not needed on GitHub runners.

## Run the validation suite

```bash
/opt/gradle/bin/gradle :demo-app:desktopTest              # kotest controller/rendering suite — must be green
/opt/gradle/bin/gradle build                              # whole project, every module still green
/opt/gradle/bin/gradle :demo-app:assembleDebug            # the installable APK
/opt/gradle/bin/gradle :demo-app:wasmJsBrowserDistribution # the deployable web site
```

## Expected mapping: spec item → validation

| Spec item | Validation |
|---|---|
| US1 scenarios 1-2, 5 (health always visible/accurate; valid submission resolves; battle ends with overlay) | `BattleControllerTest` (participants snapshots, phase transitions, `BattleOver`) |
| US1 scenario 3 / FR-002 / SC-004 (rejection shows reason, never consumes the turn) | `BattleControllerTest` |
| US1 scenario 4 / FR-003 (automatic turns need no player involvement) | `BattleControllerTest` (auto-turns advance internally; `AwaitingPlayerAction.actorId` is always human-controlled) |
| US1 scenario 6 / FR-009 / SC-002 (determinism) | `BattleControllerDeterminismTest` |
| US2 / FR-004 / SC-003 (every occurrence kind rendered; health agrees with log) | `EventTextTest` (every `BattleEvent` subtype) + `BattleControllerTest` (log/health consistency) |
| US3 / FR-008 (every prior mechanic reachable: status tick, limit break, synergy, summon) | `BattleControllerPlaythroughTest` |
| FR-006 (limit break/summon offered iff acceptable) | `BattleControllerTest` (offerability vs. gauge/resource states) |
| FR-007 / SC-006 (orchestration verifiable with no UI) | the whole suite is UI-free by construction |
| FR-008 (zero changes to specs 001-008 files) | `git diff` scope + whole-project build green |
| FR-010 / SC-005 (installable APK; deployable web build) | `assembleDebug` output installed on a real phone (user step); `wasmJsBrowserDistribution` output served |

## Expected outcome

- `:demo-app:desktopTest` green; `gradle build` green across all five modules.
- `demo-app/build/outputs/apk/debug/demo-app-debug.apk` exists and installs on an
  Android phone (debug-signed — Android asks to allow installing from unknown
  sources; that is expected for a debug build).
- `demo-app/build/dist/wasmJs/productionExecutable/` contains `index.html` + the
  wasm/js bundle — a static site servable by any web server.

## Manual smoke (desktop, optional)

```bash
/opt/gradle/bin/gradle :demo-app:run
```

Play the battle in the desktop window: HP bars, log, action buttons on
Cloud/Aerith's turns, target picker with cancel, automatic Bomb turns, and the
Victory/Defeat overlay.

## GitHub Pages (optional deliverable — owner steps)

1. Merge this feature (the workflow lives at `.github/workflows/deploy-pages.yml`
   and runs from the default branch, or trigger it manually via *Run workflow*).
2. In the repository settings → Pages, set **Source = GitHub Actions** (one-time).
3. After the workflow's first successful run, the battle is playable at the
   repository's Pages URL. Requires a WasmGC-capable browser (Chrome 119+,
   Firefox 120+, Safari 18.2+).
