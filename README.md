# sddrpg — reusable turn-based combat engine

A game-agnostic, deterministic, data-driven turn-based combat engine in Kotlin
Multiplatform, developed end-to-end with **Spec-Driven Development** using
[GitHub Spec Kit](https://github.com/github/spec-kit).

## Modules

| Module | Status | Purpose |
|---|---|---|
| `core` | specs 001-006, 008 implemented | Pure KMP combat engine (no platform/UI deps, seeded RNG, data-driven) — combatant/class model + action/damage resolution + turn scheduler + status effects + battle events/synergies + limit breaks/summons + a deterministic automatic action rule + battle-outcome detection done |
| `content` | specs 007-008 implemented | Content definitions (classes, skills, spells, enemies…) + loaders — `loadContentPack` parses/validates a full content pack, including cross-catalog reference checks; the shipped demo content's own action definitions (`DemoActionDefinition`/`DEMO_ACTIONS`) and playable roster assembly (`buildDemoBattleState`) |
| `demo-console` | spec 008 implemented | JVM console demo (Final Fantasy-style) — the first end-to-end validation of specs 001-007 working together: a complete, watchable, playable battle from start to victory/defeat |
| `tactical` | reserved slot | Optional grid-positioning module (future milestone, not a Gradle module yet) |
| `demo-app` | spec 009 implemented | Compose Multiplatform battle UI (Android APK + web/wasmJs + desktop) — the same shipped demo battle, playable by touch/click via an event-driven `BattleController` over the unmodified engine |

Dependency direction: `demo-*` → `content` → `core`. See
[.specify/memory/constitution.md](.specify/memory/constitution.md) for the project
principles that govern all development.

## SDD workflow

Every feature goes through the Spec Kit skills, in order:

`/speckit-constitution` → `/speckit-specify` → `/speckit-clarify` → `/speckit-plan` →
`/speckit-tasks` → `/speckit-analyze` → `/speckit-implement`

Specs live under `specs/`. Planned spec sequence:

1. **001 — Combatant & class model** ✅ *(implemented)*: combatant kinds (party / enemy /
   temporary ally), character classes, enemy archetypes, stats, elemental affinities (as data).
2. **002 — Actions & damage resolution** ✅ *(implemented)*: combat actions as Command,
   damage/heal formulas as Strategy, elemental adjustment, targeting shapes, battle-time
   health tracking (`BattleState`).
3. **003 — Turn scheduling** ✅ *(implemented)*: `TurnScheduler<State>` abstraction,
   classic/ATB implementation (speed-driven readiness, deterministic tie-breaking).
4. **004 — Status effects** ✅ *(implemented)*: data-driven `EffectKind` (stat
   modifier / incapacitate / damage-over-time), apply/tick/expire lifecycle,
   integration via a `Combatant`-delegation wrapper — no changes to specs 001–003.
5. **005 — Battle events, synergies & combos** ✅ *(implemented)*: `BattleEvent`/`EventLog`
   (pure event-derivation over specs 002-004's unmodified outputs — no callbacks) and a
   data-driven `SynergyDefinition` (two required skills, same-target, turn window,
   bonus) detected by a nearest-prior-match scan over the log.
6. **006 — Limit breaks & summons** ✅ *(implemented)*: a per-combatant `LimitGaugeState`
   charged purely by scanning spec 005's already-derived `BattleEvent.DamageDealt`
   events; both mechanics resolve by synthesizing a `CombatAction` from existing
   fields and delegating to `resolveAction` unmodified (limit breaks reuse
   `CommandKind.SKILL`, summons reuse `CommandKind.SUMMON`, which spec 001 already
   declared for exactly this purpose).
7. **007 — Content loading** ✅ *(implemented)*: `loadContentPack(text): ContentLoadResult`
   in the `content` module — DTOs + mapping functions (not retrofitted annotations)
   keep specs 001-006 untouched, every existing `validate*Catalog` function runs
   unconditionally, and four cross-catalog reference checks catch dangling
   identifiers no single spec's own validation can see (e.g. a class's limit break
   id that has no matching `LimitBreakDefinition`). Ships a small demo content pack
   exercising every mechanic.
8. **008 — Console demo** ✅ *(implemented)*: `demo-console` drives specs 001-007's
   turn loop end-to-end for the first time (`TurnScheduler` → `resolveAction` →
   status-effect tick → event derivation → synergy/limit-gauge bookkeeping),
   gates human input exactly as `resolveAction` gates it, and gives
   automatically-controlled combatants a small deterministic action rule
   (`core.ai.selectAutomaticCommand`/`selectAutomaticSkill`/`selectAutomaticTarget`,
   reusable beyond this one demo). Every battle occurrence renders as plain text —
   the first place `core`'s events become human-readable, per constitution
   Principle IV. Research also surfaced and fixed a real, previously-uncovered
   defect (`RosterBuilder.addEnemy` always producing an empty command set, so no
   enemy could ever act) via `Combatant` interface delegation in `content`, without
   touching any spec 001-007 file. This completes the originally-planned 8-spec
   roadmap for `core` + `content` + `demo-console`.
9. **009 — Compose demo app** ✅ *(implemented)*: `demo-app` becomes a real Compose
   Multiplatform module — the same shipped demo battle through a graphical
   touch/click screen (health bars, scrolling event log, action buttons, target
   picker with cancel, victory/defeat overlay). The central piece is
   `BattleController`, an event-driven, UI-framework-free inversion of the console
   demo's blocking loop: the UI observes an immutable `uiState` value and pushes
   submissions in, while automatic turns advance internally with spec 008's exact
   per-turn bookkeeping — no coroutines, fully kotest-tested with zero UI. Targets:
   Android (installable debug APK), web/wasmJs (static site, deployable to GitHub
   Pages via `.github/workflows/deploy-pages.yml`), desktop/JVM (test host + dev
   app). `core`/`content` gained a `wasmJs` target declaration (build files only,
   zero source changes) so the web target can consume them — their kotest suites
   now run on a third platform.

## Build

```bash
./gradlew build                                # all modules + the kotest suites
./gradlew :demo-app:assembleDebug              # the installable Android APK
./gradlew :demo-app:wasmJsBrowserDistribution  # the deployable web build
./gradlew :demo-app:run                        # desktop dev app
```

Android builds need an SDK (`local.properties` with `sdk.dir=...`, platform 36).
Web deploys to GitHub Pages once the owner enables Pages (Source = GitHub Actions).

Toolchain: Kotlin 2.4.0, Gradle 8.14.3, JDK 21, kotest 6, kotlinx.serialization,
Compose Multiplatform 1.11.1, AGP 8.13.2.
