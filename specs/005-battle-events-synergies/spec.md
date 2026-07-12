# Feature Specification: Battle Events & Synergies

**Feature Branch**: `005-battle-events-synergies`

**Created**: 2026-07-12

**Status**: Draft

**Input**: User description: "An Observer/event-bus mechanism per the constitution
that publishes structured battle events (damage dealt, healing applied, status effect
applied/expired, combatant defeated, turn granted) as a side effect of the existing
resolution pipeline (resolveAction, tickStatusEffects, TurnScheduler), without those
functions needing to know about subscribers. On top of the event bus, class-
combination synergies: when specific combinations of combatants act together within a
defined window (e.g. two allies attacking the same target in sequence), a bonus effect
triggers automatically — data-driven, not hardcoded per class pair. Must consume
Combatant/BattleState/CombatantId/resolveAction/TurnScheduler/status-effects from
specs 001–004 without modifying their public contracts. Out of scope: the specific
synergy catalog content for the demo (feature 007) and any UI/rendering of events
(constitution Principle IV forbids that in core); this feature defines the event
mechanism and the synergy-trigger mechanism only."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Every battle occurrence is discoverable as a structured event (Priority: P1)

A game developer or engine consumer resolves an action, ticks status effects, or asks
the scheduler who's next, and afterward can inspect a record of exactly what happened —
damage dealt, healing applied, a status effect applied or expired, a combatant
defeated, or a turn granted — as structured data, without having wired up anything
beyond calling the existing resolution functions.

**Why this priority**: This is the observability foundation everything else in this
feature depends on — synergy detection (User Story 2) has nothing to scan without a
recorded history of what happened, so this must exist first and delivers value on its
own (a demo or test author already benefits from an inspectable event history).

**Independent Test**: Resolve a damaging action, tick a damage-over-time status
effect, and query the next turn; verify each produces the documented event, discoverable
by inspecting the accumulated record afterward, with no separate wiring per call site.

**Acceptance Scenarios**:

1. **Given** a fresh battle with no actions yet resolved, **When** the event record is
   inspected, **Then** it is empty — not an error.
2. **Given** a damaging action is resolved, **When** the event record is inspected
   afterward, **Then** it contains a damage-dealt event identifying the target and the
   amount.
3. **Given** a damage-over-time status effect ticks, **When** the event record is
   inspected afterward, **Then** it contains a damage-dealt event for that tick,
   indistinguishable in shape from one produced by a resolved action.
4. **Given** a status effect is applied and later expires, **When** the event record is
   inspected at each point, **Then** it contains a status-effect-applied event at
   application and a status-effect-expired event at expiry.
5. **Given** a combatant's health reaches zero, **When** the event record is inspected,
   **Then** it contains a combatant-defeated event for that combatant.
6. **Given** the turn scheduler grants a turn, **When** the event record is inspected,
   **Then** it contains a turn-granted event naming that combatant.
7. **Given** several occurrences have already been recorded, **When** a new one occurs,
   **Then** the record grows by exactly the new event(s) — nothing already recorded is
   lost or reordered.

---

### User Story 2 - A class-flavored combination synergy triggers its bonus automatically (Priority: P1)

A content designer defines a synergy: when a combatant with one qualifying capability
and, within a defined window, a combatant with a second qualifying capability each act
against the same target, the synergy's bonus effect (e.g., bonus damage) is applied
automatically — without the game developer having to detect the combination or invoke
anything beyond the normal resolution flow. Because spec 001's classes are what grant
combatants their capabilities in the first place, authoring a synergy around two
class-signature capabilities (e.g., "whoever can Cleave" + "whoever can cast Fira") is
how a designer expresses a "Warrior + Mage" style combination in practice.

**Why this priority**: This is the feature's namesake payoff — an event log with no
synergy detection on top of it doesn't deliver what "synergies" promises; both User
Story 1 and this one are required together for the feature's stated purpose, mirroring
how earlier specs treated two load-bearing mechanisms as equally P1 when neither alone
is the point.

**Independent Test**: Author a synergy requiring two specific capabilities acting on
the same target within a window of a few turns; have two combatants each possessing
one of those capabilities act on the same enemy within that window, and verify the
synergy's bonus effect is applied automatically, with no explicit "trigger this
synergy" call.

**Acceptance Scenarios**:

1. **Given** a defined synergy requiring capabilities A and B on the same target
   within a window, **When** a combatant with capability A and then a combatant with
   capability B each act on the same target within that window, **Then** the
   synergy's bonus effect is applied.
2. **Given** the same synergy, **When** the second qualifying action happens *outside*
   the defined window, **Then** the synergy does not trigger.
3. **Given** the same synergy, **When** both qualifying actions target *different*
   combatants, **Then** the synergy does not trigger.
4. **Given** the same synergy, **When** the same single combatant would need to satisfy
   both capability roles, **Then** the synergy does not trigger — two distinct combatants
   are required.
