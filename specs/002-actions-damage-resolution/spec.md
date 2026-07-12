# Feature Specification: Actions & Damage Resolution

**Feature Branch**: `002-actions-damage-resolution`

**Created**: 2026-07-12

**Status**: Draft

**Input**: User description: "Combat actions (attack, skill/ability, magic, defend, item)
modeled as Command; damage/healing formulas as Strategy; elemental weakness/resistance
arithmetic applying the Affinity data already modeled in spec 001 to actual damage
numbers; targeting (single/multiple/self/ally/enemy/all); command gating by the acting
combatant's granted commands/skills from spec 001. Must build on top of the existing
combatant & class model without modifying its public contract."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Resolve a basic attack into a damage number (Priority: P1)

A content designer or engine consumer submits an attack from one combatant against
another and receives back a concrete outcome: how much damage was dealt, whether it
was affected by an elemental weakness or resistance, and the resulting state of the
target's health. The same submission, replayed with the same inputs, always produces
the same outcome.

**Why this priority**: This is the smallest end-to-end slice that makes the engine a
combat engine rather than a data model — nothing else in this feature (skills, magic,
targeting variety) has value until one action can resolve to a number. It also
exercises the full pipeline (command validity → formula → elemental adjustment → state
update) that every other action type reuses.

**Independent Test**: Submit an attack from combatant A to combatant B with known stats
and a known elemental stance; verify the reported damage matches the documented formula
and stance multiplier, and B's health decreases by exactly that amount.

**Acceptance Scenarios**:

1. **Given** an attacker and a neutral-affinity target, **When** an attack is resolved,
   **Then** the reported damage follows the documented base formula with no elemental
   adjustment.
2. **Given** a target with a declared weakness to the attack's element, **When** the
   attack is resolved, **Then** the reported damage is increased by the documented
   weakness multiplier.
3. **Given** a target with a declared resistance to the attack's element, **When** the
   attack is resolved, **Then** the reported damage is reduced by the documented
   resistance multiplier.
4. **Given** a target with immunity to the attack's element, **When** the attack is
   resolved, **Then** the reported damage is zero.
5. **Given** a target that absorbs the attack's element, **When** the attack is
   resolved, **Then** the target's health increases instead of decreasing.
6. **Given** identical attacker, target, and action inputs, **When** the attack is
   resolved twice independently, **Then** both resolutions report the identical outcome.

---

### User Story 2 - Gate actions by what a combatant can actually do (Priority: P1)

A game developer attempts to submit an action on behalf of a combatant. The engine
only allows actions the combatant is currently capable of performing — determined by
the granted commands and skills already modeled in spec 001 (a combatant's class for
characters, or its definition for enemies) — and rejects anything else with a specific,
actionable reason.

**Why this priority**: Without gating, spec 001's entire capability model (classes
granting distinct skill sets and command types) would be decorative — any combatant
could do anything. This is equally foundational to User Story 1 and must land with it
for the MVP to be meaningful; both are P1.

**Independent Test**: Submit a skill action the combatant's class does not grant, and a
magic action a Warrior-only combatant cannot cast; verify both are rejected with a
reason naming the missing capability, while a granted action of the same combatant
succeeds.

**Acceptance Scenarios**:

1. **Given** a combatant whose capabilities grant the ATTACK command, **When** an attack
   action is submitted, **Then** it is accepted for resolution.
2. **Given** a combatant whose capabilities do not include a specific skill, **When** a
   skill action referencing that skill is submitted, **Then** it is rejected, naming the
   missing skill.
3. **Given** a combatant whose capabilities do not include the MAGIC command, **When** a
   magic action is submitted, **Then** it is rejected, naming the missing command.
4. **Given** a combatant swaps its active class (per spec 001 job change) mid-battle,
   **When** an action is submitted afterward, **Then** gating reflects the new class's
   grants, not the old one.

---

### User Story 3 - Target the right combatants for the action's shape (Priority: P2)

A game developer submits an action against a target selection — a single enemy, every
enemy, a single ally, every ally, all combatants, or the actor itself — and the engine
resolves the action against exactly the combatants that selection implies, rejecting
selections that don't match the action's declared targeting shape or that reference
invalid/defeated combatants.

**Why this priority**: Single-target resolution (User Story 1) already proves the
resolution pipeline; broadening to the full targeting vocabulary is additive value
needed before real skills/spells (which vary in shape) can be authored, but the engine
is already useful without it.

**Independent Test**: Submit the same damaging action with an "all enemies" targeting
shape against a roster of three enemies and one ally; verify all three enemies take
damage and the ally does not, and that the resolution reports one outcome per affected
combatant.

**Acceptance Scenarios**:

