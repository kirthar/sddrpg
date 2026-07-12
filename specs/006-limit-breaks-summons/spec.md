# Feature Specification: Limit Breaks & Summons

**Feature Branch**: `006-limit-breaks-summons`

**Created**: 2026-07-12

**Status**: Draft

**Input**: User description: "Limit breaks & summons: two class-flavored, resource-
gated burst mechanics that build on specs 001-005. Limit breaks: spec 001's
ClassDefinition already declares a class's limitBreaks as a Set<LimitBreakId>
(validated for referential integrity only -- no LimitBreakDefinition, no gauge, no
trigger behavior exists yet in any spec). This feature gives limit breaks real
behavior: a per-combatant accumulating "limit gauge" that charges from battle
occurrences (most naturally: taking damage, and/or dealing damage -- derived from
spec 005's BattleEvent log, reusing it as the accrual data source rather than
inventing a second observation mechanism), and once a combatant's gauge reaches its
threshold, their class's limit break skill becomes an available, especially powerful
action for one use, gated the same way spec 002 already gates ATTACK/SKILL/MAGIC by
capability, then the gauge resets. Summons: a data-driven, class-agnostic burst effect
(the classic "call forth a powerful ally for one devastating attack, then it's gone"
pattern) triggered as a combat action consuming a resource (e.g. MP), resolved through
spec 002's existing action/damage resolution mechanics -- NOT by dynamically adding a
new participant to BattleState mid-battle. Must consume Combatant/BattleState/
CombatantId/resolveAction/TurnScheduler/status-effects/BattleEvent/EventLog from specs
001-005 without modifying their public contracts. Out of scope: the specific
limit-break/summon catalog content for the demo game (feature 007) and any UI/
rendering of gauges or summon animations (constitution Principle IV forbids that in
core); this feature defines the two mechanisms only."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A combatant's limit gauge fills from the intensity of battle (Priority: P1)

A combatant takes damage over the course of a battle, and a personal "limit gauge"
rises in response -- harder hits fill it faster than glancing ones -- building toward
a threshold defined by their class's signature limit break.

**Why this priority**: This is the resource half of the mechanic; without it there is
nothing for User Story 2 to gate on. It delivers value on its own (a demo or test
author can already observe gauge state rising and inspect it) even before a limit
break can be used.

**Independent Test**: Apply a sequence of damaging occurrences to a combatant and
verify their gauge rises by exactly the documented amount each time, starting at zero,
never exceeding the threshold.

**Acceptance Scenarios**:

1. **Given** a combatant with a fresh (zero) limit gauge, **When** they take damage,
   **Then** their gauge rises by an amount reflecting that damage.
2. **Given** a combatant's gauge partway to threshold, **When** they take further
   damage that would push the gauge past threshold, **Then** the gauge is capped at
   threshold, never exceeding it.
3. **Given** a combatant whose class declares no limit break, **When** they take
   damage, **Then** no gauge state that could ever become usable is produced for them.

---

### User Story 2 - A full limit gauge unlocks one especially powerful action (Priority: P1)

Once a combatant's limit gauge reaches its threshold, their class's limit break
becomes available to use like any other action -- gated the same way an ungranted
skill would be -- and using it resolves as an especially powerful effect against its
target(s), after which the gauge resets to build toward the next one.

**Why this priority**: This is the mechanic's namesake payoff -- a gauge that never
unlocks anything doesn't deliver what "limit breaks" promises. Both this and User
Story 1 are required together for the feature's stated purpose, mirroring how spec
005 treated its event-log foundation and its synergy-trigger payoff as equally P1.

**Independent Test**: Fill a combatant's gauge to threshold, use their limit break,
and verify it resolves as a powerful effect with the same health-bounds guarantees as
any other resolved action, and that the gauge is back to zero afterward.

**Acceptance Scenarios**:

1. **Given** a combatant's gauge is below threshold, **When** an attempt is made to
   use their limit break, **Then** it is rejected, the same way an ungranted skill is
   rejected -- never silently ignored, never partially applied.
2. **Given** a combatant's gauge has reached threshold, **When** their limit break is
   used, **Then** it resolves, producing its authored effect against its target(s)
   within the same [0, maximum] health bounds every other resolved action respects.
3. **Given** a limit break has just been used, **When** the combatant's gauge is
   inspected afterward, **Then** it has reset to zero.

---

### User Story 3 - A summon delivers a class-agnostic burst effect for a resource cost (Priority: P2)

Any combatant with the summon capability can call forth a summon by spending a defined
resource (e.g. MP); the summon's authored effect resolves immediately against its
target(s), the same way any other damaging or healing action would, without adding a
new participant to the battle.

