package fi.eonwe.wikilinks.fatpages

/**
 * 
 */
data class WikiPageData(val title: String, val id: Int, val links: List<PagePointer>) : WikiPage {

    override val isRedirect: Boolean = false

    override fun id(): Int = id

    override fun title(): String = title
}
