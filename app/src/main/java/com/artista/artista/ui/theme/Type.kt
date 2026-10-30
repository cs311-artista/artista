// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.artista.artista.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// Set of Material typography styles to start with
val Typography =
    Typography(
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = TypographyDimensions.bodyLargeFontSize,
                lineHeight = TypographyDimensions.bodyLargeLineHeight,
                letterSpacing = TypographyDimensions.bodyLargeLetterSpacing,
            )
    )
