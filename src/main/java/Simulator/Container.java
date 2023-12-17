package Simulator;

import Simulator.Enums.ContainerState;

import static Simulator.Enums.ContainerState.*;

/**
 * 容器实体类
 */
public class Container {
    /**
     * 容器最近的被访问时间
     */
    private int lastAccessTime;
    /**
     * 容器被预热的时间
     */
    private int preWarmedTime;
    /**
     * 容器开始进行keep alive 的时间
     */
    private int keepAliveStartTime;
    /**
     * 容器状态
     */
    private ContainerState state = Cold;
    /**
     * 容器中的函数
     */
    private final Function function;

    public Container(Function function){
        this.function = function;
    }

    public void update_initPreWarm(int time){
        this.state = Warm;
        this.keepAliveStartTime = time;
    }

    /**
     * 更新容器状态为启动后立即运行
     * @param time 毫秒时间戳
     */
    public void update_initRun(int time){
        this.state = Running;
        int executeTime = this.function.getColdRunTime();
        this.lastAccessTime = time;
        this.keepAliveStartTime = time + executeTime;
    }

    /**
     * 更新容器状态 warm -> running
     * @param time 毫秒时间戳
     */
    public void update_WarmToRunning(int time){
        this.state = Running;
        int executeTime = this.function.getWarmRunTime();
        //int memToOccupy = this.function.getMemSize();
        this.lastAccessTime = time;
        this.keepAliveStartTime = time + executeTime;
    }

    /**
     * 更新容器状态 running -> warm
     */
    public void update_RunningToWarm(){
        this.state = Warm;
    }

    /**
     * 更新容器状态为结束
     */
    public int update_Terminate(){
        //TODO:可以保留一个对象池
        return this.getFunction().getMemSize();
    }

    /**
     * 判断一个容器中的函数此时刻是否运行结束
     * @param currentTime 毫秒时间戳
     * @return boolean
     */
    public boolean shouldFuncRunFinish(int currentTime){
        return currentTime >= this.keepAliveStartTime;
    }

    /**
     * 判断容器是否空闲，即容器是否为warm状态
     * @return boolean
     */
    public boolean isFree(){
        return this.state == Warm ;
    }

    /**
     * 判断容器是否warm
     * @return boolean
     */
    public boolean isWarm(){
        return this.state == Warm; // || this.state == Cold
    }

    /**
     * 判断容器是否在执行函数运行
     * @return boolean
     */
    public boolean isRunning(){
        return this.state == Running;
    }

    public Function getFunction() {
        return function;
    }

    public int getLastAccess_t() {
        return lastAccessTime;
    }

    public void setLastAccess_t(int lastAccess_t) {
        this.lastAccessTime = lastAccess_t;
    }

    public int getPreWarmedTime() {
        return preWarmedTime;
    }

    public void setPreWarmedTime(int preWarmedTime) {
        this.preWarmedTime = preWarmedTime;
    }

    public int getKeepAliveStartTime() {
        return keepAliveStartTime;
    }

    public void setKeepAliveStartTime(int keepAliveStartTime) {
        this.keepAliveStartTime = keepAliveStartTime;
    }

    public ContainerState getState() {
        return state;
    }

    public void setState(ContainerState state) {
        this.state = state;
    }
}
