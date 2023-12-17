import gym
import numpy as np
from gym.envs.classic_control import rendering

class KnapsackEnvironment(gym.Env):
    """
    Custom environment for the Knapsack problem.
    """
    def __init__(self):
        super(KnapsackEnvironment, self).__init__()
        self.viewer = None
        self.items = [(2, 3), (3, 4), (4, 5), (5, 6), (4, 3), (7, 12), (3, 3), (2, 2)]
        self.min_item_weight = 2
        self.max_capacity = 8
        self.state_dimension = len(self.items)
        self.action_space = np.arange(self.state_dimension)  # Action space representing item indices
        self.is_repeat_action = False

    def step(self, action):
        """
        Perform an action in the environment.
        """
        if self.state[action] == 1:  # Selected item already in the knapsack
            weight_sum = sum(self.state[i] * self.items[i][0] for i in range(self.state_dimension))
            reward = -30
            is_terminal = weight_sum > self.max_capacity
            next_state = self.state
        else:
            self.state[action] = 1  # Add item to the knapsack
            weight_sum = sum(self.state[i] * self.items[i][0] for i in range(self.state_dimension))
            if weight_sum > self.max_capacity:
                reward = -30
                is_terminal = True
            else:
                reward = sum(self.state[i] * self.items[i][1] for i in range(self.state_dimension))
                is_terminal = False

            next_state = self.state

        if weight_sum + self.min_item_weight > self.max_capacity:
            is_terminal = True

        return next_state, reward, is_terminal, self.is_repeat_action

    def reset(self):
        """
        Reset the environment to the initial state.
        """
        self.state = np.zeros(self.state_dimension, dtype=int)
        return self.state

    def render(self, mode='human'):
        """
        Render the environment for visualization.
        """
        if self.viewer is None:
            self.viewer = rendering.Viewer(300, 200)
            # Add rendering elements here (if necessary)

        # Rendering logic (if applicable)
        # ...

        return self.viewer.render(return_rgb_array=mode == 'rgb_array')

    def close(self):
        """
        Close the environment and clean up resources.
        """
        if self.viewer:
            self.viewer.close()
            self.viewer = None
