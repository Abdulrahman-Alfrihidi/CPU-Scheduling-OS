public class Job {
    public final int jobId;
    public final double arrivalTime;
    public final int reqMemory;
    public final double serviceTime;
    public final int reqDevices;
    public final int priority;

    // used to break ties (HQ1 FIFO after mem)
    public long serial = 0L;

    public Job(int jobId, double arrivalTime, int reqMemory, double serviceTime, int reqDevices, int priority) {
        this.jobId = jobId;
        this.arrivalTime = arrivalTime;
        this.reqMemory = reqMemory;
        this.serviceTime = serviceTime;
        this.reqDevices = reqDevices;
        this.priority = priority;
    }
}
