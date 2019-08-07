package fi.eonwe.wikilinks;

import javax.annotation.Nullable;

public class BadRouteException extends Exception {

    private final boolean startDoesNotExist;
    private final boolean endDoesNotExist;

    private final String startName;
    private final String endName;


    public BadRouteException(boolean startDoesNotExist, boolean endDoesNotExist, @Nullable String startName, @Nullable String endName) {
        this.startDoesNotExist = startDoesNotExist;
        this.endDoesNotExist = endDoesNotExist;
        this.startName = startName;
        this.endName = endName;
    }

    public BadRouteException(String startName, String endName) {
        this(false, false, startName, endName);
    }

    public boolean startExists() {
        return !startDoesNotExist;
    }

    public boolean endExist() {
        return !endDoesNotExist;
    }

    @Nullable
    public String getStartName() {
        return startName;
    }

    @Nullable
    public String getEndName() {
        return endName;
    }

    public boolean noRouteFound() {
        return startExists() && endExist();
    }
}
