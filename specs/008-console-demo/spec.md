# Feature Specification: Console Demo

**Feature Branch**: `008-console-demo`

**Created**: 2026-07-12

**Status**: Draft

**Input**: User description: "Console demo: the demo-console module (currently a
JVM-only placeholder with a stub main()) becomes a playable, watchable, turn-based
battle -- the first end-to-end validation of specs 001-007 working together, and the
first place any battle occurrence is ever rendered as human-readable text. A player
controls their own party's actions turn by turn; enemies act automatically via a new
deterministic action-selection rule, since zero AI decision-making logic exists
anywhere in core today. The turn loop itself has never been driven end-to-end
anywhere in the existing test suite. This feature must also resolve a real gap: what
a combatant's ATTACK command or a specific skill actually does in combat
(formula/targeting/element) was never defined by specs 001-007 for anything except
limit breaks and summons -- resolved for exactly the shipped demo content's own
basic attack and skills, without inventing a new generic engine-wide catalog type.
Must consume Combatant/BattleState/CombatantId/resolveAction/TurnScheduler/status-
effects/BattleEvent/EventLog/limit-breaks/summons/loadContentPack from specs 001-007
without modifying their existing public behavior. Out of scope: any graphical UI;
tactical/grid positioning; save/load; anything beyond a single playable demo battle
from start to conclusion."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A complete battle plays out from start to a clear conclusion (Priority: P1)

A player starts the console demo, and a battle begins using the shipped demo
content. Turn order is determined automatically. On the player's own combatants'
turns, the player chooses an action; on every other combatant's turn, it acts on its
own, automatically. The battle continues, turn after turn, until one side has no
combatants left able to fight -- and the outcome (victory or defeat) is
unambiguous.

**Why this priority**: This is the foundation everything else in this feature
depends on -- without a working, complete battle loop, there is nothing to render
(User Story 2) and nothing to validate specs 001-007 against. It is also the
feature's core promise: a genuinely playable demo, not just a library of parts.

**Independent Test**: Start the demo, play a full battle through to its conclusion
by supplying actions for the player's own combatants when prompted, and verify the
battle always ends with a clear victory or defeat, with every enemy turn handled
automatically with no player input required.

**Acceptance Scenarios**:

1. **Given** the demo has started, **When** it is a player-controlled combatant's
   turn, **Then** the player is prompted to choose an action and its target(s).
2. **Given** the demo has started, **When** it is an automatically-controlled
   combatant's turn, **Then** it selects and submits a valid action on its own, with
   no prompt to the player.
3. **Given** a battle in progress, **When** every enemy has been defeated,
   **Then** the battle ends with an unambiguous victory outcome.
4. **Given** a battle in progress, **When** every player-controlled combatant has
   been defeated, **Then** the battle ends with an unambiguous defeat outcome.
5. **Given** the same starting content and the same sequence of player inputs,
   **When** the battle is played through twice, **Then** both playthroughs produce
   the identical outcome and the identical sequence of occurrences.

---

### User Story 2 - Every occurrence in the battle is visible as it happens (Priority: P1)

A player watching the console can see, in plain language, everything that happens
during the battle as it happens -- damage dealt, healing applied, a status effect
taking hold or wearing off, a combatant falling, a turn beginning, a class-combo
bonus triggering, a limit break unleashed, a summon called forth.

**Why this priority**: A battle loop that runs correctly but shows nothing isn't a
"console demo" -- it's a silent simulation. This is what actually makes User Story
1's mechanics into something a person can follow, watch, and understand; the two
together are what this feature's name promises.

**Independent Test**: Play through a battle and verify that each of the documented
occurrence kinds, when it happens, appears as readable text before the next turn
begins.

**Acceptance Scenarios**:

1. **Given** an action resolves, **When** the console output is inspected,
   **Then** it shows what happened (who was affected, and the effect) in plain
   language, not raw internal data.
2. **Given** a status effect is applied, ticks, or expires, **When** the console
   output is inspected, **Then** each of those moments is shown.
3. **Given** a combatant is defeated, **When** the console output is inspected,
   **Then** that is shown clearly, distinct from ordinary damage.
4. **Given** a class-combo synergy triggers, a limit break resolves, or a summon is
   cast, **When** the console output is inspected, **Then** each is shown distinctly
   from an ordinary action.

---

### User Story 3 - Every mechanic built so far is actually reachable in play (Priority: P2)

Playing through the shipped demo content naturally exercises the mechanics built in
specs 001-007 -- not just basic attacks, but a status effect ticking down, a limit
gauge filling and a limit break firing, and a class-combo synergy or a summon
resolving.

**Why this priority**: This is the payoff of "end-to-end validation" the feature
promises -- proving the whole engine works together, not just that a battle loop
runs. It depends on User Stories 1-2 already working, and the core demo is already
valuable without it, so it's hardening rather than foundational.

**Independent Test**: Play through the shipped demo content far enough and verify
that a status effect tick, a limit break use, and a synergy or summon resolution
each occur at least once over the course of the battle.

**Acceptance Scenarios**:

1. **Given** a full playthrough of the shipped demo content, **When** the sequence
   of occurrences is reviewed afterward, **Then** it includes at least one status
   effect tick, one limit break resolution, and one synergy trigger or summon cast.

---

### Edge Cases

- The player enters something that doesn't correspond to a valid action or a valid
  target for their combatant: the attempt is rejected the same way `resolveAction`
  already rejects it, the player is told why in plain language, and is able to try
  again -- never a crash, never a silently-skipped turn.
- Input ends unexpectedly (e.g. no more input is available): the demo ends
  gracefully rather than crashing or hanging indefinitely.
