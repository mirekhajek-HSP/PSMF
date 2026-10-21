package cz.hspinovace.psmf.ui.export

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import cz.hspinovace.psmf.export.ExportZou
import cz.hspinovace.psmf.export.ZouAppearance
import cz.hspinovace.psmf.export.ZouAssessment
import cz.hspinovace.psmf.export.ZouCards
import cz.hspinovace.psmf.export.ZouConfirmation
import cz.hspinovace.psmf.export.ZouGoal
import cz.hspinovace.psmf.export.ZouHeader
import cz.hspinovace.psmf.export.ZouIdentificationKind
import cz.hspinovace.psmf.export.ZouLineup
import cz.hspinovace.psmf.export.ZouReport
import cz.hspinovace.psmf.export.ZouResult
import cz.hspinovace.psmf.export.ZouSide
import cz.hspinovace.psmf.export.ZouTeamAssessment
import cz.hspinovace.psmf.ui.breaksInsideAWord
import cz.hspinovace.psmf.ui.textLayout
import cz.hspinovace.psmf.ui.theme.PsmfDimens
import cz.hspinovace.psmf.ui.theme.PsmfTheme
import cz.hspinovace.psmf.ui.withLanguage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * *Save to device* and *Send to PSMF* (0.3.1, Gate 4). `ActionRow` right-aligned
 * the two at their own widths and wrapped the second below the first on a narrow
 * phone: two buttons of different widths and heights, stacked, which is the
 * ragged look the console had. They are one set, so they are laid out as one
 * ([cz.hspinovace.psmf.ui.common.EvenActionRow]): side by side, the same size, the
 * width of the content.
 */
@OptIn(ExperimentalTestApi::class)
class ExportButtonsTest {
    private val report =
        ZouReport(
            header =
                ZouHeader(
                    "ZAKOS",
                    LocalDate(2026, 8, 31),
                    LocalTime(19, 0),
                    "6K",
                    "Kominíci",
                    "Sp. Sumýš",
                    "Jiri Vlk",
                    false,
                    "Roman Liska",
                    true,
                    "Celtic THK",
                ),
            lineups =
                listOf(
                    ZouLineup(
                        ZouSide.HOME,
                        "Kominíci",
                        "modrá",
                        listOf(ZouAppearance(9, "900615", ZouIdentificationKind.DATE_OF_BIRTH, "Poupě Petr")),
                    ),
                ),
            goals = listOf(ZouGoal(ZouSide.HOME, "13´", null, null, "1:0")),
            cards = ZouCards(true, emptyList(), emptyList()),
            result = ZouResult("1:0", "1:0", "Kominíci"),
            assessment = ZouAssessment(ZouTeamAssessment(9, 0, true, true), ZouTeamAssessment(13, 5, false, true), "x"),
            confirmations =
                listOf(ZouConfirmation("Podpis rozhodčího", "Jiri Vlk", Instant.parse("2026-08-31T20:05:00Z"), false)),
        )

    @Test
    fun saveAndSendAreOneRowOfTwoEqualButtonsAtEveryPhoneWidthFontAndLanguage() {
        val problems = mutableListOf<String>()
        for ((language, labels) in LABELS) {
            for (width in listOf(360.dp, 411.dp)) {
                for (fontScale in listOf(1f, 1.3f)) {
                    runComposeUiTest {
                        withLanguage(language) {
                            setContent {
                                CompositionLocalProvider(LocalDensity provides Density(2.625f, fontScale)) {
                                    Box(Modifier.requiredSize(width, 6000.dp)) {
                                        PsmfTheme {
                                            val documents = ExportZou()(report)
                                            ExportScreen(
                                                ExportUiState(loading = false, report = report, documents = documents),
                                                {},
                                            )
                                        }
                                    }
                                }
                            }
                            val (save, send) = labels.map { onNodeWithText(it).getBoundsInRoot() }
                            val where = "$language ${width.value.toInt()}dp x$fontScale"
                            if (abs((save.width - send.width).value) >
                                1f
                            ) {
                                problems += "$where: widths ${save.width} / ${send.width}"
                            }
                            if (abs((save.height - send.height).value) >
                                1f
                            ) {
                                problems += "$where: heights ${save.height} / ${send.height}"
                            }
                            if (abs((save.top - send.top).value) >
                                1f
                            ) {
                                problems += "$where: tops ${save.top} / ${send.top}"
                            }
                            val span: Dp = send.right - save.left
                            if (abs((span - (width - PsmfDimens.screenPadding * 2)).value) > 1f) {
                                problems += "$where: the two span $span of ${width - PsmfDimens.screenPadding * 2}"
                            }
                            labels.filter { onNodeWithText(it).textLayout().breaksInsideAWord() }.forEach {
                                problems += "$where: \"$it\" is broken inside a word"
                            }
                        }
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), "${problems.size} problems:\n" + problems.take(8).joinToString("\n"))
    }

    private companion object {
        val LABELS =
            mapOf(
                "cs" to listOf("Uložit do zařízení", "Odeslat na PSMF"),
                "uk" to listOf("Зберегти на пристрій", "Надіслати до PSMF"),
            )
    }
}
