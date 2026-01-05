import java.util.*;

public class PrManager {
    private final OtherKerServices kernel;

    // Queues
    private final Deque<Process> readyQ = new ArrayDeque<>();
    private final PriorityQueue<Job> holdQ1; // asc by requested memory, then FIFO
    private final Deque<Job> holdQ2 = new ArrayDeque<>();

    // Scheduler variants
    private Scheduler dynRR;   // default
    private Scheduler staticRR;
    private Scheduler fcfs;

    // Which CPU scheduler to use (default dynamic RR)
    private Scheduler cpuScheduler;

    // Internal clock concept (kept for requirement 3.7.4)
    private double internalClock = 0.0;

    // Currently running process & its time slice end
    private Process running = null;
    private double nextInternalEvent = Double.POSITIVE_INFINITY; // time for slice end or completion

    // SR/AR tracking for dynamic RR
    private double SR = 0.0;
    private int readyCount = 0;

    // Finished records
    public static class FinishedRecord {
        int jobId;
        double arrivalTime, completeTime, turnaround, weightedTurnaround;
    }
    private final List<FinishedRecord> finished = new ArrayList<>();

    // FIFO tie-breaker for HQ1
    private long arrivalSerial = 0;

    public PrManager(OtherKerServices kernel) {
        this.kernel = kernel;
        this.holdQ1 = new PriorityQueue<>((a, b) -> {
            if (a.reqMemory != b.reqMemory) return Integer.compare(a.reqMemory, b.reqMemory);
            return Long.compare(a.serial, b.serial);
        });
    }

    public void setSchedulers(Scheduler dyn, Scheduler stat, Scheduler f) {
        this.dynRR = dyn;
        this.staticRR = stat;
        this.fcfs = f;
        this.cpuScheduler = this.dynRR; // default dynamic
    }

    @SuppressWarnings("unused")
    public void setCpuToDynamicRR() { this.cpuScheduler = this.dynRR; }
    @SuppressWarnings("unused")
    public void setCpuToStaticRR()  { this.cpuScheduler = this.staticRR; }
    @SuppressWarnings("unused")
    public void setCpuToFCFS()      { this.cpuScheduler = this.fcfs; }

    public boolean hasRunnableOrQueuedWork() {
        return running != null || !readyQ.isEmpty() || !holdQ1.isEmpty() || !holdQ2.isEmpty();
    }

    public void cpuTimeAdvanceTo(double t) {
        if (t > internalClock) internalClock = t;
    }

    // Called by controller when a job arrival line is seen
    public void procArrivalRoutine(double now, Job j) {
        j.serial = (++arrivalSerial);

        // Reject rule: if job requests more than system contains (not only available)
        if (j.reqMemory > kernel.getTotalMemory() || j.reqDevices > kernel.getTotalDevices()) {
            // rejected (do nothing)
            return;
        }

        // If enough available memory & devices ⇒ allocate & move to ready
        if (kernel.canAllocate(j.reqMemory, j.reqDevices)) {
            kernel.allocateMemory(j.reqMemory);
            kernel.reserveDevice(j.reqDevices);
            Process p = new Process(j);
            p.arrivalOnReady = now;
            pushReady(p);
            tryStartCpuIfIdle(now);
        } else {
            // Go to Hold queues based on priority
            if (j.priority == 1) holdQ1.add(j);
            else holdQ2.addLast(j);
        }
    }

    // Internal event handling: complete time slice or completion
    public void dispatch(double now) {
        // complete any slice/termination already scheduled
        if (running == null) {
            tryStartCpuIfIdle(now);
            return;
        }

        // Advance running by the planned quantum
        double runFor = Math.min(running.tqPlanned, running.remService);
        running.remService = round2(running.remService - runFor);

        if (running.remService <= 1e-9) {
            // Terminate
            finishProcess(now);
            // free resources & move jobs from holds if possible
            admitFromHolds(now);
            // Start next
            tryStartCpuIfIdle(now);
        } else {
            // Time slice expired, preempt and go back to ready :)
            running.lastEnqueueTime = now;
            pushReady(running);
            running = null;

            // After requeue, try start next immediately
            tryStartCpuIfIdle(now);
        }
        // schedule next internal event timestamp accordingly inside tryStartCpuIfIdle()
    }

