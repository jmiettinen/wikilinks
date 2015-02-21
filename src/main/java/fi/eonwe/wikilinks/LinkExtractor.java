package fi.eonwe.wikilinks;

import com.google.common.collect.ImmutableSet;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 */
public class LinkExtractor {

    private static final Pattern LINK_RE = Pattern.compile("(#REDIRECT\\s*)?\\[\\[" +
            "([^:|]]+:)?" +
            "([^]#|]+)" +
            "(#[^]|]+)?" +
            "(|[^]]+)?" +
            "\\]\\]", Pattern.MULTILINE);

    private Matcher m;

    public LinkExtractor resetTo(CharSequence text) {
        m = LINK_RE.matcher(text);
        return this;
    }

    public boolean advance() {
        return m.find();
    }

    public String getTitle() {
        return m.group(3);
    }

    public String getNamespace() {
        return m.group(1);
    }

    public static boolean isTitleSpecial(String title) {
        return false;
    }
}
