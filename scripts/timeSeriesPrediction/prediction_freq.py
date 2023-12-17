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
high_cost_function_names = ['302d008d9ce2416f673edbf4947568af7ad0bda43625e60b9495a8cd3837c14d',
'e79fff2fdf4c23cd6c1e2e09e73bfb29ad15d05fea859eb1a11f603247dd7b2d',
'52df3473cd810e5d9eb4923260c0189b6136da3193253f984fb97bca8e8b3deb',
'26a1acad7557c7c2a530057e1f67cc314fb85bc8cb14d18a773acb904ccebcb7',
'9997866f59da25f7bf81fa2f196ea2fe29ead8e7667b38f7b97baead85a02a7b',
'32716dda51c8da83a50988d9c29cce9d52bbfc5b420b501790431ec691ccd5c4',
'e7cd675a9087f97c9c3f5861ef1c646938f820063f313ac639bd23f80b1463ba',
'0ac2bba85d41212c738939478d6c143a6dcc6b104dc3cb5d7c910a12cc1d1f66']

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

daily_datas = []
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
    if has_csv_data == True:
        data = readDataFromCSV(func_name)
    else:
        data = readDataFromMemory(func_name)
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
