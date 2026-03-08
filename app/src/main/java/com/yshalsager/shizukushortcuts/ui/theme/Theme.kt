package com.yshalsager.shizukushortcuts.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

data class AppColors(
    val background: Color,
    val background_accent: Color,
    val surface: Color,
    val surface_alt: Color,
    val surface_raised: Color,
    val text: Color,
    val text_muted: Color,
    val accent: Color,
    val accent_soft: Color,
    val accent_text: Color,
    val success: Color,
    val success_surface: Color,
    val success_border: Color,
    val border: Color
)

private val light_colors = AppColors(
    background = Color(0xFFF6F6F8),
    background_accent = Color(0xFFE8F0FF),
    surface = Color(0xFFF1F5FF),
    surface_alt = Color(0xFFE7EEFF),
    surface_raised = Color(0xFFDCE7FF),
    text = Color(0xFF101A2B),
    text_muted = Color(0xFF657089),
    accent = Color(0xFF1F72F2),
    accent_soft = Color(0xFFD0E0FF),
    accent_text = Color.White,
    success = Color(0xFF0A9B7A),
    success_surface = Color(0xFFD2F4EA),
    success_border = Color(0xFF86D9C4),
    border = Color(0xFFABC4FF)
)

private val dark_colors = AppColors(
    background = Color(0xFF0A0F17),
    background_accent = Color(0xFF10203E),
    surface = Color(0xFF172B4B),
    surface_alt = Color(0xFF1C3560),
    surface_raised = Color(0xFF224176),
    text = Color(0xFFF6F7FB),
    text_muted = Color(0xFFA7B5CA),
    accent = Color(0xFF2D8CFF),
    accent_soft = Color(0xFF1A4B89),
    accent_text = Color(0xFFF6FAFF),
    success = Color(0xFF1ED5B2),
    success_surface = Color(0xFF0D3F52),
    success_border = Color(0xFF1B9D90),
    border = Color(0xFF3D64A8)
)

@Composable
fun shizuku_shortcuts_colors(): AppColors {
    val context = LocalContext.current
    val is_dark = isSystemInDarkTheme()

    return remember(context, is_dark) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamic_colors(context, is_dark)
        else if (is_dark) dark_colors
        else light_colors
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun dynamic_colors(context: Context, is_dark: Boolean) =
    if (is_dark) {
        AppColors(
            background = blend(app_color(context, android.R.color.system_neutral2_900), app_color(context, android.R.color.system_accent1_900), 0.08f),
            background_accent = blend(
                app_color(context, android.R.color.system_neutral2_800),
                app_color(context, android.R.color.system_accent1_800),
                0.42f
            ),
            surface = blend(
                app_color(context, android.R.color.system_neutral2_800),
                app_color(context, android.R.color.system_accent1_800),
                0.5f
            ),
            surface_alt = blend(
                app_color(context, android.R.color.system_neutral2_700),
                app_color(context, android.R.color.system_accent1_700),
                0.6f
            ),
            surface_raised = blend(
                app_color(context, android.R.color.system_neutral2_600),
                app_color(context, android.R.color.system_accent1_600),
                0.7f
            ),
            text = app_color(context, android.R.color.system_neutral1_100),
            text_muted = app_color(context, android.R.color.system_neutral2_300),
            accent = app_color(context, android.R.color.system_accent1_200),
            accent_soft = blend(
                app_color(context, android.R.color.system_accent1_700),
                app_color(context, android.R.color.system_accent1_500),
                0.68f
            ),
            accent_text = app_color(context, android.R.color.system_accent1_900),
            success = app_color(context, android.R.color.system_accent3_200),
            success_surface = blend(
                app_color(context, android.R.color.system_neutral2_700),
                app_color(context, android.R.color.system_accent3_800),
                0.62f
            ),
            success_border = app_color(context, android.R.color.system_accent3_600),
            border = blend(
                app_color(context, android.R.color.system_neutral2_600),
                app_color(context, android.R.color.system_accent1_500),
                0.6f
            )
        )
    } else {
        AppColors(
            background = blend(
                app_color(context, android.R.color.system_neutral2_10),
                app_color(context, android.R.color.system_accent1_50),
                0.08f
            ),
            background_accent = blend(
                app_color(context, android.R.color.system_neutral2_50),
                app_color(context, android.R.color.system_accent1_100),
                0.42f
            ),
            surface = blend(
                app_color(context, android.R.color.system_neutral2_50),
                app_color(context, android.R.color.system_accent1_50),
                0.52f
            ),
            surface_alt = blend(
                app_color(context, android.R.color.system_neutral2_100),
                app_color(context, android.R.color.system_accent1_100),
                0.64f
            ),
            surface_raised = blend(
                app_color(context, android.R.color.system_neutral2_200),
                app_color(context, android.R.color.system_accent1_200),
                0.78f
            ),
            text = app_color(context, android.R.color.system_neutral1_900),
            text_muted = app_color(context, android.R.color.system_neutral2_600),
            accent = app_color(context, android.R.color.system_accent1_600),
            accent_soft = blend(
                app_color(context, android.R.color.system_accent1_100),
                app_color(context, android.R.color.system_accent1_200),
                0.72f
            ),
            accent_text = app_color(context, android.R.color.system_accent1_0),
            success = app_color(context, android.R.color.system_accent3_600),
            success_surface = blend(
                app_color(context, android.R.color.system_accent3_50),
                app_color(context, android.R.color.system_accent3_100),
                0.68f
            ),
            success_border = app_color(context, android.R.color.system_accent3_200),
            border = blend(
                app_color(context, android.R.color.system_neutral2_300),
                app_color(context, android.R.color.system_accent1_200),
                0.72f
            )
        )
    }

private fun app_color(context: Context, color_res: Int) = Color(ContextCompat.getColor(context, color_res))

private fun blend(base: Color, overlay: Color, overlay_alpha: Float) = Color(
    red = base.red * (1f - overlay_alpha) + overlay.red * overlay_alpha,
    green = base.green * (1f - overlay_alpha) + overlay.green * overlay_alpha,
    blue = base.blue * (1f - overlay_alpha) + overlay.blue * overlay_alpha,
    alpha = 1f
)
