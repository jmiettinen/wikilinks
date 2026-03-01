package fi.eonwe.wikilinks.server

import fi.eonwe.wikilinks.segmentgraph.SegmentWikiGraphSerialization
import fi.eonwe.wikilinks.segmentgraph.SegmentWikiGraphStore
import fi.eonwe.wikilinks.segmentgraph.SegmentWikiRoutes
import java.nio.file.Files
import java.nio.file.Path

data class WikiInput(val name: String, val file: Path)

object ServerWikiInputParser {
    fun parse(nameFilePairs: List<String>): List<WikiInput> {
        require(nameFilePairs.isNotEmpty()) { "Expected at least one wiki pair: <name> <file>" }
        require(nameFilePairs.size % 2 == 0) { "Expected pairs of <name> <file>, got ${nameFilePairs.size} args" }
        val parsed = ArrayList<WikiInput>(nameFilePairs.size / 2)
        val seenNames = HashSet<String>()
        var i = 0
        while (i < nameFilePairs.size) {
            val name = nameFilePairs[i].trim()
            val file = Path.of(nameFilePairs[i + 1])
            require(name.isNotEmpty()) { "Wiki name cannot be empty" }
            require(seenNames.add(name)) { "Duplicate wiki name '$name'" }
            require(Files.exists(file) && Files.isRegularFile(file)) { "Wiki file does not exist: $file" }
            require(Files.isReadable(file)) { "Wiki file is not readable: $file" }
            parsed.add(WikiInput(name, file))
            i += 2
        }
        return parsed
    }
}

class ServerWikiRegistry private constructor(
    private val wikisByName: Map<String, LoadedWiki>
) : AutoCloseable {
    data class LoadedWiki(
        val store: SegmentWikiGraphStore,
        val routes: SegmentWikiRoutes,
        val searchIndex: WikiSearchIndex
    )

    fun wikiNames(): List<String> = wikisByName.keys.sorted()

    fun wiki(name: String): LoadedWiki? = wikisByName[name]

    override fun close() {
        wikisByName.values.forEach { it.store.close() }
    }

    companion object {
        fun open(inputs: List<WikiInput>): ServerWikiRegistry {
            val wikis = LinkedHashMap<String, LoadedWiki>(inputs.size)
            try {
                for (input in inputs) {
                    val store = SegmentWikiGraphSerialization.open(input.file)
                    wikis[input.name] = LoadedWiki(
                        store = store,
                        routes = SegmentWikiRoutes(store),
                        searchIndex = WikiSearchIndex(store)
                    )
                }
                return ServerWikiRegistry(wikis)
            } catch (t: Throwable) {
                wikis.values.forEach { it.store.close() }
                throw t
            }
        }
    }
}
