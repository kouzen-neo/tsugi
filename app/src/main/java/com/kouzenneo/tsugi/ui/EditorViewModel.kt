package com.kouzenneo.tsugi.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kouzenneo.tsugi.core.Box
import com.kouzenneo.tsugi.core.DrawMapper
import com.kouzenneo.tsugi.core.LayoutConfig
import com.kouzenneo.tsugi.core.LayoutEngine
import com.kouzenneo.tsugi.core.NaturalOrder
import com.kouzenneo.tsugi.core.Photo
import com.kouzenneo.tsugi.core.Preset
import com.kouzenneo.tsugi.core.Project
import com.kouzenneo.tsugi.core.SourceSize
import com.kouzenneo.tsugi.core.Trim
import com.kouzenneo.tsugi.core.applyPreset
import com.kouzenneo.tsugi.data.BitmapLoader
import com.kouzenneo.tsugi.data.BitmapPixmap
import com.kouzenneo.tsugi.data.ProjectStore
import com.kouzenneo.tsugi.render.Rendered
import com.kouzenneo.tsugi.render.Scene
import com.kouzenneo.tsugi.render.SceneItem
import com.kouzenneo.tsugi.render.StitchExporter
import com.kouzenneo.tsugi.render.compressFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class EditorState(
    val project: Project = Project(),
    val previews: List<Bitmap?> = emptyList(),
    val scene: Scene = Scene.EMPTY,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val busy: Boolean = false,
    val exporting: Boolean = false,
    val failed: Int = 0,
)

