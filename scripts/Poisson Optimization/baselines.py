import numpy as np
import pandas as pd

class SimplePoisson:
    def __init__(self):
        self.lambda_ = 0

    def fit(self, data):
        self.lambda_ = np.mean(data)

    def predict(self):
        return self.lambda_

class NormalDistribution:
    def __init__(self):
        self.mean = 0
        self.std = 1

    def fit(self, data):
        self.mean = np.mean(data)
        self.std = np.std(data)

    def predict(self):
        return np.random.normal(self.mean, self.std)

class LogNormalDistribution:
    def __init__(self):
        self.mean = 0
        self.std = 1

    def fit(self, data):
        self.mean = np.mean(np.log(data))
        self.std = np.std(np.log(data))

    def predict(self):
        return np.random.lognormal(self.mean, self.std)

class GammaDistribution:
    def __init__(self):
        self.shape = 1
        self.scale = 1

    def fit(self, data):
        self.shape, self.scale = np.histogram(data, bins=30, density=True)

    def predict(self):
        return np.random.gamma(self.shape, self.scale)