**Why this priority**: A self-contained, valuable mechanic in its own right, but
independent of the limit gauge machinery in User Stories 1-2 -- a combatant can summon
without ever having a limit break, so it doesn't block the gauge MVP.

**Independent Test**: Cast a summon with sufficient resource available and verify its
effect resolves against its target(s) and the resource is deducted; attempt to cast
one without sufficient resource and verify it is rejected with nothing deducted.

**Acceptance Scenarios**:

1. **Given** a combatant with enough of a summon's required resource, **When** they
   cast it, **Then** its authored effect resolves against its target(s) and the
   resource is deducted by exactly the defined cost.
2. **Given** a combatant without enough of a summon's required resource, **When** an
   attempt is made to cast it, **Then** it is rejected and no resource is deducted.
3. **Given** a summon has just resolved, **When** the battle's participants are
   inspected, **Then** no new combatant has been added -- the summon acted as a burst
   effect, not a new party member.

---

### User Story 4 - Gauge-full, limit-break-used, and summon-cast are each observable (Priority: P2)

A game developer inspecting the event record (spec 005) can see, as distinct events,
the moment a combatant's gauge reaches threshold, the moment a limit break is used,
and the moment a summon is cast -- using the same event-record mechanism every other
occurrence in this engine is already discoverable through.

**Why this priority**: Extends spec 005's observability to this feature's two new
mechanics; valuable for a future demo/log, but the mechanics themselves (User Stories
1-3) already deliver their core value without this specifically.

**Independent Test**: Trigger each of the three occurrences (gauge reaching
threshold, a limit break resolving, a summon resolving) and verify each produces its
own distinct, discoverable event, with no change required to `resolveAction`,
`tickStatusEffects`, `TurnScheduler`, or `EventLog`'s existing public shape.

**Acceptance Scenarios**:

1. **Given** a combatant's gauge reaches threshold, **When** the event record is
   inspected, **Then** it contains an event identifying that combatant.
2. **Given** a limit break resolves, **When** the event record is inspected, **Then**
   it contains an event identifying the limit break and the combatant who used it, in
   addition to the damage/heal event(s) its effect produced.
3. **Given** a summon resolves, **When** the event record is inspected, **Then** it
   contains an event identifying the summon and the combatant who cast it, in addition
   to the damage/heal event(s) its effect produced.

---

### Edge Cases

- A combatant's gauge sits exactly at threshold (not one point below or above): their
  limit break is usable -- the boundary is inclusive, not exclusive.
- A single occurrence that produces multiple damage outcomes (e.g. an area attack
  hitting several combatants) charges each affected combatant's own gauge
  independently by their own share of damage taken.
- A defeated combatant continues to have their gauge state exist (no special-casing),
  matching spec 004/005's precedent that battle-time bookkeeping doesn't stop simply
  because a combatant reached zero health; a defeated combatant cannot itself submit a
  limit break or summon action, the same way it cannot submit any other action today.
- A summon's resource cost exactly equal to the caster's current resource is allowed
  (leaves exactly zero remaining); only a cost that *exceeds* current resource is
  rejected.
- A class declaring no limit break, or a combatant with no summon capability granted,
  simply never becomes eligible for that mechanic -- not an error.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST maintain a per-combatant accumulating "limit gauge"
  value, starting at zero, that increases when that combatant takes damage, by an
  amount reflecting the damage taken -- data-driven, never a hardcoded flat increment
  (constitution Principle II).
- **FR-002**: A combatant's limit gauge MUST never exceed its associated limit break's
  authored threshold -- once reached, further charging has no additional effect until
  the gauge next resets.
- **FR-003**: The system MUST allow a limit break to be authored as data: an
  identifier, a gauge threshold, and an effect (magnitude/formula) -- never as
  hardcoded logic naming a specific class (constitution Principle II).
- **FR-004**: A combatant's limit break MUST be usable as an action only once their
  gauge has reached its threshold; an attempt to use it before then MUST be rejected,
  the same way an action requiring an ungranted skill is rejected today (spec 002).
- **FR-005**: Using a limit break MUST resolve through the existing action/damage
  resolution mechanism (spec 002), producing its authored effect against its
  target(s) with the same [0, maximum] health-bounds guarantees as any other resolved
  action -- never a new, separate health-modification pathway.
- **FR-006**: After a limit break resolves, that combatant's limit gauge MUST reset to
  zero.
- **FR-007**: The system MUST allow a summon to be authored as data: an identifier, a
  resource cost, and an effect (magnitude/formula) -- never as hardcoded logic naming
  a specific summon (constitution Principle II).
- **FR-008**: Casting a summon MUST be rejected if the casting combatant's available
  resource is less than the summon's defined cost; nothing MUST be deducted when
  rejected.
