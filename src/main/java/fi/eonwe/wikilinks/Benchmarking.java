package fi.eonwe.wikilinks;

import com.google.common.base.Joiner;
import fi.eonwe.wikilinks.leanpages.BufferWikiPage;

import java.util.List;
import java.util.Random;

import static fi.eonwe.wikilinks.fibonacciheap.Helpers.quote;

/**
 */
public class Benchmarking {

    private static final int SAMPLED_PAGES = 1_000;

    public static void runBenchmarks(List<BufferWikiPage> pages, int measurements) {
        final Random rng = new Random(0xcafebabe);
        WikiRoutes routes = new WikiRoutes(pages);
        long[] runtimes = new long[measurements];
        System.out.printf("Running %d random measurements%n", measurements);
        for (int i = 0; i < runtimes.length; i++) {
            String p1 = pages.get(rng.nextInt(pages.size())).getTitle();
            String p2 = pages.get(rng.nextInt(pages.size())).getTitle();
            long startTime = System.currentTimeMillis();
            try {
                System.out.printf("Finding route %s -> %s%n", quote(p1), quote(p2));
                WikiRoutes.Result result = routes.findRoute(p1, p2);
                long totalTime = System.currentTimeMillis() - startTime;
                System.out.printf("%s (%d ms)%n", result.getRoute().isEmpty() ? "Found no route" : "Found route " + result.toString(), totalTime);
                runtimes[i] = totalTime;
            } catch (WikiRoutes.BadRouteException e) {
                // Not going to happen.
            }
        }
        printStatistics(runtimes);
    }

    private static void printStatistics(long[] runtimes) {
        double mean = 0.0;
        double m2 = 0.0;
        int n = 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        double sum = 0.0;
        for (long x : runtimes) {
            n++;
            if (x < min) min = x;
            if (x > max) max = x;
            sum += x;
            double delta = x - mean;
            mean = mean + delta / n;
            m2 = m2 + delta * (x - mean);
        }
        double stddev = runtimes.length < 2 ? 0 : Math.sqrt(m2 / (n-1));

        System.out.printf("Runs      : %d%n", n);
        System.out.printf("Min       : %010.2f%n", min);
        System.out.printf("Max       : %010.2f%n", max);
        System.out.printf("Mean      : %010.2f%n", mean);
        System.out.printf("Std. dev. : %010.2f%n", stddev);
        System.out.printf("Sum       : %010.2f%n", sum);
    }

}
