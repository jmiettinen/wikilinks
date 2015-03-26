package fi.eonwe.wikilinks;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static fi.eonwe.wikilinks.Helpers.quote;

/**
 */
public class Interactive {

    private static String findTarget(WikiRoutes routes, BufferedReader reader, boolean startPoint) throws IOException {
        final String wildcard = "#";
        final String randomPage = "<";
        System.out.printf("Please type the %s article ('<' for random article and '#' for wildcard)", startPoint ? "starting" : "end");
        while (true) {
            System.out.print("> ");
            String read = reader.readLine();
            String trimmed = read == null ? "" : read.trim();
            if (wildcard.equals(trimmed)) {
                System.out.printf("Must have at last one char before the wildcards%n");
            } else if (trimmed.endsWith(wildcard)) {
                String prefix = trimmed.substring(0, trimmed.length() - 1);
                List<String> matches = routes.findWildcards(prefix, 10);
                if (matches.isEmpty()) {
                    System.out.printf("No articles start with %s%n", quote(prefix));
                } else {
                    System.out.printf("At least these articles start with %s: %s%n", quote(prefix), Arrays.asList(matches.stream().map(Helpers::quote).toArray()));
                }
            } else if (trimmed.equals(randomPage)) {
                String page = routes.getRandomPage();
                System.out.printf("Selected \"%s\" as %s page%n", page, startPoint ? "starting" : "end");
                return page;
            } else if (!trimmed.isEmpty() && routes.hasPage(trimmed)) {
                return trimmed;
            } else {
                System.out.printf("No page with name %s found. Try wildcards?%n", quote(trimmed));

            }
        }
    }

    public static void doSearch(WikiRoutes routes, BufferedReader console) throws IOException {
        while (true) {
            String start = findTarget(routes, console, true);
            if (start == null) return;
            String end = findTarget(routes, console, false);
            if (end == null) return;
            doSearch(routes, start, end);
        }
    }

    private static void doSearch(WikiRoutes routes, String start, String end) {
        String result;
        try {
            WikiRoutes.Result route = routes.findRoute(start, end);
            String routeString;
            if (route.getRoute().isEmpty()) {
                routeString = "No route found";
            } else {
                routeString = "Route: " + route.toString();
            }
            result = String.format("%s (in %d ms)", routeString, route.getRuntime());
        } catch (WikiRoutes.BadRouteException e) {
            if (e.endExist()) {
                if (e.startExists()) {
                    result = String.format("No route found between %s and %s", e.getStartName(), e.getEndName());
                } else {
                    result = String.format("Starting point %s does not exists", e.getStartName());
                }
            } else {
                if (e.startExists()) {
                    result = String.format("End point %s does not exists", e.getEndName());
                } else {
                    result = String.format("Neither start point %s or end point %s do exist", e.getStartName(), e.getEndName());
                }
            }
        }
        System.out.printf("%s%n", result);
    }

}
