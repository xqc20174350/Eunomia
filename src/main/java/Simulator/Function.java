package Simulator;

/**
 * 函数实体类
 */
public class Function {
    /**
     * 函数内存占用
     */
    private int memSize;
    /**
     * 冷启动执行时间
     */
    private int coldRunTime;
    /**
     * 热启动执行时间
     */
    private int warmRunTime;
    /**
     * 一天内总调用次数
     */
    private int invocationCount;
    /**
     * 得分
     */
    private int score;
    /**
     * 所属的app
     */
    private String appName;
    /**
     * 函数名
     */
    private String name;
    /**
     * 属于此函数的容器数量
     */
    private int containerNum = 0;


    public Function(int memSize, int coldRunTime, int warmRunTime, String name, String appName, int invocationCount) {
        this.memSize = memSize;
        this.coldRunTime = coldRunTime;
        this.warmRunTime = warmRunTime;
        this.name = name;
        this.appName = appName;
        this.invocationCount = invocationCount;
        this.score = invocationCount * memSize;
    }

    /**
     * 增加函数容器数量
     */
    public void increaseContainerNum(){
        this.containerNum++;
    }

    /**
     * 减少函数容器数量
     */
    public void decreaseContainerNum(){
        this.containerNum--;
        if (containerNum < 0){
            containerNum = 0;
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getMemSize() {
        return memSize;
    }

    public void setMemSize(int memSize) {
        this.memSize = memSize;
    }

    public int getColdRunTime() {
        return coldRunTime;
    }

    public void setColdRunTime(int coldRunTime) {
        this.coldRunTime = coldRunTime;
    }

    public int getWarmRunTime() {
        return warmRunTime;
    }

    public void setWarmRunTime(int warmRunTime) {
        this.warmRunTime = warmRunTime;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public int getInvocationCount() {
        return invocationCount;
    }

    public void setInvocationCount(int invocationCount) {
        this.invocationCount = invocationCount;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getContainerNum() {
        return containerNum;
    }

    public void setContainerNum(int containerNum) {
        this.containerNum = containerNum;
    }
}
