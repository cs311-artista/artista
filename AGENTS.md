# AGENTS.md

Durable rules for any AI agent working in this repository. Read this before acting.
Writing them down also helps the human team agree on how we build.

## The app

A Kotlin/Android Artista app (`com.artista.artista`), built with an **MVVM** architecture.

- `model/` holds the data and repositories (Firestore, Location, ...).
- `ui/` holds the screens and their **ViewModels**.

## Architecture rules

- Keep the **MVVM** separation. **ViewModels never import Firebase** or a repository implementation; they depend on repository interfaces. Firebase lives only in the `model/` repositories.

## Language and documentation

- **Everything is in English**: code, identifiers, comments, KDoc, test names, commit messages, PR descriptions.
- Every public class, function and property gets a **KDoc** comment describing what it does, its parameters and its return value:

```kotlin
/**
 * Computes the total price of an order line.
 * 
 * @author the one writting 
 * @param price unit price of the product
 * @param quantity number of units
 * @return the total price
 */
fun calculateTotal(price: Double, quantity: Int): Double = price * quantity
```

- Use `@param` for each parameter, `@author` with the github ID of the author, `@return` when the function returns something, and `@throws` when it can throw.
- Comments explain **why**, not what the code already says.

## Branches

Format: `<type>-<short-description>`

- `<type>` is one of the commit types below (`feat`, `fix`, `refactor`, `test`, `docs`, `ci`, `chore`, ...).
- `<short-description>` is brief, lowercase and hyphen-separated.
- Hyphens only, **no slashes**. Do not put the issue number in the branch name; it goes in the PR and in commit footers.

Examples: `feat-login-screen`, `fix-location-permission-crash`, `refactor-repository-layer`.

## Commits

Commits are made by humans. We follow **Conventional Commits**:

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

| Type       | Use for                                                 |
|------------|---------------------------------------------------------|
| `feat`     | A new feature                                           |
| `fix`      | A bug fix                                               |
| `refactor` | Code change that neither fixes a bug nor adds a feature |
| `test`     | Adding or updating tests                                |
| `docs`     | Documentation only (README, wiki, comments)             |
| `style`    | Formatting changes with no logic change                 |
| `build`    | Build system or dependency changes                      |
| `ci`       | CI configuration changes                                |
| `chore`    | Other maintenance tasks                                 |

- Description in the **imperative mood**, **lowercase**, **no trailing period** (`add`, not `Added`).
- First line **at most 72 characters**.
- Optional scope names the affected area: `feat(auth): add google sign-in`.
- Use the body to explain **why** when it isn't obvious.
- Reference the issue in the footer: `Refs #12` or `Closes #12`.
- Breaking changes: `!` after the type/scope (`feat(api)!: ...`) plus a `BREAKING CHANGE:` footer.
- Keep commits **focused and small**; never mix unrelated changes. Push regularly.
- Stage only the files you changed; never `git add .` or `git add -A` (it can pull in local config like `local.properties`).
- **Acknowledge your contributors**: an AI agent is a contributor, so add a `Co-authored-by:` footer crediting it.

## Testing and TDD

- We follow **TDD**: write the tests **before** the implementation whenever applicable.
- **All new code comes with unit tests**, in the **same PR** as the feature.
- **UI tests identify elements by test tags** (`Modifier.testTag(...)`), never by pixel positions or screen coordinates.
- Read failing tests carefully and iterate until they pass.

## Definition of done

- The feature works and matches the acceptance criteria of its issue.
- Tests are written (TDD) and included in the PR.
- `./gradlew check` is green (unit tests + lint) and `./gradlew ktfmtCheck` passes (formatting).
- Features covered by instrumented tests (under `androidTest/`) also pass `./gradlew connectedDebugAndroidTest`, with an Android emulator and the Firebase emulator running.
- **CI passes** on the PR. CI runs after pushing, so the rule is "green before merging", not "green before pushing".
- The PR links its issue and has at least one accepting review.

## How to work

- Make one **bounded, reviewable** change per PR. If it sprawls across unrelated files, split it.
- Read the failing tests carefully and iterate until `./gradlew check` passes.
- Stage only the files you changed; never `git add .` or `git add -A` (it can pull in local config like `local.properties`).
- Commit with an imperative subject of at most 50 characters, capitalized (e.g. `Add user authentication`). Add a body wrapped at 72 characters when the subject is not enough.
- **Acknowledge your contributors** at the top of the file: credit the AI that wrote it with a `Co-authored-by` line. An AI agent is a contributor, so credit it.

## Agent boundaries

The agent **only writes code and tests**, and may run local builds and tests (`./gradlew ...`) to check its work. Everything that touches the repository history or the team's process stays **human**. The agent never:

- commits, pushes, force-pushes or rewrites git history;
- creates, switches or deletes branches;
- opens, reviews, approves or merges Pull Requests;
- creates, edits, assigns or closes GitHub Issues, or moves cards on the Scrum Board;
- changes CI configuration, repository settings or secrets.

When its work is done, the agent stops and summarizes the changed files. It may **suggest** a commit message following the conventions above; a human decides whether to use it.

## Your role

You provide the goal, the context, the acceptance criteria and the permissions. The agent plans, acts and observes. **You review the diff, and you own every line you submit.** "The agent wrote it" is not a defence.
