import json
import sys
import os

def handle(req):
    """
    Serverless function logic for Carbon Footprint calculation.
    """
    try:
        # 1. Parse Input
        data = json.loads(req)
        kwh = data.get("kwh", 0)
        
        # 2. Get Intensity Factor from Environment (with fallback to 0.45)
        # This allows us to change the calculation without rebuilding the image
        intensity = float(os.getenv("intensity_factor", "0.45"))
        
        # 3. Calculate Math
        carbon_score = round(kwh * intensity, 2)

        # 4. Return focused response
        result = {
            "carbonScore": carbon_score,
            "unit": "kg CO2",
            "engine": "OpenFaaS-Watchdog"
        }

        return json.dumps(result)
    except Exception as e:
        return json.dumps({"error": str(e)})

if __name__ == "__main__":
    # Watchdog pattern: Read from stdin
    st = sys.stdin.read()
    print(handle(st))