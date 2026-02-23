package fi.eonwe.wikilinks.pages

sealed interface NewWikiPage {

    val id: Int
    val title: String

}

interface NewRedirectPage: NewWikiPage {

    val redirectTarget: String

}

interface NewConcreteWikiPage: NewWikiPage {

}
