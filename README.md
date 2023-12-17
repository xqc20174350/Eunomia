# Incendio Simulator

## Overview

Main components

* FaaS system simulator with a single invoker.
* Part of the intermediate data generated during the paper's experiments.
* Code for data processing and visualization.

## Simulator Usage Process

<br>

### 一. Generate Input Data

#### 1.Dataset Acquisition

First, obtain the publicly available dataset provided by Azure on GitHub. The simulator uses the Azure dataset from 2019. [Click here](https://github.com/Azure/AzurePublicDataset/blob/master/AzureFunctionsDataset2019.md) to download and view relevant information.

Azure provides a complete 12 days' worth of data. In the experimental process, the data from the first day is primarily used. To process simulator input data, use the Python scripts in the `scripts` folder.  **Before calling the scripts, manually change the paths for input data and script output data in each script** .

#### 2.Function Sampling

Start by calling the combine script, which merges the `mem`, `invocation`, and `duration` tables from the first day into a single table.

Next, use the `generate_func` script. This script samples functions from the `combine.csv` file. The script provides three sampling methods: uniform sampling, sampling low-frequency functions, and sampling high-frequency functions. These correspond to the three workloads used in the paper. Readers can modify the last line of the script to determine the sampling method and quantity.

In the `generate_func` script, after sampling, the `generate_invoke_df()` function is called by default. This Python function generates the invocation file for the first day using the sampled function dataset. The invocation file `invokes.csv` represents each call record, sorted in ascending order by milliseconds of invocation.  *Note that if the sampled functions are called too frequently, resulting in a large number of calls in a day, `invokes.csv` may have millions of rows, leading to a file of several tens of gigabytes* . Additionally, this process is time-consuming. If readers only want to sample functions simply, comment out the call to `generate_invoke_df()` in the script.

#### 3.Results

The files obtained after sampling, `functions.csv` and `invokes.csv`, serve as the sampled function database and invocation table, which the simulator will read for simulation.

<br>

### 二. Generate Time Series Prediction Results

In the paper, when employing dynamic strategy (DSMP), time series prediction techniques are used to predict high-frequency functions. The scripts in the `timeSeriesPrediction` folder can be used for time series prediction of functions. If readers are not attempting dynamic strategy, this step is unnecessary.

Time series prediction requires the complete 12 days of data. Pay attention to modifying and setting the paths. Also, correctly set the hash name of the function to be predicted at the beginning of the script.

During prediction, intermediate files will be generated. These can be ignored, and the `predictions.csv` file generated after prediction is used as input for the simulator.

<br>

### 三. Use the Simulator

The `test.java` file contains an example of using the simulator. Users need to manually set various input and output paths, simulated memory size, simulation strategy, etc., and then call a few functions. The `test` function in the file demonstrates how to simulate once, and the `test2` function demonstrates how to simulate multiple times with different strategies, memory, and timeout settings. Readers can refer to the source code example for experimentation.

<br>

### 四. Simulator Output

After the simulator simulation is complete, basic call result statistics and identified high-frequency functions will be printed on the console. To obtain data such as memory usage and container occupancy, users need to manually add recording classes and record them at the appropriate location in the simulation loop. We have implemented several recording classes in the `Record` package, and users can refer to the source code to add their own.

Various result files corresponding to the simulation will be output after the simulation ends.  **Please pay attention to modifying and adding output paths** .
