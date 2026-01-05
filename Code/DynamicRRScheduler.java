import java.util.Deque;

public class DynamicRRScheduler implements Scheduler {
    @Override
    public double chooseQuantum(double now, Deque<Process> readyQ, Process running, double SR, int readyCount) {
        // Include the running process into the average
        double totalRemaining = SR + running.remService;
        int totalCount = readyCount + 1;

        double tq = totalRemaining / totalCount;

        if (tq <= 0) {
            return running.remService;
        }

        return tq;
    }
}
