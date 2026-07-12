# Feature Specification: Turn Scheduler

**Feature Branch**: `003-turn-scheduler`

**Created**: 2026-07-12

**Status**: Draft

**Input**: User description: "An abstracted TurnScheduler that determines whose turn it
is to act in battle, decoupled from any specific scheduling algorithm. Ship a
classic/ATB implementation for the demo, where turn readiness accrues based on each
combatant's Speed stat, but the abstraction must not preclude a future phase-based
scheduling mode (Fire Emblem style). Must consume the existing Combatant/BattleState/
CombatantId model from specs 001-002 without modifying their public contracts. Scope:
whose turn is next and when defeated combatants stop being scheduled; does not include
AI decision-making or the actual submission of actions."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Determine and advance whose turn is next (Priority: P1)

A game developer asks the engine whose turn it is to act next in an ongoing battle, and
the answer consistently favors faster combatants over time: given each combatant's
Speed, a faster combatant is offered more turns across a stretch of battle than a
slower one. Once a combatant's turn has been granted, the developer marks it spent so
the schedule advances — the same combatant is not offered the very next turn again
immediately, and the query can be repeated indefinitely to drive a battle loop.

**Why this priority**: This is the smallest slice that makes the engine turn-based
rather than turnless — a "next turn" query with no way to advance past it isn't a
usable scheduler (it would return the same combatant forever), so query and advance
must land together for the feature to have any standalone value.

**Independent Test**: Build a battle with a fast and a slow combatant, repeatedly ask
for and consume the next turn a fixed number of times, and verify the faster combatant
is offered turns more often than the slower one, without ever being offered the same
turn twice in a row artificially (the schedule visibly advances after each consumption).

**Acceptance Scenarios**:

1. **Given** a battle with combatants of different Speed values, **When** the next turn
   is requested, **Then** the scheduler reports exactly one ready combatant.
2. **Given** a combatant's turn has just been marked spent, **When** the next turn is
   requested again immediately, **Then** a different combatant is reported ready (the
   one just acted does not become ready again until it re-accrues readiness).
3. **Given** a roster where one combatant has a distinctly higher Speed than the
   others, **When** turns are requested and consumed repeatedly over an extended
   stretch, **Then** the faster combatant is offered a proportionally greater share of
   turns than any single slower combatant.
4. **Given** a battle configuration, **When** the developer queries readiness before
   any turn has ever been granted, **Then** the scheduler still reports a single ready
   combatant (readiness is available from the start, not only after a turn is spent).

---

### User Story 2 - Defeated combatants are automatically excluded (Priority: P1)

