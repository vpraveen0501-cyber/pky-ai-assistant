import os
import logging
import json
import paho.mqtt.client as mqtt
from typing import Optional

logger = logging.getLogger(__name__)

class MQTTService:
    def __init__(self):
        self.broker_host = os.getenv("MQTT_BROKER", "localhost")
        self.broker_port = int(os.getenv("MQTT_PORT", 1883))
        self.client = mqtt.Client()
        
    def connect(self):
        try:
            self.client.connect(self.broker_host, self.broker_port, 60)
            self.client.loop_start()
            logger.info(f"Connected to MQTT Broker at {self.broker_host}")
        except Exception as e:
            logger.error(f"Failed to connect to MQTT: {e}")

    def publish(self, topic: str, message: Any):
        """Publishes a message to an MQTT topic."""
        try:
            if isinstance(message, (dict, list)):
                payload = json.dumps(message)
            else:
                payload = str(message)
            
            self.client.publish(topic, payload)
            logger.info(f"MQTT Publish: [{topic}] -> {payload}")
        except Exception as e:
            logger.error(f"MQTT Publish failed: {e}")

    def control_device(self, device_id: str, action: str):
        """Controls a smart home device via Home Assistant MQTT discovery/topic convention."""
        topic = f"homeassistant/switch/{device_id}/set"
        payload = "ON" if action.lower() in ["on", "start", "enable"] else "OFF"
        self.publish(topic, payload)

    def cleanup(self):
        self.client.loop_stop()
        self.client.disconnect()
        logger.info("MQTT cleanup complete.")
