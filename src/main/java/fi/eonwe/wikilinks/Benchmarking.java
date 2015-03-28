package fi.eonwe.wikilinks;

import fi.eonwe.wikilinks.leanpages.BufferWikiPage;

import java.util.List;
import java.util.Random;

import static fi.eonwe.wikilinks.utils.Helpers.quote;

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

    public static void runBenchmarksAndTest(List<BufferWikiPage> pages) {
        WikiRoutes routes = new WikiRoutes(pages);
        long[] runtimes = new long[ROUTES.length];
        System.out.printf("Running %d random measurements%n", ROUTES.length);
        for (int i = 0; i < ROUTES.length; i++) {
            Object[] route = ROUTES[i];
            long startTime = System.currentTimeMillis();
            String p1 = (String) route[0];
            String p2 = (String) route[1];
            try {
                System.out.printf("Finding route %s -> %s%n", quote(p1), quote(p2));
                WikiRoutes.Result result = routes.findRoute(p1, p2);
                long totalTime = System.currentTimeMillis() - startTime;
                int foundSize = result.getRoute().size();
                int expectedSize = ((Number)route[2]).intValue();
                String statusString = foundSize == expectedSize ? "OK" : "FAIL";
                System.out.printf("Found route length: %d, expected length: %d (%d ms) [%s]%n", foundSize, expectedSize, totalTime, statusString);
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

    private final static Object[][] ROUTES = {
            {"Pepe Ozan", "Kryponite", 0},
            {"Phœbus", "Olga Alexandrova", 7},
            {"Niccolo Berrettoni", "Ἐπίδαυρος", 0},
            {"Adlivun", "Danutė Kvietkevičiūtė", 8},
            {"Harbour Le Cou, Newfoundland and Labrador", "Thorsten Marschner", 0},
            {"At the gate of horn", "Sunga (disambiguation)", 0},
            {"Felix Francois Faure", "Kenwood (disambiguation)", 10},
            {"Jean-Paul Fournier", "Dianne DeLeeuw", 0},
            {"Tanja Bakić", "American Force (tag team)", 0},
            {"James Griffin (Irish politician)", "Digital audio mixing", 0},
            {"EU Directive on Agency Workers", "Westshore, New Zealand", 7},
            {"Foreign Trade Bank", "Mercedes-Benz Championship (European Tour)", 7},
            {"Solanum interandinum", "James Orengo", 5},
            {"Green Airport", "Big Time Rush: The Movie", 0},
            {"Tarom, Hormozgan", "Cockersdale (band)", 7},
            {"Catene (album)", "Burma at the 1996 Summer Olympics", 0},
            {"Al-Hamraa", "Verici", 0},
            {"Erős Pista", "Technoself", 6},
            {"Mohammed Dajani Daoudi", "Storegga Slide", 5},
            {"Efraín Loyola", "Sublegatus modestus", 7},
            {"Stereotypic behavior", "Ceramica Flaminia - Bossini Docce", 0},
            {"Holly Blake", "Bryan Zoubek", 0},
            {"Edsvalla", "Grant waters", 0},
            {"Kurram (disambiguation)", "Desert Storm Journal", 7},
            {"Simeon the Righteous", "Pence Springs", 9},
            {"Spilarctia nobilis", "ソニックアドバンス3", 0},
            {"Samuel John Gurney Hoare, 1st Viscount Templewood of Chelsea", "New Zealand sand diver", 7},
            {"Dhanwar language (Nepal)", "Maguricea", 0},
            {"List of Kurdish people", "Lasasaurus", 7},
            {"1975 in the environment", "History of mauritius", 0},
            {"Amarcord Ensemble", "Sebastião (given name)", 7},
            {"Worldwide Universities Network", "LP gas", 5},
            {"Calgary flames draft picks", "Glossary of systems theory", 7},
            {"USP16", "China State Construction International Holdings Limited", 8},
            {"Fuck You! (song)", "Variable message sign", 6},
            {"Lake Zurich right bank railway line", "Cyber-Ark", 7},
            {"Pteropurpura deroyana", "Visa requirements for Dominican Republic citizens", 6},
            {"Italy women's national floorball team", "Proto–Pama-Nyungan language", 0},
            {"Manx Telecom", "2888 BC", 0},
            {"Virginia State Route 334 (pre-1928)", "Swedish general election, 1924", 7},
            {"Sasaks", "Kamula language", 6},
            {"Bach family", "Ali Kuşçu", 5},
            {"Niphargus timavi", "Icon Health & Fitness Inc.", 0},
            {"Journey to the West in popular culture", "Matsuyama-jo", 0},
            {"Viscount Hughenden", "Dalhousie Faculty of Computer Science", 8},
            {"Hansgal", "Earl of Forth", 6},
            {"ORP Grom (disambiguation)", "Drug weapon", 0},
            {"Euler Line", "Encausse-les-Thermes", 7},
            {"John Jacob of Montferrat", "Vice Project Doom", 0},
            {"RAFI", "Floyd Lawson The Barber", 0}
    };

}
