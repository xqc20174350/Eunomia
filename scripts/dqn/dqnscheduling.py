import gym
import torch
import torch.nn as nn
import torch.nn.functional as F
import numpy as np
import collections
import random
import matplotlib.pyplot as plt
from torch import optim

class DeepQNetwork(nn.Module):
    """
    Deep Q-Network with 4 layers to approximate Q-values in reinforcement learning.
    """
    def __init__(self, num_actions, input_dim, learning_rate=0.01):
        super(DeepQNetwork, self).__init__()
        self.fc1 = nn.Linear(input_dim, 2048)  # First hidden layer
        self.fc2 = nn.Linear(2048, 1024)  # Second hidden layer
        self.fc3 = nn.Linear(1024, 512)   # Third hidden layer
        self.fc4 = nn.Linear(512, num_actions)  # Output layer

        self.optimizer = optim.Adam(self.parameters(), lr=learning_rate)
        self.loss_function = nn.MSELoss()
        self.device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
        self.to(self.device)

    def forward(self, state):
        """
        Forward pass through the network.
        """
        layer1_output = F.relu(self.fc1(state))
        layer2_output = F.relu(self.fc2(layer1_output))
        layer3_output = F.relu(self.fc3(layer2_output))
        action_values = self.fc4(layer3_output)
        return action_values


class ReinforcementAgent:
    """
    Agent that interacts with the environment and learns using DQN.
    """
    def __init__(self, environment, num_actions, state_dimension, gamma=0.99, epsilon=1.0):
        self.environment = environment
        self.num_actions = num_actions
        self.state_dimension = state_dimension
        self.gamma = gamma  # Discount factor
        self.epsilon = epsilon  # Exploration rate
        self.epsilon_min = 0.05
        self.epsilon_decay = 1e-4
        self.learning_iterations = 0
        self.policy_network = DeepQNetwork(num_actions, state_dimension)
        self.target_network = DeepQNetwork(num_actions, state_dimension)
        self.target_network.load_state_dict(self.policy_network.state_dict())
        self.replay_memory = collections.deque(maxlen=10000)
        self.min_replay_memory_size = 100
        self.batch_size = 64
        self.update_target_frequency = 10
        self.episode_rewards = []

    def store_transition(self, transition):
        """
        Store a transition in the replay memory.
        """
        self.replay_memory.append(transition)

    def select_action(self, state, episode_score):
        """
        Select an action using epsilon-greedy policy.
        """
        if np.random.random() > self.epsilon and episode_score >= 0:
            state_tensor = torch.tensor([state], dtype=torch.float32).to(self.device)
            action_values = self.policy_network(state_tensor)
            action = torch.argmax(action_values).item()
        else:
            action = np.random.choice(self.environment.action_space)
        return action

    def learn_from_experience(self):
        """
        Train the network using a batch of experiences from the replay memory.
        """
        if len(self.replay_memory) < self.batch_size:
            return

        self.policy_network.optimizer.zero_grad()
        batch = random.sample(self.replay_memory, self.batch_size)
        states, actions, rewards, next_states, dones = zip(*batch)

        states_tensor = torch.tensor(states, dtype=torch.float32).to(self.device)
        next_states_tensor = torch.tensor(next_states, dtype=torch.float32).to(self.device)
        actions_tensor = torch.tensor(actions).to(self.device)
        rewards_tensor = torch.tensor(rewards).to(self.device)
        dones_tensor = torch.tensor(dones, dtype=torch.bool).to(self.device)

        current_q_values = self.policy_network(states_tensor).gather(1, actions_tensor.unsqueeze(-1)).squeeze(-1)
        next_q_values = self.target_network(next_states_tensor).max(1)[0]
        next_q_values[dones_tensor] = 0.0
        expected_q_values = rewards_tensor + self.gamma * next_q_values

        loss = self.policy_network.loss_function(current_q_values, expected_q_values)
        loss.backward()
        self.policy_network.optimizer.step()

        self.learning_iterations += 1
        if self.learning_iterations % self.update_target_frequency == 0:
            self.target_network.load_state_dict(self.policy_network.state_dict())

       def run_episode(self):
        """
        Run a single episode of interaction with the environment.
        """
        state = self.environment.reset()
        done = False
        episode_reward = 0

        while not done:
            action = self.select_action(state, episode_reward)
            next_state, reward, done, _ = self.environment.step(action)
            self.store_transition((state, action, reward, next_state, done))
            
            episode_reward += reward
            self.learn_from_experience()

            # Update the current state to the next state
            state = next_state

            # Update the epsilon value for exploration-exploitation trade-off
            self.epsilon = max(self.epsilon - self.epsilon_decay, self.epsilon_min)

        self.episode_rewards.append(episode_reward)

    def save_model(self, file_name):
        """
        Save the trained model.
        """
        torch.save(self.policy_network.state_dict(), file_name)


    if __name__ == "__main__":
        # Environment setup and agent creation
        env = gym.make('YourCustomEnv-v0')
        num_actions = env.action_space.n
        state_dim = env.observation_space.shape[0]
        agent = ReinforcementAgent(env, num_actions, state_dim)

        # Training loop
        num_episodes = 2000
        for episode in range(num_episodes):
            agent.run_episode()
            print(f"Episode: {episode}, Reward: {agent.episode_rewards[-1]}")

        # Plotting the rewards
        plt.plot(range(num_episodes), agent.episode_rewards)
        plt.xlabel('Episodes')
        plt.ylabel('Rewards')
        plt.show()

        # Save the trained model
        agent.save_model('dqn_model.pth')
