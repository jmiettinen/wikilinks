package fi.eonwe.wikilinks;

import org.junit.experimental.categories.Categories;
import org.junit.runner.RunWith;

@RunWith(Categories.class)
@Categories.IncludeCategory(SlowTests.class)
public interface SlowTests {
}

