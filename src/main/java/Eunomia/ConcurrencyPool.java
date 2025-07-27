package Eunomia;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * pool entity
 */
public class ConcurrencyPool {
    /**
     * mem pool
     */
    private int capacity;
    /**
     * used mem
     */
    private int memUsed = 0;
    /**
     * 
     */
    private String functionNameForThisPool = null;
    /**
     * 内存块中的最大消息队列长度
     */
    public static int maxQueueLength = 100000;// a queue can contain at most 100,000 messages
    /**
     * message timeout
     */
    public static int messageTTL = 10; //the longest time a message can unit: ms


    // 为指定函数创建并发池
    // public void createConcurrencyPool(String functionId, int initialSize) {
    //     if (!ConcurrencyPool.containsKey(functionId)) {
    //         ConcurrencyPool.put(functionId, new FunctionConcurrencyPool(functionId, initialSize));
    //     }
    // }

    // 获取指定函数的并发池
    // public ConcurrencyPool getConcurrencyPool(String functionId) {
    //     return ConcurrencyPool.get(functionId);
    // }

    // 动态调整指定函数的并发池大小
    // public void adjustConcurrencyPoolSize(String functionId, int newSize) {
    //     ConcurrencyPool pool = getConcurrencyPool(functionId);
    //     if (pool != null) {
    //         pool.setSize(newSize);
    //     }
    // }

    /**
     * container pool
     */
    private List<Container> containerPool = new ArrayList<>();
    /**
     * message queue
     */
    private Queue<FunctionInvoke> messageQueue = new LinkedList<>();

    public ConcurrencyPool(int capacity) {
        this.capacity = capacity;
    }

    public ConcurrencyPool(int capacity, String functionNameForThisPool) {
        this.capacity = capacity;
        this.functionNameForThisPool = functionNameForThisPool;
    }

    /**
     * increase memory
     * @param increase increasement
     */
    public void increaseMemUsed(int increase) {
        this.memUsed += increase;
        if(memUsed > capacity){
            memUsed = capacity;
        }
    }

    /**
     * decrease memory
     * @param decrease decrease ment
     */
    public void decreaseMemUsed(int decrease){
        this.memUsed -= decrease;
        if(memUsed < 0){
            memUsed = 0;
        }
    }

    /**
     * message queue
     * @param invoke queued message
     */
    public void offerMessage(FunctionInvoke invoke){
        this.messageQueue.offer(invoke);
    }

    /**
     * queue info
     * @return queue top
     */
    public FunctionInvoke peekMessage(){
        return this.messageQueue.peek();
    }

    /**
     * dequeue
     * @return dequeued message
     */
    public FunctionInvoke pollMessage(){
        return this.messageQueue.poll();
    }

    public int getMessageQueueLength(){
        return this.messageQueue.size();
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getMemUsed() {
        return memUsed;
    }

    public void setMemUsed(int memUsed) {
        this.memUsed = memUsed;
    }

    public List<Container> getContainerPool() {
        return containerPool;
    }

    public void setContainerPool(List<Container> containerPool) {
        this.containerPool = containerPool;

    }
}