5. **Given** a synergy's bonus effect is a bonus-damage amount, **When** it applies,
   **Then** the target's health decreases according to the same bounds every other
   damage application already respects.

---

### User Story 3 - Multiple synergy definitions coexist without interfering (Priority: P2)

A content designer authors several different synergy definitions in the same battle.
Each is evaluated independently against the recorded events; one synergy's condition
being satisfied does not prevent another, independently-satisfied synergy from also
triggering.

**Why this priority**: Real content will define more than one synergy; this hardens
correctness at catalog scale but a single synergy already proves the mechanism (User
Story 2), so this is not required for the MVP.

**Independent Test**: Author two different synergies whose conditions are both
satisfied by the same sequence of actions; verify both trigger, neither suppressing
the other.

**Acceptance Scenarios**:

1. **Given** two independently-defined synergies whose conditions are both satisfied
   by the same qualifying actions, **When** those actions occur, **Then** both
   synergies' bonus effects apply.
2. **Given** a catalog of several synergies where only one's condition is satisfied,
   **When** the qualifying actions occur, **Then** only that one triggers.

---

### User Story 4 - A synergy trigger is itself an observable event (Priority: P2)

A game developer inspecting the event record can distinguish "a synergy just
triggered" from the ordinary events that caused it, using the same event-record
mechanism from User Story 1.

**Why this priority**: Closes the loop between the two mechanisms this feature builds
(events and synergies) so a future consumer (a demo's UI/log, a test) has one place to
observe everything that happened — valuable, but the synergy's mechanical effect (User
Story 2) already delivers the core value without this specifically.

**Independent Test**: Trigger a synergy as in User Story 2, then inspect the event
record and verify it contains a synergy-triggered event distinct from the damage/heal
events that caused it.

**Acceptance Scenarios**:

1. **Given** a synergy triggers, **When** the event record is inspected, **Then** it
   contains a synergy-triggered event identifying which synergy and which combatants
   satisfied it, in addition to the event(s) its bonus effect produced.

---

### Edge Cases

