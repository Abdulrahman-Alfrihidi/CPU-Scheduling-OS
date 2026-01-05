import java.util.Deque;

public class StaticRRScheduler implements Scheduler {
    private final double quantum;

    public StaticRRScheduler(int q) {
        this.quantum = Math.max(1, q);
    }

    @Override
    public double chooseQuantum(double now, Deque<Process> readyQ, Process running,
                                double SR, int readyCount) {
        // Fixed time quantum = 10 + teamNumber (as file said)
        return quantum;
    }
}