1. **Given** an action with single-target shape, **When** submitted with exactly one
   valid target, **Then** it resolves against that target only.
2. **Given** an action with single-target shape, **When** submitted with zero or more
   than one target, **Then** it is rejected as a shape mismatch.
3. **Given** an action with "all enemies" shape, **When** submitted, **Then** it
   resolves against every currently-eligible opposing combatant, producing one outcome
   per combatant.
4. **Given** an action with self-only shape, **When** submitted with any target other
   than the actor, **Then** it is rejected.
5. **Given** a target reference that does not correspond to any combatant in the current
   battle roster, **When** any action targets it, **Then** the submission is rejected
   naming the invalid reference.
6. **Given** a target that is already defeated, **When** an action still selects it
   (explicitly or via an "all" shape), **Then** the engine excludes it from resolution
   rather than resolving against a defeated combatant.

---

### User Story 4 - Heal instead of harm (Priority: P3)

A content designer authors a healing action (e.g., a Cura-style spell or a potion
item) and submits it against a friendly target; the engine increases the target's
health by the formula's result instead of decreasing it, respecting the target's
current-vs-maximum bound, and elemental adjustment does not apply to non-elemental
healing.

**Why this priority**: Healing is a natural extension of the same resolution pipeline
(a formula that produces a positive health delta instead of negative) and depends on
User Stories 1–3 being solid; it is valuable but not required to prove the engine's
core resolution loop.

**Independent Test**: Submit a healing action against a damaged ally with a known
formula; verify health increases by the formula's result and never exceeds the
target's maximum.

**Acceptance Scenarios**:

1. **Given** a damaged target below its maximum health, **When** a healing action is
   resolved, **Then** the target's health increases by the formula's result.
2. **Given** a target near its maximum health, **When** a healing action would push it
   past the maximum, **Then** the target's health is capped at its maximum.
3. **Given** a healing action with no declared element, **When** resolved against any
   target, **Then** no elemental adjustment is applied regardless of the target's
   affinities.

---

### Edge Cases

- An action's formula produces a negative or fractional result before rounding: the
  system MUST define and apply one documented rounding/flooring rule so replays are
  bit-for-bit identical (ties to User Story 1 scenario 6).
- A defeated combatant is submitted as the *actor* of an action: rejected — defeated
  combatants cannot act.
- An action references a skill/spell identifier absent from the battle's catalog: this
  is a content error caught at catalog validation time (spec 001), not a runtime
  concern of this feature — this feature assumes actions only reference
  catalog-validated capabilities.
- Absorption pushes a target's health above its maximum: capped at maximum, matching
  the healing bound (User Story 4 scenario 2).
- An "all allies" or "all enemies" selection resolves against zero combatants (every
  candidate already defeated): the action is accepted and simply produces zero
  outcomes, not rejected as a shape mismatch.
- A damage formula's non-elemental portion is defined independently of any specific
  element, so a physical (non-elemental) attack still resolves without requiring an
  element reference.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST represent every combat action (attack, skill, magic,
  defend, item) as a discrete, self-contained submission that can be resolved
  independently and whose resolution is fully determined by its inputs (constitution
  Principle I: no hidden state, no non-deterministic sources).
- **FR-002**: Before resolving an action, the system MUST verify the acting combatant's
  currently granted capabilities (from spec 001 — active class for characters,
  definition for enemies) include the command type and, where applicable, the specific
  skill the action requires; actions failing this check MUST be rejected with a reason
  naming the missing capability, and MUST NOT be resolved.
- **FR-003**: The system MUST reject an action whose actor is a defeated combatant.
- **FR-004**: Damage and healing outcomes MUST be computed by a formula that is
  substitutable independent of the action invoking it (Strategy pattern per
  constitution): the engine MUST support at least one physical-style formula (driven by
  attack/defense-like stats) and one magical-style formula (driven by magic/resistance-
  like stats), selectable per action.
- **FR-005**: When an action declares an element, the system MUST adjust the formula's
  raw result using the target's elemental affinity for that element (from spec 001's
  affinity model) before applying it: weakness increases damage, resistance decreases
  it, immunity zeroes it, absorption reverses the sign (damage heals instead of
  harming). Actions without a declared element MUST skip elemental adjustment entirely.
- **FR-006**: The system MUST define one fixed multiplier per affinity stance
  (weakness, resistance, immunity, absorption) applied uniformly across all elemental
  actions, and one fixed rounding rule for fractional formula results, so that identical
  inputs always produce identical outcomes (constitution Principle I, ties to spec 001
  SC-006).
- **FR-007**: The system MUST support at least these targeting shapes: single ally,
  single enemy, self, all allies, all enemies, and all combatants. Each action declares
  exactly one shape.
