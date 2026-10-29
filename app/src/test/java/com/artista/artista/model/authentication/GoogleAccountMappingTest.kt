package com.artista.artista.model.authentication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Co-authored-by: DeepSeek Flash
/**
 * Unit tests for [authUserOf], the mapping that decides what a signed-in Google account exposes.
 *
 * The display name is what the profile of a new user defaults its username to. The mapping is pure,
 * so these run on the JVM and `./gradlew check` guards the name without an emulator.
 *
 * @author timo-by
 */
class GoogleAccountMappingTest {

  private val uid = "google-uid-1"

  /**
   * Verifies that the name Google reports for the account is exposed as its display name.
   *
   * @author timo-by
   */
  @Test
  fun googleDisplayNameIsExposed() {
    assertEquals("Alice Martin", authUserOf(uid, "Alice Martin").displayName)
  }

  /**
   * Verifies that the identifier is preserved, so the name stays attached to the right account.
   *
   * @author timo-by
   */
  @Test
  fun uidIsPreserved() {
    assertEquals(uid, authUserOf(uid, "Alice Martin").uid)
  }

  /**
   * Verifies that an account whose provider reports no display name exposes none, so a profile that
   * defaults to it keeps asking for a name instead of storing an empty one.
   *
   * The named account is checked first on purpose: a `null` on its own also holds for a mapping
   * that ignores its input.
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
   * made only of spaces.
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
   * Verifies that the surrounding whitespace Google sometimes returns is trimmed.
   *
   * @author timo-by
   */
  @Test
  fun surroundingWhitespaceIsTrimmed() {
    assertEquals("Alice", authUserOf(uid, "  Alice  ").displayName)
  }

  /**
   * Verifies that the name keeps its inner spacing, so a multi-word name is not mangled.
   *
   * @author timo-by
   */
  @Test
  fun innerWhitespaceIsPreserved() {
    assertEquals("Alice  Martin", authUserOf(uid, "Alice  Martin").displayName)
  }

  /**
   * Verifies that the identifier survives a missing display name, so a nameless account keeps its
   * own profile.
   *
   * Without this, a mapping returning a placeholder for a nameless sign-in would pass every other
   * test in this class.
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
   * Verifies that whitespace other than spaces is trimmed too, since a pasted name can carry a tab
   * or a line break.
   *
   * @author timo-by
   */
  @Test
  fun tabsAndLineBreaksAreTrimmed() {
    assertEquals("Alice", authUserOf(uid, "\tAlice\n").displayName)
  }

  /**
   * Verifies that a name made only of whitespace which is not a space is treated as missing too.
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
   * Verifies that an accented name is exposed unchanged, since the mapping must not normalise the
   * user's own spelling.
   *
   * @author timo-by
   */
  @Test
  fun accentedDisplayNameIsPreserved() {
    assertEquals("José Müller", authUserOf(uid, " José Müller ").displayName)
  }

  /**
   * Verifies that a one-character name survives the trim-and-drop rule.
   *
   * @author timo-by
   */
  @Test
  fun singleCharacterDisplayNameIsPreserved() {
    assertEquals("A", authUserOf(uid, " A ").displayName)
  }

  /**
   * Verifies that the concrete type also defaults a missing name to `null`.
   *
   * @author timo-by
   */
  @Test
  fun firebaseAuthUserDefaultsToNoDisplayName() {
    assertNull(FirebaseAuthUser(uid).displayName)
  }

  /**
   * Verifies that two accounts sharing a name stay distinct when their identifiers differ.
   *
   * @author timo-by
   */
  @Test
  fun accountsWithTheSameNameButDifferentUidsAreNotEqual() {
    assertNotEquals(authUserOf("uid-1", "Alice"), authUserOf("uid-2", "Alice"))
  }

  /**
   * Verifies that two mappings of the same account are equal, which lets the session flow drop a
   * repeated notification.
   *
   * @author timo-by
   */
  @Test
  fun mappingsOfTheSameAccountAreEqual() {
    assertEquals(authUserOf(uid, " Alice "), authUserOf(uid, "Alice"))
  }
}
