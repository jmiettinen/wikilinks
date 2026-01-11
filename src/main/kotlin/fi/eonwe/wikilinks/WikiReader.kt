package fi.eonwe.wikilinks

import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

object WikiReader {

    private fun possiblyCapitalize(linkName: String): String {
        if (linkName.isNotEmpty() && !Character.isUpperCase(linkName[0])) {
            val chars = linkName.toCharArray()
            chars[0] = chars[0].uppercaseChar()
            return String(chars)
        }
        return linkName
    }

/*
    fun processWikiPart(input: InputStream) {
        try {
            val parser = WikiXMLParser(input) { article, siteinfo ->
                if (article.isMain) {
                    val text = article.text ?: ""
                    val matcher = WikiPatternMatcher(text)
                    // The page identifier is assumed to integer. I don't think this is actually guaranteed anywhere
                    // in wikimedia XML schema (if it exists), but so far I've only encountered number < 2^31.

                    val id = article.id.toInt()
                    if (matcher.isRedirect) {
                        val page = WikiRedirectPage(article.title.intern(), id, matcher.redirectText.intern())
                        fixPagePointers(titleToPage, page)
                    } else {
                        val links = matcher.links.asSequence()
                            .map { linkName -> possiblyCapitalize(linkName) }
                            .distinct()
                            .toList()
                        val pointerLinks = arrayOfNulls<PagePointer>(links.size)
                        for (i in links.indices) {
                            val link = links[i]
                            var ptr = titleToPage[link]
                            if (ptr == null) {
                                ptr = PagePointer(null)
                                titleToPage.put(link.intern(), ptr)
                            }
                            pointerLinks[i] = ptr
                        }
                        val page = WikiPageData(article.title.intern(), id, pointerLinks)
                        fixPagePointers(titleToPage, page)
                    }
                }
            }
            parser.parse()
            return titleToPage
        } catch (_: SAXException) {
            return titleToPage
        } catch (_: IOException) {
            return titleToPage
        }
    }
    */

    fun doWork(bb: ByteBuffer) {

    }

    fun processWiki(data: ByteBuffer, config: ProcessingConfig) {
        val blocksQueue = ArrayBlockingQueue<WorkToDo>(config.maxBlocksWaiting.toInt())

        val threads = (1.. config.parallelism.toInt()).map {
            Thread.ofVirtual().unstarted {
                var shouldStop = false
                while(!shouldStop) {
                    val read = blocksQueue.take()
                    when (read) {
                        is WorkToDo.BufferContainer -> doWork(read.value)
                        WorkToDo.Done -> {
                            blocksQueue.put(read)
                            shouldStop = true
                        }
                    }
                }
            }
        }
        threads.forEach { it.start() }
        generateSubstreams(bzip2Data = data).forEach {
            blocksQueue.put(WorkToDo.BufferContainer(it))
        }
        blocksQueue.put(WorkToDo.Done)
        threads.forEach { it.join() }
    }

    fun generateSubstreams(bzip2Data: ByteBuffer): Sequence<ByteBuffer> = sequence {
        val magic = byteArrayOf(0x42, 0x5A, 0x68) // 'BZh'
        val limit = bzip2Data.limit()
        var pos = 0
        var currentStart = -1

        while (pos <= limit - 4) {
            val isStreamStart =
                bzip2Data.get(pos) == magic[0] &&
                        bzip2Data.get(pos + 1) == magic[1] &&
                        bzip2Data.get(pos + 2) == magic[2] &&
                        (bzip2Data.get(pos + 3).toInt() in '1'.code..'9'.code)

            if (isStreamStart) {
                if (currentStart >= 0) {
                    // We found the start of the next stream → yield the previous
                    yield(sliceBuffer(bzip2Data, currentStart, pos))
                }
                currentStart = pos
            }
            pos++
        }

        if (currentStart >= 0 && currentStart < limit) {
            // Yield the final stream (up to end of buffer)
            yield(sliceBuffer(bzip2Data, currentStart, limit))
        }
    }

    private fun sliceBuffer(original: ByteBuffer, start: Int, end: Int): ByteBuffer {
        val copy = original.duplicate()
        copy.position(start).limit(end)
        return copy.slice()
    }

    val defaultProcessingConfig = min(1, Runtime.getRuntime().availableProcessors()).toUInt().let { cpus ->
        ProcessingConfig(
            parallelism = (3U * cpus) / 2U,
            maxBlocksWaiting = cpus + 2U
        )
    }
}

private class ProcessingContext(parallelism: Int) {
    val nameToId: ConcurrentMap<String, Long> = ConcurrentHashMap(
        1024 * 1024,
        0.85f,
        parallelism
    )
    val runningId = AtomicLong(0)

    fun idFor(name: String): Long {
        val key = possiblyCapitalize(name)
        return nameToId.computeIfAbsent(key) { k ->
            runningId.andIncrement
        }!!
    }

    fun possiblyCapitalize(linkName: String): String {
        return linkName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

}


sealed interface WorkToDo {
    object Done: WorkToDo
    @JvmInline
    value class BufferContainer(val value: ByteBuffer): WorkToDo
}

data class ProcessingConfig(
    val parallelism: UInt,
    val maxBlocksWaiting: UInt
)