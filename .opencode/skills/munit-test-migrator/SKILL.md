---
name: munit-test-migrator
description: Migrates Kamon Telemetry tests from Scalatest to MUnit
---

Whenever you get asked to migrate a test from Scalatest to MUnit, follow these guidelines:

- Do not change the code being tested. Only change the tests and if you ever see
  that a code change would be necessary for the test migration to continue then
  ask for confirmation with a clear explanation of why the change is necessary
- Do not try to keep the nested structure from Scalatest's `WordSpec`. When you
  encounter `"something being tested" when/should {...}` blocks and then the
  `"the behaviour we are testing" in {...}` try to keep the test names with the
  same as in the `in` expression, but prefix with a bit of the outer context if
  necessary for clarity
- Do not bring additional dependencies unless explicitly asked for
- Do not change the file names.
- Migrate all assertions to plain MUnit asserts. The full list of available assertions
  is available here: <https://raw.githubusercontent.com/scalameta/munit/refs/heads/main/docs/assertions.md>
- Do not try to reproduce the matchers DSL from Scalatest. We will migrate to plain
  assertions
- Use the tests in the `kamon-core-tests` subproject as an example of how tests should
  look like
- Use the MUnit-specific traits in the `kamon-testkit` project
- Use the `--client --mem 3000` flag whenever you call SBT so that it reuses a server that
  should already be running
- Compile after all modifications to a single test file. Fix issues as necessary
- Cross-compile and test after done to make sure that the tests are working properly
  for all Scala versions
