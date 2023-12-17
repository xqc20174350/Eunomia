import os
import pandas as pd
from multiprocessing import Pool
#from LambdaData import *
import pickle
from math import ceil


#每个function的mem为平均值/同一app数，trace中的mem为applicationlevel，在此每个app中的function均分mem
def divvied_by_func_num(row):
    return ceil(row["AverageAllocatedMb"] / group_by_app[row["HashApp"]])

#"1","2","3",...,"1440"
buckets = [str(i) for i in range(1, 1441)]

datapath = "C:\\Users\\Administrator\\Desktop\\Cloud\\Azure2019Data\\Azure2019"
durations = "function_durations_percentiles.anon.d01.csv"
invocations = "invocations_per_function_md.anon.d01.csv"
mem_fnames = "app_memory_percentiles.anon.d01.csv"


# 1.读取并处理duration
file = os.path.join(datapath, durations.format(1))
durations = pd.read_csv(file)
durations.index = durations["HashFunction"] #以HashFunction作为index
durations = durations.drop_duplicates("HashFunction") #去重
group_by_app = durations.groupby("HashApp").size() #按照HashApp group，并返回各组的size


#2.处理invocation
file = os.path.join(datapath, invocations.format(1))
invocations = pd.read_csv(file)
invocations = invocations.dropna()
invocations.index = invocations["HashFunction"]
sums = invocations.sum(axis=1) #对每一行求和，得到总调用次数
invocations = invocations[sums > 1] # action must be invoked at least twice
invocations = invocations.drop_duplicates("HashFunction")


#3.连接invocation和duration
joined = invocations.join(durations, how="inner", lsuffix='', rsuffix='_durs')

#4.处理memory
file = os.path.join(datapath, mem_fnames.format(1))
memory = pd.read_csv(file)
memory = memory.drop_duplicates("HashApp")
memory.index = memory["HashApp"]
new_mem = memory.apply(divvied_by_func_num, axis=1, raw=False, result_type='expand') #对每行进行计算，memory只保留平均值
memory["divvied"] = new_mem

#5.连接三个表
joined = joined.join(memory, how="inner", on="HashApp", lsuffix='', rsuffix='_mems')
joined = joined.T.drop_duplicates().T.reindex() #去除重复的列
joined.index = joined["HashApp"]
joined.to_csv("C:\\Users\\Administrator\\Desktop\\Cloud\\combined.csv") #生成组合后的csv



#生成函数调用data frame
def generate_invoke_df():
    df = pd.DataFrame(columns=["name","time"])
    count = 0
    for row_index, row in joined.iterrows():
        print(count)
        if count > 2000:
            break
        
        func_name = row["HashFunction"]
        trace = list()
        #遍历1440分钟 minute：第几分钟， invokeCount：此分钟调用次数
        for minute, invokeCount in enumerate(row[buckets]):
            start = minute * 60 * 1000
            if invokeCount == 0:
                continue
            elif invokeCount == 1:
                df.loc[len(df.index)] = [func_name, start]
            else:
                gap = int(60000/invokeCount)
                for i in range(invokeCount):
                    df.loc[len(df.index)] = [func_name, start + i * gap] #调用一次则为此分钟初始时调用，多次此分钟均分
        count = count + 1
    return df




# df = df.sort_values(by="time",ascending=True) #按时间大小排序
# df = df.reindex()
# df.to_csv("E:\\data\\sorted.csv")





