package Eunomia.Utils;

import Eunomia.ContainerScheduler;
import Eunomia.Enums.Policy;
import Eunomia.Enums.schedulePolicy;
import Eunomia.Function;
import Eunomia.Record.ContainerRecord;
import Eunomia.Record.PerMinInvokeRecord;
import Eunomia.Record.InvokeResultRecord;
import Eunomia.Record.PerMinMemRecord;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CSV工具类，负责处理CSV的读写等操作。完成数据的输入输出。
 */
public class CSVUtil {
    /**
     * functions.csv路径
     */
    private String dataPath;
    //private String intermediateRecordPath;
    /**
     * 预测文件路径
     */
    private String predictionPath;


    private Map<String,Function> nameToFunctionMap = new HashMap<>();
    private List<String> highCostFunctionNameList = new ArrayList<>();
    private Map<String,List<Integer>> highCostFunctionPredictions = new HashMap<>();

    public CSVUtil(String dataPath, String predictionPath) {
        this.dataPath = dataPath;
        //this.intermediateRecordPath = intermediateRecordPath;
        this.predictionPath = predictionPath;
    }

    /**
     * 清理csv util类中存储的数据
     */
    public void clear(){
        this.highCostFunctionPredictions.clear();
        this.highCostFunctionNameList.clear();
        this.nameToFunctionMap.clear();
    }

