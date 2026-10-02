package com.kouzenneo.tsugi.data

import com.kouzenneo.tsugi.core.AxisMode
import com.kouzenneo.tsugi.core.BgMode
import com.kouzenneo.tsugi.core.FitMode
import com.kouzenneo.tsugi.core.HAlign
import com.kouzenneo.tsugi.core.LayoutConfig
import com.kouzenneo.tsugi.core.OutFormat
import com.kouzenneo.tsugi.core.Photo
import com.kouzenneo.tsugi.core.Project
import com.kouzenneo.tsugi.core.SizeMode
import com.kouzenneo.tsugi.core.VAlign
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists the project as JSON in app-private storage so a half-finished stitch
 * survives process death. Anything unparseable is discarded rather than crashing.
 */
class ProjectStore(private val file: File) {

    fun load(): Project? = runCatching {
        if (!file.exists()) return null
        decode(JSONObject(file.readText()))
    }.getOrNull()

    fun save(project: Project) {
        runCatching {
            file.writeText(encode(project).toString())
        }
    }

    private fun encode(project: Project): JSONObject = JSONObject().apply {
        put("axis", project.config.axis.name)
        put("columns", project.config.columns)
        put("gap", project.config.gap)
        put("padding", project.config.padding)
        put("sizeMode", project.config.sizeMode.name)
        put("target", project.config.target)
        put("fit", project.config.fit.name)
        put("hAlign", project.config.hAlign.name)
        put("vAlign", project.config.vAlign.name)
        put("cornerRadius", project.config.cornerRadius)
        put("separator", project.config.separator)
        put("separatorColor", project.config.separatorColor)
        put("separatorWidth", project.config.separatorWidth)
        put("bgMode", project.config.bgMode.name)
        put("bgColor", project.config.bgColor)
        put("trimUniform", project.config.trimUniform)
        put("trimTail", project.config.trimTail)
        put("format", project.config.format.name)
        put("quality", project.config.quality)
        put("photos", JSONArray().apply {
            project.photos.forEach { photo ->
                put(
                    JSONObject().apply {
                        put("id", photo.id)
                        put("uri", photo.uri)
                        put("name", photo.name)
                        put("scale", photo.scale.toDouble())
                    },
                )
            }
        })
    }

    private fun decode(json: JSONObject): Project {
        val defaults = LayoutConfig()
        val config = LayoutConfig(
            axis = json.enum("axis", defaults.axis),
            columns = json.optInt("columns", defaults.columns),
            gap = json.optInt("gap", defaults.gap),
            padding = json.optInt("padding", defaults.padding),
            sizeMode = json.enum("sizeMode", defaults.sizeMode),
            target = json.optInt("target", defaults.target),
            fit = json.enum("fit", defaults.fit),
            hAlign = json.enum("hAlign", defaults.hAlign),
            vAlign = json.enum("vAlign", defaults.vAlign),
            cornerRadius = json.optInt("cornerRadius", defaults.cornerRadius),
            separator = json.optBoolean("separator", defaults.separator),
            separatorColor = json.optInt("separatorColor", defaults.separatorColor),
            separatorWidth = json.optInt("separatorWidth", defaults.separatorWidth),
            bgMode = json.enum("bgMode", defaults.bgMode),
            bgColor = json.optInt("bgColor", defaults.bgColor),
            trimUniform = json.optBoolean("trimUniform", defaults.trimUniform),
            trimTail = json.optBoolean("trimTail", defaults.trimTail),
            format = json.enum("format", defaults.format),
            quality = json.optInt("quality", defaults.quality),
        )

        val photosArray = json.optJSONArray("photos") ?: JSONArray()
        val photos = buildList {
            for (i in 0 until photosArray.length()) {
                val item = photosArray.optJSONObject(i) ?: continue
                val uri = item.optString("uri").takeIf { it.isNotEmpty() } ?: continue
                add(
                    Photo(
                        id = item.optString("id", uri),
                        uri = uri,
                        name = item.optString("name", ""),
                        scale = item.optDouble("scale", 1.0).toFloat(),
                    ),
                )
            }
        }
        return Project(photos, config)
    }

    private inline fun <reified T : Enum<T>> JSONObject.enum(key: String, fallback: T): T {
        val name = optString(key)
        return enumValues<T>().firstOrNull { it.name == name } ?: fallback
    }
}