/**
 * Owns the project, the undo history and the derived scene.
 *
 * The project is the single source of truth: every edit replaces it with a new value,
 * which is what makes undo a one-liner and the layout a pure function of the state.
 * Continuous controls (sliders) bracket their drag with [beginEdit] and [endEdit] so a
 * whole drag is one undo step.
 */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val loader = BitmapLoader(application.contentResolver)
    private val store = ProjectStore(File(application.filesDir, "project.json"))
    private val exporter = StitchExporter(application, loader)
    private val app get() = getApplication<Application>()
    private val sourceDir get() = File(app.filesDir, "sources")

    private val _state = MutableStateFlow(EditorState())
    val state = _state.asStateFlow()

    private val _project = MutableStateFlow(Project())
    val project = _project.asStateFlow()

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val undo = ArrayDeque<Project>()
    private val redo = ArrayDeque<Project>()
    private var editing = false

    /** Per-image content box keyed by uri: the analysis only depends on the pixels. */
    private val analysis = HashMap<String, Box?>()

    init {
        viewModelScope.launch {
            _project.collectLatest { project -> rebuild(project) }
        }
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { store.load() }
            if (saved != null && saved.photos.isNotEmpty()) _project.value = saved
        }
        viewModelScope.launch { persist() }
    }

    // --- edits -------------------------------------------------------------

    /**
     * Picked images are copied into app storage before joining the project: a picker
     * grant only survives this process, and a stitch is expected to still open
     * tomorrow. The original display name is kept so ordering stays 2 before 10.
     */
    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) { uris.mapNotNull(::import) }
            if (added.isEmpty()) {
                _events.send("No readable images found")
                return@launch
            }
            mutate { project ->
                project.copy(
                    photos = (project.photos + added).sortedWith(compareBy(NaturalOrder) { it.name }),
                )
            }
        }
    }

    private fun import(uri: Uri): Photo? {
        val id = UUID.randomUUID().toString()
        val name = loader.displayName(uri.toString())
            .ifEmpty { uri.lastPathSegment ?: "image" }
            .take(80)
        val bytes = runCatching {
            app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null
        sourceDir.mkdirs()
        val target = File(sourceDir, "$id-${name.filter { c -> c.isLetterOrDigit() || c in "._-" }}")
        return runCatching {
            target.writeBytes(bytes)
            Photo(id = id, uri = Uri.fromFile(target).toString(), name = name)
        }.getOrNull()
    }

    /** Drops copied sources no longer referenced by the project. */
    private fun pruneSources(keep: List<Photo>) {
        val live = keep.mapNotNull { it.uri.toSourceFile() }.map { it.absolutePath }.toSet()
        sourceDir.listFiles()?.forEach { file ->
            if (file.absolutePath !in live) file.delete()
        }
    }

    private fun String.toSourceFile(): File? {
        val uri = Uri.parse(this)
        return if (uri.scheme == "file") uri.path?.let(::File) else null
    }

    fun removeAt(index: Int) {
        mutate { project ->
            project.copy(photos = project.photos.filterIndexed { i, _ -> i != index })
        }
        viewModelScope.launch(Dispatchers.IO) { pruneSources(_project.value.photos) }
    }

    /** One-shot config change that counts as a single undo step. */
    fun mutateConfig(block: LayoutConfig.() -> LayoutConfig) = mutate { project ->
        project.copy(config = project.config.block())
    }

    fun move(from: Int, to: Int) = mutate { project ->
        if (from !in project.photos.indices || to !in project.photos.indices || from == to) {
            return@mutate project
        }
        val photos = project.photos.toMutableList()
        photos.add(to, photos.removeAt(from))
        project.copy(photos = photos)
    }

    fun applyPreset(preset: Preset) = mutate { project ->
        project.copy(config = project.config.applyPreset(preset))
    }

    fun beginEdit() {
        if (editing) return
        editing = true
        undo.addLast(_project.value)
        while (undo.size > UNDO_LIMIT) undo.removeFirst()
        redo.clear()
    }

    fun endEdit() {
        if (!editing) return
        editing = false
        publishHistory()
    }

    fun updateConfig(block: LayoutConfig.() -> LayoutConfig) {
        val current = _project.value
        _project.value = current.copy(config = current.config.block())
    }

    fun setItemScale(index: Int, scale: Float) {
        val current = _project.value
        if (index !in current.photos.indices) return
        val photos = current.photos.toMutableList()
        photos[index] = photos[index].copy(scale = scale.coerceIn(0.1f, 4f))
        _project.value = current.copy(photos = photos)
    }

    fun undo() {
        val previous = undo.removeLastOrNull() ?: return
        redo.addLast(_project.value)
        _project.value = previous
        editing = false
        publishHistory()
    }

    fun redo() {
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(_project.value)
        _project.value = next
        editing = false
        publishHistory()
    }

    // --- export ------------------------------------------------------------

    /** Renders at full resolution and writes the result to the gallery. */
    suspend fun exportToGallery(): Uri? {
        val project = _project.value
        if (project.photos.isEmpty()) return null
        _state.update { it.copy(exporting = true) }
        var rendered: Rendered? = null
        return try {
            rendered = exporter.render(project)
            rendered.note?.let { _events.send(it) }
            val uri = exporter.save(project, rendered)
            _events.send(if (uri != null) "Saved to gallery" else "Save failed")
            uri
        } catch (oom: OutOfMemoryError) {
            _events.send("Out of memory — reduce the target size or image count")
            null
        } finally {
            rendered?.bitmap?.takeIf { !it.isRecycled }?.recycle()
            _state.update { it.copy(exporting = false) }
        }
    }

    /** Renders into the cache and hands back an intent for the caller to start. */
    suspend fun exportForShare(): Intent? {
        val project = _project.value
        if (project.photos.isEmpty()) return null
        _state.update { it.copy(exporting = true) }
        var rendered: Rendered? = null
        return try {
            rendered = exporter.render(project)
            val uri = withContext(Dispatchers.IO) { cacheCopy(project, rendered) }
            if (uri == null) _events.send("Failed to prepare file")
            uri?.let { exporter.shareIntent(it, project.config.format) }
        } catch (oom: OutOfMemoryError) {
            _events.send("Out of memory — reduce the target size or image count")
            null
        } finally {
            rendered?.bitmap?.takeIf { !it.isRecycled }?.recycle()
            _state.update { it.copy(exporting = false) }
        }
    }

    private suspend fun cacheCopy(project: Project, rendered: Rendered): Uri? =
        withContext(Dispatchers.IO) {
            val dir = File(app.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "tsugi.${project.config.format.ext}")
            val ok = file.outputStream().use {
                rendered.bitmap.compress(project.config.format.compressFormat(), project.config.quality, it)
            }
            if (ok) FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file) else null
        }

    // --- derivation --------------------------------------------------------

    private suspend fun rebuild(project: Project) {
        if (project.photos.isEmpty()) {
            _state.value = EditorState(
                project = project,
                canUndo = undo.isNotEmpty(),
                canRedo = redo.isNotEmpty(),
            )
            return
        }
        _state.update { it.copy(busy = true) }
        val resolved = withContext(Dispatchers.IO) { resolve(project) }
        _state.value = resolved
    }

    private fun resolve(project: Project): EditorState {
        val cfg = project.config
        val previews = project.photos.map { loader.loadBlocking(it.uri, PREVIEW_MAX_DIM) }
        analysis.keys.retainAll(project.photos.mapTo(HashSet()) { it.uri })

        val perImage = project.photos.mapIndexed { i, photo ->
            val bitmap = previews.getOrNull(i)
            if (!cfg.trimTail || bitmap == null) {
                null
            } else {
                contentBox(photo, bitmap)
            }
        }

        val present = perImage.filterNotNull()
        val uniform = if (cfg.trimUniform && present.size == project.photos.size && present.isNotEmpty()) {
            present.reduce { a, b -> a.intersect(b) }
                .takeIf { it.width >= Trim.MIN_KEEP && it.height >= Trim.MIN_KEEP }
        } else {
            null
        }

        val boxes = project.photos.indices.map { i ->
            val bitmap = previews.getOrNull(i)
            val full = if (bitmap != null) Box.full(bitmap.width, bitmap.height) else Box(0, 0, 1, 1)
            when {
                !cfg.trimTail -> full
                uniform != null -> uniform
                else -> perImage.getOrNull(i) ?: full
            }
        }

        val scales = project.photos.associate { it.id to it.scale }
        val sources = project.photos.mapIndexed { i, photo ->
            SourceSize(photo.id, boxes[i].width, boxes[i].height)
        }
        val layout = LayoutEngine.compute(cfg, sources, scales)
        val scene = Scene(
            width = layout.width,
            height = layout.height,
            items = layout.placements.mapIndexed { i, placed ->
                SceneItem(
                    id = placed.id,
                    srcBox = boxes[i],
                    draw = DrawMapper.map(placed.id, boxes[i], placed, cfg.fit, scales[placed.id] ?: 1f),
                )
            },
            config = cfg,
            shrink = layout.shrink,
        )
        return EditorState(
            project = project,
            previews = previews,
            scene = scene,
            canUndo = undo.isNotEmpty(),
            canRedo = redo.isNotEmpty(),
            failed = previews.count { it == null },
        )
    }

    /** Cached per image, and the cache distinguishes "not analysed" from "is blank". */
    private fun contentBox(photo: Photo, bitmap: Bitmap): Box? {
        if (analysis.containsKey(photo.uri)) return analysis[photo.uri]
        val box = Trim.blankBox(BitmapPixmap(bitmap))
        analysis[photo.uri] = box
        return box
    }

    // --- history and persistence -------------------------------------------

    private fun mutate(block: (Project) -> Project) {
        undo.addLast(_project.value)
        while (undo.size > UNDO_LIMIT) undo.removeFirst()
        redo.clear()
        _project.value = block(_project.value)
        editing = false
        publishHistory()
    }

    private fun publishHistory() {
        _state.update { it.copy(canUndo = undo.isNotEmpty(), canRedo = redo.isNotEmpty()) }
    }

    @OptIn(FlowPreview::class)
    private fun persist() {
        viewModelScope.launch {
            _project.drop(1).debounce(PERSIST_DEBOUNCE_MS).collect { project ->
                if (project.photos.isEmpty()) return@collect
                withContext(Dispatchers.IO) { store.save(project) }
            }
        }
    }

    private companion object {
        // A preview is only ever shown inside the editor, so 1024 px is at or above the
        // canvas it lands in while keeping a 30 shot stack near 60 MB instead of 150 MB.
        // Export re-decodes the sources at full resolution.
        const val PREVIEW_MAX_DIM = 1024
        const val UNDO_LIMIT = 60
        const val PERSIST_DEBOUNCE_MS = 600L
    }
}
