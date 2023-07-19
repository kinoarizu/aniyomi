package eu.kanade.tachiyomi.ui.player.settings.dialogs

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.util.collectAsState
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerSettingsScreenModel
import `is`.xyz.mpv.MPVLib
import tachiyomi.core.preference.Preference
import tachiyomi.core.preference.getAndSet
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.material.ReadItemAlpha
import kotlin.math.floor
import kotlin.math.max

@Composable
fun SubtitleSettingsDialog(
    screenModel: PlayerSettingsScreenModel,
    onDismissRequest: () -> Unit,
) {
    val overrideSubtitles by screenModel.preferences.overrideSubtitlesStyle().collectAsState()

    val updateOverride = {
        val overrideType = if (overrideSubtitles) "no" else "force"
        screenModel.togglePreference(PlayerPreferences::overrideSubtitlesStyle)
        MPVLib.setPropertyString("sub-ass-override", overrideType)
    }

    PlayerDialog(
        titleRes = R.string.player_subtitle_settings,
        onDismissRequest = onDismissRequest,
    ) {
        Column {
            CheckboxItem(
                label = stringResource(R.string.player_override_subtitle_style),
                checked = overrideSubtitles,
                onClick = updateOverride,
            )
            if (overrideSubtitles) {
                SubtitleLook(screenModel)
                SubtitleColors(screenModel)
            }
        }
    }
}

@Composable
private fun SubtitleLook(
    screenModel: PlayerSettingsScreenModel,
) {
    val boldSubtitles by screenModel.preferences.boldSubtitles().collectAsState()
    val italicSubtitles by screenModel.preferences.italicSubtitles().collectAsState()

    val updateBold = {
        val toBold = if (boldSubtitles) "no" else "yes"
        screenModel.togglePreference(PlayerPreferences::boldSubtitles)
        MPVLib.setPropertyString("sub-bold", toBold)
    }

    val updateItalic = {
        val toItalicize = if (italicSubtitles) "no" else "yes"
        screenModel.togglePreference(PlayerPreferences::italicSubtitles)
        MPVLib.setPropertyString("sub-italic", toItalicize)
    }

    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = Icons.Outlined.FormatSize,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
        )

        val boldAlpha = if (boldSubtitles) 1f else ReadItemAlpha
        Icon(
            imageVector = Icons.Outlined.FormatBold,
            contentDescription = null,
            modifier = Modifier
                .alpha(boldAlpha)
                .size(32.dp)
                .clickable(onClick = updateBold),
        )

        val italicAlpha = if (italicSubtitles) 1f else ReadItemAlpha
        Icon(
            imageVector = Icons.Outlined.FormatItalic,
            contentDescription = null,
            modifier = Modifier
                .alpha(italicAlpha)
                .size(32.dp)
                .clickable(onClick = updateItalic),
        )
    }
}

@Composable
private fun SubtitleColors(
    screenModel: PlayerSettingsScreenModel,
) {
    var subsColor by remember { mutableStateOf(SubsColor.NONE) }

    fun updateType(newColor: SubsColor) {
        subsColor = if (newColor != subsColor) newColor else SubsColor.NONE
    }

    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
        SubtitleColorSelector(
            label = R.string.player_subtitle_text_color,
            onClick = { updateType(SubsColor.TEXT) },
            selected = subsColor == SubsColor.TEXT,
            preference = screenModel.preferences.textColorSubtitles(),

        )
        SubtitleColorSelector(
            label = R.string.player_subtitle_border_color,
            onClick = { updateType(SubsColor.BORDER) },
            selected = subsColor == SubsColor.BORDER,
            preference = screenModel.preferences.borderColorSubtitles(),
        )
        SubtitleColorSelector(
            label = R.string.player_subtitle_background_color,
            onClick = { updateType(SubsColor.BACKGROUND) },
            selected = subsColor == SubsColor.BACKGROUND,
            preference = screenModel.preferences.backgroundColorSubtitles(),
        )
    }

    if (subsColor != SubsColor.NONE) {
        SubtitleColorSlider(
            argb = ARGBValue.ALPHA,
            subsColor = subsColor,
            preference = subsColor.preference(screenModel.preferences),
        )

        SubtitleColorSlider(
            argb = ARGBValue.RED,
            subsColor = subsColor,
            preference = subsColor.preference(screenModel.preferences),
        )

        SubtitleColorSlider(
            argb = ARGBValue.GREEN,
            subsColor = subsColor,
            preference = subsColor.preference(screenModel.preferences),
        )

        SubtitleColorSlider(
            argb = ARGBValue.BLUE,
            subsColor = subsColor,
            preference = subsColor.preference(screenModel.preferences),
        )
    }
}

@Composable
private fun SubtitleColorSelector(
    @StringRes label: Int,
    selected: Boolean,
    onClick: () -> Unit,
    preference: Preference<Int>,
) {
    val colorCode by preference.collectAsState()

    val borderColor = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(label))
        Canvas(
            modifier = Modifier
                .wrapContentSize(Alignment.Center)
                .requiredSize(20.dp),
        ) {
            drawColorBox(
                boxColor = Color(colorCode),
                borderColor = borderColor,
                radius = floor(2.dp.toPx()),
                strokeWidth = floor(2.dp.toPx()),
            )
        }

        Text(text = colorCode.toHexString())

        if (selected) {
            Icon(imageVector = Icons.Outlined.ArrowDropDown, contentDescription = null)
        }
    }
}