    @SuppressWarnings("unused")
    public double getNextDecisionTime(double now) {
        // if CPU is running, nextInternalEvent is already set; else +∞ :O
        return nextInternalEvent;
    }

    private void tryStartCpuIfIdle(double now) {
        if (running != null) return;

        Process next = pickNextReady();
        if (next == null) {
            nextInternalEvent = Double.POSITIVE_INFINITY;
            return;
        }

        running = next;
        running.waitAccum += (now - running.arrivalOnReady);
        running.arrivalOnReady = now;

        // Choose quantum based on scheduler rules
        double tq = cpuScheduler.chooseQuantum(now, readyQ, running, SR, readyCount);
        running.tqPlanned = tq;

        // schedule next internal event (slice end or completion)
        double runFor = Math.min(tq, running.remService);
        nextInternalEvent = now + runFor;
    }

    private void finishProcess(double now) {
        // Free resources
        kernel.deallocateMemory(running.reqMemory);
        kernel.releaseDevice(running.reqDevices);

        // Stats
        FinishedRecord fr = new FinishedRecord();
        fr.jobId = running.jobId;
        fr.arrivalTime = running.arrivalTime;
        fr.completeTime = now;
        fr.turnaround = now - running.arrivalTime;
        fr.weightedTurnaround = fr.turnaround - running.serviceTime;
        finished.add(fr);

        // Remove from SR counts (running was not in SR while it ran; SR tracked only readyQ)
        running = null;
    }

    private void admitFromHolds(double now) {
        boolean moved = true;
        while (moved) {
            moved = false;
            // First HQ1 (priority 1)
            while (!holdQ1.isEmpty()) {
                Job j = holdQ1.peek();
                if (kernel.canAllocate(j.reqMemory, j.reqDevices)) {
                    holdQ1.poll();
                    kernel.allocateMemory(j.reqMemory);
                    kernel.reserveDevice(j.reqDevices);
                    Process p = new Process(j);
                    p.arrivalOnReady = now;
                    pushReady(p);
                    moved = true;
                } else break;
            }
            // Then HQ2 FIFO
            while (!holdQ2.isEmpty()) {
                Job j = holdQ2.peekFirst();
                if (kernel.canAllocate(j.reqMemory, j.reqDevices)) {
                    holdQ2.pollFirst();
                    kernel.allocateMemory(j.reqMemory);
                    kernel.reserveDevice(j.reqDevices);
                    Process p = new Process(j);
                    p.arrivalOnReady = now;
                    pushReady(p);
                    moved = true;
                } else break;
            }
        }
    }

    private void pushReady(Process p) {
        readyQ.addLast(p);
        SR += p.remService;
        readyCount++;
    }

    private Process pickNextReady() {
        Process p = readyQ.pollFirst();
        if (p != null) {
            SR -= p.remService;
            readyCount--;
        }
        return p;
    }

    // Snapshots for display
    public List<Process> snapshotReady() {
        return new ArrayList<>(readyQ);
    }
    public List<Job> snapshotHQ1() {
        return new ArrayList<>(holdQ1);
    }
    public List<Job> snapshotHQ2() {
        return new ArrayList<>(holdQ2);
    }
    public List<FinishedRecord> snapshotFinished() {
        return new ArrayList<>(finished);
    }

    private static double round2(double v) {
        // minor rounding to mitigate FP noise
        return Math.abs(v) < 1e-9 ? 0.0 : v;
    }

    // Public wrapper to start CPU
    public void tryStartCpuIfIdlePublic(double now) {
        tryStartCpuIfIdle(now);
    }

}
