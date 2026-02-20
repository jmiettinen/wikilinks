package fi.eonwe.wikilinks.fatpages

/**
 */
interface WikiPage {
    /**
     * Is this a redirect to another page or an actual page?
     * @return
     */
    val isRedirect: Boolean

    /**
     * @return a unique identifier for this page
     */
    fun id(): Int

    /**
     * @return title of the page
     */
    fun title(): String
}