@Composable
private fun SubtitleColorSlider(
    argb: ARGBValue,
    subsColor: SubsColor,
    preference: Preference<Int>,
) {
    val colorCode by preference.collectAsState()

    fun getColorValue(currentColor: Int, color: Float, mask: Long, bitShift: Int): Int {
        return (color.toInt() shl bitShift) or (currentColor and mask.inv().toInt())
    }

    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(argb.label),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.5f),
        )

        val borderColor = MaterialTheme.colorScheme.onSurface
        Canvas(
            modifier = Modifier
                .wrapContentSize(Alignment.Center)
                .requiredSize(20.dp)
                .weight(0.5f),
        ) {
            drawColorBox(
                boxColor = argb.asColor(colorCode),
                borderColor = borderColor,
                radius = floor(2.dp.toPx()),
                strokeWidth = floor(2.dp.toPx()),
            )
        }

        val colorValue = argb.toValue(colorCode)
        Text(
            text = String.format("%03d", colorValue),
            modifier = Modifier.weight(0.5f),
        )

        Slider(
            value = argb.toValue(colorCode).toFloat(),
            onValueChange = { newColorValue ->
                preference.getAndSet { getColorValue(it, newColorValue, argb.mask, argb.bitShift) }
                MPVLib.setPropertyString(subsColor.mpvProperty, colorCode.toHexString())
            },
            modifier = Modifier.weight(10f),
            valueRange = 0f..255f,
            steps = 255,
        )
    }
}

private fun DrawScope.drawColorBox(
    boxColor: Color,
    borderColor: Color,
    radius: Float,
    strokeWidth: Float,
) {
    val halfStrokeWidth = strokeWidth / 2.0f
    val stroke = Stroke(strokeWidth)
    val checkboxSize = size.width
    if (boxColor == borderColor) {
        drawRoundRect(
            boxColor,
            size = Size(checkboxSize, checkboxSize),
            cornerRadius = CornerRadius(radius),
            style = Fill,
        )
    } else {
        drawRoundRect(
            boxColor,
            topLeft = Offset(strokeWidth, strokeWidth),
            size = Size(checkboxSize - strokeWidth * 2, checkboxSize - strokeWidth * 2),
            cornerRadius = CornerRadius(max(0f, radius - strokeWidth)),
            style = Fill,
        )
        drawRoundRect(
            borderColor,
            topLeft = Offset(halfStrokeWidth, halfStrokeWidth),
            size = Size(checkboxSize - strokeWidth, checkboxSize - strokeWidth),
            cornerRadius = CornerRadius(radius - halfStrokeWidth),
            style = stroke,
        )
    }
}

private enum class SubsColor(val mpvProperty: String, val preference: (PlayerPreferences) -> Preference<Int>) {
    NONE("", PlayerPreferences::textColorSubtitles),
    TEXT("sub-color", PlayerPreferences::textColorSubtitles),
    BORDER("sub-border-color", PlayerPreferences::borderColorSubtitles),
    BACKGROUND("sub-back-color", PlayerPreferences::backgroundColorSubtitles),
    ;
}

private enum class ARGBValue(@StringRes val label: Int, val mask: Long, val bitShift: Int, val toValue: (Int) -> Int, val asColor: (Int) -> Color) {

    ALPHA(R.string.color_filter_a_value, 0xFF000000L, 24, ::toAlpha, ::asAlpha),
    RED(R.string.color_filter_r_value, 0x00FF0000L, 16, ::toRed, ::asRed),
    GREEN(R.string.color_filter_g_value, 0x0000FF00L, 8, ::toGreen, ::asGreen),
    BLUE(R.string.color_filter_b_value, 0x000000FFL, 0, ::toBlue, ::asBlue),
    ;
}

private fun toAlpha(color: Int) = (color ushr 24) and 0xFF
private fun asAlpha(color: Int) = Color((color.toLong() and 0xFF000000L) or 0x00FFFFFFL)

private fun toRed(color: Int) = (color ushr 16) and 0xFF
private fun asRed(color: Int) = Color((color.toLong() and 0x00FF0000L) or 0xFF000000L)

private fun toGreen(color: Int) = (color ushr 8) and 0xFF
private fun asGreen(color: Int) = Color((color.toLong() and 0x0000FF00L) or 0xFF000000L)

private fun toBlue(color: Int) = (color ushr 0) and 0xFF
private fun asBlue(color: Int) = Color((color.toLong() and 0x000000FFL) or 0xFF000000L)

fun Int.toHexString(): String {
    val colorCodeAlpha = String.format("%02X", toAlpha(this))
    val colorCodeRed = String.format("%02X", toRed(this))
    val colorCodeGreen = String.format("%02X", toGreen(this))
    val colorCodeBlue = String.format("%02X", toBlue(this))

    return "#$colorCodeAlpha$colorCodeRed$colorCodeGreen$colorCodeBlue"
}
