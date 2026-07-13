# Feature Specification: Compose Demo App

**Feature Branch**: `009-compose-demo-app`

**Created**: 2026-07-12

**Status**: Draft

**Input**: User description: "Compose Multiplatform demo app: the demo-app reserved
module slot (currently just a README placeholder, intentionally not included in
settings.gradle.kts) becomes a real Gradle module -- a graphical, touch/click-driven
battle UI consuming core and content exactly as published by specs 001-008, with zero
changes to any earlier spec's files. The player fights the same shipped demo battle
(cloud and aerith vs. the bomb) but through a graphical screen instead of a terminal:
party/enemy health bars, a scrolling battle log where every battle occurrence appears
as readable text, action buttons on a player-controlled combatant's turn, a target
picker, and an unambiguous victory/defeat overlay. Enemies act automatically via spec
008's deterministic rule. demo-console's runBattleLoop is a *blocking* loop driven by
a readInput callback -- unusable from an event-driven GUI, so this feature needs an
event-driven inversion of that same orchestration. Deliverables the user explicitly
requested: (1) an installable Android APK to test the battle on a real phone, and
(2) optionally the web version deployable to GitHub Pages. Out of scope:
tactical/grid positioning; save/load; multiplayer; iOS target; content editing UI;
any change to core/content/demo-console beyond adding the new module to the root
Gradle settings."

> Constitution gate note: the constitution's v1 non-goal was explicitly conditional
> ("No `demo-app` (Compose Multiplatform) **until the console demo validates the
> core**"). Spec 008 delivered and validated the console demo end-to-end, so that
> condition is satisfied and this feature is in scope without a constitution
> amendment.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - The same battle, playable through a graphical screen (Priority: P1)

A player opens the app and the shipped demo battle begins. The screen shows both
sides' combatants with their current health at all times. When it is one of the
player's combatants' turns, the available actions appear as buttons; picking one
prompts for a target where a choice is needed; the chosen action resolves exactly as
the engine resolves it. Every other combatant acts automatically. The battle
continues until one side can no longer fight, and the outcome is displayed
unmistakably.

**Why this priority**: This is the feature itself — a graphical, touch-driven
version of the battle the console demo proved. Without it there is nothing to
install on a phone or deploy to the web.

**Independent Test**: Open the app, play a full battle to its conclusion by tapping
actions and targets, and verify the same engine guarantees hold as in the console
demo: prompts only on the player's combatants' turns, automatic enemy turns,
health never out of bounds, and a clear victory or defeat.

**Acceptance Scenarios**:

1. **Given** the app has started, **When** the battle screen appears, **Then** every
   participant's name and current/maximum health are visible, and they remain
   accurate after every action.
2. **Given** it is a player-controlled combatant's turn, **When** the player picks
   an available action and a valid target, **Then** the action resolves and its
   effects appear in the log and on the health displays.
3. **Given** it is a player-controlled combatant's turn, **When** the player picks
   an action or target the engine would reject, **Then** the reason is shown in
   plain language and the player can pick again — never a crash, never a lost turn.
4. **Given** it is an automatically-controlled combatant's turn, **When** the turn
   plays out, **Then** no action buttons are offered to the player and the
   combatant's chosen action resolves on its own.
5. **Given** one side has no combatants left able to fight, **When** the last
   resolution completes, **Then** a victory or defeat overlay appears immediately
   and no further turns are offered.
6. **Given** the same sequence of player choices, **When** the battle is played
   twice, **Then** both playthroughs produce the identical sequence of occurrences
   and the identical outcome.

---

### User Story 2 - Every occurrence is visible as readable text and reflected in the display (Priority: P1)

While the battle runs, a scrolling log shows every occurrence — damage, healing,
status effects taking hold/ticking/wearing off, defeats, turns, synergies, limit
breaks, summons — in plain language as it happens, and the always-visible health
displays stay consistent with what the log says.

**Why this priority**: A graphical battle whose events are invisible or inconsistent
with the display is worse than the console demo, not better. This is what makes the
screen trustworthy.

**Independent Test**: Play through a battle and verify each occurrence kind, when it
happens, appears in the log before the next turn starts, and the health bars always
match the last logged values.

**Acceptance Scenarios**:

1. **Given** any action resolves, **When** the log is inspected, **Then** the
   occurrence is described in plain language (who, what, effect), never as raw
   internal data.
2. **Given** a status effect is applied, ticks, or expires, **When** the log is
   inspected, **Then** each of those moments appears.
3. **Given** a synergy triggers, a limit break resolves, or a summon is cast,
   **When** the log is inspected, **Then** each is shown distinctly from an
   ordinary action.
4. **Given** any logged health-changing occurrence, **When** the health displays are
   compared to it, **Then** they agree.

---

### User Story 3 - The battle installs on a phone and (optionally) runs on the web (Priority: P2)

The player installs a provided app package on their Android phone and plays the
battle with touch. Optionally, the same battle is reachable in a web browser via a
public page.

**Why this priority**: This is the user's explicitly requested deliverable — the
proof that the same engine and UI genuinely run beyond the development machine. It
depends entirely on User Stories 1-2 already working.

**Independent Test**: Install the produced package on an Android device and play a
full battle by touch; open the deployed web page in a browser and do the same.

**Acceptance Scenarios**:

1. **Given** the produced installable package, **When** it is installed and opened
   on an Android phone, **Then** the full battle is playable by touch from start to
   conclusion.
2. **Given** the web build is deployed, **When** the public page is opened in a
   modern browser, **Then** the same battle is playable there.

---

### Edge Cases

- The player taps an action button but then wants a different action before picking
  a target: they can back out of the target picker without losing the turn.
- The player picks a target the engine rejects for that action's shape (e.g. an
  already-defeated combatant): the reason is shown and the turn is still theirs.
