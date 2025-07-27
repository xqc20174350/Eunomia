from flask import Flask, request, jsonify
from kafka import KafkaProducer, KafkaConsumer
import json
import time

import logging


# 设置日志
logging.basicConfig(level=logging.INFO)

@app.route('/monitor', methods=['GET'])
def monitor():
    # 返回当前的并发池状态和排队信息
    return jsonify({
        "status": "running",
        "concurrency_pools": concurrency_pools,
        "request_queue_length": len(request_queue)
    })



app = Flask(__name__)

# Kafka 配置
KAFKA_BROKER = 'localhost:9092'  # Kafka 的地址
producer = KafkaProducer(bootstrap_servers=KAFKA_BROKER, value_serializer=lambda v: json.dumps(v).encode('utf-8'))

@app.route('/request', methods=['POST'])
def handle_request():
    data = request.json
    function_id = data['functionId']
    slo = data['SLO']
    payload = data['payload']

    # 将请求发送到 Kafka
    producer.send('function_requests', {'functionId': function_id, 'SLO': slo, 'payload': payload})
    producer.flush()  # 确保消息已发送

    return jsonify({"status": "Request queued", "functionId": function_id})

@app.route('/status', methods=['GET'])
def status():
    return jsonify({"status": "running"})

if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0')  # 使服务可从外部访问

