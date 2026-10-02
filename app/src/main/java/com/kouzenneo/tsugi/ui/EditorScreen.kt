package com.kouzenneo.tsugi.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CropOriginal
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kouzenneo.tsugi.core.AxisMode
import com.kouzenneo.tsugi.core.BgMode
import com.kouzenneo.tsugi.core.FitMode
import com.kouzenneo.tsugi.core.HAlign
import com.kouzenneo.tsugi.core.LayoutConfig
import com.kouzenneo.tsugi.core.OutFormat
import com.kouzenneo.tsugi.core.Photo
import com.kouzenneo.tsugi.core.Preset
import com.kouzenneo.tsugi.core.SizeMode
import com.kouzenneo.tsugi.core.VAlign
import com.kouzenneo.tsugi.core.applyPreset
import com.kouzenneo.tsugi.render.ExportRenderer
import com.kouzenneo.tsugi.render.Scene
import kotlinx.coroutines.launch
import kotlin.math.min

private val SWATCHES = listOf(
    0xFFFFFFFF.toInt(), // Pure White
    0xFFF5F3EF.toInt(), // Warm Paper / Cream
    0xFF1C1C1E.toInt(), // Dark Charcoal
    0xFF000000.toInt(), // Pitch Black
    0xFF1E293B.toInt(), // Slate Blue
    0xFF2F6B4F.toInt(), // Forest Green
    0xFF1E2B45.toInt(), // Midnight Blue
    0xFF5C2D2D.toInt(), // Deep Maroon
)

