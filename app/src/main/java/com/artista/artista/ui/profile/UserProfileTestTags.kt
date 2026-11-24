package com.artista.artista.ui.profile

// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: DeepSeek Flash <noreply@deepseek.com>
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

  /** Tag identifying the picture area, which holds the image or its placeholder. */
  const val PROFILE_PICTURE = "user_profile_picture"

  /** Tag identifying the picture of the user, shown instead of the placeholder once loaded. */
  const val PROFILE_PICTURE_IMAGE = "user_profile_picture_image"

  /**
   * Tag identifying the placeholder shown in the picture area while there is no picture to display,
   * or once its download has failed.
   */
  const val PROFILE_PICTURE_PLACEHOLDER = "user_profile_picture_placeholder"

  /** Tag identifying the button on the picture banner, which opens the change photo sheet. */
  const val EDIT_PHOTO_BUTTON = "user_profile_edit_photo_button"

  /** Tag identifying the bottom sheet listing where the profile photo can come from. */
  const val CHANGE_PHOTO_SHEET = "user_profile_change_photo_sheet"

  /** Tag identifying the sheet option picking the photo from the device library. */
  const val CHANGE_PHOTO_LIBRARY_OPTION = "user_profile_change_photo_library_option"

  /** Tag identifying the sheet option picking the photo from the saved artworks. */
  const val CHANGE_PHOTO_SAVED_ARTWORK_OPTION = "user_profile_change_photo_saved_artwork_option"

  /** Tag identifying the button closing the change photo sheet. */
  const val CHANGE_PHOTO_CANCEL = "user_profile_change_photo_cancel"

  /** Tag identifying the Name card, which opens the username edit dialog on click. */
  const val USERNAME = "user_profile_username"

  /** Tag identifying the dialog used to edit the username. */
  const val USERNAME_DIALOG = "user_profile_username_dialog"

  /** Tag identifying the text field of the username edit dialog. */
  const val USERNAME_FIELD = "user_profile_username_field"

  /** Tag identifying the hint or error shown under the username field. */
  const val USERNAME_SUPPORTING_TEXT = "user_profile_username_supporting_text"

  /** Tag identifying the character counter shown under the username field. */
  const val USERNAME_COUNTER = "user_profile_username_counter"

  /** Tag identifying the button saving the username entered in the dialog. */
  const val USERNAME_SAVE = "user_profile_username_save"

  /** Tag identifying the button closing the dialog without saving the username. */
  const val USERNAME_CANCEL = "user_profile_username_cancel"

  /** Tag identifying the button opening the user's artist preferences. */
  const val PREFERENCES_BUTTON = "user_profile_preferences_button"

  /** Tag identifying the button opening the user's saved artworks. */
  const val SAVED_ARTWORK_BUTTON = "user_profile_saved_artwork_button"
}
