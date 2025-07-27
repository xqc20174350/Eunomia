import numpy as np
import pandas as pd

class OptimizedPoisson:
    def __init__(self, initial_window_size=5, min_window_size=1, max_window_size=60, 
                 alpha=1, beta=0.1, sigma_threshold=1.5):
        self.window_size = initial_window_size
        self.min_window_size = min_window_size
        self.max_window_size = max_window_size
        self.alpha = alpha
        self.beta = beta
        self.sigma_threshold = sigma_threshold
        self.history = []
        self.predictions = []
        self.errors = []

    def update_window_size(self):
        if len(self.history) <= self.window_size:
            return

        # Calculate the standard deviation of the current window
        window_data = self.history[-self.window_size:]
        std_dev = np.std(window_data)

        if std_dev > self.sigma_threshold:
            self.window_size = max(self.min_window_size, int(self.window_size * 0.8))  # Decrease window size
        else:
            self.window_size = min(self.max_window_size, self.window_size + 1)  # Increase window size

    def calculate_weights(self):
        weights = np.array([self.alpha * np.exp(-self.beta * i) for i in range(self.window_size)])
        return weights / np.sum(weights)  # Normalize weights

    def predict_lambda(self):
        if len(self.history) < self.window_size:
            return np.mean(self.history) if self.history else 0  # Default to 0 if no data

        self.update_window_size()
        window_data = self.history[-self.window_size:]
        weights = self.calculate_weights()

        predicted_lambda = np.dot(weights, window_data)
        self.predictions.append(predicted_lambda)
        return predicted_lambda

    def add_data_point(self, data_point):
        self.history.append(data_point)

    def calculate_rmse(self, actual):
        if len(self.predictions) == 0:
            return float('inf')
        return np.sqrt(np.mean((np.array(self.predictions) - np.array(actual)) ** 2))

    def reset(self):
        self.history.clear()
        self.predictions.clear()
        self.errors.clear()