- A limit break or summon is offered only when the engine would actually accept it
  (gauge full / sufficient resource); attempting one that has just become invalid is
  rejected with the reason, never a crash.
- Several automatic turns occur in a row: each one's occurrences appear in the log
  in order; the player is never asked for input during them.
- The battle ends during an automatic combatant's turn: the overlay still appears
  immediately, without waiting for player input.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST present the shipped demo battle graphically: every
  participant visible with name and current/maximum health at all times, updated
  after every resolution.
- **FR-002**: On a player-controlled combatant's turn, the system MUST offer that
  combatant's available actions as selectable controls, collect a target where the
  action requires one, and submit the choice gated exactly the way the engine
  already gates submissions — a rejected submission MUST show its reason in plain
  language and return the choice to the player, never crash and never consume the
  turn.
- **FR-003**: On an automatically-controlled combatant's turn, the system MUST
  select and resolve that combatant's action with no player involvement, using the
  engine's published deterministic automatic-action rule.
- **FR-004**: The system MUST render every battle occurrence as readable text in a
  scrolling log at the moment it happens, covering every occurrence kind the engine
  produces (damage, healing, status applied/expired, defeat, turn granted, synergy,
  gauge full, limit break, summon).
- **FR-005**: The system MUST end the battle with an unambiguous, prominently
  displayed victory or defeat the moment one side has no combatants able to act.
- **FR-006**: The system MUST offer limit break and summon submissions when — and
  only when — the engine would accept them, resolving them through the engine's
  published limit-break/summon resolution exactly as the console demo does.
- **FR-007**: The battle orchestration (turn progression, submission gating,
  automatic turns, per-turn bookkeeping, win/loss detection) MUST be verifiable
  without any graphical interface, driven and observed purely programmatically —
  and MUST preserve spec 008's established per-turn bookkeeping order (event
  derivation, synergy detection/bonus, limit-gauge charging, one status tick per
  granted turn, effective-state derivation before the next turn).