private enum class EditorTab(val label: String, val icon: ImageVector) {
    LAYOUT("Layout", Icons.Filled.Dashboard),
    SPACING("Spacing", Icons.Filled.AspectRatio),
    STYLE("Style", Icons.Filled.Palette),
    TRIM("Trim", Icons.Filled.ContentCut),
    IMAGE("Image", Icons.Filled.CropOriginal),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, onStart: () -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { onStart() }
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(64)) { uris ->
        vm.addUris(uris)
    }
    val writePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scope.launch { vm.exportToGallery() }
        else scope.launch { snackbar.showSnackbar("Storage permission required") }
    }

    var showExport by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var activeTab by rememberSaveable { mutableStateOf(EditorTab.LAYOUT) }
    var controlsExpanded by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(state.project.photos.size) {
        if (selected > state.project.photos.lastIndex) {
            selected = state.project.photos.lastIndex.coerceAtLeast(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tsugi", fontWeight = FontWeight.Bold)
                        if (!state.project.isEmpty) {
                            Text(
                                text = "${state.project.photos.size} images · ${state.scene.width} × ${state.scene.height} px",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (!state.project.isEmpty) {
                        IconButton(
                            onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        ) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Add images")
                        }
                    }
                    IconButton(onClick = vm::undo, enabled = state.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = vm::redo, enabled = state.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    if (!state.project.isEmpty) {
                        Button(
                            onClick = { showExport = true },
                            enabled = !state.exporting,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Export", fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Live Preview Canvas takes all remaining vertical space
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (state.project.isEmpty) {
                    EmptyState(
                        onPickImages = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    PreviewCanvas(
                        scene = state.scene,
                        previews = state.previews,
                        photos = state.project.photos,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                if (state.exporting) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
            }

            // Docked editor controls: always visible below canvas, live interactive
            if (!state.project.isEmpty) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                DockedEditorPanel(
                    state = state,
                    vm = vm,
                    selected = selected,
                    onSelect = {
                        selected = it
                        activeTab = EditorTab.IMAGE
                    },
                    activeTab = activeTab,
                    onTabChange = { activeTab = it },
                    expanded = controlsExpanded,
                    onToggleExpand = { controlsExpanded = !controlsExpanded },
                    onAddImages = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                )
            }
        }
    }

    if (showExport) {
        ExportSheet(
            state = state,
            vm = vm,
            onDismiss = { showExport = false },
            onSave = {
                val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                    PackageManager.PERMISSION_GRANTED
                if (needsPermission) {
                    writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    scope.launch { vm.exportToGallery() }
                }
            },
            onShare = {
                scope.launch {
                    val intent = vm.exportForShare()
                    if (intent != null) context.startActivity(Intent.createChooser(intent, "Share result"))
                }
            },
        )
    }
}

@Composable
private fun EmptyState(onPickImages: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Stitch Screenshots",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Combine multiple screenshots into one seamless long image or photo collage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        Button(
            onClick = onPickImages,
            modifier = Modifier.padding(top = 8.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Select Screenshots", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun PreviewCanvas(
    scene: Scene,
    previews: List<Bitmap?>,
    photos: List<Photo>,
    modifier: Modifier = Modifier,
) {
    val byId = remember(previews, photos) {
        photos.indices.associate { photos[it].id to previews.getOrNull(it) }
    }
    val source: (String) -> Bitmap? = remember(byId) { { id -> byId[id] } }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    zoom = (zoom * zoomChange).coerceIn(0.2f, 8f)
                    pan += panChange
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    zoom = 1f
                    pan = Offset.Zero
                })
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (scene.isEmpty) return@Canvas
            val padding = 20f
            val fit = min(
                (size.width - padding * 2) / scene.width,
                (size.height - padding * 2) / scene.height,
            )
            val scale = fit * zoom
            val originX = (size.width - scene.width * scale) / 2f + pan.x
            val originY = (size.height - scene.height * scale) / 2f + pan.y
            val bounds = RectF(0f, 0f, scene.width * scale, scene.height * scale)

            withTransform({ translate(originX, originY) }) {
                if (scene.config.bgMode == BgMode.TRANSPARENT) {
                    drawCheckerboard(bounds.width(), bounds.height())
                }
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    ExportRenderer.drawBackdrop(native, scene, source, scale, bounds)
                    ExportRenderer.drawItems(native, scene, source, scale)
                }
                drawRect(
                    color = Color.Black.copy(alpha = 0.25f),
                    size = Size(bounds.width(), bounds.height()),
                    style = Stroke(width = 1f),
                )
            }
        }

        // Quick button to snap back to 100% zoom
        if (zoom > 1.05f || pan != Offset.Zero) {
            AssistChip(
                onClick = {
                    zoom = 1f
                    pan = Offset.Zero
                },
                label = { Text("Reset Zoom") },
                leadingIcon = { Icon(Icons.Filled.ZoomOutMap, null, Modifier.size(16.dp)) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
            )
        }
    }
}

private fun DrawScope.drawCheckerboard(width: Float, height: Float) {
    val cell = 16f
    var row = 0
    var y = 0f
    while (y < height) {
        var column = 0
        var x = 0f
        while (x < width) {
            if ((row + column) % 2 == 0) {
                drawRect(
                    color = Color(0xFF9E9E9E).copy(alpha = 0.35f),
                    topLeft = Offset(x, y),
                    size = Size(cell, cell),
                )
            }
            x += cell
            column++
        }
        y += cell
        row++
    }
}

/**
 * Docked bottom control center.
 * Live-updates canvas in real time: no modal sheets obscuring the preview!
 */
@Composable
private fun DockedEditorPanel(
    state: EditorState,
    vm: EditorViewModel,
    selected: Int,
    onSelect: (Int) -> Unit,
    activeTab: EditorTab,
    onTabChange: (EditorTab) -> Unit,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onAddImages: () -> Unit,
) {
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            // Row 1: Thumbnails strip with "+ Add" card at the end
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(state.project.photos) { index, photo ->
                    val bitmap = state.previews.getOrNull(index)
                    val isSelected = index == selected
                    Box(
                        Modifier
                            .size(width = 48.dp, height = 66.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable { onSelect(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = photo.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                            )
                        }
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(2.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }

                // Append an "+ Add" card directly at the end of the thumbnail list
                item {
                    Box(
                        Modifier
                            .size(width = 48.dp, height = 66.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable(onClick = onAddImages),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Add image",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp),
                            )
                            Text(
                                text = "Add",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            // Row 2: Preset Chips & Expand Toggle
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Preset.entries.forEach { preset ->
                        FilterChip(
                            selected = matchesPreset(state.project.config, preset),
                            onClick = { vm.applyPreset(preset) },
                            label = { Text(preset.label) },
                        )
                    }
                }

                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                        contentDescription = if (expanded) "Collapse controls" else "Expand controls",
                    )
                }
            }

            // Row 3: Tab Bar & Tab Content (Collapsible)
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    // Category Tabs
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EditorTab.entries.forEach { tab ->
                            val isSelected = activeTab == tab
                            FilterChip(
                                selected = isSelected,
                                onClick = { onTabChange(tab) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (tab == EditorTab.IMAGE && selected in state.project.photos.indices) {
                                            "Image #${selected + 1}"
                                        } else {
                                            tab.label
                                        },
                                    )
                                },
                            )
                        }
                    }

                    // Content of the active tab (scrollable if screen is dense)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 180.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        when (activeTab) {
                            EditorTab.LAYOUT -> LayoutTabContent(state, vm)
                            EditorTab.SPACING -> SpacingTabContent(state, vm)
                            EditorTab.STYLE -> StyleTabContent(state, vm)
                            EditorTab.TRIM -> TrimTabContent(state, vm)
                            EditorTab.IMAGE -> ImageTabContent(state, vm, selected, onSelect)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LayoutTabContent(state: EditorState, vm: EditorViewModel) {
    val cfg = state.project.config
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChipRow(
            label = "Direction",
            options = AxisMode.entries,
            selected = cfg.axis,
            text = {
                when (it) {
                    AxisMode.VERTICAL -> "Vertical"
                    AxisMode.HORIZONTAL -> "Horizontal"
                    AxisMode.GRID -> "Grid"
                }
            },
            onSelect = { axis -> vm.mutateConfig { copy(axis = axis) } },
        )

        if (cfg.axis == AxisMode.GRID) {
            Stepper(
                label = "Columns",
                value = cfg.columnsClamped,
                range = 1..6,
                onChange = { cols -> vm.mutateConfig { copy(columns = cols) } },
            )
        }

        if (cfg.axis != AxisMode.HORIZONTAL) {
            ChipRow(
                label = "Horizontal align",
                options = HAlign.entries,
                selected = cfg.hAlign,
                text = {
                    when (it) {
                        HAlign.START -> "Left"
                        HAlign.CENTER -> "Center"
                        HAlign.END -> "Right"
                    }
                },
                onSelect = { align -> vm.mutateConfig { copy(hAlign = align) } },
            )
        }

        if (cfg.axis != AxisMode.VERTICAL) {
            ChipRow(
                label = "Vertical align",
                options = VAlign.entries,
                selected = cfg.vAlign,
                text = {
                    when (it) {
                        VAlign.TOP -> "Top"
                        VAlign.CENTER -> "Center"
                        VAlign.BOTTOM -> "Bottom"
                    }
                },
                onSelect = { align -> vm.mutateConfig { copy(vAlign = align) } },
            )
        }
    }
}

@Composable
private fun SpacingTabContent(state: EditorState, vm: EditorViewModel) {
    val cfg = state.project.config
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledSlider(
            label = "Gap between images",
            value = cfg.gap.toFloat(),
            range = 0f..120f,
            valueText = cfg.gap.pxText(),
            onDragStart = vm::beginEdit,
            onValueChange = { value -> vm.updateConfig { copy(gap = value.toInt()) } },
            onDragEnd = vm::endEdit,
        )

        LabeledSlider(
            label = "Outer frame",
            value = cfg.padding.toFloat(),
            range = 0f..120f,
            valueText = cfg.padding.pxText(),
            onDragStart = vm::beginEdit,
            onValueChange = { value -> vm.updateConfig { copy(padding = value.toInt()) } },
            onDragEnd = vm::endEdit,
        )

        LabeledSlider(
            label = "Rounded corners",
            value = cfg.cornerRadius.toFloat(),
            range = 0f..80f,
            valueText = cfg.cornerRadius.pxText(),
            onDragStart = vm::beginEdit,
            onValueChange = { value -> vm.updateConfig { copy(cornerRadius = value.toInt()) } },
            onDragEnd = vm::endEdit,
        )

        ToggleRow(
            label = "Divider line",
            checked = cfg.separator,
            onChange = { value -> vm.mutateConfig { copy(separator = value) } },
        )
    }
}

@Composable
private fun StyleTabContent(state: EditorState, vm: EditorViewModel) {
    val cfg = state.project.config
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ChipRow(
            label = "Background mode",
            options = BgMode.entries,
            selected = cfg.bgMode,
            text = {
                when (it) {
                    BgMode.COLOR -> "Color"
                    BgMode.TRANSPARENT -> "Transparent"
                    BgMode.BLUR -> "Blur"
                }
            },
            onSelect = { mode -> vm.mutateConfig { copy(bgMode = mode) } },
        )

        if (cfg.bgMode == BgMode.COLOR) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Color palette", style = MaterialTheme.typography.bodyMedium)
                ColorSwatches(
                    colors = SWATCHES,
                    selected = cfg.bgColor,
                    onSelect = { color -> vm.mutateConfig { copy(bgColor = color) } },
                )
            }
        }
    }
}

