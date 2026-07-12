# Feature Specification: Status Effects

**Feature Branch**: `004-status-effects`

**Created**: 2026-07-12

**Status**: Draft

**Input**: User description: "Alterable combatant states applied and removed during
battle (poison, stun/paralysis, defense buffs/debuffs, damage-over-time, stat
modifiers), modeled as State per the constitution, data-driven per spec 001's content
philosophy. Must integrate with the existing resolution pipeline: applied as a side
effect of resolveAction's outcomes (spec 002), modify stats/capabilities read during
resolution (a stun prevents scheduling/acting; a defense buff changes the DEFENSE stat
resolveAction reads), and tick down/expire over the turn sequence (spec 003). Must
consume Combatant/BattleState/CombatantId/resolveAction/TurnScheduler from specs
001–003 without modifying their public contracts. Out of scope: the specific catalog
of status effects the demo will ship (feature 007 content); this feature defines the
engine mechanism only."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Apply a status effect and watch it expire on schedule (Priority: P1)

A content designer or engine consumer applies a status effect to a combatant — as a
result of a resolved action, or directly — and can observe it as active on that
combatant afterward. As battle turns proceed, the effect's remaining duration
decreases, and once it reaches zero the effect is automatically removed with no
further action required.

**Why this priority**: This apply–track–tick–expire lifecycle is what makes "status
effect" a real, bounded concept rather than a permanent, unmanaged marker. It is the
smallest slice that is meaningfully testable end-to-end and every other status
behavior (buffs, stun, damage-over-time) is built on top of this same lifecycle.

**Independent Test**: Apply a status effect with a known duration to a combatant,
advance the turn sequence the documented number of times, and verify the effect is
reported active immediately after application and reported gone once its duration is
exhausted — with no manual cleanup step required.

**Acceptance Scenarios**:

1. **Given** a combatant with no active effects, **When** a status effect is applied to
   it, **Then** that effect is immediately reported as active on that combatant.
2. **Given** an active status effect with 3 turns of remaining duration, **When** the
   turn sequence advances once, **Then** its remaining duration is 2 and it is still
   reported active.
3. **Given** an active status effect with 1 turn of remaining duration, **When** the
   turn sequence advances once more, **Then** the effect is no longer reported active
   on that combatant, with no separate removal step needed.
4. **Given** a status effect was applied as the outcome of a resolved combat action,
   **When** the target is inspected immediately afterward, **Then** the effect is
   already active — application does not require a separate follow-up step.

---

### User Story 2 - A stat-modifying effect changes resolution outcomes (Priority: P2)

A content designer applies a status effect that adjusts one of a combatant's stats
(e.g., a defense buff or debuff). While that effect is active, actions resolved
against that combatant use the adjusted stat value; the combatant's underlying stats
(as defined in spec 001) are never altered — only what resolution reads is different.

**Why this priority**: This is the first of three ways a status effect's mechanical
consequence becomes observable (alongside incapacitation and damage-over-time); each
is independently valuable and testable once User Story 1's lifecycle exists, so this
hardens rather than blocks the MVP.

**Independent Test**: Resolve an identical attack against a combatant once with no
modifiers and once with an active defense-increasing modifier; verify the modified
case takes less damage while the combatant's own base stats remain identical in both
runs.

**Acceptance Scenarios**:

1. **Given** an active stat-increasing modifier on a combatant's Defense, **When** an
   attack is resolved against that combatant, **Then** the resolved damage is lower
   than an otherwise-identical attack would deal without the modifier.
2. **Given** an active stat-decreasing modifier, **When** an attack is resolved,
   **Then** the resolved damage is higher than without the modifier.
3. **Given** an active stat modifier of any kind, **When** the combatant's underlying
   stats are inspected, **Then** they are unchanged — only resolution's reading of them
   differs.
4. **Given** two different active modifiers affecting the same stat, **When** an
   action is resolved, **Then** their effects combine additively into one adjusted
   value, not just the most recently applied one.

---

### User Story 3 - An incapacitating effect removes a combatant from the turn order (Priority: P2)

A content designer applies a status effect that incapacitates a combatant (e.g., a
stun). While that effect remains active, the turn scheduler never offers that
combatant a turn; once the effect ends, normal scheduling resumes for that combatant.

**Why this priority**: A second independently valuable and testable consequence of the
same User Story 1 lifecycle; not required for the MVP but essential to the feature's
stated scope (stun/paralysis was explicitly requested).

**Independent Test**: Apply an incapacitating effect to one combatant in a multi-
combatant battle, advance the turn sequence many times, and verify that combatant
never receives a turn while the effect is active, and resumes receiving turns once it
ends.

**Acceptance Scenarios**:

1. **Given** an active incapacitating effect on a combatant, **When** the turn
   scheduler is queried repeatedly, **Then** that combatant is never the one reported
   ready.
2. **Given** an incapacitating effect that has just ended, **When** the turn scheduler
   is next queried, **Then** that combatant becomes eligible to be scheduled again
   under the normal rules.
