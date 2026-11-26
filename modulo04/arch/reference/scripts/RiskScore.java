/**
 * Prints the score and the level of one risk on the risk matrix: impact times likelihood, each
 * from 1 to 3.
 *
 * <p>Usage: {@code java RiskScore.java <impact> <likelihood>}, or {@code java RiskScore.java unproven}
 * for a technology unknown to the team, which always scores 9 without the matrix. Prints
 * {@code <score> <level>}, for example {@code 6 high}. Levels: 1 and 2 low, 3 and 4 medium, 6 and 9
 * high. Any other input exits with code 2 and the usage.
 */
public class RiskScore {

    private static final String USAGE =
            "usage: java RiskScore.java <impact 1-3> <likelihood 1-3> | java RiskScore.java unproven";

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("unproven")) {
            System.out.println("9 high");
            return;
        }
        if (args.length != 2) {
            usage();
        }
        int impact = rating(args[0]);
        int likelihood = rating(args[1]);
        int score = impact * likelihood;
        System.out.println(score + " " + level(score));
    }

    private static int rating(String value) {
        switch (value) {
            case "1":
                return 1;
            case "2":
                return 2;
            case "3":
                return 3;
            default:
                usage();
                return 0;
        }
    }

    private static String level(int score) {
        if (score <= 2) {
            return "low";
        }
        if (score <= 4) {
            return "medium";
        }
        return "high";
    }

    private static void usage() {
        System.err.println(USAGE);
        System.exit(2);
    }
}