- A synergy's window expires with no second qualifying action: no trigger, no error.
- A combatant that satisfied one half of a synergy is defeated before the second
  qualifying action happens: the already-recorded event remains valid history: the
  synergy can still trigger using it (a past fact doesn't un-happen), but the defeated
  combatant cannot supply a *second* qualifying action.
- Zero synergy definitions in the catalog: the event record still works fully (User
  Story 1 has no dependency on User Story 2 existing).
- An event record for a very long battle grows without bound: acceptable for this
  feature's scope (in-memory, single-battle lifetime); pruning/retention policy is not
  addressed here.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST produce a structured event for each of: damage dealt,
  healing applied, status effect applied, status effect expired, combatant defeated,
  and turn granted, whenever one of these occurs as part of resolving an action (spec
  002), ticking status effects (spec 004), or granting a turn (spec 003) — without
  requiring resolveAction, tickStatusEffects, or the turn scheduler to be modified to
  know about subscribers or event consumers.
- **FR-002**: Every event MUST carry enough structured data to identify what happened
  and to whom (at minimum the affected combatant's identifier, and kind-appropriate
  data: an amount for damage/healing, an effect identifier for status events) — never
  freeform or formatted text (constitution Principle IV).
- **FR-003**: The system MUST accumulate events into an ordered record for a battle
  that a caller can inspect at any point; already-recorded events are never lost or
  reordered by later occurrences.
- **FR-004**: The system MUST allow class-flavored combination synergies to be
  authored as data: an identifier, two required distinct qualifying capabilities
  (identified by skill — spec 001 grants specific skills per class, so pairing two
  skills is how a "Warrior + Mage" style combination is authored in practice; see
  Assumptions), a same-target condition, a window, and a bonus effect — never as
  hardcoded logic naming specific classes or skills (constitution Principle II).
- **FR-005**: The system MUST automatically detect, from the accumulated event record,
  when a synergy's condition is satisfied — a combatant currently having each of the
  synergy's two required qualifying capabilities acted against the same target, the
  two qualifying occurrences falling within the synergy's window, and the two acting
  combatants being distinct — and MUST trigger that synergy's bonus effect at the
  point the condition becomes true.
- **FR-006**: A synergy's bonus effect MUST be applied through the existing resolution
  mechanisms (e.g., bonus damage applied with the same health-bounds guarantees as
  spec 002's action resolution) — never a new, separate health-modification pathway.
- **FR-007**: Multiple different synergy definitions MUST be evaluated independently
  against the same event record; one synergy triggering MUST NOT prevent another,
  independently-satisfied synergy from also triggering.
- **FR-008**: A synergy MUST require two distinct combatants; the same combatant
  satisfying both of a synergy's required-capability roles MUST NOT trigger it.
- **FR-009**: Triggering a synergy MUST itself produce a discoverable event identifying
  the synergy and the combatants that satisfied it, observable the same way as any
  other event in this feature.
- **FR-010**: Every behavior in this feature MUST be fully deterministic — the same
  sequence of resolved actions, status ticks, and granted turns, against the same
  synergy catalog, MUST always produce the identical event record and the identical
  synergy triggers, with no randomness (constitution Principle I).
- **FR-011**: This feature MUST consume the existing `Combatant`/`BattleState`/
  `CombatantId`/`resolveAction`/`TurnScheduler`/status-effects model exactly as
  published by specs 001–004, without requiring any change to their existing public
  signatures (purely additive extensions only, matching specs 002–004's discipline).

### Key Entities

- **Battle Event**: a structured record of one occurrence — its kind (damage dealt /
  healing applied / status effect applied / status effect expired / combatant
  defeated / turn granted / synergy triggered) and kind-appropriate identifying data.
- **Event Record**: the ordered, growing collection of Battle Events accumulated for
  one battle.
- **Synergy Definition**: data describing one synergy — identifier, its two required
  distinct qualifying capabilities (see Assumptions — expressed as skills, since
  skills are what spec 001's classes actually grant), the same-target condition, its
  window, and its bonus effect.
- **Synergy Trigger**: the fact of a Synergy Definition's condition becoming satisfied,
  producing its bonus effect and its own Battle Event.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every resolved action, status-effect tick, and granted turn produces
  exactly its documented event(s), discoverable immediately afterward, in 100% of
  observed cases.
- **SC-002**: A synergy whose condition is satisfied by two distinct, qualifying
  combatants acting on the same target within its window triggers every time, with
  zero missed triggers and zero triggers outside the window or across mismatched
  targets.
- **SC-003**: A synergy's bonus damage is never observed to move any combatant's
  health outside its [0, maximum] bounds.
- **SC-004**: Two or more independently-defined synergies whose conditions are both
  satisfied by the same events both trigger, in 100% of observed cases — neither
  suppresses the other.
- **SC-005**: Given the same sequence of resolved actions, status ticks, and granted
  turns against the same synergy catalog, the resulting event record and synergy
  triggers are identical every time.
- **SC-006**: A synergy trigger always appears in the event record as a distinct event
  from the events that caused it, in 100% of observed cases.

## Assumptions

- **Window unit**: a synergy's window is measured in turns consumed anywhere in the
  battle (spec 003's `markSpent`), the same unit spec 004's status-effect ticking
  already established (FR-007 there) — chosen for consistency with that precedent
  rather than introducing a second notion of "battle time."
- **Qualifying action**: a combatant "acts against" a target for synergy-detection
  purposes whenever a resolved-action event (damage dealt or healing applied)
  identifies that combatant as the actor and that target as affected; this is
  intentionally broad (not damage-only) since the input's own example ("attacking")
  is illustrative, not exhaustive.
- **Synergy shape (v1)**: exactly two required qualifying capabilities per synergy
  definition (a pair), matching the input's own example; broader N-way or set-based
  combinations are a natural future extension this design does not preclude, but are
  not built now (constitution Principle V).
- **"Class-combination" is realized as skill-combination**: spec 001's public
  `Combatant` interface exposes `capabilities` (including granted skills) but never a
  combatant's class identifier directly — that detail lives only on spec 001's
  internal implementation, deliberately not part of the public contract this feature
  must consume unchanged (FR-011). Since a class's entire purpose is to grant a
  specific skill set, authoring a synergy around two required skills achieves the
  same "class-flavored combination" business outcome the input asked for, using data
  that is genuinely observable through the existing public API — without reaching
  into spec 001's private implementation details or requiring any change to its
  files. This adjustment was made during `/speckit-plan` once the constraint was
  confirmed against spec 001's actual source; every requirement above already
  reflects it.
- **Multiple triggers**: when several independently-defined synergies' conditions are
  all satisfied by the same events, all of them trigger — synergies are treated as
  independent bonuses, not mutually exclusive alternatives (per FR-007/SC-004).
- **The Observer/event-bus mechanism's exact shape** — how events reach a caller
  without `resolveAction`/`tickStatusEffects`/the scheduler being modified to notify
  subscribers — is a technical decision for `/speckit-plan`. This spec fixes the
  observable behavior (what events exist, what they contain, when they occur) and the
  constraint that specs 001–004's existing functions require no signature changes, not
  the underlying mechanism.
- The specific synergy catalog content the demo will ship (which classes pair up, the
  exact window size, the exact bonus amount) is content, deferred to feature 007 —
  this spec defines the mechanism and its closed synergy shape only, the same boundary
  every prior spec has drawn around its own content specifics.
- Rendering, logging, or any UI presentation of events is explicitly out of scope
  (constitution Principle IV) — this feature only makes events exist and be
  inspectable as data.