3. **Given** an incapacitated combatant among otherwise-eligible combatants, **When**
   turns are requested and consumed, **Then** only non-incapacitated combatants are
   ever reported ready.

---

### User Story 4 - A damage-over-time effect harms its target automatically (Priority: P2)

A content designer applies a damage-over-time effect (e.g., poison) to a combatant.
Each time the effect ticks, the target's health decreases by the effect's documented
amount automatically — no actor submits an action to cause this damage — following the
same health bounds already guaranteed for action resolution.

**Why this priority**: The third independently valuable and testable consequence of
User Story 1's lifecycle; equally important to the feature's stated scope but not
required for the base lifecycle to be useful.

**Independent Test**: Apply a damage-over-time effect with a known per-tick amount to
a combatant at known health, advance the turn sequence through several ticks, and
verify health decreases by exactly the documented amount each tick, never below zero.

**Acceptance Scenarios**:

1. **Given** an active damage-over-time effect, **When** it ticks, **Then** the
   target's health decreases by the effect's documented amount, with no action having
   been submitted by any actor.
2. **Given** a damage-over-time effect that would reduce health below zero on its next
   tick, **When** that tick occurs, **Then** the target's health is clamped at zero,
   never negative.
3. **Given** a damage-over-time effect ticks while its target is already at zero
   health, **When** that tick is processed, **Then** it has no further effect (health
   stays at zero).

---

### User Story 5 - Reapplying an already-active effect behaves predictably (Priority: P3)

A content designer applies a status effect to a combatant that already has that same
effect active. The result follows one fixed, documented rule (this engine's rule:
refresh the remaining duration to full, without stacking magnitude), so the outcome is
never ambiguous or inconsistent between otherwise-identical situations.

**Why this priority**: Hardens correctness for a real but secondary situation (repeat
application); the feature is useful and testable without exercising this specifically,
so it lands last.

**Independent Test**: Apply the same status effect to a combatant twice, with turns
advancing between the two applications, and verify the second application resets the
remaining duration to the effect's full duration rather than adding to it or being
rejected.

**Acceptance Scenarios**:

1. **Given** a combatant with an active effect at partial remaining duration, **When**
   the same effect is applied again, **Then** its remaining duration resets to the
   effect's full documented duration.
2. **Given** a stat-modifying effect reapplied per scenario 1, **When** resolution
   reads the affected stat, **Then** the adjustment magnitude is unchanged (not
   doubled) by the reapplication.

---

### Edge Cases

