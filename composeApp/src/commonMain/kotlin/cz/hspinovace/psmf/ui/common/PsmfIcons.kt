/*
 * The vector paths of the seven icons below are Google's Material Icons (the Filled set),
 * copied from org.jetbrains.compose.material:material-icons-core and material-icons-extended
 * 1.7.3, which this app no longer depends on (0.3.1, Gate 6; DECISIONS 2026-10-21).
 *
 * Copyright The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * The licence text is in composeApp/licences/Apache-2.0-MaterialIcons.txt.
 */
package cz.hspinovace.psmf.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private val IconSize = 24.dp
private const val ICON_VIEWPORT = 24f

/**
 * The icons the app uses, drawn here so that it needs no icon library. They came from
 * `materialIconsExtended`, which holds eleven thousand icons as classes and cost 31 MB of the APK
 * for these seven. Each is the Material icon of the same name, 24 dp on a 24 by 24 grid, filled
 * black for a tint to colour, and built the first time it is shown.
 *
 * A path is the SVG path data Material publishes. It is split into lines here only to fit a
 * screen; the lines are joined as they stand, so a cut falls between commands.
 */
object PsmfIcons {
    val Check: ImageVector by lazy {
        filled(
            "Check",
            "M9,16.17L4.83,12l-1.42,1.41L9,19L21,7l-1.41,-1.41z",
        )
    }

    val ExpandLess: ImageVector by lazy {
        filled(
            "ExpandLess",
            "M12,8l-6,6l1.41,1.41L12,10.83l4.59,4.58L18,14z",
        )
    }

    val ExpandMore: ImageVector by lazy {
        filled(
            "ExpandMore",
            "M16.59,8.59L12,13.17L7.41,8.59L6,10l6,6l6,-6z",
        )
    }

    val KeyboardArrowUp: ImageVector by lazy {
        filled(
            "KeyboardArrowUp",
            "M7.41,15.41L12,10.83l4.59,4.58L18,14l-6,-6l-6,6z",
        )
    }

    val SportsSoccer: ImageVector by lazy {
        filled(
            "SportsSoccer",
            "M12,2C6.48,2,2,6.48,2,12c0,5.52,4.48,10,10,10s10,-4.48,10,-10C22,6.48,17.52,2,12,2z",
            "M13,5.3l1.35,-0.95c1.82,0.56,3.37,1.76,4.38,3.34l-0.39,1.34l-1.35,0.46L13,6.7V5.3z",
            "M9.65,4.35L11,5.3v1.4L7.01,9.49L5.66,9.03L5.27,7.69C6.28,6.12,7.83,4.92,9.65,4.35z",
            "M7.08,17.11l-1.14,0.1C4.73,15.81,4,13.99,4,12c0,-0.12,0.01,-0.23,0.02,-0.35l1,-0.73L6.4,11.4",
            "l1.46,4.34L7.08,17.11z",
            "M14.5,19.59C13.71,19.85,12.87,20,12,20s-1.71,-0.15,-2.5,-0.41l-0.69,-1.49L9.45,17h5.11l0.64,1.11",
            "L14.5,19.59z",
            "M14.27,15H9.73l-1.35,-4.02L12,8.44l3.63,2.54L14.27,15z",
            "M18.06,17.21l-1.14,-0.1l-0.79,-1.37l1.46,-4.34l1.39,-0.47l1,0.73C19.99,11.77,20,11.88,20,12",
            "C20,13.99,19.27,15.81,18.06,17.21z",
        )
    }

    val Star: ImageVector by lazy {
        filled(
            "Star",
            "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2L9.19,8.63L2,9.24l5.46,4.73L5.82,21z",
        )
    }

    val StarBorder: ImageVector by lazy {
        filled(
            "StarBorder",
            "M22,9.24l-7.19,-0.62L12,2L9.19,8.63L2,9.24l5.46,4.73L5.82,21L12,17.27L18.18,21l-1.63,-7.03",
            "L22,9.24z",
            "M12,15.4l-3.76,2.27l1,-4.28l-3.32,-2.88l4.38,-0.38L12,6.1l1.71,4.04l4.38,0.38l-3.32,2.88l1,4.28",
            "L12,15.4z",
        )
    }
}

private fun filled(
    name: String,
    vararg path: String,
): ImageVector =
    ImageVector
        .Builder(
            name = "Filled.$name",
            defaultWidth = IconSize,
            defaultHeight = IconSize,
            viewportWidth = ICON_VIEWPORT,
            viewportHeight = ICON_VIEWPORT,
        ).addPath(pathData = addPathNodes(path.joinToString("")), fill = SolidColor(Color.Black))
        .build()
