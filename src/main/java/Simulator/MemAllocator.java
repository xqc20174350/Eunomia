package Simulator;

import Simulator.Enums.Policy;
import Simulator.MemoryBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * mem allocator
 */
public class MemAllocator {
    public static int maxMemoryBlockCount = 500;
    public static int maxMemoryBlockCountForOneFunc = 100;

    //主内存块
    private MemoryBlock mainMemBlock;
    //独立内存块
    private Map<String,MemoryBlock> seperatedMemBlocksMap = new HashMap<>();
    private int maxSepMemBlockCapacity;
    private int sepMemLeft;


    private Map<String, Function> nameToFunctionMap;
    private List<String> highCostFunctionNameList;
    private Map<String, List<Integer>> predictionData;

    public MemAllocator(ContainerScheduler scheduler){
        this.mainMemBlock = new MemoryBlock(scheduler.getMemCapacity());
        this.highCostFunctionNameList = scheduler.getHighCostFunctionNameList();
        this.predictionData = scheduler.getPredictionData();
        this.nameToFunctionMap = scheduler.getNameToFunctionMap();
        this.maxSepMemBlockCapacity = scheduler.getMaxSepMemBlockCapacity();

        this.sepMemLeft = this.maxSepMemBlockCapacity;
    }


    /**
     * 对allocator进行初始化，按照策略进行不同的方式分配
     * 静态时会按照调用均值计算吞吐量进行分配
     * 动态时会按照预测数据第一分钟的值进行分配
     * @param policy 采取的策略
     * @param simple 是否进行简单分配
     */
    public void initAllocator(Policy policy, boolean simple){
        int sepMemLeft = maxSepMemBlockCapacity;
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

            int allocateBlockNum = Math.min(minutes, MemAllocator.maxMemoryBlockCountForOneFunc);
            int memToAllocate = allocateBlockNum * mem;

            boolean full = false;
            if(sepMemLeft < memToAllocate){
                memToAllocate = sepMemLeft;
                full = true;
            }

            MemoryBlock block = new MemoryBlock(memToAllocate, func.getName());
            int oldCapacity = mainMemBlock.getCapacity();
            mainMemBlock.setCapacity(oldCapacity - memToAllocate);
            sepMemLeft -= memToAllocate;
            this.seperatedMemBlocksMap.put(func.getName(), block);

            if(policy != Policy.LRU){
                System.out.println("mem allocated for " + name + ": " + memToAllocate
                        + "  base num: " + minutes
                        + "  allocate block num: " + allocateBlockNum);
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
            int allocateBlockNum = Math.min(minutes, MemAllocator.maxMemoryBlockCountForOneFunc);
            int memToAllocate = allocateBlockNum * mem;
            MemoryBlock block = seperatedMemBlocksMap.get(name);
            int diff = memToAllocate - block.getCapacity();
            nameToMemChanges.put(name,diff);
        }
        //Map按照要重新分配的空间大小排序，这样可以优先处理空间要缩小的，能腾出空间
        List<Map.Entry<String,Integer>> list = new ArrayList<>(nameToMemChanges.entrySet());
        list.sort(Map.Entry.comparingByValue());

        int sepMemLeft = this.sepMemLeft;

        //对所有独立空间进行分配
        for(String name : nameToMemChanges.keySet()) {
            int diff = nameToMemChanges.get(name);
            MemoryBlock block = this.seperatedMemBlocksMap.get(name);

            //空间要缩小，但此时在使用的mem比更新后的capacity大，不做更新
            if (diff < 0 && block.getMemUsed() > block.getCapacity() + diff) {
                continue;
            } else if (diff > 0 && sepMemLeft < diff) {//空间要增大，但是剩余可分配空间不足,不做更新
                continue;
            } else {
                //更新独立块的capacity
                block.setCapacity(block.getCapacity() + diff);

                //更新主内存块的capacity
                int mainMemBlockCapacity = mainMemBlock.getCapacity();
                mainMemBlock.setCapacity(mainMemBlockCapacity - diff);

                sepMemLeft = sepMemLeft - diff;
            }
        }
    }

    public static int getMaxMemoryBlockCount() {
        return maxMemoryBlockCount;
    }

    public static void setMaxMemoryBlockCount(int maxMemoryBlockCount) {
        MemAllocator.maxMemoryBlockCount = maxMemoryBlockCount;
    }

    public static int getMaxMemoryBlockCountForOneFunc() {
        return maxMemoryBlockCountForOneFunc;
    }

    public static void setMaxMemoryBlockCountForOneFunc(int maxMemoryBlockCountForOneFunc) {
        MemAllocator.maxMemoryBlockCountForOneFunc = maxMemoryBlockCountForOneFunc;
    }

    public MemoryBlock getMainMemBlock() {
        return mainMemBlock;
    }


    public Map<String, MemoryBlock> getSeperatedMemBlocksMap() {
        return seperatedMemBlocksMap;
    }


    public int getMaxSepMemBlockCapacity() {
        return maxSepMemBlockCapacity;
    }

    public void setMaxSepMemBlockCapacity(int maxSepMemBlockCapacity) {
        this.maxSepMemBlockCapacity = maxSepMemBlockCapacity;
    }
}
