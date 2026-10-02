package com.kouzenneo.tsugi.core

/**
 * Starting points for the two things people actually make: one long screenshot strip
 * and a contact sheet. Presets only set layout; they never touch the item list.
 */
enum class Preset(val label: String) {
    LONG("Long"),
    GRID("Grid"),
    SIDE("Side"),
    PHOTO("Photo"),
}

fun LayoutConfig.applyPreset(preset: Preset): LayoutConfig = when (preset) {
    Preset.LONG -> copy(
        axis = AxisMode.VERTICAL,
        sizeMode = SizeMode.FIT_WIDTH,
        target = 1080,
        gap = 0,
        padding = 0,
        cornerRadius = 0,
        separator = false,
        hAlign = HAlign.CENTER,
        trimUniform = true,
        trimTail = true,
        bgMode = BgMode.COLOR,
    )
    Preset.GRID -> copy(
        axis = AxisMode.GRID,
        columns = 2,
        sizeMode = SizeMode.FIT_WIDTH,
        target = 1080,
        gap = 12,
        padding = 12,
        cornerRadius = 16,
        separator = false,
        hAlign = HAlign.CENTER,
        vAlign = VAlign.TOP,
        trimUniform = true,
        trimTail = true,
    )
    Preset.SIDE -> copy(
        axis = AxisMode.HORIZONTAL,
        sizeMode = SizeMode.FIT_HEIGHT,
        target = 1080,
        gap = 0,
        padding = 0,
        cornerRadius = 0,
        separator = false,
        vAlign = VAlign.TOP,
        trimUniform = true,
        trimTail = true,
    )
    Preset.PHOTO -> copy(
        axis = AxisMode.GRID,
        columns = 2,
        sizeMode = SizeMode.FIT_WIDTH,
        target = 1080,
        gap = 16,
        padding = 16,
        cornerRadius = 32,
        separator = false,
        hAlign = HAlign.CENTER,
        vAlign = VAlign.CENTER,
        trimUniform = false,
        trimTail = true,
        bgMode = BgMode.COLOR,
    )
}
