# AGENTS.md
 
(ATTENTION: THIS FILE HAS TO BE UPDATED ACCRODING TO OUR PROJECT, NOW IT IS A SIMPLE
COPY FROM THE BOOTCAMP)

Durable rules for any AI agent working in this repository. Read this before acting.
Writing them down also helps the human team agree on how we build.

## The app

A Kotlin/Android ToDo app (`com.github.se.bootcamp`), built with an **MVVM** architecture.

- `model/` holds the data and repositories (Firestore, Location, ...).
- `ui/` holds the screens and their **ViewModels**.
- `sigchecks/` are signature checks used for grading. **Never edit them.**

## Architecture rules

- Keep the **MVVM** separation. **ViewModels never import Firebase** or a repository implementation; they depend on repository interfaces. Firebase lives only in the `model/` repositories.
- Do not edit anything under `sigchecks/` or generated code.

## Definition of done

- The feature works and matches its acceptance criteria (the milestone's tests).
- **All new code comes with unit tests.**
- `./gradlew check` is green (unit tests + lint) and `./gradlew ktfmtCheck` passes (formatting) before you submit.
- Features covered by instrumented tests (under `androidTest/`, e.g. the Firestore repository) must also pass `./gradlew connectedDebugAndroidTest`, with an Android emulator and the Firebase emulator running.
- Select a milestone's tests with the `-B1` / `-B2` and `-public` / `-staff` flags, e.g. `./gradlew check -B1 -public` or `./gradlew connectedCheck -B2 -staff`. With no flag the whole suite runs.
- CI is part-filtered via `BOOTCAMP_PART` in `.github/workflows/CI.yml` (`B1` or `B2`). Set that to the milestone you are working on; the `part-public` job then runs that milestone's public tests, and its staff tests as a soft-fail.

## How to work

- Make one **bounded, reviewable** change per PR. If it sprawls across unrelated files, split it.
- Read the failing tests carefully and iterate until `./gradlew check` passes.
- Stage only the files you changed; never `git add .` or `git add -A` (it can pull in local config like `local.properties`).
- Commit with an imperative subject of at most 50 characters, capitalized (e.g. `Add user authentication`). Add a body wrapped at 72 characters when the subject is not enough.
- **Acknowledge your contributors** at the top of the file: credit the AI that wrote it with a `Co-authored-by` line. An AI agent is a contributor, so credit it.

## Your role

You provide the goal, the context, the acceptance criteria and the permissions. The agent plans, acts and observes. **You review the diff, and you own every line you submit.** "The agent wrote it" is not a defence.
