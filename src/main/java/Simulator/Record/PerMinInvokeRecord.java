package Simulator.Record;

import java.util.ArrayList;
import java.util.List;

/**
 * 每分钟调用记录类，记录某函数1440分钟每分钟热启动，冷启动以及drop的次数
 *
 */
public class PerMinInvokeRecord {
    public String name;
    public List<Integer> warmList = new ArrayList<>();
    public List<Integer> coldList = new ArrayList<>();
    public List<Integer> queueFullList = new ArrayList<>();
    public List<Integer> ttlList = new ArrayList<>();

    public void increaseWarm(int index){
        int old = warmList.get(index);
        warmList.set(index, old + 1);
    }

    public void increaseCold(int index){
        int old = coldList.get(index);
        coldList.set(index, old + 1);
    }

    public void increaseQueueFullDrop(int index){
        int old = queueFullList.get(index);
        queueFullList.set(index, old + 1);
    }

    public void increaseTTLDrop(int index){
        int old = ttlList.get(index);
        ttlList.set(index, old + 1);
    }

    public PerMinInvokeRecord(String name) {
        this.name = name;
        for (int i = 0; i < 1440; i++) {
            warmList.add(0);
            coldList.add(0);
            queueFullList.add(0);
            ttlList.add(0);
        }
    }

    public String getName() {
        return name;
    }

    public List<Integer> getWarmList() {
        return warmList;
    }

    public List<Integer> getColdList() {
        return coldList;
    }

    public List<Integer> getQueueFullList() {
        return queueFullList;
    }

    public List<Integer> getTTlList() {
        return ttlList;
    }
}
