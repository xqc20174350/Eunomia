package Simulator;

/**
 * 函数调用类，从文件中读取的调用记录会转化为此类
 */
public class FunctionInvoke {
    /**
     * 被调用的函数名
     */
    private String functionName;
    /**
     * 调用时刻，单位：ms
     */
    private int invokeTime;

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public int getInvokeTime() {
        return invokeTime;
    }

    public void setInvokeTime(int invokeTime) {
        this.invokeTime = invokeTime;
    }

    public FunctionInvoke(String functionName, int invokeTime) {
        this.functionName = functionName;
        this.invokeTime = invokeTime;
    }
}

