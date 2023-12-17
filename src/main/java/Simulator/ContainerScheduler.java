package Simulator;

import Simulator.Record.PerMinInvokeRecord;
import Simulator.Record.PerMinMemRecord;
import Simulator.Utils.CSVUtil;
import Simulator.Enums.Policy;
import Simulator.Enums.schedulePolicy;
import Simulator.Enums.InvokeStatus;
import Simulator.Record.ContainerRecord;
import Simulator.Record.InvokeResultRecord;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class ContainerScheduler {
    private static final String ALL_NAME = "All";

    /**
     * 内存分配策略
     */
    private Policy policy;
    /**
     * 内存分配器
     */
    MemAllocator allocator;
    /**
     * 总内存块大小
     */
    private int memCapacity;
    /**
     * 可用于独立空间分配的空间最大大小
     */
    private int maxSepMemBlockCapacity;

    // data paths
    /**
     * 调用数据路径
     */
    private String invokeRecordsPath;
    /**
     * 调用结果记录路径
     */
    private String invokeResPath;
    /**
     * 容器结果记录路径
     */
    private String containerResPath;
    /**
     * 每分钟调用结果记录路径
     */
    private String perMinuteRecordPath;
    /**
     * 每分钟内存占用记录路径
     */
    private String memUsedPerMinRecordPath;


    // store info about functions
    private Map<String, Function> nameToFunctionMap = new HashMap<>();
    private List<String> highCostFunctionNameList = new ArrayList<>();
    private Map<String, List<Integer>> predictionData = new HashMap<>();

    //store info about invoke records
    /**
     * 每个函数的调用记录
     */
    private Map<String, InvokeResultRecord> invokeResultRecordMap = new HashMap<>();
    /**
     * 每分钟容器evict，ttl记录
     */
    private Map<Integer, ContainerRecord> containerRecordMap = new HashMap<>();
    private ContainerRecord currentContainerRecord;
    /**
     * 每分钟总的warm，cold，evict之和的记录
     */
    private PerMinInvokeRecord ALLPerMinInvokeRecord = new PerMinInvokeRecord(ALL_NAME);
    /**
     * 保存高cost函数每分钟warm，cold，evict的记录
     */
    private Map<String, PerMinInvokeRecord> highCostPerMinInvokeRecordMap = new HashMap<>();
    /**
     * 保存高cost函数每分钟的内存占用情况
     */
    private Map<String, PerMinMemRecord> highCostMemPerMinRecordMap = new HashMap<>();


    public ContainerScheduler(int memCapacity, Policy policy,
                              String invokeRecordsPath, String invokeResPath,
                              String containerResPath, String perMinuteRecordPath,
                              String memUsedPerMinRecordPath,
                              int maxSepMemBlockCapacity) {
        this.memCapacity = memCapacity;
        this.policy = policy;

        this.invokeRecordsPath = invokeRecordsPath;
        this.invokeResPath = invokeResPath;
        this.containerResPath = containerResPath;
        this.perMinuteRecordPath = perMinuteRecordPath;
        this.memUsedPerMinRecordPath = memUsedPerMinRecordPath;

        this.maxSepMemBlockCapacity = maxSepMemBlockCapacity;
    }




    /**
     * 执行模拟循环,按照生成的调用记录进行模拟调用。并且记录各个函数的调用结果(warm/cold/drop)
     * 同时每个时间循环内对container状态进行更新处理
     * 循环结束后记录每个function的调用结果
     * @param minutesToSimulate 模拟的时间
     * @param simpleAllocate 是否进行简单分配，若是，则SSMP分配时只分配一块/两块
     */
    public void doMainLoop(int minutesToSimulate, boolean simpleAllocate) {
        final int minutesADay = 1440;
        final int secondsAMinus = 60;
        final int millisASec = 1000;
        int currentTime = 0;
        int queueWaitTime = MemoryBlock.messageTTL;
        int endTime = (minutesToSimulate) * secondsAMinus * millisASec + MemoryBlock.messageTTL;
        int keepAliveTime = 10 * secondsAMinus * millisASec; //10min

        //初始化操作
        initRecords();
        this.allocator = new MemAllocator(this);
        allocator.initAllocator(policy, simpleAllocate);



        try(BufferedReader br = new BufferedReader(new FileReader(this.invokeRecordsPath))) {

            //先读一行，越过csv header
            br.readLine();
            //用于处理csv读取的变量
            int latestInvokeTime = 0;
            String oldRecord = null;

            //时间循环
            int currentMinute = 0;
            while (currentTime <= endTime) {
                //1.每分钟初进行的操作
                if(currentTime % 60000 == 0){
                    currentMinute = currentTime/60000;
                    System.out.print("time: " + currentMinute + "th minute\r");

                    this.currentContainerRecord = new ContainerRecord();
                    containerRecordMap.put(currentMinute,this.currentContainerRecord);
                    //采用动态策略时每分钟初变化大小
                    if(policy == Policy.DSMP){
                        allocator.dynamicScale(currentMinute);
                    }
                }


                //2.每时刻首先进行container处理
                containerPoolUpdate(allocator.getMainMemBlock(), currentTime ,keepAliveTime);
                if(policy == Policy.DSMP || policy == Policy.SSMP){
                    for (String funcName: allocator.getSeperatedMemBlocksMap().keySet()) {
                        MemoryBlock memoryBlock = allocator.getSeperatedMemBlocksMap().get(funcName);
                        containerPoolUpdate(memoryBlock,currentTime,keepAliveTime);
                    }
                }

                //3.接下来进行读取以及消息入队,仅入队本时刻的调用
                if(currentTime <= endTime - queueWaitTime  && currentTime == latestInvokeTime){
                    while (true) {
                        String str;
                        if(oldRecord != null){
                            str = oldRecord;
                            oldRecord = null;
                        } else {
                            str = br.readLine();
                        }

                        //记录已经读完，不再读
                        if(str == null){
                            break;
                        }

                        String[] record = str.split(",");
                        int invokeTime = Integer.parseInt(record[2]);
                        String funcHash = record[1];
                        FunctionInvoke invoke = new FunctionInvoke(funcHash,invokeTime); //到此已经获取function invoke

                        if(invokeTime <= currentTime){
                            if((this.policy == Policy.DSMP || this.policy == Policy.SSMP) && highCostFunctionNameList.contains(funcHash)){
                                MemoryBlock block = allocator.getSeperatedMemBlocksMap().get(funcHash);
                                block.offerMessage(invoke);
                            } else {
                                allocator.getMainMemBlock().offerMessage(invoke);
                            }
                        } else {
                            //invokeTime大于当前时间，不在本时刻入队
                            latestInvokeTime = invokeTime;
                            oldRecord = str;
                            break;
                        }
                    }

                }

                //4.接下来从消息队列中取出调用消息，进行处理
                if(this.policy == Policy.DSMP || this.policy == Policy.SSMP){
                    //处理seperated blocks
                    for (MemoryBlock block: allocator.getSeperatedMemBlocksMap().values()) {
                        pollMessageAndInvoke(block, currentTime);
                    }
                }
                //处理main block
                pollMessageAndInvoke(allocator.getMainMemBlock(),currentTime);

                //5.记录每分钟内存的整体占用情况以及高频函数的内存占用情况
                for (String name :highCostMemPerMinRecordMap.keySet()) {
                    //记录总体占用
                    if(Objects.equals(name, ALL_NAME)){
                        int memUsed = 0;
                        if(this.policy == Policy.DSMP || this.policy == Policy.SSMP){
                            for (MemoryBlock block: allocator.getSeperatedMemBlocksMap().values()) {
                                memUsed += block.getMemUsed();
                            }
                        }
                        memUsed += allocator.getMainMemBlock().getMemUsed();
                        PerMinMemRecord record = highCostMemPerMinRecordMap.get(ALL_NAME);
                        record.setMemAtMilliSec(currentTime,memUsed);
                    } else {
                        //记录高频占用
                        PerMinMemRecord record = highCostMemPerMinRecordMap.get(name);
                        Function function = nameToFunctionMap.get(name);
                        int mem  = function.getContainerNum() * function.getMemSize();
                        record.setMemAtMilliSec(currentTime,mem);

                    }
                }

                currentTime++;
            }
            //将container num置0，用于连续的下次模拟
            for (String name :nameToFunctionMap.keySet()) {
                nameToFunctionMap.get(name).setContainerNum(0);
            }

            System.out.println();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //6.记录所有调用结果与container移除数量表
        CSVUtil.writeSimulationResults(this.invokeResPath,this.invokeResultRecordMap);
        //CSVUtil.writeContainerRecords(this.containerResPath,this.containerRecordMap);

        highCostPerMinInvokeRecordMap.put(ALLPerMinInvokeRecord.getName(), ALLPerMinInvokeRecord);
        CSVUtil.writeSimulationResultsPerMinute(this.perMinuteRecordPath,this.highCostPerMinInvokeRecordMap);
        CSVUtil.writeMemRecordPerMin(this.memUsedPerMinRecordPath,this.highCostMemPerMinRecordMap);
    }

    /**
     * 从block的消息队列中取出消息并进行处理,进行以下步骤：
     * 1.移除超出队列长度的消息
     * 2.删除过期消息
     * 3.处理消息直到遇到不成功的调用，不成功的调用留到下一次时间戳
     * @param block 内存块
     * @param time 调用时间
     */
    private void pollMessageAndInvoke(MemoryBlock block, int time){

        //1.先移除超出队列长度的消息
        while (block.getMessageQueueLength() > MemoryBlock.maxQueueLength){
            FunctionInvoke fi = block.pollMessage();
            if(fi != null){
                recordInvoke(fi.getFunctionName(),InvokeStatus.QueueFullDop,time);
            } else {
                return;
            }
            if(block.peekMessage() == null){
                return;
            }
        }

        if(block.getMessageQueueLength() == 0){
            return;
        }

        //2.删除过期消息
        while (time - block.peekMessage().getInvokeTime() > MemoryBlock.messageTTL){
            FunctionInvoke fi = block.pollMessage();
            if(fi != null){
                recordInvoke(fi.getFunctionName(),InvokeStatus.TTLDrop,time);
            } else {
                return;
            }
            if(block.peekMessage() == null){
                return;
            }
        }

        if(block.getMessageQueueLength() == 0){
            return;
        }

        //3.执行invoke
        while (true){
            //获得队首message但不出队
            FunctionInvoke invoke = block.peekMessage();
            if(invoke == null){
                break;
            }
            int invokeTime = invoke.getInvokeTime();
            //队首的invoke时间比当前大,不再invoke（这种情况理论上不会发生）
            if(invokeTime > time){
                break;
            }

            //尝试执行队首invoke
            boolean res = executeInvoke(invoke,time,block);
            if(res){
                //执行成功则删除队首消息，处理下一条消息
                block.pollMessage();
            } else {
                //执行失败则等待下一毫秒去执行
                break;
            }

        }
    }

    /**
     * 执行一次调用，回去寻找container，依据container寻找结果，执行成功则记录调用结果(cold/warm),执行失败则返回false
     *
     * @param invoke 要执行的调用
     * @param time   调用时间
     * @param memoryBlock 调用所在的内存块
     * @return 是否执行成功
     */
    private boolean executeInvoke(FunctionInvoke invoke, int time, MemoryBlock memoryBlock){
        String functionName = invoke.getFunctionName();
        List<Container> containerPool = memoryBlock.getContainerPool();
        boolean res;

        //1.寻找属于此Function的Warm Container
        List<Container> warmContainerForCurrentFunc = containerPool.stream().filter(container ->
                Objects.equals(container.getFunction().getName(), invoke.getFunctionName())
                        && container.isWarm()).collect(Collectors.toList());
        //找到了warmContainer
        if (warmContainerForCurrentFunc.size() > 0) {
            //TODO:暂时先找到最新的container
            warmContainerForCurrentFunc.sort((o1, o2) -> o1.getLastAccess_t() - o2.getLastAccess_t());
            Container container = warmContainerForCurrentFunc.get(warmContainerForCurrentFunc.size() - 1);
            container.update_WarmToRunning(time);
            recordInvoke(functionName, InvokeStatus.Warm, time);
            res = true;

        } else { //没有warmContainer,尝试生成新的container

            Function function = nameToFunctionMap.get(functionName);
            int memNeeded = function.getMemSize();

            //若剩余内存充足,生成新的container
            if (memoryBlock.getCapacity() - memoryBlock.getMemUsed() >= memNeeded) {
                Container container = new Container(function);
                containerPool.add(container);
                container.update_initRun(time);
                memoryBlock.increaseMemUsed(memNeeded);
                function.increaseContainerNum();
                recordInvoke(functionName, InvokeStatus.Cold, time);
                res = true;
            } else { //内存不足,evict或drop
                //寻找所有非running的container
                List<Container> notRunningContainers = containerPool.stream().filter(Container::isFree).collect(Collectors.toList());

                //所有container都在Running,无法evict,执行失败
                if (notRunningContainers.size() == 0) {
                    res = false;

                } else { //有可驱逐的container，依照LRU策略驱逐
                    boolean evictRes = evictContainer(notRunningContainers, memNeeded, memoryBlock);
                    if (evictRes) {
                        //驱逐成功,生成新的Container
                        Container container = new Container(function);
                        containerPool.add(container);
                        container.update_initRun(time);
                        memoryBlock.increaseMemUsed(memNeeded);
                        function.increaseContainerNum();
                        recordInvoke(functionName, InvokeStatus.Cold, time);
                        res = true;
                    } else {
                        //驱逐失败,执行失败
                        res = false;
                    }
                }
            }
        }

        return res;
    }


    /**
     * 尝试驱逐一个container pool中的container
     *
     * @param notRunningContainers 未在running状态的container，即可以被驱逐的container
     * @param memToSpare           需要腾出的mem空间，若驱逐一个LRU container 后还未满足，则继续驱逐，知道没有可驱逐的container或mem已满足要求
     * @param memoryBlock          container所在的内存块
     * @return 驱逐是否成功
     */
    private boolean evictContainer(List<Container> notRunningContainers, int memToSpare, MemoryBlock memoryBlock) {
        notRunningContainers.sort((o1, o2) -> o2.getLastAccess_t() - o1.getLastAccess_t()); //时间从大到小排序
        while (true) {
            if (notRunningContainers.size() == 0) {
                return false;
            }
            Container oldestContainer = notRunningContainers.get(notRunningContainers.size() - 1);

            int mem = oldestContainer.update_Terminate();
            memoryBlock.decreaseMemUsed(mem);
            Function function = oldestContainer.getFunction();
            function.decreaseContainerNum();

            notRunningContainers.remove(oldestContainer);
            memoryBlock.getContainerPool().remove(oldestContainer);

            this.currentContainerRecord.increaseEvict();

            if (memoryBlock.getCapacity() - memoryBlock.getMemUsed() >= memToSpare) {
                return true;
            }

            /*if(memoryBlock.getCapacity() - memoryBlock.getMemUsed() < memToSpare && memoryBlock.memUsed == 0){
                return false;
            }*/
        }
    }

    /**
     * 记录function的一次调用结果,包含每个函数的调用结果与每分钟的调用结果
     *
     * @param functionName 函数名
     * @param status       调用状态
     */
    private void recordInvoke(String functionName, InvokeStatus status, int time) {
        if (!invokeResultRecordMap.containsKey(functionName)) {
            invokeResultRecordMap.put(functionName, new InvokeResultRecord());
        }
        int timeInMinute = time/60000;
        InvokeResultRecord record = invokeResultRecordMap.get(functionName);
        //InvokeResultPerMinute singleFuncRecordPerMin = allFunctionPerMinRecordsMap.get(functionName);
        switch (status) {
            case Cold:
                record.increaseColdStartTime();
                ALLPerMinInvokeRecord.increaseCold(timeInMinute);
                //singleFuncRecordPerMin.increaseCold(timeInMinute);
                break;
            case Warm:
                record.increaseWarmStartTime();
                ALLPerMinInvokeRecord.increaseWarm(timeInMinute);
                //singleFuncRecordPerMin.increaseWarm(timeInMinute);
                break;
            case QueueFullDop:
                record.increaseQueueDropTime();
                ALLPerMinInvokeRecord.increaseQueueFullDrop(timeInMinute);
                //singleFuncRecordPerMin.increaseQueueFullDrop(timeInMinute);
                break;
            case TTLDrop:
                record.increaseTTLDropTime();;
                ALLPerMinInvokeRecord.increaseTTLDrop(timeInMinute);
                //singleFuncRecordPerMin.increaseTTLDrop(timeInMinute);
        }

        if(highCostFunctionNameList.contains(functionName)){
            PerMinInvokeRecord ir = highCostPerMinInvokeRecordMap.get(functionName);
            switch (status) {
                case Cold:
                    ir.increaseCold(timeInMinute);
                    break;
                case Warm:
                    ir.increaseWarm(timeInMinute);
                    break;
                case QueueFullDop:
                    ir.increaseQueueFullDrop(timeInMinute);
                    break;
                case TTLDrop:
                    ir.increaseTTLDrop(timeInMinute);
            }
        }
    }


    /**
     * 在此毫秒更新容器池中的容器，移除到达TTL的container并将run结束的container状态由running设为warm（(keep_alive)
     * @param memoryBlock 容器池所在的内存块
     * @param currentTime 当前时间戳
     * @param keepAliveTime keep Alive时间
     */
    private void containerPoolUpdate(MemoryBlock memoryBlock, int currentTime, int keepAliveTime){
        List<Container> containerPool = memoryBlock.getContainerPool();
        //container pool中移除所有keepAlive满10min的函数
        containerPool.removeIf(container ->{
                    if(currentTime - container.getKeepAliveStartTime() >= keepAliveTime){
                        this.currentContainerRecord.increaseAutoDie();
                        memoryBlock.decreaseMemUsed(container.getFunction().getMemSize());

                        Function function = container.getFunction();
                        function.decreaseContainerNum();
                        return true;
                    }
                    return false;
                }
        );
        //run结束的function，状态设为warm(keep alive)
        for (Container con : containerPool) {
            if (con.shouldFuncRunFinish(currentTime)) {
                con.update_RunningToWarm();
            }
        }
    }

    /**
     * 对recorder进行初始化
     */
    private void initRecords(){
        highCostMemPerMinRecordMap.put(ALL_NAME,new PerMinMemRecord(ALL_NAME));
        for (String name :highCostFunctionNameList) {
            highCostPerMinInvokeRecordMap.put(name,new PerMinInvokeRecord(name));
            highCostMemPerMinRecordMap.put(name,new PerMinMemRecord(name));
        }
    }


    public int getMemCapacity() {
        return memCapacity;
    }

    public void setMemCapacity(int memCapacity) {
        this.memCapacity = memCapacity;
    }



    public Map<String, Function> getNameToFunctionMap() {
        return nameToFunctionMap;
    }

    public void setNameToFunctionMap(Map<String, Function> nameToFunctionMap) {
        this.nameToFunctionMap = nameToFunctionMap;
    }

    public Map<String, List<Integer>> getPredictionData() {
        return predictionData;
    }

    public void setPredictionData(Map<String, List<Integer>> predictionData) {
        this.predictionData = predictionData;
    }

    public Map<String, InvokeResultRecord> getInvokeResultRecordMap() {
        return invokeResultRecordMap;
    }

    public void setInvokeResultRecordMap(Map<String, InvokeResultRecord> invokeResultRecordMap) {
        this.invokeResultRecordMap = invokeResultRecordMap;
    }

    public String getInvokeRecordsPath() {
        return invokeRecordsPath;
    }

    public void setInvokeRecordsPath(String invokeRecordsPath) {
        this.invokeRecordsPath = invokeRecordsPath;
    }

    public List<String> getHighCostFunctionNameList() {
        return highCostFunctionNameList;
    }

    public void setHighCostFunctionNameList(List<String> highCostFunctionNameList) {
        this.highCostFunctionNameList = highCostFunctionNameList;
    }

    public int getMaxSepMemBlockCapacity() {
        return maxSepMemBlockCapacity;
    }

    public void setMaxSepMemBlockCapacity(int maxSepMemBlockCapacity) {
        this.maxSepMemBlockCapacity = maxSepMemBlockCapacity;
    }

    public Policy getPolicy() {
        return policy;
    }

    public void setPolicy(Policy policy) {
        this.policy = policy;
    }
}