# Feature Specification: Combatant & Class Model

**Feature Branch**: `001-combatant-class-model`

**Created**: 2026-07-11

**Status**: Draft

**Input**: User description: "Domain model for battle participants: combatant kinds
(playable party members, enemies, temporary AI-controlled allies such as summons), a
class system for playable characters (skill set, stat growth, equipment restrictions,
allowed actions, class-specific limit breaks, open to future class progression), enemy
archetypes/roles as data (affecting AI profile and reward/difficulty), stats, and
elemental affinities — all defined as data, all participating in the same battle rules."

## Clarifications

### Session 2026-07-11

- Q: Is the stat catalog fixed by the engine or data-defined per game? → A: Hybrid — the
  engine defines a mandatory core stat set (HP, MP, Attack, Defense, Magic, Resistance,
  Speed, Luck) that formulas may always assume present; catalogs may declare additional
  custom stats referencable by content.
- Q: What is the leveling scope for this feature? → A: Characters instantiate at a given
  level and stats derive deterministically from the class growth curve at that level;
  XP gain and level-up mechanics are deferred to a later rewards/progression feature.
- Q: Is equipment in scope? → A: Only restriction categories: classes declare named
  equipment categories they may use; equipment items and their stat effects are a future
  feature that will honor these categories.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Define playable characters through classes (Priority: P1)

A content designer creates a playable character by authoring data only: the character
references a class (e.g., Warrior, Mage, Healer, Summoner), and the class definition
determines which skills the character can use, how its stats grow, which equipment
categories it may use, and which battle commands it can perform. No engine code is
written or modified to add a new character or a new class.

**Why this priority**: The class system is the keystone of the domain model — actions,
damage, synergies, and limit breaks (future specs) all resolve against what a class
grants. Without it no other combat feature can be specified against a stable model.

**Independent Test**: Author two characters with different classes in a content catalog,
load the catalog, and verify each character reports exactly the skill set, allowed
commands, stat profile, and equipment restrictions granted by its class.

**Acceptance Scenarios**:

1. **Given** a catalog with a Warrior class granting skills A and B, **When** a character
   referencing Warrior is loaded, **Then** the character's available skills are exactly A
   and B and its allowed commands are those of the Warrior class.
2. **Given** two characters of the same class with different base stats, **When** both
   are loaded, **Then** they share class-granted capabilities but keep their individual
   stat values.
3. **Given** a class that declares a class-specific limit break, **When** a character of
   that class is loaded, **Then** that limit break is listed among the character's
   capabilities.
4. **Given** a character definition, **When** its class reference is later changed to
   another valid class (job change), **Then** the character remains valid and reports the
   new class's capabilities — without redefining the character itself.

---

### User Story 2 - Define enemies through archetypes (Priority: P2)

A content designer creates enemies by authoring data: each enemy definition references an
archetype/role (e.g., common mob, elite, boss, enemy summoner) that selects its AI
behavior profile and its reward/difficulty tier. Adding a new enemy — even a boss with a
unique behavior profile reference — requires only new data entries.

**Why this priority**: Enemies are the second mandatory participant of any battle; the
archetype mechanism is what keeps enemy variety a content concern instead of an engine
concern.

**Independent Test**: Author enemies of different archetypes, load them, and verify each
reports the AI profile reference and reward/difficulty tier of its archetype.

**Acceptance Scenarios**:

1. **Given** an archetype "boss" mapping to AI profile X and reward tier T, **When** an
   enemy referencing "boss" is loaded, **Then** the enemy exposes AI profile X and tier T.
2. **Given** an existing catalog, **When** a designer adds a new enemy referencing an
   existing archetype, **Then** the enemy is usable in battle with no other changes.
3. **Given** two enemies sharing an archetype, **When** both are loaded, **Then** they
   share behavior profile and tier but keep individual stats and elemental affinities.

---

### User Story 3 - All combatant kinds share one battle model (Priority: P3)

A game developer assembles a battle roster mixing player characters, enemies, and a
temporary AI-controlled ally (e.g., a summoned creature or a guest NPC fighting on the
player's side). All participants expose the same battle-relevant attributes (stats,
elemental affinities, available actions, allegiance) and will be subject to the same
resolution rules; what differs is only each combatant's decision source (human input vs
an AI profile).

