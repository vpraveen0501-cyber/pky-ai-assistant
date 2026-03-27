#!/usr/bin/env python3
"""
Async integration tests for the PKY AI Assistant backend.

Requirements:
  pip install pytest pytest-anyio httpx

Usage:
  PKY_AI_ADMIN_PASSWORD=<password> pytest tests/test_backend.py -v

The server must already be running at http://localhost:8000.
"""
import os
import pytest
import httpx

BASE_URL = "http://localhost:8000"


@pytest.fixture(scope="session")
def auth_token():
    """Obtain a real JWT by logging in once before the test suite runs."""
    admin_password = os.environ.get("PKY_AI_ADMIN_PASSWORD")
    assert admin_password, "Set PKY_AI_ADMIN_PASSWORD env var before running tests"

    response = httpx.post(
        f"{BASE_URL}/auth/token",
        data={"username": "admin@pky-ai.com", "password": admin_password},
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    assert response.status_code == 200, f"Login failed: {response.text}"
    return response.json()["access_token"]


@pytest.fixture
def headers(auth_token):
    return {"Authorization": f"Bearer {auth_token}"}


# --- Public endpoints ---

def test_root():
    response = httpx.get(f"{BASE_URL}/")
    assert response.status_code == 200
    assert "message" in response.json()


def test_models_public():
    response = httpx.get(f"{BASE_URL}/models")
    assert response.status_code == 200
    data = response.json()
    assert "models" in data


# --- Auth ---

def test_login_invalid_credentials():
    response = httpx.post(
        f"{BASE_URL}/auth/token",
        data={"username": "wrong@example.com", "password": "wrongpassword"},
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    assert response.status_code == 401
    assert "error" in response.json()


def test_token_refresh(auth_token):
    response = httpx.post(
        f"{BASE_URL}/auth/refresh",
        headers={"Authorization": f"Bearer {auth_token}"},
    )
    assert response.status_code == 200
    data = response.json()
    assert "access_token" in data
    assert data["token_type"] == "bearer"


# --- Protected endpoints require auth ---

def test_protected_without_token():
    response = httpx.get(f"{BASE_URL}/system/stats")
    assert response.status_code == 401


def test_system_stats(headers):
    response = httpx.get(f"{BASE_URL}/system/stats", headers=headers)
    assert response.status_code == 200
    data = response.json()
    assert "ram_usage" in data
    assert "version" in data
    assert "status" in data


def test_system_health(headers):
    response = httpx.get(f"{BASE_URL}/system/health", headers=headers)
    assert response.status_code == 200
    data = response.json()
    assert "status" in data
    assert "database" in data
    assert "langgraph_memory" in data


def test_user_history(headers):
    response = httpx.get(f"{BASE_URL}/user/history", headers=headers)
    assert response.status_code == 200
    assert isinstance(response.json(), list)


def test_user_preferences_get(headers):
    response = httpx.get(f"{BASE_URL}/user/preferences", headers=headers)
    assert response.status_code == 200


def test_user_preferences_update(headers):
    payload = {"preferences": {"name": "Test Persona"}}
    response = httpx.post(f"{BASE_URL}/user/preferences", json=payload, headers=headers)
    assert response.status_code == 200
    data = response.json()
    assert data.get("status") == "preferences updated"


def test_user_correction(headers):
    payload = {"original": "schedule", "corrected": "arrange", "context": "test"}
    response = httpx.post(f"{BASE_URL}/user/correction", json=payload, headers=headers)
    assert response.status_code == 200
    data = response.json()
    assert data.get("status") == "correction recorded"


def test_user_correction_missing_fields(headers):
    payload = {"original": "something"}
    response = httpx.post(f"{BASE_URL}/user/correction", json=payload, headers=headers)
    assert response.status_code == 400


def test_user_alerts(headers):
    response = httpx.get(f"{BASE_URL}/user/alerts", headers=headers)
    assert response.status_code == 200
    data = response.json()
    assert "user_alert" in data
    assert "global_alert" in data


def test_task_creation(headers):
    task_data = {
        "title": "Test Task",
        "description": "This is a test task",
        "due_date": "2027-12-31"
    }
    response = httpx.post(f"{BASE_URL}/tasks/create", json=task_data, headers=headers)
    assert response.status_code == 200


def test_news_digest(headers):
    response = httpx.get(f"{BASE_URL}/news/digest", headers=headers)
    assert response.status_code == 200


def test_daily_report(headers):
    response = httpx.get(f"{BASE_URL}/reports/daily", headers=headers)
    assert response.status_code == 200


if __name__ == "__main__":
    import sys
    sys.exit(pytest.main([__file__, "-v"]))
