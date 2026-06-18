package fi.eonwe.wikilinks

import java.io.File

object WikiInputFiles {
    private val contentPartPattern = Regex(""".*-p(\d+)p\d+\.xml\.bz2$""")

    fun resolveXmlInputs(input: File): List<File> {
        if (!input.isDirectory) {
            return listOf(input)
        }

        val files = input.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".xml.bz2") }
            ?.sortedWith(compareBy<File> { contentPartStartPage(it) ?: Long.MAX_VALUE }.thenBy { it.name })
            ?: emptyList()

        require(files.isNotEmpty()) { "No .xml.bz2 files found in input directory $input" }
        return files
    }

    private fun contentPartStartPage(file: File): Long? {
        return contentPartPattern.matchEntire(file.name)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
    }
}
