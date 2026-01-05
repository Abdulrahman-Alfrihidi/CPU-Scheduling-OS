import java.util.Deque;

public interface Scheduler {
    // This is for choosing the slot slices :)
    double chooseQuantum(double now, Deque<Process> readyQ, Process running,
                         double SR, int readyCount);
}
