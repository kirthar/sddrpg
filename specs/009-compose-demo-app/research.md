# Phase 0 Research: Compose Demo App

## R1: Toolchain version pins (all verified live from this environment)

**Decision**:

| Piece | Version | Where verified |
|---|---|---|
| Compose Multiplatform gradle plugin (`org.jetbrains.compose`) | **1.11.1** | Maven Central metadata — latest stable (1.12.x is alpha/beta only) |
| Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`) | **2.4.0** | Maven Central — version-locked to the project's Kotlin 2.4.0 (this plugin ships with Kotlin itself) |
| AGP (`com.android.application`) | **8.13.2** | Google Maven metadata — latest 8.x; AGP 9.x requires Gradle 9, project is on Gradle 8.14.3 |
| `androidx.activity:activity-compose` | **1.13.0** | Google Maven metadata — latest stable |
| compileSdk / targetSdk | **36** | `platforms;android-36` installed at `/opt/android-sdk` |
| minSdk | **24** | Compose Multiplatform's documented minimum for Android |

**Rationale**: Compose 1.11.1's own runtime is built against Kotlin 2.2.20 (checked
its POM) — a *lower* Kotlin than ours, which is the safe direction: Kotlin 2.4.0
consumes 2.2.x-built libraries; the Compose *compiler* comes from Kotlin 2.4.0's own
matching plugin, so there is no compiler/runtime version conflict. AGP 8.13.2 is the
newest AGP that runs on Gradle 8.14.x (upgrading Gradle to 9 just for AGP 9 would be
a much larger, riskier change with zero feature benefit here).

**Alternatives considered**: Compose 1.12.0-beta01 (rejected: pre-release, no
feature this milestone needs); upgrading Gradle to 9.x for AGP 9 (rejected: touches
every module's build for no benefit).

## R2: Repository wiring for Android/Compose artifacts

**Decision**: Add `google()` to both `pluginManagement.repositories` and
`dependencyResolutionManagement.repositories` in `settings.gradle.kts` (before
`mavenCentral()`, the conventional order). The Android SDK location is provided via
`local.properties` (`sdk.dir=/opt/android-sdk`), which is added to `.gitignore` —
it is machine-specific by definition.

**Rationale**: AGP and all `androidx.*` artifacts live on Google Maven only;
`dl.google.com` is confirmed reachable through this environment's proxy (HTTP 200).
`local.properties` is the standard AGP mechanism and the only uncommitted file.

**Alternatives considered**: `ANDROID_HOME` env var only (works, but doesn't
survive a fresh session/another machine without documentation; `local.properties`
is self-describing and the gitignore entry documents the convention).

## R3: The event-driven controller — shape and observability

**Decision**: `BattleController` is a plain Kotlin class in `demo-app`'s commonMain
with **no UI-framework, coroutine, or Flow dependency**:

```kotlin
class BattleController(content: ContentPack, initialSession: DemoSession) {
    var uiState: BattleUiState   // immutable value, replaced (never mutated) on change
        private set
    fun submit(choice: PlayerChoice)   // the only mutation entry point
}
```

- On construction it advances all leading automatic turns, so `uiState` is already
  `AwaitingPlayerAction` (or `BattleOver`) when the UI first reads it.
- `submit` gates via `resolveAction`/`resolveLimitBreak`/`resolveSummon` exactly as
  spec 008's `humanSubmission` does; a rejection only replaces
  `uiState.lastRejection` (turn not consumed, FR-002/SC-004); a resolution runs the
  full spec 008 bookkeeping order, then advances consecutive automatic turns until
  a human's turn or `BattleOver`.
- The Compose layer holds `var state by remember { mutableStateOf(controller.uiState) }`
  and refreshes it after each `submit` call — a single synchronous read, no
  subscription machinery.

**Rationale**: Everything `submit` does is synchronous and deterministic, so
"observable" needs nothing more than reading a value after calling a function —
kotest tests drive it identically to how the UI does (FR-007/SC-006), with zero
async test scaffolding. Introducing StateFlow would drag kotlinx.coroutines into
the controller and force `runTest` scaffolding through every test for no behavioral
gain in a strictly turn-based, submission-driven battle.

**Alternatives considered**: `StateFlow<BattleUiState>` (rejected: async machinery
without an async problem); making the controller immutable and returning a new
controller per submission (rejected: pushes session-threading boilerplate into the
UI layer; the controller's single `var` + immutable state values keeps the same
testability with a simpler consumer contract).

## R4: Session composition and per-turn bookkeeping — reuse spec 008's exact order

**Decision**: The controller holds the same session pieces spec 008's
`BattleSession` established (raw `BattleState` as health source-of-truth,
`AtbScheduleState`, `StatusEffectState`, `LimitGaugeState`, `ResourceState`,
`EventLog`), built from `content`'s published `buildDemoBattleState()` +
`loadContentPack(DEMO_CONTENT_JSON)` + `DEMO_ACTIONS`. Each granted turn applies,
in order: event derivation for the resolution (`eventsFromResolution` /
`eventsFromLimitBreak` / `eventsFromSummon`), status-effect application from
`DemoActionDefinition.appliesStatusEffect` (+`eventsFromApply`),
`detectSynergyTriggers` → `applySynergyBonus` + `eventsFromSynergyBonus`,
`chargeLimitGauge` + `eventsFromGaugeCharge`, one `tickStatusEffects` +
`eventsFromTick`, `markSpent`, then the raw/effective merge-back
(`deriveEffectiveBattleState` derives the next turn's view; health changes merge
onto raw participants so stat modifiers never compound — the exact fix spec 008's
loop established).

**Rationale**: This is not a new design — it is spec 008's proven per-turn
convention (its research R6 + the compounding fix), re-hosted in an event-driven
shell. Duplicating the ~100 lines of orchestration in `demo-app` is the price of
the "zero changes to earlier specs' files" discipline: `demo-console` is a JVM-only
*application* module (not a library another module may depend on), and extracting a
shared orchestration library would mean restructuring spec 008's artifacts — a
refactor this feature explicitly must not do. If a third frontend ever appears,
extraction becomes a properly-specced refactor with two real consumers.

**Alternatives considered**: depending on `demo-console` (impossible: JVM-only
application module, wrong dependency direction); extracting a shared `demo-shared`
library now (rejected: restructures spec 008 artifacts, violating FR-008).

## R5: Limit break / summon offerability (FR-006)

**Decision**: The controller computes offerability *before* offering, using the
same published gates the resolvers enforce: a limit break is offerable iff the
actor's class declares one, the definition exists in the catalog, and
`gauges[actor] >= threshold`; a summon is offerable iff the actor's capabilities
include `SUMMON`, a definition exists, and `resources[actor] >= cost`. Submission
still goes through `resolveLimitBreak`/`resolveSummon` — offerability is a UI
convenience, the resolvers stay the actual gate (a rejection is still surfaced,
never a crash, per the edge case).

**Rationale**: Buttons that are only shown when the engine would accept them
satisfy FR-006's "when — and only when"; keeping the resolver as the real gate
preserves the single-source-of-truth gating spec 008 established (offerability can
never drift into a parallel rule set because the resolver still runs).

## R6: wasmJs target, entry point, and distribution

**Decision**: `wasmJs { browser(); binaries.executable() }`. Entry point uses
`ComposeViewport(document.body!!)` (the current CMP web API); a minimal
`index.html` in `wasmJsMain/resources` hosts the bundle. The deployable static
site is produced by the Compose/Kotlin tooling's **`wasmJsBrowserDistribution`**
task into `demo-app/build/dist/wasmJs/productionExecutable/`. The existing root
`build.gradle.kts` Node-download opt-out (`download.set(false)`) already covers the
Node.js the wasm toolchain needs; system Node 22 is present.

**Rationale**: This is the documented CMP web setup; the distribution directory is
exactly what GitHub Pages serves. Browser tests are deliberately avoided (spec
Assumptions) — compilation + distribution production is wasmJs's verification bar.

## R7: GitHub Pages deployment (optional deliverable)

**Decision**: `.github/workflows/deploy-pages.yml`: on push to the default branch
(plus `workflow_dispatch`), set up JDK 21, run
`./gradlew :demo-app:wasmJsBrowserDistribution`, upload
`demo-app/build/dist/wasmJs/productionExecutable` with
`actions/upload-pages-artifact@v3`, deploy with `actions/deploy-pages@v4` using the
standard `pages: write`/`id-token: write` permissions.

**Caveats surfaced in quickstart**: (1) the repository owner must enable Pages with
Source = "GitHub Actions" once, in repo settings — not doable from the codebase;
(2) the workflow fires from the default branch, so it runs after this feature's
branch merges (or via manual dispatch from that branch); (3) if pushing workflow
files is rejected by the git integration's permissions, the YAML is delivered in
the repo anyway as a normal file path — worst case the owner re-adds it manually.
Note: the CI workflow uses `./gradlew`; the wrapper works on GitHub runners (the
sandbox-proxy limitation that forces `/opt/gradle/bin/gradle` locally does not
apply there).

## R8: Testing wiring for a KMP module with an Android target

**Decision**: kotest `framework-engine`/`assertions-core` in `commonTest`;
`kotest-runner-junit5` + `useJUnitPlatform()` on the **desktop** JVM target's test
task only (`desktopTest`), mirroring `demo-console`'s wiring. Android unit/
instrumented tests are not used (they would duplicate the same commonTest logic on
a slower host); wasmJs tests are not wired (no browser harness, per spec).
The controller/rendering tests never reference Compose — they exercise exactly the
same surface the UI consumes.

**Rationale**: Same fast, deterministic test discipline as specs 001-008; the
desktop target exists chiefly to host these tests (spec Assumptions) and doubles as
a dev-run app for free.

## R9 (added during implementation): three environment/toolchain findings

1. **`core`/`content` must declare the `wasmJs` target.** A KMP library must
   declare every platform its consumers compile for -- `demo-app`'s wasmJs target
   could not resolve `project(":core")`/`project(":content")` until both declared
   `wasmJs { nodejs() }`. Build-file target declaration only, zero source changes
   (FR-008 was amended to make this carve-out explicit). Every dependency already
   publishes wasm-js variants (kotest 6.2.2, kotlinx-serialization 1.11.0 --
   verified live), and both modules' full kotest suites now run green on wasmJs as
   a third platform, strengthening the determinism evidence.
2. **Binaryen (wasm-opt) cannot be downloaded in this environment.** The Kotlin
   toolchain fetches it from GitHub releases, which this session's proxy blocks
   (403 -- GitHub access is scoped to the project repository only). Resolved by
   installing binaryen from npm (`npm install -g binaryen`, the allowed registry)
   and setting `BinaryenEnvSpec.download = false` for all projects in the root
   build (the same pattern as the existing Node.js download opt-out; note
   `BinaryenPlugin` applies per-subproject, unlike the Node root plugins). On
   GitHub runners (the Pages workflow) the default download works -- the opt-out
   only redirects *where* binaryen comes from, locally to the system PATH.
3. **AGP 8.13's embedded Kotlin (2.2.x) cannot consume Kotlin 2.4.0 binaries.**
   Android unit-test compilation and Android lint both run on AGP's embedded
   Kotlin and fail against this project's 2.4.0 metadata. Both are disabled for
   `demo-app` (`UnitTest*`/`lint*` tasks) -- consistent with research R8's
   already-decided testing strategy (the kotest suite runs on the desktop target;
   Android unit tests would only duplicate it on a slower host). The APK build
   itself (KGP-compiled) is unaffected.
