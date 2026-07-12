# Contract: Core Catalog & Combatant Public API

**Feature**: 001-combatant-class-model | **Consumers**: `content` module (007), demos,
future engine features (002+)

This is the public surface `core` commits to for this feature. Signatures are
normative; bodies/internals are not. See [data-model.md](../data-model.md) for field
tables and validation rules.

## Kotlin API (commonMain, package `io.github.kirthar.sddrpg.core`)

```kotlin
// catalog.CatalogValidation
fun validateCatalog(raw: Catalog): CatalogResult

sealed interface CatalogResult {
    data class Valid(val catalog: ValidatedCatalog) : CatalogResult
    data class Invalid(val errors: List<CatalogError>) : CatalogResult  // non-empty, exhaustive
}

// catalog.ValidatedCatalog — immutable, indexed lookup; only obtainable via validateCatalog
interface ValidatedCatalog {
    fun classDefinition(id: ClassId): ClassDefinition
    fun archetype(id: ArchetypeId): ArchetypeDefinition
    fun character(id: CharacterId): CharacterDefinition
    fun enemy(id: EnemyId): EnemyDefinition
    val customStats: Set<StatId>
    val elements: Set<ElementId>
}

// model.Stats
fun statsAt(classDef: ClassDefinition, level: Int, baseStats: StatBlock): StatBlock
// pure; deterministic; requires level >= 1 (FR-008a)

// combatant.Roster
class RosterBuilder(catalog: ValidatedCatalog) {
    fun addPartyMember(id: CharacterId, level: Int,
                       decisionSource: DecisionSource = DecisionSource.Human): RosterBuilder
    fun addEnemy(id: EnemyId,
                 decisionSource: DecisionSource? = null): RosterBuilder
                 // null ⇒ archetype's AI profile
    fun addTemporaryAlly(id: CharacterId, level: Int,
                         aiProfile: AiProfileId): RosterBuilder
    fun build(): Roster  // deterministic CombatantIds by insertion order
}

// combatant.Combatant — uniform attribute surface (FR-001)
interface Combatant {
    val id: CombatantId
    val displayName: String
    val kind: CombatantKind
    val allegiance: Allegiance
    val stats: StatBlock
    val affinities: Map<ElementId, Affinity>   // total: absent element ⇒ NEUTRAL via affinityTo()
    val capabilities: CapabilitySet             // derived through active class / archetype
    val decisionSource: DecisionSource
    fun affinityTo(element: ElementId): Affinity
}

// progression point (R1): returns a copy with the new class applied, everything else intact
fun Combatant.withActiveClass(classId: ClassId, catalog: ValidatedCatalog): Combatant
fun Combatant.withDecisionSource(source: DecisionSource): Combatant
```

Stability rules:
- `CatalogResult.Invalid.errors` is exhaustive (all errors, accumulated — never first-only).
- `ValidatedCatalog` lookups never fail for IDs originating from the same catalog.
- `statsAt` is a pure function: same inputs ⇒ same `StatBlock`, on every platform.
- Roster instance IDs depend only on insertion order (replay-stable).

## JSON contract (canonical interchange, FR-014)

A catalog document (top-level keys; all sections optional except where content needs
them; strict parsing — unknown keys are errors):

```json
{
  "customStats": ["bravery"],
  "elements": ["fire", "ice", "thunder"],
  "equipmentCategories": ["swords", "staves"],
  "knownSkills": ["fira", "cura", "cleave"],
  "knownLimitBreaks": ["braver"],
  "knownAiProfiles": ["aggressive", "healer-priority"],
  "classes": [
    {
      "id": "warrior",
      "displayName": "Warrior",
      "skills": ["cleave"],
      "commands": ["ATTACK", "SKILL", "DEFEND", "ITEM"],
      "growth": {
        "hp":     { "type": "linear", "base": 120, "perLevel": 11 },
        "attack": { "type": "table", "values": [10, 12, 15, 19] }
      },
      "equipmentCategories": ["swords"],
      "limitBreaks": ["braver"]
    }
  ],
  "archetypes": [
    { "id": "boss", "displayName": "Boss", "aiProfile": "aggressive", "rewardTier": 3 }
  ],
  "characters": [
    {
      "id": "cloud",
      "displayName": "Cloud",
      "classId": "warrior",
      "baseStats": { "hp": 100, "mp": 20, "attack": 12, "defense": 10,
                      "magic": 6, "resistance": 8, "speed": 9, "luck": 5 },
      "affinities": { "ice": "RESISTANCE" }
    }
  ],
  "enemies": [
    {
      "id": "bomb",
      "displayName": "Bomb",
      "archetypeId": "boss",
      "skills": ["fira"],
      "stats": { "hp": 300, "mp": 50, "attack": 14, "defense": 8,
                  "magic": 12, "resistance": 6, "speed": 7, "luck": 3 },
      "affinities": { "fire": "ABSORPTION", "ice": "WEAKNESS" }
    }
  ]
}
```

Notes:
- Core stat keys (`hp`, `mp`, `attack`, `defense`, `magic`, `resistance`, `speed`,
  `luck`) are reserved; `customStats` may not redeclare them.
- `growth` uses a `type` discriminator with kinds `linear` | `table` (closed set,
  engine capability — R5).
- Affinity values: `WEAKNESS | NEUTRAL | RESISTANCE | IMMUNITY | ABSORPTION`;
  omitted elements are NEUTRAL.
- `knownSkills` / `knownLimitBreaks` / `knownAiProfiles` are placeholder ID
  declarations until features 002/006 define real schemas; referencing content will not
  change when that happens.
```
