package com.github.mwiest.voclet.ui.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.Hiking
import androidx.compose.material.icons.outlined.Looks3
import androidx.compose.material.icons.outlined.LooksOne
import androidx.compose.material.icons.outlined.LooksTwo
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.mwiest.voclet.R
import com.github.mwiest.voclet.data.database.PracticeType
import com.github.mwiest.voclet.data.database.PracticeTypeLevel
import com.github.mwiest.voclet.ui.Routes

@Composable
fun PracticeLabel(practice: PracticeType) = when (practice) {
    PracticeType.FLASHCARD -> stringResource(id = R.string.flashcard_flip)
    PracticeType.CONNECT -> stringResource(id = R.string.connect)
    PracticeType.FILL_BLANKS -> stringResource(id = R.string.fill_blanks)
}

@Composable
fun PracticeIcon(practice: PracticeType) = when (practice) {
    PracticeType.FLASHCARD -> Icons.Default.Style
    PracticeType.CONNECT -> Icons.AutoMirrored.Filled.CompareArrows
    PracticeType.FILL_BLANKS -> Icons.Outlined.Hiking
}

fun PracticeRoute(practice: PracticeType) = when (practice) {
    PracticeType.FLASHCARD -> Routes.FLASHCARD_PRACTICE
    PracticeType.CONNECT -> Routes.CONNECT_PRACTICE
    PracticeType.FILL_BLANKS -> Routes.FILL_BLANKS_PRACTICE
}

@Composable
fun PracticeLevelLabel(level: PracticeTypeLevel) = when (level) {
    PracticeTypeLevel.UNDERSTAND -> stringResource(id = R.string.level_understand)
    PracticeTypeLevel.REMEMBER -> stringResource(id = R.string.level_remember)
    PracticeTypeLevel.SPELL -> stringResource(id = R.string.level_spell)
}

@Composable
fun PracticeLevelIcon(level: PracticeTypeLevel) = when (level) {
    PracticeTypeLevel.UNDERSTAND -> Icons.Outlined.LooksOne
    PracticeTypeLevel.REMEMBER -> Icons.Outlined.LooksTwo
    PracticeTypeLevel.SPELL -> Icons.Outlined.Looks3
}