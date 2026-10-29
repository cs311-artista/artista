package com.artista.artista.ui.profile

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
/**
 * Provides stable semantics tags for the user profile screen UI tests.
 *
 * @author Timz3rr
 */
object UserProfileTestTags {

  /** Tag identifying the root container of the user profile screen. */
  const val SCREEN = "user_profile_screen"

  /** Tag identifying the button signing the user out. */
  const val LOGOUT_BUTTON = "user_profile_logout_button"

  /** Tag identifying the profile picture placeholder. */
  const val PROFILE_PICTURE = "user_profile_picture"

  /** Tag identifying the field displaying the username, which opens its edit dialog on click. */
  const val USERNAME = "user_profile_username"

  /** Tag identifying the dialog used to edit the username. */
  const val USERNAME_DIALOG = "user_profile_username_dialog"

  /** Tag identifying the text field of the username edit dialog. */
  const val USERNAME_FIELD = "user_profile_username_field"

  /** Tag identifying the button saving the username entered in the dialog. */
  const val USERNAME_SAVE = "user_profile_username_save"

  /** Tag identifying the button closing the dialog without saving the username. */
  const val USERNAME_CANCEL = "user_profile_username_cancel"

  /** Tag identifying the button opening the user's artist preferences. */
  const val PREFERENCES_BUTTON = "user_profile_preferences_button"

  /** Tag identifying the button opening the user's saved artworks. */
  const val SAVED_ARTWORK_BUTTON = "user_profile_saved_artwork_button"
}