- **FR-008**: This feature MUST consume specs 001-008's published behavior exactly
  as-is: zero changes to any existing *source* file in `core`, `content`, or
  `demo-console`. The only permitted changes outside the new module are build
  infrastructure (not spec artifacts): the root build wiring that registers the
  module, and — a constraint discovered during implementation — declaring the web
  platform target in `core`/`content`'s build files, since a multiplatform library
  must declare every platform its consumers compile for. Target declarations add a
  compilation platform to unchanged common code; no source line of any earlier spec
  changes, and their existing suites must stay green on every platform including
  the new one.
- **FR-009**: Everything that affects the battle's outcome MUST be fully
  deterministic: the same starting content and the same sequence of player choices
  MUST always produce the identical sequence of occurrences and the identical
  outcome.
- **FR-010**: The feature MUST produce an installable Android application package
  playable by touch on a real phone, and a web build deployable as a static public
  page — from the same shared battle UI and orchestration, not parallel
  implementations.

### Key Entities

- **Battle Session Controller**: the event-driven counterpart of the console demo's
  blocking loop — holds one playthrough's state, exposes what the UI needs to
  observe (whose turn it is, which actions/targets are offerable, the rendered log
  so far, whether the battle is over) and accepts player submissions, advancing
  automatic turns internally.
- **Battle Screen**: the single graphical screen — health displays, scrolling log,
  action controls, target picker, outcome overlay.
- **Deliverable Packages**: the installable Android package and the static web
  build, both wrapping the same shared screen and controller.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A full playthrough via the graphical screen always ends in an
  unambiguous victory or defeat, in 100% of observed runs.
- **SC-002**: Given the same sequence of player choices, two playthroughs always
  produce the identical sequence of logged occurrences and the identical outcome.
- **SC-003**: Every battle occurrence kind the engine produces is visible as
  readable text at the moment it happens, in 100% of observed runs, and the health
  displays always agree with the log.
- **SC-004**: An invalid player selection never ends the battle, crashes the app,
  or consumes the turn — the player can always complete a valid submission
  afterward, in 100% of observed cases.
- **SC-005**: The Android package installs and runs a complete touch-driven battle
  on a real device; the web build serves a complete battle in a modern browser.
- **SC-006**: The battle orchestration is fully exercised by automated tests with
  no graphical interface involved, at the same green-suite bar as every prior spec.

## Assumptions

- **Event-driven controller**: the console demo's blocking loop cannot serve a GUI,
  so this feature provides an event-driven equivalent of the same orchestration,
  built from the same published engine APIs. Its exact shape (state exposure,
  submission entry points, auto-turn advancement) is the central technical "how"
  deferred to `/speckit-plan`, per every prior spec's pattern.
- **Multiplatform placement**: all UI and orchestration live in shared
  multiplatform code so future targets never restructure anything; the
  console demo's own JVM-only files are spec 008 artifacts and stay untouched —
  this feature ships its own shared equivalents (log-text rendering included).
- **Targets**: Android (the requested APK), web (the optional public page), and a
  desktop/JVM target whose primary purpose is running the orchestration tests with
  the project's established fast test discipline — web-browser test harnesses are
  deliberately avoided.
- **Web deployment**: "deployable to GitHub Pages" is satisfied by producing the
  static web build plus a ready-to-use deployment workflow; actually enabling Pages
  on the repository is the repository owner's one-time manual step (surfaced in the
  quickstart), since it cannot be done from inside the codebase.
- **Same demo content**: the battle is the shipped spec 007 demo content, loaded
  through the published loader, assembled with spec 008's published demo roster —
  this feature authors no new game content.
- **Visual design**: a clean, functional battle screen is sufficient; polished
  art/animation/sound are explicitly not success criteria for this milestone.
- Out of scope: tactical/grid positioning, save/load, multiplayer, iOS target,
  content-editing UI, and any change to `core`/`content`/`demo-console` files.
