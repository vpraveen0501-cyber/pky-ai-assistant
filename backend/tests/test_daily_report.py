#!/usr/bin/env python3
"""
Test script for the daily report endpoint.
"""
import requests
import json

def test_daily_report():
    """Test the daily report endpoint."""
    try:
        response = requests.get("http://localhost:8000/reports/daily")
        print(f"Daily Report: {response.status_code}")
        if response.status_code == 200:
            data = response.json()
            print(json.dumps(data, indent=2))
            return True
        else:
            print(f"Error: {response.text}")
            return False
    except Exception as e:
        print(f"Daily report test failed: {e}")
        return False

if __name__ == "__main__":
    print("Testing PKY AI Assistant Daily Report Endpoint...")
    test_daily_report()