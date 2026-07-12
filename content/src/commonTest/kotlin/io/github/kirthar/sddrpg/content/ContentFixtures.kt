package io.github.kirthar.sddrpg.content

import kotlinx.serialization.json.Json

/** Strict decoding (unknown keys rejected), matching core's own `TestJson` convention. */
val ContentTestJson: Json = Json { ignoreUnknownKeys = false }

/**
 * A minimal but complete content text covering every catalog kind, with every
 * cross-catalog reference actually resolving. cloud (warrior: skill "cleave", limit
 * break "omnislash"), aerith (mage: skill "fira"), bomb (enemy). One status effect
 * (poison), one synergy (cleave+fira), one limit break (omnislash), one summon
 * (meteor).
 */
val completeContentText: String = """
{
  "catalog": {
    "customStats": [],
    "elements": ["fire"],
    "equipmentCategories": [],
    "knownSkills": ["cleave", "fira"],
    "knownLimitBreaks": ["omnislash"],
    "knownAiProfiles": ["aggressive"],
    "classes": [
      {"id": "warrior", "displayName": "Warrior", "skills": ["cleave"], "commands": ["ATTACK", "SKILL"], "limitBreaks": ["omnislash"]},
      {"id": "mage", "displayName": "Mage", "skills": ["fira"], "commands": ["ATTACK", "MAGIC"]}
    ],
    "archetypes": [
      {"id": "common", "displayName": "Common", "aiProfile": "aggressive", "rewardTier": 0}
    ],
    "characters": [
      {"id": "cloud", "displayName": "Cloud", "classId": "warrior", "baseStats": {"hp": 100, "mp": 20, "attack": 20, "defense": 10, "magic": 5, "resistance": 5, "speed": 10, "luck": 5}}
    ],
    "enemies": [
      {"id": "bomb", "displayName": "Bomb", "archetypeId": "common", "stats": {"hp": 50, "mp": 0, "attack": 10, "defense": 5, "magic": 5, "resistance": 5, "speed": 5, "luck": 5}}
    ]
  },
  "statusEffects": [
    {"id": "poison", "displayName": "Poison", "kind": {"type": "DamageOverTime", "amountPerTick": 5}, "duration": 3}
  ],
  "synergies": [
    {"id": "warriorMage", "firstSkillId": "cleave", "secondSkillId": "fira", "window": 2, "bonus": {"type": "BonusDamage", "amount": 10}}
  ],
  "limitBreaks": [
    {"id": "omnislash", "threshold": 50, "effectKind": "DAMAGE", "formula": {"type": "Fixed", "amount": 999}, "targeting": "SINGLE_ENEMY", "element": "fire"}
  ],
  "summons": [
    {"id": "meteor", "cost": 15, "effectKind": "DAMAGE", "formula": {"type": "Fixed", "amount": 999}, "targeting": "SINGLE_ENEMY"}
  ]
}
""".trimIndent()

/** Same as [completeContentText] but statusEffects/synergies/limitBreaks/summons omitted entirely. */
val minimalContentText: String = """
{
  "catalog": {
    "knownAiProfiles": ["aggressive"],
    "archetypes": [{"id": "common", "displayName": "Common", "aiProfile": "aggressive", "rewardTier": 0}],
    "enemies": [{"id": "bomb", "displayName": "Bomb", "archetypeId": "common", "stats": {"hp": 50, "mp": 0, "attack": 10, "defense": 5, "magic": 5, "resistance": 5, "speed": 5, "luck": 5}}]
  }
}
""".trimIndent()

/** A duplicated status effect id -- a single-catalog (spec 004) validation problem, everything else valid. */
val duplicateStatusEffectContentText: String = """
{
  "catalog": {"knownAiProfiles": ["aggressive"], "archetypes": [{"id": "common", "displayName": "Common", "aiProfile": "aggressive", "rewardTier": 0}], "enemies": [{"id": "bomb", "displayName": "Bomb", "archetypeId": "common", "stats": {"hp": 50, "mp": 0, "attack": 10, "defense": 5, "magic": 5, "resistance": 5, "speed": 5, "luck": 5}}]},
  "statusEffects": [
    {"id": "poison", "displayName": "Poison", "kind": {"type": "DamageOverTime", "amountPerTick": 5}, "duration": 3},
    {"id": "poison", "displayName": "Poison 2", "kind": {"type": "DamageOverTime", "amountPerTick": 3}, "duration": 2}
  ]
}
""".trimIndent()

/** Problems in two unrelated catalog kinds at once: a duplicate status effect id AND a non-positive limit break threshold. */
val multipleUnrelatedProblemsContentText: String = """
{
  "catalog": {"knownAiProfiles": ["aggressive"], "archetypes": [{"id": "common", "displayName": "Common", "aiProfile": "aggressive", "rewardTier": 0}], "enemies": [{"id": "bomb", "displayName": "Bomb", "archetypeId": "common", "stats": {"hp": 50, "mp": 0, "attack": 10, "defense": 5, "magic": 5, "resistance": 5, "speed": 5, "luck": 5}}]},
  "statusEffects": [
    {"id": "poison", "displayName": "Poison", "kind": {"type": "DamageOverTime", "amountPerTick": 5}, "duration": 3},
    {"id": "poison", "displayName": "Poison 2", "kind": {"type": "DamageOverTime", "amountPerTick": 3}, "duration": 2}
  ],
  "limitBreaks": [
    {"id": "omnislash", "threshold": 0, "effectKind": "DAMAGE", "formula": {"type": "Fixed", "amount": 999}, "targeting": "SINGLE_ENEMY"}
  ]
}
""".trimIndent()

val malformedContentText: String = "{ not valid json at all"
