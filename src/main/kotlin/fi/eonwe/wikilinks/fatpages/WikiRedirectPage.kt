package fi.eonwe.wikilinks.fatpages

/**
 * 
 */
data class WikiRedirectPage(val title: String, val id: Int, val target: String?) : WikiPage {
    override val isRedirect: Boolean = true
    override fun id(): Int = id

    override fun title(): String = title
}
