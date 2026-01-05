import java.io.*;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: NO ARGS, java Main <input.txt> [output.txt]");
            System.exit(1);
        }
        String inputPath = args[0];
        String outputPath = (args.length == 2) ? args[1] : null;

        try (BufferedReader br = new BufferedReader(new FileReader(inputPath));
             PrintWriter out = (outputPath == null)
                     ? new PrintWriter(System.out, true)
                     : new PrintWriter(new FileWriter(outputPath))) {

            SimulationController controller = new SimulationController(br, out);
            controller.run();

        } catch (IOException e) {
            System.err.println("I/O Error:");
            e.printStackTrace(System.err);
            System.exit(2);
        }
    }
}
