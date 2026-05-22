#!/usr/bin/env python3
import urllib.request
import json
import sys

# Configuration - Should ideally be read from environment or config, 
# but hardcoding for the smoke test scope.
PRIMARY_URL = "https://propozitii-nostime.onrender.com/api/all"
FALLBACK_URL = "https://propozitii-nostime.vercel.app/api/all"

REQUIRED_KEYS = {"haiku", "distih", "comparison", "definition", "tautogram", "mirror"}

def check_endpoint(url):
    print(f"Checking endpoint: {url}")
    try:
        with urllib.request.urlopen(url, timeout=30) as response:
            if response.status != 200:
                print(f"  [ERROR] Non-200 status: {response.status}")
                return None
            data = json.loads(response.read().decode())
            keys = set(data.keys())
            missing = REQUIRED_KEYS - keys
            if missing:
                print(f"  [ERROR] Missing keys: {missing}")
                return None
            print("  [OK] All keys present.")
            return data
    except Exception as e:
        print(f"  [ERROR] Request failed: {e}")
        return None

def main():
    print("Starting Smoke Parity Test...")
    
    primary_data = check_endpoint(PRIMARY_URL)
    if primary_data is None:
        sys.exit(1)
        
    fallback_data = check_endpoint(FALLBACK_URL)
    if fallback_data is None:
        sys.exit(1)

    # Deep comparison of keys and structure (rough check)
    # We don't compare content because it changes randomly, 
    # but we do compare the set of keys and if they are strings.
    for key in REQUIRED_KEYS:
        if not isinstance(primary_data[key], str) or not isinstance(fallback_data[key], str):
            print(f"  [ERROR] Content type mismatch for key '{key}'")
            sys.exit(1)

    print("SUCCESS: Both endpoints satisfy the parity contract (keys and types match).")
    sys.exit(0)

if __name__ == "__main__":
    main()
