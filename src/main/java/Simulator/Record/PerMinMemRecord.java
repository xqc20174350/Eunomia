package Simulator.Record;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * 每分钟内存占用纪录类，记录某函数1440分钟每分钟内存占用
 * 每毫秒进行记录并在每分钟莫计算内存占用的均值，最大值和最小值
 */
public class PerMinMemRecord {
    /**
     * 函数名，可以使用ALL代指所有函数
     */
    private String name;

    /**
     * 保存每分钟的内存占用平均值
     */
    private List<Integer> averageList = new ArrayList<>();
    /**
     * 保存每分钟的内存占用最大值
     */
    private List<Integer> maxList = new ArrayList<>();
    /**
     * 保存每分钟的内存占用最大值
     */
    private List<Integer> minList = new ArrayList<>();
    /**
     * 保存每分钟的内存占用最大值
     */
    private List<Integer> memPerMilliList = new ArrayList<>();

    public PerMinMemRecord(String name){
        this.name = name;
        for(int i = 0; i < 1440; i++){
            averageList.add(0);
            maxList.add(0);
            minList.add(0);
        }
        for (int i = 0; i < 60000; i++) {
            memPerMilliList.add(0);
        }
    }

    /**
     * 清理纪毫秒级录列表，用于下一分钟记录
     */
    private void clearMilliList(){
        memPerMilliList.clear();
        for (int i = 0; i < 60000; i++) {
            memPerMilliList.add(0);
        }
    }

    /**
     * 设置第millisec毫秒的内存占用
     * @param milliSec 毫秒数
     * @param mem 内存占用 单位MB
     */
    public void setMemAtMilliSec(int milliSec, int mem){
        //System.out.println(memPerMilliList.size());
        int milliToAMin = milliSec % 60000;

        memPerMilliList.set(milliToAMin,mem);
        if(milliToAMin + 1 == 60000){
            int average = getAverage();
            int max = getMax();
            int min = getMin();
            int minute = milliSec/60000;
            //System.out.println(max + " " + min + " " + average);
            setAverageAtIMin(minute,average);
            setMaxAtIMin(minute,max);
            setMinAtIMin(minute,min);
            clearMilliList();
        }
    }

    private int getAverage(){
        long sum = memPerMilliList.stream().mapToLong(Integer::intValue).sum();
        return (int) (sum/memPerMilliList.size());
    }

    private int getMax(){
        OptionalInt max =  memPerMilliList.stream().mapToInt(Integer::intValue).max();
        return max.getAsInt();
    }

    private int getMin(){
        OptionalInt min = memPerMilliList.stream().mapToInt(Integer::intValue).min();
        return min.getAsInt();
    }

    /**
     * 设置第i分钟函数内存占用的平均值
     * @param i 第i分钟
     */
    private void setAverageAtIMin(int i, int mem){
        averageList.set(i,mem);
    }

    /**
     * 设置第i分钟函数内存占用的最大值
     * @param i 第i分钟
     */
    private void setMaxAtIMin(int i, int mem){
        maxList.set(i,mem);
    }

    /**
     * 设置第i分钟函数内存占用的最小值
     * @param i 第i分钟
     */
    private void setMinAtIMin(int i, int mem){
        minList.set(i,mem);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Integer> getAverageList() {
        return averageList;
    }

    public void setAverageList(List<Integer> averageList) {
        this.averageList = averageList;
    }

    public List<Integer> getMaxList() {
        return maxList;
    }

    public void setMaxList(List<Integer> maxList) {
        this.maxList = maxList;
    }

    public List<Integer> getMinList() {
        return minList;
    }

    public void setMinList(List<Integer> minList) {
        this.minList = minList;
    }


    public List<Integer> getMemPerMilliList() {
        return memPerMilliList;
    }

    public void setMemPerMilliList(List<Integer> memPerMilliList) {
        this.memPerMilliList = memPerMilliList;
    }
}
