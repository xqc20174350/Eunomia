package Simulator.Utils;

import Simulator.Function;

import java.util.List;

/**
 * 函数判断器，目前只用于找出高频高占用函数
 */
public class FunctionJudge {
    public static int memLimit = 100;
    public static int durationLimit = 0;
    public static int invokeCountLimit = 0;
    public static int scoreLimit = 10000;

    public static boolean isMemOccupyHigh(Function function){
        return function.getMemSize() >= memLimit;
    }

    public static boolean isDurationLong(Function function){
        return function.getWarmRunTime() >= durationLimit;
    }

    public static boolean isOftenInvoked(Function function){
        return function.getInvocationCount() >= invokeCountLimit;
    }

    /**
     * 判断函数是否为高占用函数，占用值score计算： 总调用次数 * 内存占用大小
     * 也要结合duration判断，即使调用频率高，占用内存大，但如果执行时间很短，那么反而要放在主内存块中让其他函数将其evict
     * 只有当 平均每分钟调用次数 * duration > 1min时，则有将其放入独立空间的需求
     * @param function 待判断的function
     * @return 是否为高占用
     */
    public static boolean isFunctionCostHigh(Function function){
        int invokeCount = function.getInvocationCount();
        int duration = function.getWarmRunTime();
        int invokePerMinute = invokeCount/1440;

        int oneMinTotalDuration = invokePerMinute * duration;

        boolean isScoreHigh =  function.getScore() >= scoreLimit;
        boolean isDurationLong = oneMinTotalDuration >= 60000; //60s

        return isScoreHigh && isDurationLong;
    }
}
