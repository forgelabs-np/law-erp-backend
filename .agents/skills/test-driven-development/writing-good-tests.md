# Writing Good Tests
**Load this reference when:** writing or changing tests, adding mocks, or
adding cleanup/helper methods for tests.
## Overview
A test exists to catch a specific break. Two principles govern everything
here:
```
1. Every test names the break it catches
2. Every test exercises the real thing
```
Strict TDD produces both naturally: a test written first and watched
failing against real code has already proven it can fail, and only earns
a mock when the real dependency proves slow or external.
## Principle 1: Name the Break
Before writing the test body, answer: **what production change should
make this test fail — and is that change a bug or a decision?** A test
earns its place by catching a wrong branch, missing side effect, wrong
argument, boundary case, or broken contract.
**Derive expectations independently.** Use literals and hand-checked
fixtures; table-driven tests with literal `want` values are the preferred
shape. An expectation computed by the code under test — or its helpers —
passes no matter what that code does:
```typescript
// ❌ Mirror assertion: the same builder computes both sides — always true
const expected = buildSearchQuery({ tag: 'urgent' });
expect(buildSearchQuery({ tag: 'urgent' })).toBe(expected);
// ✅ Hand-derived literal
expect(buildSearchQuery({ tag: 'urgent' })).toBe('tag:"urgent"');
```
**No change detectors.** If only intentional decisions can fail a test —
a constant's value, exact message wording, private structure — it fires
on redesign and sleeps through bugs. Test the behavior that depends on
the decision: not `expect(MAX_RETRIES).toBe(5)` but "a failing call is
retried 5 times and the 6th attempt never happens."
**Behavior, not text.** Asserting that a script, skill, or config
contains an exact line proves only that the source is the source. Run
scripts against controlled inputs and assert outputs, side effects, or
exit codes.
**Your code, not the framework.** Test the contract your code makes at
its boundaries — the route you register, the query you emit, the payload
you produce.
### Gate Function
```
BEFORE writing the test body:
Name the production change that would make this test fail.
Cannot name one → redesign around an observable behavior
"The source text changed" → run the artifact and assert its effects
Only intentional decisions → change detector; test the behavior
that depends on the decision
Confirm the expected value is derived without the code under test.
IF it reuses the code's logic or helpers:
Replace it with a literal or hand-checked fixture
```
## Principle 2: Exercise the Real Thing
**The mock earns no assertions.** A mock assertion passes when the mock
is present and fails when it is absent — it says nothing about the
component. Assert the real component's behavior; if the mock is what you
are checking, unmock it or delete the assertion.
```typescript
// ✅ Real behavior
expect(screen.getByRole('navigation')).toBeInTheDocument();
// ❌ Mock existence
expect(screen.getByTestId('sidebar-mock')).toBeInTheDocument();
```
**Mock at the right level.** Learn every side effect of the real method
before replacing it; mock the slow or external operation and keep what
the test depends on real.
```typescript
// ❌ The mock swallows the config write that duplicate detection reads
vi.mock('ToolCatalog', () => ({
discoverAndCacheTools: vi.fn().mockResolvedValue(undefined)
}));
// ✅ Mock only the slow server startup; the config write stays real
vi.mock('MCPServerManager');
```
**Make doubles specific.** When arguments, call counts, or ordering are
part of the contract, assert them — a fake that accepts anything verifies
nothing.
**Mirror real data completely.** Mock the complete structure as it exists
in reality — all documented fields — not just the ones your test reads.
**Production classes carry production methods only.** Cleanup that only
tests need lives in test utilities, never as a `destroy()` on the
production class.
**Prefer real components over complex mocks.** When mock setup outgrows
the test logic, mocks miss methods the real components have, or tests
break when the mock changes, switch to an integration test with real
components.
## The Mutation Check
Before finishing, mentally mutate the production code; at least one test
should fail for each realistic mutation:
- Wrong constant or argument
- Wrong branch handler
- Missing state change or side effect
- Empty or default return
- Missing validation for zero, empty, nil, unauthorized, or malformed input
A mutation nothing catches marks the behavior as unprotected — or the
test as tautological.
## Quick Reference
| When you... | Do |
|-------------|-----|
| Write any test | Name the break it catches — a bug, not a decision |
| Build an expected value | Derive it by hand; never with the code under test |
| Test a script or document | Run it / pressure-test its consumer; never grep its text |
| Reach for a dependency test | Test your boundary contract, not their documented mechanics |
| Want to assert on a mocked element | Test the real component, or unmock it |
| Are about to mock a method | Learn its side effects; mock the slow/external level |
| Build a mock response | Mirror the real structure completely |
| Need cleanup only tests use | Put it in test utilities |
| Watch mock setup balloon | Switch to an integration test with real components |
| Finish a test file | Run the mutation check |
