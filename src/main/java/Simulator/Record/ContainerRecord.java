package Simulator.Record;

/**
 * 容器纪录类，记录每分钟被驱逐以及自动死亡的容器数
 */
public class ContainerRecord {
    private int minute = 0;
    private int autoDieCount = 0;
    private int evictCount = 0;

    public void increaseAutoDie(){
        this.autoDieCount++;
    }

    public void increaseEvict(){
        this.evictCount++;
    }

    public int getMinute() {
        return minute;
    }

    public void setMinute(int minute) {
        this.minute = minute;
    }

    public int getAutoDieCount() {
        return autoDieCount;
    }

    public void setAutoDieCount(int autoDieCount) {
        this.autoDieCount = autoDieCount;
    }

    public int getEvictCount() {
        return evictCount;
    }

    public void setEvictCount(int evictCount) {
        this.evictCount = evictCount;
    }
}
