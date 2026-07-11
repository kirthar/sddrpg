# tactical (reserved module slot)

Grid-positioning capabilities (Fire Emblem-style tactical combat) will live here as an
**optional** module layered on top of `core`.

Not implemented yet — this directory is an architectural placeholder only, per the
project constitution (Principle V: extensible architecture without speculative
implementation). It is intentionally **not** included in `settings.gradle.kts`.

Constraints already agreed for when this milestone starts:

- `core` must remain free of positional concepts; `tactical` adds them on top.
- The `TurnScheduler` abstraction in `core` must already support a phase-based
  implementation without contract changes.
