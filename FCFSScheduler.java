import java.util.Deque;

public class FCFSScheduler implements Scheduler {
    @Override
    public double chooseQuantum(double now, Deque<Process> readyQ, Process running,
                                double SR, int readyCount) {
        return running.remService; // to completion
    }
}
