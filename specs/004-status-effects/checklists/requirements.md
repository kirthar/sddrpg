# Specification Quality Checklist: Status Effects

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-07-12
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [ ] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- Unlike specs 002/003 (which reached a fully green checklist by routing every
  deferrable decision into Assumptions), this spec keeps **one** genuine
  `[NEEDS CLARIFICATION]` marker on FR-007: how an incapacitating effect's own
  duration decreases when the affected combatant never receives ordinary turns from
  the scheduler. Unlike the stacking-policy or tick-granularity questions (resolved
  via Assumptions with a defensible genre-standard default), this one has two
  materially different, defensible interpretations with real pacing/game-feel
  consequences and no clear industry-standard default — exactly the case the
  spec-writing guidance reserves `[NEEDS CLARIFICATION]` for.
- Everything else (stacking policy, tick granularity, content-catalog scope, the
  additive-extension technical question) was resolved via Assumptions with stated
  reasoning, same convention as specs 001–003.
- `/speckit-clarify` is **required** before `/speckit-plan` for this spec — do not
  skip it, unlike specs 002/003 where it was optional.