- **FR-008**: The system MUST reject a target selection that does not match the
  action's declared shape (e.g., two targets for a single-target shape) or that
  references a combatant not present in the current battle roster, naming the mismatch
  or invalid reference.
- **FR-009**: When a targeting shape resolves against multiple combatants ("all ..."
  shapes), the system MUST exclude already-defeated combatants from resolution without
  rejecting the action, and MUST report one outcome per actually-affected combatant.
- **FR-010**: A resolved action MUST report, per affected combatant: the numeric health
  delta applied, whether elemental adjustment was applied and which stance drove it (if
  any), and the combatant's resulting health value.
- **FR-011**: Health changes MUST respect existing bounds: a combatant's current health
  MUST NOT exceed its maximum (healing/absorption caps at maximum) and MUST NOT go
  below zero (this feature computes the floor at zero; whether zero health causes a
  "defeated" state transition is the concern of a future battle-flow/state feature —
  this feature only guarantees the numeric floor).
- **FR-012**: This feature MUST consume the existing combatant/class/catalog model
  (`Combatant`, `ValidatedCatalog`, `StatBlock`, `Affinity`) exactly as published by
  spec 001's contract, without requiring any change to that contract.
- **FR-013**: Every functional requirement above MUST be verifiable through deterministic,
  replayable resolution — the same action submission against the same battle state MUST
  always produce the same reported outcome (constitution Principle I; spec 001 SC-006
  precedent).

### Key Entities

- **Action**: a submission to resolve — references its actor, its command/skill
  identity, its declared element (optional), its formula, and its target selection.
- **Command**: the category of an action (per spec 001's `CommandKind`: ATTACK, SKILL,
  MAGIC, DEFEND, ITEM); gates whether a combatant may submit the action at all.
- **Formula**: the substitutable calculation (Strategy) that turns actor/target stats
  into a raw numeric result before elemental adjustment; at minimum a physical-style and
  a magical-style formula ship with the engine.
- **Elemental Adjustment**: the fixed-multiplier transformation applied to a formula's
  raw result based on the target's declared [Affinity](../001-combatant-class-model/data-model.md)
  to the action's element.
- **Targeting Shape**: the declared cardinality/scope of an action's valid targets
  (single ally/enemy, self, all allies/enemies, all combatants).
- **Resolution Outcome**: the reported result of resolving an action against one
  affected combatant — health delta, elemental stance applied (if any), resulting
  health.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An attack submitted against a target of known stats and affinity produces
  a damage number matching the documented formula and stance multiplier in 100% of
  cases, verified without inspecting engine internals.
- **SC-002**: 100% of action submissions that violate capability gating (missing
  command or skill) or targeting shape are rejected before any health change occurs,
  each rejection naming the specific violated rule.
- **SC-003**: Resolving the same action submission against the same battle state twice
  produces byte-for-byte identical reported outcomes, every time.
- **SC-004**: An "all enemies" or "all allies" action against a roster produces exactly
  one outcome per currently-eligible (non-defeated) combatant of that group, with zero
  outcomes for excluded (defeated) combatants.
- **SC-005**: No resolved outcome, across weakness/resistance/immunity/absorption/heal
  cases, ever leaves a combatant's health outside its [0, maximum] bounds.
- **SC-006**: A new formula or a new elemental stance behavior can be added by content
  authors or engine maintainers without modifying the action-submission or targeting
  code paths (constitution Principle II/V — capabilities are substitutable, not
  hardcoded per action).

## Assumptions

- "Defeated" is defined here solely as health equal to zero for the purpose of
  excluding a combatant from being a valid actor or targetable-in-an-"all"-shape
  participant; the full battle-flow state machine (turn skipping, victory/defeat
  conditions) is a later feature's scope.
- Command gating in FR-002 checks capability membership only (does the combatant have
  the command/skill); resource costs (MP, item consumption, cooldowns) are out of scope
  for this feature and belong to a later feature.
- This feature does not introduce a battle-wide event/turn loop; "resolving an action"
  is a self-contained operation the future `TurnScheduler` feature will sequence.
- The specific numeric values of the fixed elemental multipliers (FR-006) and the exact
  physical/magical formula shapes (FR-004) are technical decisions to be finalized in
  `/speckit-plan`, not business-facing choices for this spec; this spec fixes their
  existence, substitutability, and determinism, not their numbers.
- Rounding (edge case) uses standard "round half away from zero to nearest integer"
  unless `/speckit-plan` finds a reason to deviate; stat values throughout the engine
  are integers (spec 001 research R4), so all formula outputs must ultimately be
  integers too.
- Item actions in this feature are modeled identically to skill actions (a command type
  gated by capability, resolved through a formula); inventory/consumption mechanics are
  out of scope and left to a later feature.
