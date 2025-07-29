import time
import json

def web_application(event):
    start = time.time()
    
    # Simulating request processing
    request_data = event.get('data', {})
    
    # Process the request (for example, simulate a delay)
    time.sleep(0.1)  # Simulating processing time, e.g., a database call or computation
    
    # Here you can add more processing logic as needed
    response_data = {"message": "Processed successfully", "input": request_data}
    
    # Calculate latency
    latency = time.time() - start
    
    return {
        "latency": latency,
        "response": response_data,
        "status": "success"
    }

def main(event):
    latencies = {}
    timestamps = {}
    timestamps["starting_time"] = time.time()
    
    # Call the web_application function
    result = web_application(event)
    
    latencies["function_execution"] = result["latency"]
    timestamps["finishing_time"] = time.time()
    
    return {
        "latencies": latencies,
        "timestamps": timestamps,
        "response": result["response"]
    }
