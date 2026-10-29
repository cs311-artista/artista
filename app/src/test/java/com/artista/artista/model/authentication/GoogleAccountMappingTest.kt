package com.artista.artista.model.authentication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Co-authored-by: DeepSeek Flash
/**
 * Unit tests for [authUserOf], the mapping that decides what a signed-in Google account exposes.
 *
 * The display name in particular is what the profile of a new user will default its own username
 * to, so its cleanliness decides what that user is called until they pick a name: these tests are
 * what guard the value the profile will start from.
 *
 * Run on the JVM rather than against the Authentication emulator, because the mapping is pure: it
 * only reads the account's identifier and display name. That is why the mapping takes those two
 * fields instead of a `FirebaseUser`, which cannot be constructed on the JVM. Keeping it here means
 * `./gradlew check` guards the display name on every change, on the fast path.
 *
 * @author timo-by
 */
class GoogleAccountMappingTest {

  private val uid = "google-uid-1"

  /**
   * Verifies that the name Google reports for the account is exposed as its display name, so a
   * profile that defaults to it starts with the user's own name rather than empty.
   *
   * @author timo-by
   */
  @Test
  fun googleDisplayNameIsExposed() {
    assertEquals("Alice Martin", authUserOf(uid, "Alice Martin").displayName)
  }

  /**
   * Verifies that the account identifier is preserved, so the display name is attached to the right
   * account and the uid can still key the profile it will default.
   *
   * @author timo-by
   */
  @Test
  fun uidIsPreserved() {
    assertEquals(uid, authUserOf(uid, "Alice Martin").uid)
  }

  /**
   * Verifies that an account whose provider reports no display name exposes none, so a profile that
   * defaults to it keeps asking the user for a name instead of storing an empty one.
   *
   * The named account is checked first in the same test on purpose: asserting a `null` on its own
   * holds for a mapping that ignores its input, so it would not fail if the mapping stopped reading
   * the display name altogether.
   *
   * @author timo-by
   */
  @Test
  fun missingDisplayNameIsExposedAsNone() {
    assertEquals("Alice", authUserOf(uid, "Alice").displayName)
    assertNull(authUserOf(uid, null).displayName)
  }

  /**
   * Verifies that a blank display name is treated as missing, since Google lets a user pick a name
   * made only of spaces and a profile defaulting to it would store a name that renders as empty.
   *
   * The named account is checked first for the reason given in [missingDisplayNameIsExposedAsNone].
   *
   * @author timo-by
   */
  @Test
  fun blankDisplayNameIsExposedAsNone() {
    assertEquals("Alice", authUserOf(uid, "Alice").displayName)
    assertNull(authUserOf(uid, "   ").displayName)
  }

  /**
   * Verifies that the surrounding whitespace Google sometimes returns is trimmed, so the stored
   * default is the name itself rather than the name with padding.
   *
   * @author timo-by
   */
  @Test
  fun surroundingWhitespaceIsTrimmed() {
    assertEquals("Alice", authUserOf(uid, "  Alice  ").displayName)
  }

  /**
   * Verifies that the name keeps its inner spacing, so a multi-word display name is not mangled.
   *
   * @author timo-by
   */
  @Test
  fun innerWhitespaceIsPreserved() {
    assertEquals("Alice  Martin", authUserOf(uid, "Alice  Martin").displayName)
  }

  /**
   * Verifies that the account identifier survives a missing display name, so an account without a
   * name is still the account it came from and keeps its own profile.
   *
   * Without this, a mapping that returned a placeholder account for a nameless sign-in would pass
   * every other test in this class.
   *
   * @author timo-by
   */
  @Test
  fun uidIsPreservedWhenThereIsNoDisplayName() {
    val nameless = authUserOf(uid, null)

    assertNull(nameless.displayName)
    assertEquals(uid, nameless.uid)
  }

  /**
   * Verifies that whitespace other than spaces is trimmed as well, since a name pasted from another
   * application can carry a tab or a line break at either end.
   *
   * @author timo-by
   */
  @Test
  fun tabsAndLineBreaksAreTrimmed() {
    assertEquals("Alice", authUserOf(uid, "\tAlice\n").displayName)
  }

  /**
   * Verifies that a name made only of whitespace which is not a space is treated as missing too, so
   * the trimming rule does not depend on which blank characters Google happened to send.
   *
   * The named account is checked first for the reason given in [missingDisplayNameIsExposedAsNone].
   *
   * @author timo-by
   */
  @Test
  fun nonSpaceWhitespaceOnlyNameIsExposedAsNone() {
    assertEquals("Alice", authUserOf(uid, "Alice").displayName)
    assertNull(authUserOf(uid, "\t\n").displayName)
  }

  /**
   * Verifies that an accented name is exposed unchanged, because the mapping must not normalise the
   * user's own spelling of their name.
   *
   * @author timo-by
   */
  @Test
  fun accentedDisplayNameIsPreserved() {
    assertEquals("José Müller", authUserOf(uid, " José Müller ").displayName)
  }

  /**
   * Verifies that a one-character name survives, since a name that short is the value the trim-and-
   * drop rule is most likely to discard by accident.
   *
   * @author timo-by
   */
  @Test
  fun singleCharacterDisplayNameIsPreserved() {
    assertEquals("A", authUserOf(uid, " A ").displayName)
  }

  /**
   * Verifies that the interface's promise that a missing name is `null` also holds on the concrete
   * type a caller receives, so a default left empty never reaches a profile as an empty username.
   *
   * @author timo-by
   */
  @Test
  fun firebaseAuthUserDefaultsToNoDisplayName() {
    assertNull(FirebaseAuthUser(uid).displayName)
  }

  /**
   * Verifies that two accounts sharing a name stay distinct when their identifiers differ, so a
   * session is never mistaken for another account that happens to have the same name.
   *
   * @author timo-by
   */
  @Test
  fun accountsWithTheSameNameButDifferentUidsAreNotEqual() {
    assertNotEquals(authUserOf("uid-1", "Alice"), authUserOf("uid-2", "Alice"))
  }

  /**
   * Verifies that two mappings of the same account are equal, which is what lets the session flow
   * use value comparison to drop a repeated notification for an unchanged account.
   *
   * @author timo-by
   */
  @Test
  fun mappingsOfTheSameAccountAreEqual() {
    assertEquals(authUserOf(uid, " Alice "), authUserOf(uid, "Alice"))
  }
}
