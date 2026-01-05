import java.io.*;
import java.util.*;

public class SimulationController {
    private final PrintWriter out;
    private final OtherKerServices kernel;
    private final PrManager prManager;

    private double curTime = 0.0;
    private int teamNumber = 0;

    // ---- Event container for sorted queue ----
    private static class Event {
        double time;
        char type;
        String line;
        long order;

        Event(double time, char type, String line, long order) {
            this.time = time;
            this.type = type;
            this.line = line;
            this.order = order;
        }
    }

    // Sort by time, then C->A->D, then file order
    private final PriorityQueue<Event> eventQueue = new PriorityQueue<>((a, b) -> {
        int t = Double.compare(a.time, b.time);
        if (t != 0) return t;

        int pa = (a.type == 'C') ? 0 : (a.type == 'A' ? 1 : 2);
        int pb = (b.type == 'C') ? 0 : (b.type == 'A' ? 1 : 2);
        if (pa != pb) return pa - pb;

        return Long.compare(a.order, b.order);
    });

    public SimulationController(BufferedReader in, PrintWriter out) {
        this.out = out;
        this.kernel = new OtherKerServices();
        this.prManager = new PrManager(kernel);
        loadExternalEvents(in);
    }

    // Read all external events first
    private void loadExternalEvents(BufferedReader in) {
        try {
            String line;
            long order = 0;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                char c = Character.toUpperCase(line.charAt(0));
                if (c != 'A' && c != 'C' && c != 'D' && c != 'S') continue;


                String[] parts = line.split("\\s+");
                double t = Double.parseDouble(parts[1]);

                eventQueue.add(new Event(t, c, line, order++));
            }
        } catch (Exception ignored) {}
    }

    public void run() {
        while (!eventQueue.isEmpty() || prManager.hasRunnableOrQueuedWork()) {

            double e = prManager.getNextDecisionTime(curTime); // next internal
            double i = eventQueue.isEmpty() ? Double.POSITIVE_INFINITY : eventQueue.peek().time;

            double next = Math.min(e, i);
            if (Double.isInfinite(next)) break;

            if (Math.abs(e - i) < 1e-9) {
                curTime = e;
                prManager.cpuTimeAdvanceTo(curTime);
                prManager.dispatch(curTime);
                handleOneExternal();
            }
            else if (e < i) {
                curTime = e;
                prManager.cpuTimeAdvanceTo(curTime);
                prManager.dispatch(curTime);
            }
            else {
                curTime = i;
                handleOneExternal();
            }
        }

        out.flush();
    }

    private void handleOneExternal() {
        if (eventQueue.isEmpty()) return;

        Event ev = eventQueue.poll();
        String line = ev.line;
        char type = ev.type;

        try {
            switch (type) {
                case 'C':
                    parseSystemConfig(line);
                    break;

                case 'A':
                    prManager.procArrivalRoutine(curTime, parseArrival(line));
                    prManager.cpuTimeAdvanceTo(curTime);
                    prManager.tryStartCpuIfIdlePublic(curTime);
                    break;

                case 'D':
                    printDisplay();
                    break;

                case 'S':
                    handleSchedulerCommand(line);
                    break;
            }
        } catch (Exception e) {
            out.println("ERROR processing: " + line);
        }
    }

    private void parseSystemConfig(String line) {
        String[] toks = line.split("\\s+");
        double startTime = Double.parseDouble(toks[1]);

        if (curTime < startTime) curTime = startTime;

        int mem = kernel.getTotalMemory();
        int dev = kernel.getTotalDevices();

        for (int k = 2; k < toks.length; k++) {
            String part = toks[k];
            if (part.startsWith("M=")) mem = Integer.parseInt(part.substring(2));
            else if (part.startsWith("S=")) dev = Integer.parseInt(part.substring(2));
            else if (part.startsWith("TEAM=")) teamNumber = Integer.parseInt(part.substring(5));
        }

        kernel.configure(mem, dev);
        prManager.setSchedulers(new DynamicRRScheduler(), new StaticRRScheduler(10 + teamNumber), new FCFSScheduler());
    }

    private Job parseArrival(String line) {
        String[] toks = line.split("\\s+");
        double t = Double.parseDouble(toks[1]);
        int id = 0, m = 0, dev = 0, p = 1;
        double svc = 0.0; // service time (R)

        for (int k = 2; k < toks.length; k++) {
            String tok = toks[k];
            if (tok.startsWith("J=")) id = Integer.parseInt(tok.substring(2));
            else if (tok.startsWith("M=")) m = Integer.parseInt(tok.substring(2));
            else if (tok.startsWith("S=")) dev = Integer.parseInt(tok.substring(2));   // S = devices
            else if (tok.startsWith("R=")) svc = Double.parseDouble(tok.substring(2)); // R = service time (Cycles)
            else if (tok.startsWith("P=")) p = Integer.parseInt(tok.substring(2));
        }

        return new Job(id, t, m, svc, dev, p);
    }

    // ---------------- DISPLAY  ----------------
    private void printDisplay() {
        out.println("\n\n-------------------------------------------------------");
        out.println("System Status:                                         ");
        out.println("-------------------------------------------------------");
        out.printf("          Time: %.2f%n", curTime);
        out.printf("  Total Memory: %d%n", kernel.getTotalMemory());
        out.printf(" Avail. Memory: %d%n", kernel.getAvailMemory());
        out.printf(" Total Devices: %d%n", kernel.getTotalDevices());
        out.printf("Avail. Devices: %d%n", kernel.getAvailDevices());
        out.println();

        out.println("\nJobs in Ready List                                      ");
        out.println("--------------------------------------------------------");
        List<Process> rq = prManager.snapshotReady();
        if (rq.isEmpty()) out.println("  EMPTY");
        else for (Process p : rq)
            out.printf("Job ID %d, %.2f Cycles left to completion.%n", p.jobId, p.remService);
        out.println();

        out.println("\nJobs in Long Job List                                   ");
        out.println("--------------------------------------------------------");
        out.println("  EMPTY\n");

        out.println("\nJobs in Hold List 1                                     ");
        out.println("--------------------------------------------------------");
        List<Job> h1 = prManager.snapshotHQ1();
        if (h1.isEmpty()) out.println("  EMPTY");
        else for (Job j : h1)
            out.printf("Job ID %d, %.2f Cycles left to completion.%n", j.jobId, j.serviceTime);
        out.println();

        out.println("\nJobs in Hold List 2                                     ");
        out.println("--------------------------------------------------------");
        List<Job> h2 = prManager.snapshotHQ2();
        if (h2.isEmpty()) out.println("  EMPTY");
        else for (Job j : h2)
            out.printf("Job ID %d, %.2f Cycles left to completion.%n", j.jobId, j.serviceTime);
        out.println();

        out.println("\nJobs in Hold List 3                                     ");
        out.println("--------------------------------------------------------");
        out.println("  EMPTY\n\n");

        out.println("Finished Jobs (detailed)                                ");
        out.println("--------------------------------------------------------");
        out.println("  Job    ArrivalTime     CompleteTime     TurnaroundTime    WaitedTime");
        out.println("------------------------------------------------------------------------");

        List<PrManager.FinishedRecord> done = prManager.snapshotFinished();
        if (done.isEmpty()) {
            out.println("  EMPTY");
        } else {
            for (PrManager.FinishedRecord fr : done)
                out.printf("  %d%12.2f%18.2f%18.2f%18s%n",
                        fr.jobId, fr.arrivalTime, fr.completeTime,
                        fr.turnaround, format(fr.weightedTurnaround));
            out.printf("Total Finished Jobs:             %d%n", done.size());
        }

        out.println("\n");
    }

    private String format(double v) {
        if (Math.abs(v - Math.round(v)) < 1e-9) return String.valueOf((int)Math.round(v));
        return String.format(Locale.US, "%.5f", v);
    }

    private void handleSchedulerCommand(String line) {
        // formats:
        // S <time> STATIC for static RR
        // S <time> FCFS for FCFS xD
        // The config call can be:
        // C= <Number> M= <Number> S= <Number> OPTIONAL:)==> TEAM= <Number>
        // This is for you Abdulrahman, so you can know :)


        String[] toks = line.trim().split("\\s+");
        if (toks.length < 3) {
            out.println(">> Unknown scheduler command: " + line);
            return;
        }

        String mode = toks[2].toUpperCase(Locale.ROOT);

        if (mode.startsWith("STAT")) {
            prManager.setCpuToStaticRR();
            out.println(">> Scheduler switched to STATIC RR at t=" + String.format(Locale.US, "%.2f", curTime));
        } else if (mode.startsWith("DYN")) {
            prManager.setCpuToDynamicRR();
            out.println(">> Scheduler switched to DYNAMIC RR at t=" + String.format(Locale.US, "%.2f", curTime));
        } else if (mode.startsWith("FCFS")) {
            prManager.setCpuToFCFS();
            out.println(">> Scheduler switched to FCFS at t=" + String.format(Locale.US, "%.2f", curTime));
        } else {
            out.println(">> Unknown scheduler mode in: " + line);
        }
    }

}