    /**
     * 从csv中读取函数信息，若参数为true则不生成调用列表(由python脚本负责生成调用list)
     * 只生成Function相关信息
     * @param generateMapOnly 是否只生成函数map
     */
    public void readData(boolean generateMapOnly){
        int iCount = 0;
        int fCount = 0;
        try(BufferedReader br = new BufferedReader(new FileReader(this.dataPath));
            /*BufferedWriter bw = new BufferedWriter(new FileWriter(this.intermediateRecordPath))*/){
            CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().withAllowDuplicateHeaderNames().parse(br);
            /*CSVPrinter printer = new CSVPrinter(bw,CSVFormat.DEFAULT.withHeader("name","time"));*/

            //遍历所有record
            for (CSVRecord record :parser) {
                if(fCount >= 4000 ){
                    //printer.close();
                    break;
                }
                String hashApp = record.get("HashApp");
                String hashFunc = record.get("HashFunction");
                int mem = (int) Float.parseFloat(record.get("divvied"));
                int warmStartTime = (int) Float.parseFloat(record.get("Average"));
                int coldStartTime = (int) Float.parseFloat(record.get("Maximum"));
                int invocationCount = (int) Float.parseFloat(record.get("Count"));

                //生成function
                Function function = new Function(mem,coldStartTime,warmStartTime,hashFunc,hashApp,invocationCount);
                if(!nameToFunctionMap.containsKey(hashFunc)){
                    nameToFunctionMap.put(hashFunc,function);
                }

                //存储高占用的function list
                if(FunctionJudge.isFunctionCostHigh(function)){
                    highCostFunctionNameList.add(hashFunc);
                }


                //是否生成调用list,若generateMapOnly为true,则不生成(由python脚本负责生成调用list)
                /*if(!generateMapOnly){
                    //生成此function的invoke list
                    for (int minute = 1; minute <= 1440 ; minute++) {
                        int startTime = (minute-1) * 60 * 1000;
                        int invokeCount = (int) Float.parseFloat(record.get( minute + "" ));
                        if(invokeCount == 0){
                            continue;
                        } else if(invokeCount == 1){
                            iCount++;
                            printer.printRecord(hashFunc,startTime);
                        } else {
                            int gap = (60*1000)/invokeCount;
                            for (int i = 0; i < invokeCount; i++) {
                                iCount++;
                                printer.printRecord(hashFunc,startTime + i*gap);
                                //invokeList.add(new FunctionInvoke(hashFunc,startTime+i*gap));
                            }
                        }
                    }
                }*/

                fCount++;
            }
            //将function names 按照sore从大到小排序
            highCostFunctionNameList.sort(((name1, name2) -> {
                Function func1 = nameToFunctionMap.get(name1);
                Function func2 = nameToFunctionMap.get(name2);
                int score1 = func1.getScore();
                int score2 = func2.getScore();
                return score2 - score1;

            }));
            System.out.println("high cost func num: " + highCostFunctionNameList.size());
            for (String name : highCostFunctionNameList) {
                System.out.println("high cost func name: " + name);
            }
           /* printer.close();*/


        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * 将数据发送给eunomia
     * @param eunomia 模拟器
     */
    public void sendDataToEunomia(ContainerScheduler eunomia){
        eunomia.setNameToFunctionMap(this.getNameToFunctionMap());
        eunomia.setHighCostFunctionNameList(this.highCostFunctionNameList);
        if(eunomia.getPolicy() == Policy.DSMP){
            eunomia.setPredictionData(this.readPredictionData(this.predictionPath));
        }
    }

    /**
     * 读取预测数据
     * @param predictionPath 预测数据存储路径
     * @return 预测数据map
     */
    public Map<String,List<Integer>> readPredictionData(String predictionPath){
        Map<String,List<Integer>> predictions = new HashMap<>();
        try(BufferedReader br = new BufferedReader(new FileReader(predictionPath))) {
            CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(br);

            //遍历所有record
            for (CSVRecord record : parser) {
                String hashFunc = record.get("HashFunction");
                List<Integer> prediction = new ArrayList<>();
                //读取时时1-1440，使用时为0-1439
                for (int i = 1; i <= 1440; i++) {
                    int count = (int) Float.parseFloat(record.get(i+""));
                    prediction.add(count);
                }
                predictions.put(hashFunc,prediction);

            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return predictions;
    }


    /**
     * 将最基本的模拟记录写到csv中，记录的是每个函数的warm，cold，drop调用次数
     * @param resPath 结果路径
     * @param resMap 结果map
     */
    public static void writeSimulationResults(String resPath, Map<String, InvokeResultRecord> resMap){
        int coldTotalTime = 0;
        int warmTotalTime = 0;
        int queueFullDropTotalTime = 0;
        int ttlDropTotalTime = 0;
        File file = new File(resPath);
        if(file.exists()){
            file.delete();
        }
        try(BufferedWriter bw = new BufferedWriter(new FileWriter(resPath))){
            CSVPrinter printer = new CSVPrinter(bw,CSVFormat.DEFAULT.withHeader("name","warm","cold","queue full drop","ttl drop"));
            for (String name :resMap.keySet()) {
                InvokeResultRecord invokeResultRecord = resMap.get(name);
                int warmCount = invokeResultRecord.getWarmStartCount();
                int coldCount = invokeResultRecord.getColdStartCount();
                int queueFullDropTime = invokeResultRecord.getQueueFullDropCount();
                int ttlDropTime = invokeResultRecord.getTtlDropCount();
                printer.printRecord(name,warmCount,coldCount,queueFullDropTime,ttlDropTime);

                warmTotalTime += warmCount;
                coldTotalTime += coldCount;
                queueFullDropTotalTime += queueFullDropTime;
                ttlDropTotalTime += ttlDropTime;
            }

            System.out.println("total warm/cold/queue full drop/ttl drop count: " + warmTotalTime + "/ " + coldTotalTime + "/ " + queueFullDropTotalTime + "/ " + ttlDropTotalTime);
            System.out.println("total invoke count: " + (warmTotalTime + coldTotalTime + queueFullDropTotalTime + ttlDropTotalTime));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 将container的记录写到csv中，记录的是每个分钟container 被evict或ttl的次数
     * @param resPath 结果路径
     * @param resMap 结果map
     */
    public static void writeContainerRecords(String resPath, Map<Integer, ContainerRecord> resMap){
        File file = new File(resPath);
        if(file.exists()){
            file.delete();
        }
        try(BufferedWriter bw = new BufferedWriter(new FileWriter(resPath))){
            CSVPrinter printer = new CSVPrinter(bw,CSVFormat.DEFAULT.withHeader("minute","auto_die","evict"));
            for (int minute :resMap.keySet()) {
                ContainerRecord record = resMap.get(minute);
                int autoDie = record.getAutoDieCount();
                int evict = record.getEvictCount();
                printer.printRecord(minute,autoDie,evict);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 将高频函数与总和每分钟的模拟记录写到csv中，记录的是每分钟的warm，cold，ttldrop和queue full drop的次数
     * @param resPath 结果路径
     * @param resMap 结果map
     */
    public static void writeSimulationResultsPerMinute(String resPath, Map<String, PerMinInvokeRecord> resMap){
        File file = new File(resPath);
        if(file.exists()){
            file.delete();
        }
        List<String> headerList = new ArrayList<>();
        headerList.add("name");
        headerList.add("state");
        for (int i = 0; i < 1440; i++) {
            headerList.add(String.valueOf(i));
        }
        try(BufferedWriter bw = new BufferedWriter(new FileWriter(resPath))){
            CSVPrinter printer = new CSVPrinter(bw,CSVFormat.DEFAULT.withHeader(headerList.toArray(new String[0])));
            for (String name :resMap.keySet()) {
                PerMinInvokeRecord ir = resMap.get(name);
                List<String> record1 = new ArrayList<>();
                List<String> record2 = new ArrayList<>();
                List<String> record3 = new ArrayList<>();
                List<String> record4 = new ArrayList<>();
                record1.add(ir.getName());
                record1.add("Warm");
                record1.addAll(ir.getWarmList().stream().map(String::valueOf).collect(Collectors.toList()));
                printer.printRecord(record1);


                record2.add(ir.getName());
                record2.add("Cold");
                record2.addAll(ir.getColdList().stream().map(String::valueOf).collect(Collectors.toList()));
                printer.printRecord(record2);


                record3.add(ir.getName());
                record3.add("QueueFullDrop");
                record3.addAll(ir.getQueueFullList().stream().map(String::valueOf).collect(Collectors.toList()));
                printer.printRecord(record3);

                record4.add(ir.getName());
                record4.add("TTLDrop");
                record4.addAll(ir.getTTlList().stream().map(String::valueOf).collect(Collectors.toList()));
                printer.printRecord(record4);
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 将每分钟高频函数的内存占用写进csv，包含平均值，最大值，最小值
     * @param resPath 结果路径
     * @param resMap 结果map
     */
    public static void writeMemRecordPerMin(String resPath, Map<String, PerMinMemRecord> resMap){
        File file = new File(resPath);
        if(file.exists()){
            file.delete();
        }
        List<String> headerList = new ArrayList<>();
        headerList.add("name");
        headerList.add("value");
        for (int i = 0; i < 1440; i++) {
            headerList.add(String.valueOf(i));
        }
        try(BufferedWriter bw = new BufferedWriter(new FileWriter(resPath))){
            CSVPrinter printer = new CSVPrinter(bw,CSVFormat.DEFAULT.withHeader(headerList.toArray(new String[0])));
            for (String name :resMap.keySet()) {
                PerMinMemRecord mr = resMap.get(name);
                List<String> record1 = new ArrayList<>();
                List<String> record2 = new ArrayList<>();
                List<String> record3 = new ArrayList<>();
                record1.add(name);
                record1.add("Average");
                record1.addAll(mr.getAverageList().stream().map(String::valueOf).collect(Collectors.toList()));
                printer.printRecord(record1);

                record2.add(name);
                record2.add("Max");
                record2.addAll(mr.getMaxList().stream().map(String::valueOf).collect(Collectors.toList()));
                printer.printRecord(record2);

                record3.add(name);
                record3.add("Min");
                record3.addAll(mr.getMinList().stream().map(String::valueOf).collect(Collectors.toList()));
                printer.printRecord(record3);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    public Map<String, Function> getNameToFunctionMap() {
        return nameToFunctionMap;
    }

    public void setNameToFunctionMap(Map<String, Function> nameToFunctionMap) {
        this.nameToFunctionMap = nameToFunctionMap;
    }

    public List<String> getHighCostFunctionNameList() {
        return highCostFunctionNameList;
    }

    public void setHighCostFunctionNameList(List<String> highCostFunctionNameList) {
        this.highCostFunctionNameList = highCostFunctionNameList;
    }
}
