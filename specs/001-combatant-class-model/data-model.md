# Data Model: Combatant & Class Model

**Feature**: 001-combatant-class-model | **Date**: 2026-07-11
**Sources**: spec.md (Key Entities, FRs), research.md (R1–R8)

Two layers, one direction: **catalog layer** (authored, serializable, immutable after
validation) is referenced by the **battle layer** (instantiated per battle). Nothing in
the catalog layer knows the battle layer exists.

## Identifier types (R2)

Value classes over `String`: `StatId`, `ElementId`, `ClassId`, `ArchetypeId`,
`SkillId`, `LimitBreakId`, `AiProfileId`, `EquipmentCategoryId`, `CharacterId`,
`EnemyId`. Battle layer adds `CombatantId` (deterministic, roster-assigned; R7).

## Catalog layer (serializable)

### Catalog
| Field | Type | Rules |
|---|---|---|
| customStats | Set\<StatId\> | may not redeclare core stats (FR-008) |
| elements | Set\<ElementId\> | element list is catalog data (spec assumption) |
| equipmentCategories | Set\<EquipmentCategoryId\> | referenced by classes (FR-012) |
| classes | List\<ClassDefinition\> | unique ClassId (FR-013) |
| archetypes | List\<ArchetypeDefinition\> | unique ArchetypeId |
| characters | List\<CharacterDefinition\> | unique CharacterId |
| enemies | List\<EnemyDefinition\> | unique EnemyId |

Validated as a whole (R6); the validated form exposes indexed lookup by each ID type.
Skills, limit breaks, and AI profiles are **external references** in this feature
(declared as known-ID sets on the catalog: `knownSkills`, `knownLimitBreaks`,
`knownAiProfiles`) so reference validation is total today and future features replace
the known-ID sets with real definitions without changing referencing content.

### ClassDefinition (FR-004, FR-010, FR-012)
| Field | Type | Rules |
|---|---|---|
| id | ClassId | unique |
| displayName | String | opaque to engine (Principle IV) |
| skills | Set\<SkillId\> | each must exist in knownSkills; may be empty (edge case: commoner) |
| commands | Set\<CommandKind\> | non-empty (edge case rule); enum: ATTACK, SKILL, MAGIC, DEFEND, ITEM, SUMMON |
| growth | Map\<StatId, GrowthCurve\> | keys must be core or declared custom stats |
| equipmentCategories | Set\<EquipmentCategoryId\> | must exist in catalog set |
| limitBreaks | Set\<LimitBreakId\> | class-specific limit breaks (FR-004, FR-010) |

### ArchetypeDefinition (FR-006)
| Field | Type | Rules |
|---|---|---|
| id | ArchetypeId | unique |
| displayName | String | opaque |
| aiProfile | AiProfileId | must exist in knownAiProfiles |
| rewardTier | Int | ≥ 0; opaque label at this stage (spec assumption) |

### CharacterDefinition (FR-005)
| Field | Type | Rules |
|---|---|---|
| id | CharacterId | unique |
| displayName | String | opaque |
| classId | ClassId | must exist; **the mutable progression point** (R1) |
| baseStats | StatBlock | complete core set; custom keys must be declared |
| affinities | Map\<ElementId, Affinity\> | keys must exist in elements; absent ⇒ NEUTRAL |

### EnemyDefinition (FR-006)
| Field | Type | Rules |
|---|---|---|
| id | EnemyId | unique |
| displayName | String | opaque |
| archetypeId | ArchetypeId | must exist |
| skills | Set\<SkillId\> | optional, default empty; each must exist in knownSkills |
| stats | StatBlock | complete core set |
| affinities | Map\<ElementId, Affinity\> | keys must exist in elements; absent ⇒ NEUTRAL |

### Value types
- **StatBlock** (R3, R4): `Map<StatId, Int>`; invariant: contains all 8 core stats;
  all values ≥ 0.
- **GrowthCurve** (R5): sealed — `Linear(base: Int, perLevel: Int)` |
  `Table(values: List<Int>)`; both yield `at(level): Int`, level ≥ 1; Table clamps
  beyond last entry; outputs ≥ 0 enforced at validation.
- **Affinity** (FR-009): enum WEAKNESS | NEUTRAL | RESISTANCE | IMMUNITY | ABSORPTION.
- **CommandKind**: closed enum of battle command categories a class may permit; the
  behavior of commands is future features' scope — here it only gates capability.

## Battle layer (in-memory)

### Combatant (FR-001..003)
| Field | Type | Rules |
|---|---|---|
| id | CombatantId | deterministic from roster order (R7) |
| displayName | String | from definition |
| kind | CombatantKind | PARTY_MEMBER \| ENEMY \| TEMPORARY_ALLY |
| allegiance | Allegiance | PLAYER \| OPPONENT — independent axis from kind |
| stats | StatBlock | characters: `statsAt(class, level, baseStats)` (FR-008a); enemies: definition stats |
| affinities | Map\<ElementId, Affinity\> | from definition; missing ⇒ NEUTRAL |
| capabilities | CapabilitySet | **derived through the class reference at read time** (R1): skills, commands, limitBreaks from active class; enemies: skills from their definition + AI profile from archetype |
| decisionSource | DecisionSource | Human \| AiProfile(id); mutable independently (SC-004) |
| level | Int? | characters only; ≥ 1 |
| activeClassId | ClassId? | characters only; swappable to any valid class (SC-005) |

### Roster (US3, R7)
Ordered list of entries → instantiates Combatants with sequential `CombatantId`s.
Multiple entries may reference the same definition (three Goblins). Roster construction
fails if any referenced definition is absent from the validated catalog (unreachable if
catalog validation passed and IDs come from it — enforced by types where possible).

### State transitions
- `activeClassId` swap (job change): allowed to any class in catalog; recomputes
  derived capabilities and growth-based stats; nothing else changes (SC-005).
- `decisionSource` swap: changes nothing else (SC-004).
- No other mutation in this feature (HP deltas, statuses → later features).

## Validation error model (FR-013, R6)

`CatalogError` variants (each names the offending definition + reference):
- `DuplicateId(namespace, id, definitionIds)`
- `UnknownReference(fromDefinition, field, missingId)`
- `IncompleteStatBlock(definitionId, missingStatIds)`
- `UndeclaredCustomStat(definitionId, statId)`
- `CoreStatRedeclared(statId)`
- `EmptyCommands(classId)`
- `NegativeValue(definitionId, field, value)`
Result type: `ValidatedCatalog` XOR `List<CatalogError>` (all errors accumulated).
