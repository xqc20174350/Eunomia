from kafka import KafkaConsumer
import json
import requests

KAFKA_BROKER = 'localhost:9092'
TOPIC = 'function_requests'
OPENWHISK_API_URL = 'http://localhost:3233/api/v1/namespaces/default/actions/myFunction'  # OpenWhisk Action URL

# 创建 Kafka 消费者
consumer = KafkaConsumer(
    TOPIC,
    bootstrap_servers=KAFKA_BROKER,
    value_deserializer=lambda x: json.loads(x.decode('utf-8')),
    auto_offset_reset='earliest',
    enable_auto_commit=True,
    group_id='eunomia-group'
)

for message in consumer:
    data = message.value
    function_id = data['functionId']
    slo = data['SLO']
    payload = data['payload']

    # 将请求转发到 OpenWhisk
    response = requests.post(OPENWHISK_API_URL, json=payload)
    print(f'Sent request for {function_id} with SLO {slo}: {response.status_code}')
