// Co-authored-by: DeepSeek Flash <noreply@deepseek.com>

package com.artista.artista.ui.permission

/**
 * Provides stable semantics tags for the location permission screen UI tests.
 *
 * @author timo-by
 */
object LocationPermissionTestTags {

  /** Tag identifying the root container of the location permission screen. */
  const val SCREEN = "location_permission_screen"

  /** Tag identifying the title text. */
  const val TITLE = "location_permission_title"

  /** Tag identifying the rationale text. */
  const val RATIONALE = "location_permission_rationale"

  /** Tag identifying the button asking the system for location access. */
  const val ALLOW_BUTTON = "location_permission_allow_button"

  /** Tag identifying the Not now button in the initial state. */
  const val NOT_NOW_BUTTON = "location_permission_not_now_button"

  /** Tag identifying the Continue without location button in the refused state. */
  const val CONTINUE_BUTTON = "location_permission_continue_button"

  /** Tag identifying the message shown after the user refuses location access. */
  const val DENIED_MESSAGE = "location_permission_denied_message"
}
