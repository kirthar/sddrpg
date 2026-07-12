# Feature Specification: Content Loading

**Feature Branch**: `007-content-loading`

**Created**: 2026-07-12

**Status**: Draft

**Input**: User description: "Content loading: the content module (currently a
scaffold with zero loading logic) gains the ability to load a complete, validated
game-content pack from external data, plus ships a small demo-ready content pack
exercising every mechanic specs 001-006 built. Grounded in the actual codebase: spec
001's Catalog/ClassDefinition/CharacterDefinition/ArchetypeDefinition/EnemyDefinition
are already serializable with existing round-trip test infrastructure, but specs
004-006's own catalogs (StatusEffectCatalog, SynergyCatalog, LimitBreakCatalog,
SummonCatalog) and their component sealed types are NOT currently serializable at
all -- content loading is the first feature that needs all of these catalogs to
actually come from external data together, so how each becomes loadable is an open
technical question. Loading MUST validate every sub-catalog via each spec's own
existing validation, accumulating every error found across all of them in one report,
never a partial success. Loading MUST additionally catch cross-catalog reference
problems that no single spec's own validation can see today -- e.g. a class's limit
break reference is only checked against spec 001's own internal allow-list, never
against whether an actual definition exists in the separately-validated
LimitBreakCatalog; this is the first feature where these independently-designed
catalogs are ever brought together, so it's the first place such a mismatch could
even be detected. Must consume every existing catalog/validation function from specs
001-006 without modifying their existing public behavior. Out of scope: any UI/
console rendering of loaded content; this feature's own parsing/validation logic
should stay pure and testable, deferring actual file-system reading to feature 008's
console demo; the specific balance/difficulty tuning of the demo content (just needs
to load and be internally consistent, not be 'fun' or balanced)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Game content loads as one validated, complete package (Priority: P1)

A content author writes game content -- classes, characters, enemies, status
effects, synergies, limit breaks, and summons -- as external data, and the engine
loads it as a single validated package in one call. If everything is valid, the
loader hands back one complete, ready-to-use bundle covering every mechanic. If
anything anywhere is invalid, nothing is silently accepted -- loading fails and
reports every problem found, not just the first one encountered.

**Why this priority**: This is the foundation everything else in this feature
depends on -- without a working load-and-validate pipeline, there is no way to
exercise User Story 2's cross-catalog checks or to ship User Story 3's demo content.
It also delivers value on its own: a content author already gets fast, complete
feedback on mistakes instead of a crash deep inside a battle.

**Independent Test**: Author a complete, internally-valid set of content covering
every catalog kind, load it, and verify a single successful package results
containing all of it; separately author content with several unrelated mistakes
scattered across different catalog kinds, load it, and verify every mistake is
reported together, not just the first.

**Acceptance Scenarios**:

1. **Given** a complete, valid set of authored content, **When** it is loaded,
   **Then** one successful package results, containing every catalog's validated
   content.
2. **Given** authored content with a validation problem in exactly one catalog kind
   (for example, a duplicated status effect identifier), **When** it is loaded,
   **Then** loading fails and the problem is reported, identifying which catalog and
   which entry.
3. **Given** authored content with unrelated validation problems in several different
   catalog kinds at once, **When** it is loaded, **Then** loading fails once, and the
   report includes every one of those problems, not only the first encountered.
4. **Given** authored content that omits an optional catalog entirely (for example,
   no synergies authored), **When** it is loaded, **Then** loading still succeeds,
   with that catalog present but empty in the resulting package.

---

### User Story 2 - A reference between two independent catalogs that doesn't actually resolve is caught at load time (Priority: P1)

A content author's class declares it grants a limit break, or a synergy declares it
requires a skill, or any other reference that spans two independently-authored parts
of the content -- and that reference doesn't actually correspond to anything real
once every catalog is loaded together. Loading catches this and reports it, the same
way any other content mistake is reported -- not a crash discovered only when a
player happens to trigger that exact situation mid-battle.