- A battle where both sides could theoretically be reduced to zero combatants on
  the same resolution: a defined, consistent tie-breaking outcome is used (see
  Assumptions) rather than an ambiguous double result.
- An automatically-controlled combatant has no valid action available at all (e.g.
  every granted command is currently unusable): it is skipped for that turn without
  ending the battle or crashing.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST run a complete battle, using the shipped demo
  content, from start to conclusion (victory or defeat), using the existing turn
  scheduler to determine whose turn it is at every step.
- **FR-002**: On a human-controlled combatant's turn, the system MUST prompt for
  and accept an action and its target(s), gated exactly the way action resolution
  already gates a submission -- an invalid submission MUST be rejected with a clear
  reason and MUST allow the player to try again, never crash and never silently
  skip the turn.
- **FR-003**: On an automatically-controlled combatant's turn, the system MUST
  select and submit a valid action on its own, deterministically (no randomness),
  without any human input.
- **FR-004**: The system MUST render every battle occurrence (damage dealt, healing
  applied, status effect applied/expired, combatant defeated, turn granted, synergy
  triggered, limit break used, summon cast) as human-readable text at the point it
  happens.
- **FR-005**: The system MUST end the battle with an unambiguous victory or defeat
  outcome the moment one side has no combatants left able to act, and MUST report
  that outcome clearly.
- **FR-006**: The system MUST define, for exactly the shipped demo content's own
  basic attack and skills, what each actually does in combat (its formula,
  targeting, and element where applicable) -- without introducing a new
  general-purpose engine catalog type for this.
- **FR-007**: The battle loop's own mechanics (turn order, action resolution,
  automatic action selection, win/loss detection) MUST be verifiable without
  requiring real console input or output.
- **FR-008**: A full playthrough of the shipped demo content MUST be able to
  exercise, at least once each, every mechanic built in specs 001-007: a status
  effect ticking, a limit break resolving, and a synergy trigger or summon cast.
- **FR-009**: This feature MUST consume the existing `Combatant`/`BattleState`/
  `CombatantId`/`resolveAction`/`TurnScheduler`/status-effects/`BattleEvent`/
  `EventLog`/limit-breaks/summons/`loadContentPack` model exactly as published by
  specs 001-007, without requiring any change to their existing public behavior.
- **FR-010**: Every part of this feature that affects the battle's outcome (the
  automatic action-selection rule, the loop's own mechanics) MUST be fully
  deterministic -- the same starting content and the same sequence of human inputs
  MUST always produce the identical outcome and the identical sequence of
  occurrences.

### Key Entities

- **Battle Session**: the running state of one playthrough -- battle state, turn
  schedule, active status effects, limit gauges, resources, and the accumulated
  event record -- from start until conclusion.
- **Automatic Action Rule**: the deterministic rule an automatically-controlled
  combatant uses to choose its action and target each turn.
- **Demo Action Definition**: what the shipped demo content's basic attack and each
  of its skills actually do in combat -- the gap this feature resolves for exactly
  that content.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A full playthrough of the shipped demo content always ends in an
  unambiguous victory or defeat, in 100% of observed runs.
- **SC-002**: Given the same starting content and the same sequence of human
  inputs, two playthroughs always produce the identical outcome and the identical
  sequence of occurrences.
- **SC-003**: An automatically-controlled combatant always submits a valid action
  on its turn -- never a rejection, a crash, or a stall -- in 100% of observed
  turns.
- **SC-004**: Every battle occurrence is visible as readable text at the moment it
  happens, in 100% of observed runs.
- **SC-005**: Invalid player input never ends the demo or a combatant's turn
  unrecoverably -- the player can always submit a valid action afterward, in 100%
  of observed cases.
- **SC-006**: Playing through the shipped demo content triggers at least one status
  effect tick, one limit break resolution, and one synergy trigger or summon cast
  over the course of a full battle.

## Assumptions

- **Automatic action rule (v1)**: deterministic and minimal -- an automatically-
  controlled combatant uses the first usable command in a fixed preference order
  (basic attack first) against the opposing combatant with the lowest current
  health, ties broken by participant order -- the same tie-break convention spec
  003 already established for turn order. A smarter or adaptive AI is a natural
  future extension this design doesn't preclude but doesn't build now (constitution
  Principle V).
- **Demo action definitions**: resolved as a small, additive mapping scoped only to
  the shipped demo content's own basic attack and skills -- not a new generic
  engine-wide catalog type. Exactly where this mapping lives (alongside the demo
  content it describes, or inline in the console module) is a technical decision
  deferred to `/speckit-plan`, mirroring every prior spec's central "how"
  deferral.
- **Testability of the loop apart from real console I/O**: the battle loop's own
  mechanics are designed to be verifiable independent of the literal terminal
  input/output -- a technical decision, not a business requirement, deferred to
  `/speckit-plan`.
- **Simultaneous-defeat tie-break**: if a single resolution would reduce both
  sides to zero able combatants at once, the battle is reported as a defeat (the
  player's side losing is the more conservative, unambiguous default absent a
  specific request for a draw outcome).
- **Status effect tick cadence** and when limit gauges/cross-catalog mechanics are
  exercised follow the conventions specs 003/004/006 already established (one tick
  per granted turn, per spec 004's uniform rule) -- this feature does not invent a
  new cadence.
- The specific demo content (which classes, skills, enemies, status effects,
  synergy, limit break, and summon are used) was already authored by feature 007;
  this feature only makes it playable, never redefines it.
- Any graphical/Compose UI, tactical/grid positioning, save/load, and multiplayer
  are explicitly out of scope, matching this project's stated non-goals and
  roadmap.
