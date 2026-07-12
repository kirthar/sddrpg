package io.github.kirthar.sddrpg.content.demo

/**
 * A small, complete, internally-consistent demo content package (spec 007 US3):
 * cloud (warrior: skill "cleave", limit break "omnislash", 20 MP) and aerith (mage:
 * skill "fira") vs. a bomb enemy; poison (status effect), warriorMage (synergy
 * between cleave+fira), omnislash (limit break), and meteor (summon) exercise every
 * mechanic specs 001-006 built. A plain string constant (research R5) -- this
 * feature's own scope is text-in/package-out; actual file-system loading is feature
 * 008's concern.
 */
val DEMO_CONTENT_JSON: String = """
{
  "catalog": {
    "customStats": [],
    "elements": ["fire"],
    "equipmentCategories": [],
    "knownSkills": ["cleave", "fira"],
    "knownLimitBreaks": ["omnislash"],
    "knownAiProfiles": ["aggressive"],
    "classes": [
      {"id": "warrior", "displayName": "Warrior", "skills": ["cleave"], "commands": ["ATTACK", "SKILL", "SUMMON"], "limitBreaks": ["omnislash"]},
      {"id": "mage", "displayName": "Mage", "skills": ["fira"], "commands": ["ATTACK", "MAGIC"]}
    ],
    "archetypes": [
      {"id": "common", "displayName": "Common", "aiProfile": "aggressive", "rewardTier": 0}
    ],
    "characters": [
      {"id": "cloud", "displayName": "Cloud", "classId": "warrior", "baseStats": {"hp": 100, "mp": 20, "attack": 20, "defense": 10, "magic": 5, "resistance": 5, "speed": 10, "luck": 5}},
      {"id": "aerith", "displayName": "Aerith", "classId": "mage", "baseStats": {"hp": 70, "mp": 30, "attack": 8, "defense": 6, "magic": 18, "resistance": 12, "speed": 12, "luck": 8}}
    ],
    "enemies": [
      {"id": "bomb", "displayName": "Bomb", "archetypeId": "common", "stats": {"hp": 80, "mp": 0, "attack": 12, "defense": 6, "magic": 6, "resistance": 6, "speed": 8, "luck": 5}, "affinities": {"fire": "ABSORPTION"}}
    ]
  },
  "statusEffects": [
    {"id": "poison", "displayName": "Poison", "kind": {"type": "DamageOverTime", "amountPerTick": 5}, "duration": 3}
  ],
  "synergies": [
    {"id": "warriorMage", "firstSkillId": "cleave", "secondSkillId": "fira", "window": 2, "bonus": {"type": "BonusDamage", "amount": 10}}
  ],
  "limitBreaks": [
    {"id": "omnislash", "threshold": 50, "effectKind": "DAMAGE", "formula": {"type": "Fixed", "amount": 200}, "targeting": "SINGLE_ENEMY"}
  ],
  "summons": [
    {"id": "meteor", "cost": 15, "effectKind": "DAMAGE", "formula": {"type": "Fixed", "amount": 150}, "targeting": "SINGLE_ENEMY"}
  ]
}
""".trimIndent()
