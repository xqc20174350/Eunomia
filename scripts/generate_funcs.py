import os
import pandas as pd
from multiprocessing import Pool
from math import ceil

# rep_func_path = "D:\\data\\representative\\functions.csv"
# rare_func_path = "D:\\data\\rare\\functions.csv"
# freq_func_path = "D:\\data\\frequent\\functions.csv"
# rep_invoke_path = "D:\\data\\representative\\invokes.csv"
# rare_invoke_path = "D:\\data\\rare\\invokes.csv"
# freq_invoke_path = "D:\\data\\frequent\\invokes.csv"

rep_func_path = "E:\\asplos_data\\representative\\functions.csv"
rare_func_path = "E:\\asplos_data\\rare\\functions.csv"
freq_func_path = "E:\\asplos_data\\frequent\\functions.csv"
rep_invoke_path = "E:\\asplos_data\\representative\\invokes.csv"
rare_invoke_path = "E:\\asplos_data\\rare\\invokes.csv"
freq_invoke_path = "E:\\asplos_data\\frequent\\invokes.csv"
buckets = [str(i) for i in range(1, 1441)]

# datapath = "C:\\Users\\Administrator\\Desktop\\Cloud\\Azure2019Data\\Azure2019\\"
datapath = "D:\\EdgeDownloads\\azurefunctions-dataset2019\\"
durations = "function_durations_percentiles.anon.d01.csv"
invocations = "invocations_per_function_md.anon.d01.csv"
mem_fnames = "app_memory_percentiles.anon.d01.csv"

quantiles = [0.0, 0.25, 0.5, 0.75, 1.0]
quantiles2 = [0.0,0.99,1.0]

#从4个quantile中一共取出num_funcs个函数,用于生成representative数据
def gen_rep_trace(df: pd.DataFrame, num_funcs: int):
    per_qant_func_num = num_funcs // 4
    sums = df["Count"]
    qts = sums.quantile(quantiles)

    chooseRes = pd.DataFrame()
    for i in range(4):
        low = qts.iloc[i]
        high = qts.iloc[i+1]
        choose_from = df[sums.between(low, high)]
        #从每个quantile中随机采样 per_quant_func_num个函数
        chosen = choose_from.sample(per_qant_func_num)
        chooseRes = pd.concat([chooseRes,chosen])
    chooseRes.to_csv(rep_func_path)
    generate_invoke_df(chooseRes,rep_invoke_path)

#从第一个quantile中取函数，得到调用极不频繁的
def gen_rare_trace(df: pd.DataFrame, num_funcs: int):
    sums = df["Count"]
    qts = sums.quantile(quantiles)
    
    low = qts.iloc[0]
    high = qts.iloc[1]
    choose_from = df[sums.between(low, high)]
    chosen = choose_from.sample(num_funcs)
    
    chosen.to_csv(rare_func_path)
    generate_invoke_df(chosen,rare_invoke_path)
    
#从第100份中最后一个quantile中取函数，得到调用极频繁的
def gen_freq_trace(df: pd.DataFrame, num_funcs: int):
    sums = df["Count"]
    qts = sums.quantile(quantiles2)
    
    low = qts.iloc[1]
    high = qts.iloc[2]
    choose_from = df[sums.between(low, high)]
    chosen = choose_from.sample(num_funcs)
    total = chosen['Count'].sum()
    while total > 20000000 and total < 10000000:
        chosen = choose_from.sample(num_funcs)
        total = chosen['Count'].sum()
        print(total)
    print(total)
    chosen.to_csv(freq_func_path)
    generate_invoke_df(chosen, freq_invoke_path)



#生成函数调用data frame
def generate_invoke_df(chooseRes: pd.DataFrame, path:str):
    trace = list()

    for row_index, row in chooseRes.iterrows():     
        func_name = row["HashFunction"]
        #遍历1440分钟 minute：第几分钟， invokeCount：此分钟调用次数
        for minute, invokeCount in enumerate(row[buckets]):
            start = minute * 60 * 1000
            if invokeCount == 0:
                continue
            elif invokeCount == 1:
                trace.append([func_name,start])
            else:
                gap = int(60000/invokeCount)
                for i in range(invokeCount):
                    trace.append([func_name,start + i * gap]) #调用一次则为此分钟初始时调用，多次则此分钟均分
    df = pd.DataFrame(columns=["name","time"],data=trace)
    df = df.sort_values(by="time",ascending=True)
    df.to_csv(path)
    

#整合所有函数数据
def gen_all_funcs():
    global durations
    global invocations
    global memory

    def divive_by_func_num(row):
        return ceil(row["AverageAllocatedMb"] / group_by_app[row["HashApp"]])

    #处理 duration
    file = os.path.join(datapath, durations)
    durations = pd.read_csv(file)
    durations.index = durations["HashFunction"]
    durations = durations.drop_duplicates("HashFunction")

    group_by_app = durations.groupby("HashApp").size()

    #处理invocation
    file = os.path.join(datapath, invocations)
    invocations = pd.read_csv(file)
    invocations = invocations.dropna()
    invocations.index = invocations["HashFunction"]
    sums = invocations.sum(axis=1)
    invocations["InvocationSum"] = sums

    invocations = invocations[sums > 1] # action must be invoked at least twice
    invocations = invocations.drop_duplicates("HashFunction")


    joined = invocations.join(durations, how="inner", lsuffix='', rsuffix='_durs')

    #处理memory
    file = os.path.join(datapath, mem_fnames.format(1))
    memory = pd.read_csv(file)
    memory = memory.drop_duplicates("HashApp")
    memory.index = memory["HashApp"]

    new_mem = memory.apply(divive_by_func_num, axis=1, raw=False, result_type='expand')
    memory["divvied"] = new_mem

    joined = joined.join(memory, how="inner", on="HashApp", lsuffix='', rsuffix='_mems')
    joined = joined.T.drop_duplicates().T.reindex() #去除重复的列
    return joined


# if os.path.exists(func_path):
#     os.remove(func_path)

# if os.path.exists(invoke_path):
#     os.remove(invoke_path)        
# gen_traces(400)

# representative_df = pd.read_csv("C:\\Users\\Administrator\\Desktop\\functions.csv")
# generate_invoke_df(representative_df,rep_invoke_path)

all_funcs = gen_all_funcs()
#gen_rep_trace(400)
#gen_rare_trace(all_funcs,1000)
gen_freq_trace(all_funcs,20)