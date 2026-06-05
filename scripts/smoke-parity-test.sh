#!/bin/bash
set -e
python3 "$(dirname "$0")/../scripts/smoke-parity-test.py"
