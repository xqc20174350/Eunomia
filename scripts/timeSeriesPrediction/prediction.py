import pandas as pd
import os
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import numpy as np
from prophet import Prophet
# datapath = "C:\\Users\\Administrator\\Desktop\\Cloud\\Azure2019Data\\Azure2019\\"
datapath = "D:\\EdgeDownloads\\azurefunctions-dataset2019\\"
invocation_prefix = "invocations_per_function_md.anon"
suffixes = [".d01.csv",".d02.csv",".d03.csv",".d04.csv",".d05.csv",".d06.csv",
            ".d07.csv",".d08.csv",".d09.csv",".d10.csv",".d11.csv",".d12.csv"]
high_cost_function_names = ["f1de419dc75ea0f629deaf936e0b65934cbf2bc444ffd7b3116e3a19dd108f11",
                            "5608f70ad5c4f89e83b01f37bdabd7b89f79338b34776128d68676d83cd3de15",
                            "6dc157fa79f808bd7cf577af09efaee2dad035d658e549d4219e705a181d8917",
                            "3a7e7d0856fa781c7b04c5c45fb622a8eb794a0f4b84b64807c111fa8e423d22",
                            "f4dc04b1dd73316e1b916468f43a0327467320152c4d1be823279fcf2b1621cc"]

# pick_res_path = "D:\\data\\representative\\highcost_invocations"
# prediction_res_path = "D:\\data\\representative\\prediction_results"
pick_res_path = "E:\\asplos_data\\frequent\\highcost_invocations"
prediction_res_path = "E:\\asplos_data\\frequent\\prediction_results"
has_csv_data = True


#获取所有highCost函数一天的数据
def get_one_day_data(path:str, backup:pd.DataFrame):
    invocations = pd.read_csv(path)
    invocations = invocations.dropna()
    invocations.index = invocations["HashFunction"]
    functions = []
    notFoundFuncs = []
    for name in high_cost_function_names:
        if name in invocations.index.values:
            functions.append(name)
        else:
            notFoundFuncs.append(name)
    all_func_data = invocations.loc[functions]
    notFoundData = backup.loc[notFoundFuncs]
    all_func_data:pd.DataFrame = pd.concat([all_func_data,notFoundData])
    return all_func_data

#从csv中读取数据，将一个函数12天的数据全部存放在一个list中返回
def readDataFromCSV(name:str):
    vals = []
    for suffix in suffixes:
        one_day_data_path = os.path.join(pick_res_path,"invocations"+suffix)
        df = pd.read_csv(one_day_data_path)
        df.index = df["HashFunction"]
        val = df.loc[name,"1":"1440"]
        val = val.values.tolist()
        vals = np.append(vals,val)
    return vals

#从内存df数组中读取数据，将一个函数12天的数据全部存放在一个list中返回
def readDataFromMemory(name:str, dfs:list[pd.DataFrame]):
    vals = []
    for df in dfs:
        val = df.loc[name,"1":"1440"]
        val = val.values.tolist()
        vals = np.append(vals,val)
    return vals

#存储上一天的数据。如果某天某函数数据缺失，采用第一天的数据作为备份
#在数据集中第六天3a7e函数数据缺失，这里使用第一天的数据作为替代
backup_data = pd.read_csv(os.path.join(datapath,invocation_prefix+suffixes[0]))
backup_data.dropna()
backup_data.index = backup_data["HashFunction"]

daily_datas = list[pd.DataFrame]
if has_csv_data == False:
    #1.挑出每天高频函数的invocation信息并存储在csv中 TODO:可以放在内存中，不做CSV保存
    for day_suffix in suffixes:
        file_path = os.path.join(datapath, invocation_prefix+day_suffix)
        res_path = os.path.join(pick_res_path,"invocations"+day_suffix)
        data_for_a_day = get_one_day_data(file_path,backup_data)
        daily_datas.append(data_for_a_day)
        data_for_a_day.to_csv(res_path)

columns = []
columns.append("HashFunction")
for i in range(1,1441): columns.append(i)
    
predictions = pd.DataFrame(columns=columns)
for func_name in high_cost_function_names:
    data = readDataFromCSV(func_name)
    # 将数据转换为pandas的时间序列,假设第一天为2019-03-09
    dates = pd.date_range('2019-03-09', periods=len(data), freq='min')
    #series = pd.Series(data=data)
    df = pd.DataFrame({'ds':dates,'y':data})
    model = Prophet()
    model.fit(df)
    future = model.make_future_dataframe(periods=24*60, freq='min', include_history=False)

    # 使用模型预测第13天中每分钟的数据
    forecast = model.predict(future)
    
    #存储预测结果
    forecast_res = forecast[['ds','yhat','yhat_lower','yhat_upper']]
    forecast_res['yhat'] = forecast_res['yhat'].astype(int)
    forecast_res['yhat_lower'] = forecast_res['yhat_lower'].astype(int)
    forecast_res['yhat_upper'] = forecast_res['yhat_upper'].astype(int)
    forecast_res.to_csv(os.path.join(prediction_res_path, func_name+'.csv'))
    
    yhat_data:list = forecast_res["yhat"].values.tolist()
    upper_data:list = forecast_res["yhat_upper"].values.tolist()
    for i in range(len(yhat_data)):
        if yhat_data[i] < 0:
            yhat_data[i] = 0
    yhat_data = [int((i+j)/2) for i,j in zip(yhat_data,upper_data)]
    yhat_data.insert(0,func_name)
    predictions.loc[len(predictions)] = yhat_data
    #predictions = predictions.append(pd.DataFrame([yhat_data]),ignore_index = True)
    
    # 打印预测结果
    #print(forecast[['ds', 'yhat']].tail())

    fig, ax = plt.subplots(figsize=(12, 6))
    ax.set_title(func_name)
    model.plot(forecast, ax=ax)

predictions.to_csv(os.path.join(prediction_res_path, "predictions.csv"),index=0)

# 显示图表
plt.show()
