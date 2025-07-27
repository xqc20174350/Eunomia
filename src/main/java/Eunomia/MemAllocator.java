package Eunomia;

import Eunomia.Enums.Policy;
import Eunomia.ConcurrencyPool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * mem allocator
 */
public class MemAllocator {
    public static int maxConcurrencyPoolCount = 500;
    public static int maxConcurrencyPoolCountForOneFunc = 100;

    //主内存块
    private ConcurrencyPool mainMemPool;
    //独立内存块
    private Map<String,ConcurrencyPool> seperatedMemPoolsMap = new HashMap<>();
    private int maxSepMemPoolCapacity;
    private int sepMemLeft;


    private Map<String, Function> nameToFunctionMap;
    private List<String> highCostFunctionNameList;
    private Map<String, List<Integer>> predictionData;

    public MemAllocator(ContainerScheduler scheduler){
        this.mainMemPool = new ConcurrencyPool(scheduler.getMemCapacity());
        this.highCostFunctionNameList = scheduler.getHighCostFunctionNameList();
        this.predictionData = scheduler.getPredictionData();
        this.nameToFunctionMap = scheduler.getNameToFunctionMap();
        this.maxSepMemPoolCapacity = scheduler.getMaxSepMemPoolCapacity();

        this.sepMemLeft = this.maxSepMemPoolCapacity;
    }


    /**
     * 对allocator进行初始化，按照策略进行不同的方式分配
     * 静态时会按照调用均值计算吞吐量进行分配
     * 动态时会按照预测数据第一分钟的值进行分配
     * @param policy 采取的策略
     * @param simple 是否进行简单分配
     */
    public void initAllocator(Policy policy, boolean simple){
        int sepMemLeft = maxSepMemPoolCapacity;
        int size = this.highCostFunctionNameList.size();
        for(int i = 0; i < size; i++) {
            String name = highCostFunctionNameList.get(i);
            Function func = nameToFunctionMap.get(name);
            int mem = func.getMemSize();;
            int invokeCount = func.getInvocationCount();
            int duration = func.getWarmRunTime();
            int minutes = 0;
            switch (policy){
                case SSMP:
                    //如果只做简单分配，只分配两块
                    if(simple){
                        minutes = 1;
                    } else {
                        //静态分配初始化时按照总调用次数的平均值来进行计算
                        int invokePerMinute = invokeCount/1440;
                        int totalDurationPerMinute = invokePerMinute * duration;
                        //一分钟调用的总耗时可以划分为几分钟,可划分为几分钟就给他分配几倍的空间,这样平均下来每分钟都会有完整占用的调用
                        minutes  = totalDurationPerMinute/60000 + 1;
                    }
                    break;
                case DSMP:
                    //动态分配初始化时按照第一分钟的预测数据进行分配
                    int firstMinInvocation = predictionData.get(name).get(0);
                    int totalDuration = firstMinInvocation * duration;
                    //一分钟调用的总耗时可以划分为几分钟,可划分为几分钟就给他分配几倍的空间,这样平均下来每分钟都会有完整占用的调用
                    minutes = totalDuration / 60000 + 1;
                    break;
                default:
                    break;
            }

            int allocatePoolNum = Math.min(minutes, MemAllocator.maxConcurrencyPoolCountForOneFunc);
            int memToAllocate = allocatePoolNum * mem;

            boolean full = false;
            if(sepMemLeft < memToAllocate){
                memToAllocate = sepMemLeft;
                full = true;
            }

            ConcurrencyPool pool = new ConcurrencyPool(memToAllocate, func.getName());
            int oldCapacity = mainMemPool.getCapacity();
            mainMemPool.setCapacity(oldCapacity - memToAllocate);
            sepMemLeft -= memToAllocate;
            this.seperatedMemPoolsMap.put(func.getName(), pool);

            if(policy != Policy.LRU){
                System.out.println("mem allocated for " + name + ": " + memToAllocate
                        + "  base num: " + minutes
                        + "  allocate pool num: " + allocatePoolNum);
            }

            if(full){
                System.out.println("full");
                break;
            }
        }
    }


    /**
     * 在采取动态策略时每分钟进行动态缩放
     * 缩放时先处理释放空间给主空间的，这样能够较大限度的进行分配
     * @param minute 当前是第几分钟
     */
    public void dynamicScale(int minute){
        if(minute >= 1440){
            return;
        }
        Map<String, Integer> nameToMemChanges = new HashMap<>();
        for (String name : predictionData.keySet()) {
            Function func = nameToFunctionMap.get(name);
            int mem = func.getMemSize();
            int duration = func.getWarmRunTime();

            int invocationCount = predictionData.get(name).get(minute);
            int totalDuration = invocationCount * duration;
            //一分钟调用的总耗时可以划分为几分钟,可划分为几分钟就给他分配几倍的空间,这样平均下来每分钟都会有完整占用的调用
            int minutes = totalDuration / 60000 + 1;
            int allocatePoolNum = Math.min(minutes, MemAllocator.maxConcurrencyPoolCountForOneFunc);
            int memToAllocate = allocatePoolNum * mem;
            ConcurrencyPool pool = seperatedMemPoolsMap.get(name);
            int diff = memToAllocate - pool.getCapacity();
            nameToMemChanges.put(name,diff);
        }
        //Map按照要重新分配的空间大小排序，这样可以优先处理空间要缩小的，能腾出空间
        List<Map.Entry<String,Integer>> list = new ArrayList<>(nameToMemChanges.entrySet());
        list.sort(Map.Entry.comparingByValue());

        int sepMemLeft = this.sepMemLeft;

        //对所有独立空间进行分配
        for(String name : nameToMemChanges.keySet()) {
            int diff = nameToMemChanges.get(name);
            ConcurrencyPool pool = this.seperatedMemPoolsMap.get(name);

            //空间要缩小，但此时在使用的mem比更新后的capacity大，不做更新
            if (diff < 0 && pool.getMemUsed() > pool.getCapacity() + diff) {
                continue;
            } else if (diff > 0 && sepMemLeft < diff) {//空间要增大，但是剩余可分配空间不足,不做更新
                continue;
            } else {
                //更新独立块的capacity
                pool.setCapacity(pool.getCapacity() + diff);

                //更新主内存块的capacity
                int mainMemPoolCapacity = mainMemPool.getCapacity();
                mainMemPool.setCapacity(mainMemPoolCapacity - diff);

                sepMemLeft = sepMemLeft - diff;
            }
        }
    }

    public static int getMaxConcurrencyPoolCount() {
        return maxConcurrencyPoolCount;
    }

    public static void setMaxConcurrencyPoolCount(int maxConcurrencyPoolCount) {
        MemAllocator.maxConcurrencyPoolCount = maxConcurrencyPoolCount;
    }

    public static int getMaxConcurrencyPoolCountForOneFunc() {
        return maxConcurrencyPoolCountForOneFunc;
    }

    public static void setMaxConcurrencyPoolCountForOneFunc(int maxConcurrencyPoolCountForOneFunc) {
        MemAllocator.maxConcurrencyPoolCountForOneFunc = maxConcurrencyPoolCountForOneFunc;
    }

    public ConcurrencyPool getMainMemPool() {
        return mainMemPool;
    }


    public Map<String, ConcurrencyPool> getSeperatedMemPoolsMap() {
        return seperatedMemPoolsMap;
    }


    public int getMaxSepMemPoolCapacity() {
        return maxSepMemPoolCapacity;
    }

    public void setMaxSepMemPoolCapacity(int maxSepMemPoolCapacity) {
        this.maxSepMemPoolCapacity = maxSepMemPoolCapacity;
    }
}
