# sddrpg — reusable turn-based combat engine

A game-agnostic, deterministic, data-driven turn-based combat engine in Kotlin
Multiplatform, developed end-to-end with **Spec-Driven Development** using
[GitHub Spec Kit](https://github.com/github/spec-kit).

## Modules

| Module | Status | Purpose |
|---|---|---|
| `core` | specs 001-006 implemented | Pure KMP combat engine (no platform/UI deps, seeded RNG, data-driven) — combatant/class model + action/damage resolution + turn scheduler + status effects + battle events/synergies + limit breaks/summons done |
| `content` | scaffolded | Content definitions (classes, skills, spells, enemies…) + loaders |
| `demo-console` | scaffolded | JVM console demo (Final Fantasy-style), first end-to-end validation |
| `tactical` | reserved slot | Optional grid-positioning module (future milestone, not a Gradle module yet) |
| `demo-app` | reserved slot | Compose Multiplatform app for Android/web (future milestone) |

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
7. 007 — Content loading (`content` module).
8. 008 — Console demo (`demo-console`).

## Build

```bash
./gradlew build   # compiles all modules and runs the kotest suite
```

Toolchain: Kotlin 2.4.0, Gradle 8.14.3, JDK 21, kotest 6, kotlinx.serialization.
