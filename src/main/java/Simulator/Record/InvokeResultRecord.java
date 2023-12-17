package Simulator.Record;

/**
 * 调用结果纪录类，记录热启动，冷启动以及drop的次数
 * 配合函数名使用
 */
public class InvokeResultRecord {
    private int warmStartCount = 0;
    private int coldStartCount = 0;
    private int queueFullDropCount = 0;
    private int ttlDropCount = 0;


    public void increaseWarmStartTime(){
        this.warmStartCount++;
    }
    public void increaseColdStartTime(){
        this.coldStartCount++;
    }
    public void increaseQueueDropTime(){
        this.queueFullDropCount++;
    }
    public void increaseTTLDropTime(){
        this.ttlDropCount++;
    }

    public int getWarmStartCount() {
        return warmStartCount;
    }

    public void setWarmStartCount(int warmStartCount) {
        this.warmStartCount = warmStartCount;
    }

    public int getColdStartCount() {
        return coldStartCount;
    }

    public void setColdStartCount(int coldStartCount) {
        this.coldStartCount = coldStartCount;
    }

    public int getQueueFullDropCount() {
        return queueFullDropCount;
    }

    public void setQueueFullDropCount(int queueFullDropCount) {
        this.queueFullDropCount = queueFullDropCount;
    }

    public int getTtlDropCount() {
        return ttlDropCount;
    }

    public void setTtlDropCount(int ttlDropCount) {
        this.ttlDropCount = ttlDropCount;
    }
}
