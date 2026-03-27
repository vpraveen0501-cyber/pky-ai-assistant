#!/usr/bin/env python3
"""
Test script for User Persona and Self-Correction features.
"""
import asyncio
import sys
import os

# Add the services directory to the path
sys.path.append(os.path.join(os.path.dirname(__file__), 'services'))

from llm_service import LLMService

async def test_persona():
    print("Testing User Persona and Self-Correction...")
    
    # Initialize LLM Service
    llm_service = LLMService()
    
    # Test 1: Basic conversation
    print("\n1. Testing basic conversation...")
    response = await llm_service.generate("Hello, what can you do?", user_id="test_user")
    print(f"Response: {response}")
    
    # Test 2: Update preferences
    print("\n2. Testing preference update...")
    persona = llm_service.get_user_persona("test_user")
    persona.update_preferences({"name": "Alice"})
    
    # Test 3: Conversation with updated preferences
    print("\n3. Testing conversation with updated preferences...")
    response = await llm_service.generate("Hello, what can you do?", user_id="test_user")
    print(f"Response: {response}")
    
    # Test 4: Self-correction
    print("\n4. Testing self-correction...")
    # First, simulate a wrong response
    response = await llm_service.generate("Schedule a meeting", user_id="test_user")
    print(f"Initial response: {response}")
    
    # Now, handle a correction
    await llm_service.handle_correction("test_user", "schedule", "arrange", "User corrected 'schedule' to 'arrange'")
    
    # Test 5: Conversation after correction
    print("\n5. Testing conversation after correction...")
    response = await llm_service.generate("Can you schedule a meeting?", user_id="test_user")
    print(f"Response after correction: {response}")
    
    print("\nPersona data:")
    print(persona.get_persona())

if __name__ == "__main__":
    asyncio.run(test_persona())