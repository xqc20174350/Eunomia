import org.apache.commons.math3.special.Erf; // 用于计算误差函数

public class QueueModel {
    private double lambda; // 预测的到达率
    private double mu;     // 单个容器实例的服务率
    private int s;        // 容器实例的数量

    public QueueModel(double lambda, double mu, int s) {
        this.lambda = lambda;
        this.mu = mu;
        this.s = s;
    }

    // 计算平均等待时间
    public double calculateAverageWaitingTime() {
        double trafficIntensity = lambda / (s * mu);
        if (trafficIntensity >= 1) {
            throw new IllegalArgumentException("System is unstable: Traffic intensity must be less than 1.");
        }
        
        // Erlang C 公式
        double c = erlangC(s, lambda / mu);
        return c / (s * mu - lambda);
    }

    // 计算请求必须排队的概率
    public double calculateQueueingProbability() {
        return erlangC(s, lambda / mu);
    }

    // 计算等待时间超过 t 的概率
    public double calculateTailWaitingProbability(double t) {
        double queueingProb = calculateQueueingProbability();
        return queueingProb * Math.exp(-(s * mu - lambda) * t);
    }

    // Erlang C 公式实现
    private double erlangC(int s, double trafficIntensity) {
        double numerator = Math.pow(trafficIntensity, s) / factorial(s);
        double denominator = 0.0;
        
        for (int n = 0; n < s; n++) {
            denominator += Math.pow(trafficIntensity, n) / factorial(n);
        }
        
        denominator += numerator / (1 - trafficIntensity);
        return numerator / denominator;
    }

    // 计算阶乘
    private double factorial(int n) {
        if (n == 0) return 1;
        double result = 1;
        for (int i = 1; i <= n; i++) {
            result *= i;
        }
        return result;
    }

    // Getters and setters
    public double getLambda() {
        return lambda;
    }

    public void setLambda(double lambda) {
        this.lambda = lambda;
    }

    public double getMu() {
        return mu;
    }

    public void setMu(double mu) {
        this.mu = mu;
    }

    public int getS() {
        return s;
    }

    public void setS(int s) {
        this.s = s;
    }
}