**Why this priority**: This uniformity is what later lets damage, status effects, and
turn scheduling treat every participant identically — but it can only be demonstrated
meaningfully once stories 1 and 2 provide characters and enemies to mix.

**Independent Test**: Build a roster with one party member, one enemy, and one temporary
ally; verify all three expose the same attribute surface, correct allegiances, and their
configured decision sources.

**Acceptance Scenarios**:

1. **Given** a roster with a party member, an enemy, and a temporary ally, **When** the
   roster is inspected, **Then** all three expose the same set of battle-relevant
   attributes.
2. **Given** a temporary ally, **When** its allegiance is inspected, **Then** it fights on
   the player's side while its decisions come from an AI profile.
3. **Given** any combatant, **When** its decision source is reconfigured (e.g., human to
   AI), **Then** its kind, stats, and capabilities are unaffected.

---

### Edge Cases

- A character references a class that does not exist in the catalog (or an enemy
  references an unknown archetype/AI profile): loading MUST fail with an error naming the
  offending definition and reference — never a silent fallback.
- A class grants a skill or limit break identifier not present in the catalog: detected
  at load/validation time, not at battle time.
- Two definitions share the same identifier: rejected at load time as a catalog conflict.
- A combatant defines no elemental affinities: treated as neutral to all elements.
- A class with an empty skill set: valid (a "commoner"-style class) as long as it grants
  at least one permitted command.
- A temporary ally's definition is identical in shape to a party member's: the model must
  not require duplicating character data to field it as a temporary ally.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST represent every battle participant as a combatant with a
  single, common set of battle-relevant attributes: identity, display name, stat block,
  elemental affinities, capability set (skills/commands), and allegiance.
- **FR-002**: The system MUST distinguish three combatant kinds: player party members,
  enemies, and temporary allies (AI-controlled participants fighting on the player's
  side), all usable within the same battle roster.
- **FR-003**: A combatant's decision source (human input vs a named AI profile) MUST be
  configuration, independent of its kind: any combatant kind can be driven by either
  source without changing its capabilities or stats.
- **FR-004**: Character classes MUST be authorable as data. A class definition determines:
  granted skill set, stat growth curve, equipment-category restrictions, permitted battle
  commands, and optionally class-specific limit break references.
- **FR-005**: Each playable character MUST reference exactly one active class. The model
  MUST allow that reference to change over a character's lifetime (job change/promotion)
  without redefining the character; v1 gameplay uses fixed classes only.
- **FR-006**: Enemy archetypes MUST be authorable as data. An archetype determines: an AI
  behavior profile reference and a reward/difficulty tier. Enemies reference an archetype
  plus their individual stats and affinities, and MAY declare an individual skill set
  (empty by default) listing the actions available to them; how an AI selects among them
  is later features' scope.
- **FR-007**: Adding a new class, character, enemy, or archetype MUST require only new
  data entries — never engine code changes (per constitution Principle II).
- **FR-008**: Every combatant MUST carry a stat block of named numeric attributes with
  defined bounds (no negative values; current resource values never exceed their maxima).
  The stat catalog is hybrid: the engine defines a mandatory core stat set — HP, MP,
  Attack, Defense, Magic, Resistance, Speed, Luck — guaranteed present on every
  combatant, and a catalog MAY declare additional named custom stats, which are then
  referencable by content and validated like any other cross-reference.
  The current-vs-maximum rule is declared here as a model invariant and is enforced once
  battle state exists (later features); no battle-time resource tracking is built in this
  feature.
- **FR-008a**: Playable characters MUST be instantiable at a given level, with their
  effective base stats derived deterministically from their class's growth curve at that
  level (same class + same level + same individual modifiers ⇒ same stats).
- **FR-009**: Every combatant MAY declare elemental affinities per element on a scale of
  weakness / neutral / resistance / immunity / absorption. Affinity data lives in this
  model; the arithmetic of applying it belongs to the damage-resolution feature.
