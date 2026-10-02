package com.kouzenneo.tsugi.core

/** One source image in the project. [scale] is the per-item zoom override (1 = follow config). */
data class Photo(val id: String, val uri: String, val name: String, val scale: Float = 1f)

/** Whole editor state: the item list plus every layout knob. One value = one undo step. */
data class Project(
    val photos: List<Photo> = emptyList(),
    val config: LayoutConfig = LayoutConfig(),
) {
    val isEmpty: Boolean get() = photos.isEmpty()
}

/**
 * Filename ordering that humans expect: IMG_2 before IMG_10.
 * Plain lexicographic sort scatters screenshot runs, which is the whole point of a
 * stitcher, so this runs whenever images are added.
 */
object NaturalOrder : Comparator<String> {

    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val startA = i
                val startB = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val na = a.substring(startA, i).trimStart('0').ifEmpty { "0" }
                val nb = b.substring(startB, j).trimStart('0').ifEmpty { "0" }
                if (na.length != nb.length) return na.length - nb.length
                val cmp = na.compareTo(nb)
                if (cmp != 0) return cmp
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}
