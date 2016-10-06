package fi.eonwe.wikilinks.utils;

/**
 */
public final class Functions {

    @FunctionalInterface
    public interface IntInt {
        int apply(int i);
    }

    @FunctionalInterface
    public interface IntIntIntIntProcedure {
        void apply(int i1, int i2, int i3, int i4);
    }


}