A game developer asks whose turn is next during a battle where a combatant has been
defeated (health reached zero via spec 002's action resolution). The defeated
combatant is never reported as ready, and the scheduler continues correctly choosing
among the remaining combatants without needing to be told separately that someone was
defeated — it reads the same battle state the rest of the engine already updates.

**Why this priority**: A scheduler that keeps offering turns to a combatant with zero
health is broken for any real battle, since every battle eventually defeats someone;
this is as fundamental to a working scheduler as User Story 1's basic query/advance
loop, so it is equally P1.

**Independent Test**: Defeat one combatant in a battle of three, then request and
consume several turns; verify the defeated combatant is never reported ready while the
other two continue to be scheduled normally.

**Acceptance Scenarios**:

1. **Given** a battle where one combatant has zero health, **When** the next turn is
   requested, **Then** the defeated combatant is never the one reported.
2. **Given** a combatant becomes defeated partway through an ongoing battle (its health
   reaches zero between two turn requests), **When** the next turn is requested after
   that point, **Then** the now-defeated combatant is excluded from that point forward.
3. **Given** every combatant on one side of a battle is defeated, **When** the next
   turn is requested, **Then** only combatants from the remaining side are ever
   reported.

---

### User Story 3 - Deterministic turn order and tie-breaking (Priority: P2)

A game developer or test author needs battle turn order to be exactly reproducible:
given the same battle configuration and the same sequence of turns consumed, asking
for the turn order twice yields the identical sequence, and whenever two or more
combatants become ready at the same moment, the scheduler picks between them by a
documented, consistent rule rather than an arbitrary or varying one.

**Why this priority**: Determinism is what makes the scheduler debuggable and testable
(and is a project-wide non-negotiable per the engine's principles), but a first
playable battle loop can exist without a test author having exercised this
specifically — it hardens User Stories 1–2 rather than adding new player-facing
capability, so it is P2.

**Independent Test**: Configure two combatants with identical Speed, run the same
sequence of turn requests/consumptions twice from the same starting battle
configuration, and verify both runs produce the identical turn order, with ties
resolved the same way every time.

**Acceptance Scenarios**:

1. **Given** two combatants with identical Speed, **When** both become ready at the
   same point in the schedule, **Then** the scheduler picks between them using the same
   rule every time it happens.
2. **Given** a fixed battle configuration and a fixed sequence of turn
   requests/consumptions, **When** that sequence is replayed from the same starting
   point, **Then** the resulting turn order is identical every time.

---

### Edge Cases

- A battle where every combatant is defeated: the next-turn query reports that no one
  is ready, rather than erroring or reporting a defeated combatant.
- A combatant with a Speed of zero: under the standard readiness accrual, it never
  becomes ready on its own; this is documented, expected behavior for that data value,
  not an error condition the scheduler must special-case.
- A single remaining (non-defeated) combatant: the next-turn query continues to report
  that combatant repeatedly as its readiness re-accrues, with no special-casing needed.
- Requesting the next turn without having consumed the previous one: the scheduler
  keeps reporting the same still-ready combatant (asking twice does not itself advance
  the schedule — only marking a turn spent does).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST expose a scheduling capability that, given the current
  state of a battle, determines which single combatant is ready to act next, or
  reports that none are ready.
- **FR-002**: The system MUST ship a classic/ATB scheduling implementation in which
  each combatant's readiness accrues over time as a function of that combatant's Speed
  stat (spec 001's core stat set), such that higher-Speed combatants become ready more
  frequently than lower-Speed combatants over an extended stretch of battle.
- **FR-003**: The system MUST provide a way to mark a granted turn as spent, after
  which that combatant is not immediately ready again — its readiness resumes
  accruing from that point rather than being re-offered instantly.
- **FR-004**: The scheduler MUST never report a defeated combatant (per spec 002's
  battle-time health tracking) as ready to act, and MUST correctly continue scheduling
  among the remaining combatants when one becomes defeated during the battle.
- **FR-005**: The scheduling capability MUST be defined independently of any one
  ordering algorithm, so that a future phase-based scheduling mode (e.g., all
  player-side combatants act, then all opposing-side combatants act) can be introduced
  without changing how the rest of the engine asks "whose turn is it" (constitution
  Principle V). This feature ships the classic/ATB implementation only; no phase-based
  implementation is built now.
- **FR-006**: Given the same battle configuration and the same sequence of turns
  consumed, the scheduler MUST produce the identical turn order every time
  (constitution Principle I, determinism).
- **FR-007**: When multiple combatants become ready at the same point in the schedule,
  the scheduler MUST break the tie using one documented, deterministic rule — never
  randomly and never inconsistently between otherwise-identical situations.
- **FR-008**: The scheduler MUST NOT make decisions on a combatant's behalf (no AI
  logic) and MUST NOT submit or resolve any action — it only reports whose turn it is
  and advances when told a turn was spent; actually acting is spec 002's
  `resolveAction`, invoked separately once a turn is granted.
- **FR-009**: When no combatant in the battle is eligible to act (all defeated), the
  scheduler MUST report that no next turn exists rather than erroring.
- **FR-010**: This feature MUST consume the existing `Combatant`/`BattleState`/
  `CombatantId` model exactly as published by specs 001–002, without requiring any
  change to their public contracts.

### Key Entities

- **Turn Scheduler**: the capability that, given battle state, reports the next ready
  combatant (or none) and accepts notice that a turn was spent.
- **Schedule State**: the data a specific scheduling algorithm needs to track readiness
  over time for every combatant in the battle (e.g., accrued readiness values for
  ATB); introduced by this feature, additional to — and independent of — spec 002's
  `BattleState`.
- **Readiness**: a combatant's current standing toward being offered its next turn.
- **Turn**: the event of one specific combatant being reported ready to act.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In a battle with combatants of distinct Speed values, observed over many
  consumed turns, each combatant's share of turns granted trends with its Speed
  relative to the others (faster combatants are never offered fewer turns than slower
  ones over an extended stretch).
- **SC-002**: A defeated combatant receives zero turns for the remainder of the battle,
  in 100% of observed cases.
- **SC-003**: Requesting the turn order for the same battle configuration and the same
  turn-consumption sequence twice yields an identical sequence, every time.
- **SC-004**: Every tie in readiness is broken by the same documented rule, with zero
  observed inconsistency across repeated identical situations.
- **SC-005**: A battle in which every combatant is defeated always reports "no next
  turn" rather than an error, in 100% of observed cases.
- **SC-006**: A future phase-based scheduling mode can be introduced by implementing
  the scheduling capability alone, without any other engine module's code changing
  (verified structurally, mirroring spec 002's SC-006 formula-extensibility criterion).

## Assumptions

- The Speed value used for readiness accrual is spec 001's core `Speed` stat, already
  present on every combatant regardless of kind (party member, enemy, temporary ally).
- The exact readiness accrual formula, threshold, and tie-breaking rule's specific
  implementation are technical decisions deferred to `/speckit-plan` (mirroring how
  spec 002 deferred its formula/multiplier specifics) — this spec fixes their
  existence, determinism, and observable behavior, not their numbers.
- This feature answers "whose turn is it" and "advance past a spent turn"; it does not
  wire a full battle loop (repeatedly querying the scheduler, invoking spec 002's
  `resolveAction`, and checking victory/defeat conditions) — that orchestration is a
  later feature's scope.
- AI decision-making (choosing *what* action a scheduled combatant takes) is explicitly
  out of scope for this feature; a later feature supplies that behind spec 001's
  `DecisionSource` abstraction.
- Combatants joining or leaving a battle mid-fight (beyond becoming defeated) — e.g., a
  summon arriving later — is not a mechanic either spec 001 or spec 002 models yet
  (rosters are fixed at battle start), so this feature does not need to handle it; it
  is left to whichever future feature introduces that mechanic.
- Phase-based (Fire Emblem-style) scheduling is not implemented in this feature; only
  the door is kept open per FR-005/SC-006.