@Composable
private fun TrimTabContent(state: EditorState, vm: EditorViewModel) {
    val cfg = state.project.config
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ToggleRow(
            label = "Remove blank space",
            checked = cfg.trimTail,
            onChange = { value -> vm.mutateConfig { copy(trimTail = value) } },
        )
        ToggleRow(
            label = "Even out margins",
            checked = cfg.trimUniform,
            onChange = { value -> vm.mutateConfig { copy(trimUniform = value) } },
        )
    }
}

@Composable
private fun ImageTabContent(
    state: EditorState,
    vm: EditorViewModel,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    if (selected !in state.project.photos.indices) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "Tap a thumbnail above to select and edit an image.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val photo = state.project.photos[selected]
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${selected + 1}. ${photo.name.ifBlank { "Image" }}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { vm.move(selected, selected - 1) },
                    enabled = selected > 0,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Move left")
                }
                IconButton(
                    onClick = { vm.move(selected, selected + 1) },
                    enabled = selected < state.project.photos.lastIndex,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Move right")
                }
                IconButton(
                    onClick = {
                        vm.removeAt(selected)
                        onSelect(selected.coerceAtMost(state.project.photos.lastIndex - 1).coerceAtLeast(0))
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f)) {
                LabeledSlider(
                    label = "Scale",
                    value = photo.scale,
                    range = 0.2f..3f,
                    valueText = "${(photo.scale * 100).toInt()}%",
                    onDragStart = vm::beginEdit,
                    onValueChange = { value -> vm.setItemScale(selected, value) },
                    onDragEnd = vm::endEdit,
                )
            }
            if (photo.scale != 1f) {
                TextButton(
                    onClick = { vm.setItemScale(selected, 1f) },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("100%")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(
    state: EditorState,
    vm: EditorViewModel,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    val cfg = state.project.config
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Export & Share", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = "${state.scene.width} × ${state.scene.height} px",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            if (state.scene.shrink < 1f) {
                Text(
                    text = "Output scaled down ${(state.scene.shrink * 100).toInt()}% to fit device memory safely.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (state.failed > 0) {
                Text(
                    text = "${state.failed} images failed to load and will be skipped.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            ChipRow(
                label = "Format",
                options = OutFormat.entries,
                selected = cfg.format,
                text = { it.name },
                onSelect = { format -> vm.mutateConfig { copy(format = format) } },
            )

            if (cfg.format != OutFormat.PNG) {
                LabeledSlider(
                    label = "Quality",
                    value = cfg.quality.toFloat(),
                    range = 40f..100f,
                    valueText = "${cfg.quality}%",
                    onDragStart = vm::beginEdit,
                    onValueChange = { value -> vm.updateConfig { copy(quality = value.toInt()) } },
                    onDragEnd = vm::endEdit,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(
                    onClick = onShare,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save to Gallery", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun matchesPreset(cfg: LayoutConfig, preset: Preset): Boolean =
    LayoutConfig().applyPreset(preset) == cfg.applyPreset(preset)
