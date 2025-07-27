import matplotlib.pyplot as plt

# Load data from CSV
data = pd.read_csv('modifiedfunc.csv')
arrival_rates = data['arrival_rate'].values 

# Initialize models
optimized_poisson = OptimizedPoisson()
simple_poisson = SimplePoisson()
normal_dist = NormalDistribution()
log_normal_dist = LogNormalDistribution()
gamma_dist = GammaDistribution()

# Fit models
optimized_poisson.history = list(arrival_rates)  # Add historical data
optimized_poisson.predict_lambda()  # Get prediction

simple_poisson.fit(arrival_rates)
normal_dist.fit(arrival_rates)
log_normal_dist.fit(arrival_rates)
gamma_dist.fit(arrival_rates)

# Make predictions
pred_optimized = optimized_poisson.predict_lambda()
pred_simple = simple_poisson.predict()
pred_normal = normal_dist.predict()
pred_log_normal = log_normal_dist.predict()
pred_gamma = gamma_dist.predict()


