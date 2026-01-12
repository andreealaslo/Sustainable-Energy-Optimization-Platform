import json
import sys
import os

def handle(req):
    """
    Serverless function logic for Carbon Footprint calculation.
    """
    try:
        data = json.loads(req)
        kwh = data.get("kwh", 0)
        intensity = float(os.getenv("intensity_factor", "0.45"))
        carbon_score = round(kwh * intensity, 2)
        result = {
            "carbonScore": carbon_score,
            "unit": "kg CO2",
            "engine": "OpenFaaS-Watchdog"
        }

        return json.dumps(result)
    except Exception as e:
        return json.dumps({"error": str(e)})

if __name__ == "__main__":
    st = sys.stdin.read()
    print(handle(st))