**Why this priority**: Each existing catalog already validates *itself*
in isolation (spec 001, 004, 005, 006 each already do this), but none of them can see
each other -- this is the very first feature where they're ever brought together, so
it's the first and only place this class of mistake can be caught before it reaches a
player. Equally load-bearing as User Story 1: a "validated" package that still lets
this kind of dangling reference through doesn't deliver what "validated" promises.

**Independent Test**: Author content where a class's declared limit break identifier
has no corresponding definition anywhere in the loaded limit break content, load it,
and verify this is reported as a content problem identifying the dangling reference,
even though each individual catalog would pass its own isolated validation
unchanged.

**Acceptance Scenarios**:

1. **Given** a class that declares a limit break identifier with no matching
   definition anywhere in the loaded content, **When** it is loaded, **Then** loading
   fails and the report identifies the dangling reference.
2. **Given** the same situation but every reference actually does resolve correctly
   once all catalogs are considered together, **When** it is loaded, **Then** loading
   succeeds.
3. **Given** several different kinds of cross-catalog dangling references at once,
   **When** it is loaded, **Then** every one of them is reported together, using the
   same accumulate-everything reporting as User Story 1.

---

### User Story 3 - A small, complete demo content pack is ready to use (Priority: P2)

The engine ships with a small, hand-authored set of game content that exercises
every mechanic built so far -- at least one playable class with a skill and a limit
break, at least one enemy, a status effect, a synergy between two classes, and a
summon -- and it loads successfully every time.

**Why this priority**: Every prior feature deferred its own specific content
("which classes get which limit break," "the exact window size," etc.) to this
feature -- this is where that debt finally gets paid so the console demo (a later
feature) has something real to run. It depends on User Stories 1-2 already working
correctly, so it can't be done first, but it's what makes this feature's payoff
tangible rather than purely mechanical.

**Independent Test**: Load the shipped demo content pack with no modifications and
verify it succeeds, producing a package with at least one entry in every catalog
kind this engine supports.

**Acceptance Scenarios**:

1. **Given** the shipped demo content pack, **When** it is loaded, **Then** loading
   succeeds with no reported problems.
2. **Given** the loaded demo package, **When** each catalog kind is inspected,
   **Then** every one of them (classes/characters/enemies, status effects, synergies,
   limit breaks, summons) has at least one entry.

---

### Edge Cases

- Content that is syntactically malformed (not valid data at all, before any
  business-rule validation even begins) is reported as a clear parsing problem, not a
  crash and not confused with a business-rule validation problem.