- **FR-009**: Casting a summon with sufficient resource MUST deduct exactly the
  defined cost and resolve the summon's authored effect through the existing action/
  damage resolution mechanism (spec 002) -- never by adding a new participant to
  `BattleState`.
- **FR-010**: A combatant's gauge reaching its limit break's threshold, a limit break
  resolving, and a summon resolving MUST each produce a discoverable event via the
  existing event mechanism (spec 005), without requiring `resolveAction`,
  `tickStatusEffects`, `TurnScheduler`, or `EventLog`'s existing public shape to
  change.
- **FR-011**: Every behavior in this feature MUST be fully deterministic -- the same
  sequence of occurrences and actions always produces the identical gauge state,
  resource state, and event record, with no randomness (constitution Principle I).
- **FR-012**: This feature MUST consume the existing `Combatant`/`BattleState`/
  `CombatantId`/`resolveAction`/`TurnScheduler`/status-effects/`BattleEvent`/
  `EventLog` model exactly as published by specs 001-005, without requiring any
  change to their existing public signatures (purely additive extensions only,
  matching specs 002-005's discipline).

### Key Entities

- **Limit Gauge**: a combatant's current accumulated value toward their limit break's
  threshold; battle-time state, resets on use.
- **Limit Break Definition**: data describing one limit break -- identifier, gauge
  threshold, and its effect.
- **Summon Definition**: data describing one summon -- identifier, resource cost, and
  its effect.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A combatant's limit gauge always equals the sum of its data-driven
  charge contributions since the last reset, capped at threshold, in 100% of observed
  cases.
- **SC-002**: A limit break is usable exactly when its combatant's gauge has reached
  threshold -- never before, and never still locked once threshold is reached.
- **SC-003**: Every limit-break-resolved and summon-resolved health change is never
  observed to move any combatant's health outside its [0, maximum] bounds.
- **SC-004**: A summon cast without sufficient resource is rejected, with zero
  resource deducted, in 100% of observed cases.
- **SC-005**: Given the same sequence of occurrences and actions, the resulting gauge
  state, resource state, and event record are identical every time.
- **SC-006**: A gauge reaching threshold, a limit break resolving, and a summon
  resolving each appear in the event record as their own distinct event, in 100% of
  observed cases.

## Assumptions

- **Gauge charge rule**: a combatant's limit gauge rises by an amount equal to the
  raw damage taken in each occurrence (no separately-authored "charge rate" data
  needed) -- the simplest rule tying limit-break availability directly to battle
  intensity, matching genre convention, and requiring no new catalog fields beyond the
  threshold itself.
- **One active limit break per combatant (v1)**: spec 001's `ClassDefinition.limitBreaks`
  is a *set*, but this feature scopes to each combatant charging toward and using a
  single active limit break at a time; a class declaring more than one is treated as
  declaring its first for this feature's purposes. Multiple simultaneously-available
  limit breaks per class, and any player choice between them, are a natural future
  extension this design does not preclude but does not build now (constitution
  Principle V).
- **Limit-break/summon catalog independence**: like spec 004's `StatusEffectCatalog`
  and spec 005's `SynergyCatalog`, this feature's `LimitBreakDefinition`/
  `SummonDefinition` catalogs are independent of spec 001's `Catalog` -- spec 001 only
  validates that a class's declared `LimitBreakId`s exist in a known set; this feature
  gives those ids (and new summon ids) real behavior without touching spec 001's
  files.
- **How a limit break/summon action is actually submitted and gated** -- whether it
  reuses spec 002's existing `CombatAction`/`TargetingShape` shape as-is (with this
  feature's own gauge/resource check layered in front of `resolveAction`, the same way
  spec 004 layered `deriveEffectiveBattleState` in front of it) or needs some other
  additive shape -- is a technical decision deferred to `/speckit-plan`, mirroring how
  spec 004 and spec 005 each deferred their own central "how" question. This spec
  fixes the observable behavior only.
- **Resource type for summons**: assumed to be an existing stat (e.g. MP, already
  part of spec 001's `CoreStats`) rather than a new resource concept -- consistent
  with "no new participant, no new combatant-level state" and requiring no change to
  spec 001.
- The specific limit-break/summon catalog content the demo will ship (which classes
  get which limit break, the exact thresholds/costs/magnitudes) is content, deferred
  to feature 007 -- this spec defines the mechanism and its closed data shape only,
  the same boundary every prior spec has drawn around its own content specifics.
- Rendering or UI presentation of gauges, limit breaks, or summons (bars, animations,
  flashy text) is explicitly out of scope (constitution Principle IV).