- **FR-010**: Classes MUST be identifiable such that other content can reference class
  combinations (e.g., a future synergy "Warrior + Mage combined attack") and class
  membership can gate content (class-specific limit breaks).
- **FR-011**: Character stat growth curves MUST be part of the class definition and MUST
  yield the character's stats for any given level (FR-008a). Experience gain and
  level-up mechanics are out of scope for this feature and belong to a later
  rewards/progression feature.
- **FR-012**: Class definitions MUST express equipment/weapon restrictions as named
  equipment categories (e.g., "swords", "staves", "heavy armor") declared in the catalog
  and validated like any other cross-reference. Equipment items themselves — and their
  effects on stats — are out of scope for this feature; a future equipment feature will
  honor these categories.
- **FR-013**: All cross-references between definitions (character→class, class→skill,
  class→limit break, enemy→archetype, archetype→AI profile) MUST be validated when a
  catalog is loaded, before any battle starts, producing errors that name the offending
  definition and the missing/duplicate reference.
- **FR-014**: All definitions MUST be expressible in a portable, human-editable data
  format so that different games can ship different catalogs against the same engine.

### Key Entities

- **Combatant**: any battle participant; carries identity, display name, kind (party
  member / enemy / temporary ally), allegiance, stat block, elemental affinities,
  capability set, and a decision-source configuration.
- **Character Class**: data definition granting a skill set, stat growth curve, equipment
  restrictions, permitted commands, and optional limit break references; referenced by
  playable characters (one active class at a time, changeable over time).
- **Enemy Archetype**: data definition of an enemy role (common/elite/boss/summoner…)
  selecting an AI behavior profile reference and a reward/difficulty tier.
- **Character Definition**: a playable character's authored data: identity, base stats,
  active class reference, individual affinities.
- **Enemy Definition**: an enemy's authored data: identity, base stats, archetype
  reference, individual affinities.
- **Stat Block**: named numeric attributes with bounds (base values vs current values).
- **Elemental Affinity**: per-element stance: weakness / neutral / resistance / immunity
  / absorption.
- **Decision Source**: origin of a combatant's action choices — human input or a named AI
  profile (profiles themselves are defined by a future feature; here only the reference).
- **Catalog**: a loadable collection of the above definitions, validated as a whole.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A content designer can add a new character class and a new enemy archetype
  by editing catalog data only — zero engine code changes — and field them in a battle
  roster.
- **SC-002**: 100% of invalid cross-references and duplicate identifiers in a catalog are
  reported at load time (before any battle), each error naming the offending definition.
- **SC-003**: A battle roster can contain all three combatant kinds simultaneously, and
  an inspection of the roster shows every participant exposing the identical
  battle-relevant attribute surface.
- **SC-004**: Reconfiguring any combatant's decision source (human ↔ AI) changes no other
  observable property of that combatant.
- **SC-005**: A character's class reference can be swapped to another valid class and the
  character reports the new class's capabilities, with no changes to the character's own
  definition beyond the reference.
- **SC-006**: Loading the same catalog twice produces identical models (deterministic,
  order-independent load results).

## Assumptions

- The "user" of this feature is a game developer / content designer consuming the engine;
  end-player experience is exercised only through the future demo game.
- Elemental affinity levels follow the genre-standard five-stance scale (weakness,
  neutral, resistance, immunity, absorption); the element list itself is catalog data,
  not fixed by the engine.
- Temporary allies (summons, guests) reuse the same definitional shape as playable
  characters; what makes them "temporary" is battle-time participation (join/leave),
  which is governed by future battle-flow features.
- Skills, limit breaks, and AI profiles are referenced by identifier only in this
  feature; their behavior is specified by later features (actions/damage, limit breaks,
  AI strategies).
- Reward/difficulty tiers are opaque labels/values at this stage; the economy that
  consumes them (drops, experience) is a later feature.
- Definitions are templates: a battle roster instantiates combatants from definitions,
  and multiple instances of the same definition (e.g., three Goblins) receive distinct
  battle identities while sharing the definition's data.
- Class progression (job systems, promotions) is out of scope for v1 gameplay, but the
  model keeps the character→class reference mutable so progression can be added without
  reworking the model (constitution Principle V).
