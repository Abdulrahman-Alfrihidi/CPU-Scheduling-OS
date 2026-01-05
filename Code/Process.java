public class Process {
    public final int jobId;
    public final double arrivalTime;
    public final int reqMemory;
    public final int reqDevices;

    public final double serviceTime;
    public double remService;

    // runtime bookkeeping
    public double tqPlanned = 0.0;
    public double waitAccum = 0.0;
    public double arrivalOnReady = 0.0;
    public double lastEnqueueTime = 0.0;

    public Process(Job j) {
        this.jobId = j.jobId;
        this.arrivalTime = j.arrivalTime;
        this.reqMemory = j.reqMemory;
        this.reqDevices = j.reqDevices;
        this.serviceTime = j.serviceTime;
        this.remService = j.serviceTime;
    }
}