- A dangling cross-catalog reference and an ordinary single-catalog validation
  problem occurring together are both reported in the same pass (User Story 1's
  accumulate-everything guarantee extends to User Story 2's checks).
- Loading the exact same valid content twice in a row produces two structurally
  identical successful packages -- no hidden state carries over between loads.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow game content -- classes, characters, enemies,
  status effects, synergies, limit breaks, and summons -- to be authored as external
  data, not as compiled code, consistent with every prior spec's data-driven design
  (constitution Principle II).
- **FR-002**: Loading MUST validate every catalog kind using each spec's own existing
  validation rules (spec 001's class/character/enemy validation, spec 004's status
  effect validation, spec 005's synergy validation, spec 006's limit break and summon
  validation), never inventing a parallel or looser rule set.
- **FR-003**: Loading MUST accumulate every validation problem found across every
  catalog kind into a single report; loading MUST NOT stop at the first problem
  found, and MUST NOT report success when any problem exists anywhere.
- **FR-004**: Loading MUST additionally check cross-catalog references -- any
  reference from one catalog kind to an identifier defined in a different catalog
  kind -- and report any such reference that fails to resolve, even when each
  individual catalog kind would pass its own isolated validation.
- **FR-005**: A successful load MUST produce exactly one complete, immutable content
  package bundling every validated catalog, ready for a consuming feature (the
  console demo) to use.
- **FR-006**: An omitted or empty optional catalog kind MUST load successfully as an
  empty catalog of that kind, never as an error (matching the existing behavior of
  each catalog kind's own validation).
- **FR-007**: Loading MUST be fully deterministic -- the same content, loaded twice,
  MUST always produce structurally identical results, with no randomness and no
  reliance on anything beyond the content provided (constitution Principle I).
- **FR-008**: The engine MUST ship one small, complete, internally-consistent demo
  content package that loads successfully and includes at least one entry in every
  catalog kind this engine currently supports.
- **FR-009**: This feature MUST consume every existing catalog type and validation
  function from specs 001-006 exactly as published, without requiring any change to
  their existing public behavior (purely additive, matching every prior spec's
  discipline).

### Key Entities

- **Content Package**: the single, complete, validated bundle a successful load
  produces -- one instance of every catalog kind this engine supports.
- **Content Problem**: one reported validation failure, identifying what kind of
  problem it is and precisely what in the authored content caused it -- whether from
  an individual catalog's own existing rules or from a cross-catalog reference check.
- **Cross-Catalog Reference**: an identifier declared in one catalog kind (for
  example, a class's limit break identifier) that is expected to correspond to a real
  definition in a different catalog kind (for example, the limit break catalog).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Loading a complete, valid set of authored content succeeds and yields a
  package containing every catalog's content, in 100% of observed cases.
- **SC-002**: Loading content with any validation problem, anywhere, never succeeds
  and never silently drops the problem, in 100% of observed cases.
- **SC-003**: Loading content with multiple unrelated problems across different
  catalog kinds reports every one of them in a single pass, never only the first, in
  100% of observed cases.
- **SC-004**: A cross-catalog reference that fails to resolve is caught at load time
  in 100% of observed cases -- never discovered later as a runtime failure.
- **SC-005**: Loading the same content twice always produces structurally identical
  results.
- **SC-006**: The shipped demo content package loads successfully with zero reported
  problems, every time, and contains at least one entry in every catalog kind.

## Assumptions

- **How specs 004-006's catalogs become loadable from external data** -- retrofitting
  serialization support directly onto their existing files, versus this feature's own
  additive mapping layer that touches nothing in `core` -- is a technical decision
  deferred to `/speckit-plan`, mirroring how specs 004-006 each deferred their own
  central "how" question (spec 004's `Combatant`-wrapping mechanism, spec 005's event-
  derivation pattern, spec 006's action-submission mechanism).
- **External data format**: assumed to be the same textual, human-authorable format
  spec 001 already committed to and already has working round-trip infrastructure
  for, rather than introducing a second format -- consistent with "don't introduce a
  parallel mechanism where a working one already exists."
- **File-system access is out of scope for this feature**: this feature's own loading
  logic operates on already-in-memory textual content (a function from text to a
  validated package or a problem report) and stays pure and testable; actually
  reading that text from disk, a bundled resource, or any other source is feature
  008's concern, matching constitution Principle I's "no I/O in the pure core"
  discipline extended consistently to this feature's own logic.
- **Cross-catalog reference scope (v1)**: this feature checks the references that
  specs 001-006's own designs already established as spanning two catalogs (for
  example, a class's limit break identifiers against the limit break catalog); it
  does not invent new cross-catalog relationships beyond what those specs already
  implied. Which specific references qualify is enumerated during `/speckit-plan`.
- **Demo content specifics** (which exact classes, enemies, status effects, synergy,
  limit break, and summon ship) are authored during implementation of this feature,
  not prescribed here -- this spec only requires that *something* complete and
  internally consistent exists and loads, not what it specifically is.
- Rendering or presenting loaded content (a console demo's UI) is explicitly out of
  scope (constitution Principle IV) -- this feature only makes content loadable and
  validated.
