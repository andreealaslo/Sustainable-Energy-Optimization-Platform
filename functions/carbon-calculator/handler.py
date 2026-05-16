import json
import sys
import os
import requests
from datetime import datetime, timezone, timedelta

BASE_URL = "https://api.carbonintensity.org.uk"
# /tmp is persistent for the life of the container
CACHE_FILE = "/tmp/carbon_cache.json"
CACHE_DURATION = timedelta(minutes=30)

def handle(req):
    try:
        data = json.loads(req) if req else {}
        kwh = data.get("kwh", 0)
        now = datetime.now(timezone.utc)
        grid_index = "unknown"
        
        cached_data = None
        # --- TRY TO READ CACHE FROM FILE ---
        if os.path.exists(CACHE_FILE):
            try:
                with open(CACHE_FILE, 'r') as f:
                    cache_content = json.load(f)
                    last_fetch = datetime.fromisoformat(cache_content['timestamp'])
                    if (now - last_fetch) < CACHE_DURATION:
                        block_from = cache_content.get('block_from')
                        block_to = cache_content.get('block_to')
                        if block_from and block_to:
                            try:
                                block_from_dt = datetime.fromisoformat(block_from.replace('Z', '+00:00'))
                                block_to_dt = datetime.fromisoformat(block_to.replace('Z', '+00:00'))
                                if block_from_dt <= now < block_to_dt:
                                    cached_data = cache_content
                            except Exception:
                                pass
            except Exception:
                pass # If file is corrupt, we'll just fetch new data

        if cached_data:
            live_intensity = cached_data["live_intensity"]
            advice = cached_data["advice"]
            grid_index = cached_data.get("grid_index", "unknown")
            data_source = "National Grid Live API (Global Cache)"
        else:
            # --- FETCH FRESH DATA ---
            now_ts = now.strftime('%Y-%m-%dT%H:%MZ')
            url = f"{BASE_URL}/intensity/{now_ts}/fw24h"
            response = requests.get(url, timeout=5, headers={'Accept': 'application/json'})
            
            if response.status_code == 200:
                forecast_list = response.json().get('data', [])
                current_block = forecast_list[0]['intensity']
                live_intensity = current_block.get('actual') or current_block.get('forecast')
                grid_index = current_block.get('index', 'unknown') # <--- Get 'low', 'moderate', 'high', etc.
                
                best_block = min(forecast_list, key=lambda x: x['intensity']['forecast'])
                best_time = best_block['from'].split('T')[1][:5]
                low_val = best_block['intensity']['forecast']
                advice = f"Greenest window detected at {best_time} ({low_val} gCO2/kWh). Shifting loads to this time reduces footprint."
                
                # --- SAVE TO FILE CACHE ---
                with open(CACHE_FILE, 'w') as f:
                    json.dump({
                        "timestamp": now.isoformat(),
                        "live_intensity": live_intensity,
                        "advice": advice,
                        "grid_index": grid_index,
                        "block_from": forecast_list[0]['from'],
                        "block_to": forecast_list[0]['to']
                    }, f)
                data_source = "National Grid Live API (Fresh)"
            else:
                live_intensity = 450
                advice = "Try to use appliances during daylight hours."
                grid_index = "unknown"
                data_source = "Static Fallback (API Error)"

        factor = live_intensity / 1000.0
        carbon_score = round(kwh * factor, 4)

        return json.dumps({
            "carbonScore": carbon_score,
            "unit": "kg CO2",
            "intensityUsed": live_intensity,
            "advice": advice,
            "gridIndex": grid_index,
            "dataSource": data_source,
            "calculationTimestamp": now.strftime('%Y-%m-%dT%H:%MZ'),
            "engine": "OpenFaaS-Carbon-Intelligence"
        })

    except Exception as e:
        return json.dumps({"error": str(e)})

if __name__ == "__main__":
    input_data = sys.stdin.read()
    if input_data:
        print(handle(input_data))