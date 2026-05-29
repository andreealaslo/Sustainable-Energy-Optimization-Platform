import json
import sys
import os
import requests
from datetime import datetime, timezone, timedelta

BASE_URL = "https://api.carbonintensity.org.uk"
CACHE_FILE = "/tmp/carbon_cache.json"
CACHE_DURATION = timedelta(minutes=30)

def handle(req):
    try:
        data = json.loads(req) if req else {}
        kwh = data.get("kwh", 0)
        
        # 1. Establish the current wall-clock time in Romania (UTC+3)
        romanian_offset = timezone(timedelta(hours=3))
        now_ro = datetime.now(romanian_offset)
        
        grid_index = "unknown"
        cached_data = None
        forecast_list_data = [] 

        # --- READ CACHE FROM FILE ---
        if os.path.exists(CACHE_FILE):
            try:
                with open(CACHE_FILE, 'r') as f:
                    cache_content = json.load(f)
                    last_fetch = datetime.fromisoformat(cache_content['timestamp'])
                    
                    # Compare cache freshness directly using Romanian local time
                    if (now_ro - last_fetch) < CACHE_DURATION:
                        block_from = cache_content.get('block_from')
                        block_to = cache_content.get('block_to')
                        if block_from and block_to:
                            try:
                                block_from_dt = datetime.fromisoformat(block_from)
                                block_to_dt = datetime.fromisoformat(block_to)
                                
                                # Check if current Romanian time falls squarely in the cached block window
                                if block_from_dt <= now_ro.replace(tzinfo=None) < block_to_dt:
                                    cached_data = cache_content
                            except Exception:
                                pass
            except Exception:
                pass 

        if cached_data:
            live_intensity = cached_data["live_intensity"]
            advice = cached_data["advice"]
            grid_index = cached_data.get("grid_index", "unknown")
            forecast_list_data = cached_data.get("forecast_list", [])
            data_source = "National Grid Live API (Global Cache)"
        else:
            # --- FORCE WALL-CLOCK ALIGNMENT WITH THE UK API ---
            now_ts = now_ro.strftime('%Y-%m-%dT%H:%MZ')
            
            url = f"{BASE_URL}/intensity/{now_ts}/fw24h"
            response = requests.get(url, timeout=5, headers={'Accept': 'application/json'})
            
            if response.status_code == 200:
                raw_api_data = response.json().get('data', [])
                current_block = raw_api_data[0]['intensity']
                live_intensity = current_block.get('actual') or current_block.get('forecast')
                grid_index = current_block.get('index', 'unknown')
    
                best_block = min(raw_api_data, key=lambda x: x['intensity']['forecast'])
                
                # --- PROCESS USER DISPLAY ADVICE ---
                raw_from_str = best_block['from'].replace('Z', '')
                best_dt_local = datetime.fromisoformat(raw_from_str)
                formatted_window = best_dt_local.strftime('%d.%m.%Y at %H:%M')
    
                low_val = best_block['intensity']['forecast']
                advice = f"Greenest window detected on {formatted_window} ({low_val} gCO2/kWh). Shifting loads to this time reduces footprint."
                
                # --- NEW CLEAN NORMALIZATION LOOP ---
                # Explicitly parse out and build a clean tracking array to secure JSON serialization boundaries
                for item in raw_api_data:
                    forecast_list_data.append({
                        "from": item.get("from"),
                        "to": item.get("to"),
                        "intensity": {
                            "forecast": item.get("intensity", {}).get("forecast"),
                            "actual": item.get("intensity", {}).get("actual"),
                            "index": item.get("intensity", {}).get("index")
                        }
                    })

                block_from_naive = raw_api_data[0]['from'].replace('Z', '')
                block_to_naive = raw_api_data[0]['to'].replace('Z', '')

                # --- SAVE CACHE WITH EXPLICIT CLEAN PARSED FORECAST TIMELINE ---
                with open(CACHE_FILE, 'w') as f:
                    json.dump({
                        "timestamp": now_ro.isoformat(),
                        "live_intensity": live_intensity,
                        "advice": advice,
                        "grid_index": grid_index,
                        "block_from": block_from_naive,
                        "block_to": block_to_naive,
                        "forecast_list": forecast_list_data
                    }, f)
                
                data_source = "National Grid Live API (Fresh)"
            else:
                live_intensity = 450
                advice = "Try to use appliances during daylight hours."
                grid_index = "unknown"
                forecast_list_data = [] 
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
            "forecastTimeline": forecast_list_data,
            "calculationTimestamp": now_ro.strftime('%Y-%m-%dT%H:%MZ'),
            "engine": "OpenFaaS-Carbon-Intelligence"
        })

    except Exception as e:
        return json.dumps({"error": str(e)})

if __name__ == "__main__":
    input_data = sys.stdin.read()
    if input_data:
        print(handle(input_data))