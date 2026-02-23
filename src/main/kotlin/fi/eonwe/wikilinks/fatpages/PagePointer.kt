package fi.eonwe.wikilinks.fatpages

/**
 */
class PagePointer(var page: WikiPage?) {
    val id: Int = counter++

    override fun hashCode(): Int {
        return id
    }

    override fun equals(other: Any?): Boolean {
        return (other is PagePointer && other.id == id)
    }

    companion object {
        private var counter = 0
    }
}