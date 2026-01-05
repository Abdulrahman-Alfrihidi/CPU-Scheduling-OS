public class OtherKerServices {
    private int totalMemory = 0;
    private int availMemory = 0;

    private int totalDevices = 0;
    private int availDevices = 0;

    public void configure(int totalMem, int totalDev) {
        this.totalMemory = totalMem;
        this.availMemory = totalMem;
        this.totalDevices = totalDev;
        this.availDevices = totalDev;
    }

    public int getTotalMemory() { return totalMemory; }
    public int getAvailMemory() { return availMemory; }

    public int getTotalDevices() { return totalDevices; }
    public int getAvailDevices() { return availDevices; }

    public boolean canAllocate(int mem, int dev) {
        return mem <= availMemory && dev <= availDevices;
    }

    public void allocateMemory(int units) {
        availMemory -= units;
        if (availMemory < 0) availMemory = 0;
    }

    public void deallocateMemory(int units) {
        availMemory += units;
        if (availMemory > totalMemory) availMemory = totalMemory;
    }

    public void reserveDevice(int count) {
        availDevices -= count;
        if (availDevices < 0) availDevices = 0;
    }

    public void releaseDevice(int count) {
        availDevices += count;
        if (availDevices > totalDevices) availDevices = totalDevices;
    }
}
