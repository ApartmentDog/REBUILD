package com.evyr.rebuild.epub

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

/**
 * A parsed EPUB: its chapters in reading order, ready to display one at a time
 * in a WebView. EPUB is a ZIP archive containing XHTML chapter files plus an
 * OPF manifest (an XML file) that lists them in the order they should be read
 * -- that manifest's location is itself found via a top-level container.xml,
 * per the EPUB Open Container Format spec.
 *
 * No third-party EPUB library: this hand-rolled parser only needs to answer
 * "what chapters, in what order, and what HTML is in each" -- it does not
 * attempt to handle DRM, embedded fonts, or the full OPF metadata spec.
 * Malformed or unusual EPUBs may not parse; this is deliberately a narrow,
 * well-understood subset rather than a general-purpose implementation.
 */
data class EpubBook(
    val title: String,
    val chapterHrefs: List<String>,  // paths inside the zip, in spine order
    val opfDir: String                // directory the OPF file lives in, for resolving relative hrefs
)

object EpubParser {

    /** Parses just enough to build a chapter list -- title and reading order.
     *  Returns null if the file doesn't look like a valid EPUB (missing
     *  container.xml or OPF, corrupted zip, etc). */
    fun parse(file: File): EpubBook? {
        return try {
            ZipFile(file).use { zip ->
                val containerEntry = zip.getEntry("META-INF/container.xml") ?: return null
                val opfPath = zip.getInputStream(containerEntry).use { parseContainerForOpfPath(it) } ?: return null
                val opfEntry = zip.getEntry(opfPath) ?: return null
                val opfDir = opfPath.substringBeforeLast("/", "")

                val (title, spineHrefs) = zip.getInputStream(opfEntry).use { parseOpf(it) }
                if (spineHrefs.isEmpty()) return null

                val resolvedHrefs = spineHrefs.map { href ->
                    if (opfDir.isBlank()) href else "$opfDir/$href"
                }
                EpubBook(title = title, chapterHrefs = resolvedHrefs, opfDir = opfDir)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Reads one chapter's raw XHTML content out of the zip, as a string. */
    fun readChapter(file: File, href: String): String? {
        return try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry(href) ?: return null
                zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** container.xml's one job: point at the real OPF file's path (it can
     *  technically live anywhere in the archive, most commonly OEBPS/content.opf). */
    private fun parseContainerForOpfPath(input: java.io.InputStream): String? {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                return parser.getAttributeValue(null, "full-path")
            }
            eventType = parser.next()
        }
        return null
    }

    /** Parses the OPF's <manifest> (id -> href map) and <spine> (reading
     *  order, referencing manifest ids) to produce an ordered href list.
     *  Also grabs <dc:title> if present for the book's display title. */
    private fun parseOpf(input: java.io.InputStream): Pair<String, List<String>> {
        val parser = Xml.newPullParser()
        parser.setInput(input, null)

        val manifestIdToHref = mutableMapOf<String, String>()
        val spineIds = mutableListOf<String>()
        var title = ""
        var inTitleTag = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            if (id != null && href != null) manifestIdToHref[id] = href
                        }
                        "itemref" -> {
                            val idref = parser.getAttributeValue(null, "idref")
                            if (idref != null) spineIds.add(idref)
                        }
                        "title" -> inTitleTag = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inTitleTag && title.isBlank()) title = parser.text?.trim().orEmpty()
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "title") inTitleTag = false
                }
            }
            eventType = parser.next()
        }

        val orderedHrefs = spineIds.mapNotNull { manifestIdToHref[it] }
        return title to orderedHrefs
    }
}