- A status effect definition with a duration of zero or negative: rejected at catalog
  validation time (spec 001's load-time-validation precedent), never applied at
  runtime.
- A stat modifier would reduce the affected stat below zero: the effective value read
  during resolution is floored at zero, matching spec 001's non-negative stat
  invariant.
- Removing an effect that is not currently active on the named combatant: a no-op, not
  an error — removal is idempotent.
- A combatant carrying active effects becomes defeated: it is already excluded from
  scheduling by spec 003's existing defeated-combatant rule; no additional handling is
  required by this feature.
- A combatant has multiple different active effects simultaneously (e.g., a stat
  modifier and an incapacitating effect at once): each applies and ticks
  independently; nothing about one effect suppresses another.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST represent status effects as data-driven definitions —
  identifier, display name, one closed engine-recognized kind (stat modifier /
  incapacitate / damage-over-time), kind-specific parameters, and a default duration —
  never as one code class per effect (constitution Principle II).
- **FR-002**: The system MUST allow a status effect to be applied to a specific
  combatant, either as part of a resolved combat action's outcome (extending spec
  002's resolution results) or via a direct application request, producing new
  battle-time state that tracks, per combatant, every currently active effect
  instance and its remaining duration.
- **FR-003**: The system MUST remove an active status effect from a combatant when
  either its remaining duration reaches zero (automatic expiry) or an explicit,
  idempotent removal request names that combatant and effect (manual removal).
- **FR-004**: For a stat-modifier-kind effect, the system MUST ensure that stat values
  read during action resolution (spec 002) reflect every currently active modifier on
  the affected combatant — multiple modifiers on the same stat combine additively —
  without altering that combatant's underlying base/derived stats (spec 001).
- **FR-005**: For an incapacitate-kind effect, the system MUST exclude the affected
  combatant from being offered a turn by the turn scheduler (spec 003) for as long as
  the effect remains active, and MUST allow normal scheduling to resume once it ends.
- **FR-006**: For a damage-over-time-kind effect, the system MUST apply its documented
  damage automatically on each tick, without requiring any actor to submit an action,
  following the same health-bounds clamping already guaranteed for action resolution
  (spec 002 FR-011).
- **FR-007**: The system MUST define how an incapacitating effect's own remaining
  duration decreases even though the affected combatant does not receive ordinary
  turns from the scheduler while it is active.
  [NEEDS CLARIFICATION: should an incapacitated combatant still be granted a turn slot
  by the scheduler that is then automatically consumed without allowing an action (so
  the incapacitated combatant's own accruing turn-readiness is what ticks its status
  down), or should incapacitate-kind effects instead tick down based on the passage of
  *other* combatants' turns (a time-based measure independent of the incapacitated
  combatant's own readiness)? These produce materially different pacing: the first
  ties stun duration to how quickly the stunned combatant itself would have acted, the
  second ties it to how much the rest of the battle progresses around it.]
- **FR-008**: Reapplying a status effect that is already active on the same combatant
  MUST refresh its remaining duration to the effect's full documented duration and
  MUST NOT stack or otherwise increase its magnitude.
- **FR-009**: A combatant MAY have multiple different status effects active
  simultaneously; each affects resolution and ticks toward expiry independently of the
  others.
- **FR-010**: Every behavior above MUST be fully deterministic — no randomness in
  whether, how, or when a status effect applies, modifies resolution, ticks, stacks,
  or expires (constitution Principle I).
- **FR-011**: This feature MUST consume the existing `Combatant`/`BattleState`/
  `CombatantId`/`resolveAction`/`TurnScheduler` model exactly as published by specs
  001–003, without requiring any change to their existing public signatures (purely
  additive extensions are acceptable, matching how spec 002 and spec 003 each added
  new capabilities without altering prior contracts).

### Key Entities

- **Status Effect Definition**: data describing one kind of effect — identifier,
  display name, kind (stat modifier / incapacitate / damage-over-time), kind-specific
  parameters (e.g., which stat and by how much; damage per tick), and default
  duration.
- **Active Effect**: one application of a Status Effect Definition to one combatant,
  tracked with its remaining duration; distinct from the definition itself the same
  way spec 001 distinguishes a class definition from a character that references it.
- **Effect Kind**: the closed, engine-recognized category (stat modifier / incapacitate
  / damage-over-time) that determines how an Active Effect is interpreted during
  resolution and ticking.
- **Tick**: the event, driven by the turn sequence (spec 003), at which every
  combatant's active effects have their remaining duration decreased and any
  duration-triggered or per-tick behavior (damage-over-time) applied.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A status effect applied via a resolved action's outcome is discoverable
  as active on its target immediately afterward, in 100% of observed cases.
- **SC-002**: An active status effect's remaining duration decreases deterministically
  over the turn sequence and the effect is removed after exactly its documented
  duration has elapsed, every time.
- **SC-003**: An otherwise-identical attack resolves to measurably less damage against
  a combatant with an active defense-increasing modifier, and measurably more against
  one with an active defense-decreasing modifier, while that combatant's own base
  stats remain unchanged in every case.
- **SC-004**: An incapacitated combatant receives zero turns from the scheduler for
  the effect's entire active duration, in 100% of observed cases, and resumes normal
  scheduling immediately once the effect ends.
- **SC-005**: A damage-over-time effect reduces its target's health by exactly its
  documented per-tick amount on every tick it is active, never pushing health below
  zero, with no action submitted by any actor.
- **SC-006**: Reapplying the same status effect to an already-affected combatant
  produces the identical documented outcome (refreshed duration, unchanged magnitude)
  100% of the time.
- **SC-007**: Given the same starting battle state and the same sequence of turns and
  actions, status effect application, modification of resolution, ticking, and expiry
  all replay identically every time.

## Assumptions

- Stacking policy (FR-008) is fixed by this engine as "refresh duration, never stack
  magnitude" — the genre-standard default for this project's Final Fantasy-style
  target; per-effect-configurable stacking policies are not built now (constitution
  Principle V) and are left to a future feature if ever needed.
- "The turn sequence" (per the input) means ticking is driven by spec 003's
  `TurnScheduler`, which has no "round" concept — ticks happen relative to individual
  turns, not a global clock; the precise trigger point for incapacitated combatants is
  what FR-007's clarification resolves.
- The specific catalog of status effects the demo will ship (poison's exact
  damage-per-tick, a defense buff's exact magnitude, stun's exact duration) is
  content, deferred to feature 007 — this spec defines the mechanism and its closed
  set of effect kinds only, the same boundary spec 001 drew around class/enemy
  specifics and spec 002 drew around exact formula magnitudes.
- Achieving stat-modifier and health-changing (damage-over-time) effects without
  altering `resolveAction`'s or `BattleState`'s existing signatures may require a
  minimal, purely additive extension to specs 001–002's public surface (new functions
  only, never changes to existing ones) — the exact mechanism is a technical
  decision for `/speckit-plan`.
- Manual removal (e.g., a future "cure" action) is in scope as a mechanism
  (remove-by-effect-identifier); authoring any specific cure content is out of scope
  (feature 007).
- Resistance or immunity to specific status effects (e.g., "this enemy cannot be
  poisoned") is not modeled by this feature; it is a natural future extension this
  design does not preclude, but it is not built now (constitution Principle V).
