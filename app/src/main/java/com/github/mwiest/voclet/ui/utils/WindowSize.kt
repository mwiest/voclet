package com.github.mwiest.voclet.ui.utils

import androidx.window.core.layout.WindowSizeClass

/**
 * True when the window has width to spare but not the height to stack into: landscape phones and
 * tablets, and short freeform or split-screen windows.
 *
 * Screens that answer true should lay their content out in two panes rather than in one column,
 * spending the dimension the window actually has. Keeping the rule here stops the screens that
 * follow it from drifting apart.
 */
fun WindowSizeClass.prefersTwoPanes(): Boolean =
    isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) &&
        !isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND)
