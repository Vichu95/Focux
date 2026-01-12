import csv
import datetime
import os

# Files
SESSION_FILE = "pulse_user_db-app_sessions.csv"
DAILY_FILE = "pulse_user_db-daily_stats.csv"

def parse_ts(ts_str):
    try:
        return int(ts_str)
    except:
        return 0

def format_ts(ts):
    return datetime.datetime.fromtimestamp(ts / 1000.0).strftime('%Y-%m-%d %H:%M:%S')

def load_sessions():
    sessions = []
    with open(SESSION_FILE, 'r') as f:
        reader = csv.DictReader(f, delimiter=';')
        for row in reader:
            s = {
                'pkg': row['packageName'],
                'start': parse_ts(row['startTime']),
                'end': parse_ts(row['endTime']),
                'type': row['type'],
                'date': row['date']
            }
            sessions.append(s)
    return sorted(sessions, key=lambda x: x['start'])

def load_daily():
    daily = {}
    with open(DAILY_FILE, 'r') as f:
        reader = csv.DictReader(f, delimiter=';')
        for row in reader:
            daily[row['date']] = {
                'sleepStart': parse_ts(row['sleepTimeStart']),
                'sleepEnd': parse_ts(row['sleepTimeEnd']),
                'lastAppPkg': row['lastAppPackage'],
                'lastAppEnd': parse_ts(row['lastAppEndTime']),
                'firstAppPkg': row['firstAppPackage'],
                'firstAppStart': parse_ts(row['firstAppStartTime'])
            }
    return daily

def analyze():
    sessions = load_sessions()
    daily_stats = load_daily()
    
    # Analyze by Date
    dates = sorted(daily_stats.keys())
    
    for i, date_str in enumerate(dates):
        print(f"\n--- Analyzing {date_str} ---")
        stats = daily_stats[date_str]
        
        # 1. Check Sleep Window
        sleep_start = stats['sleepStart']
        sleep_end = stats['sleepEnd']
        
        print(f"Recorded Sleep: {format_ts(sleep_start)} -> {format_ts(sleep_end)}")
        
        # 2. Check Yesterday's Last App (This date's Last App should be before TOMORROW's Sleep Start)
        if i + 1 < len(dates):
            next_date = dates[i+1]
            next_stats = daily_stats[next_date]
            next_sleep_start = next_stats['sleepStart']
            
            print(f"Tomorrow ({next_date}) Sleep Starts: {format_ts(next_sleep_start)}")
            
            # Find actual last app before next_sleep_start
            # Window: [Today Noon] -> [Next Sleep Start]
            # Actually just search all sessions < next_sleep_start
            
            valid_apps = [
                s for s in sessions 
                if s['type'] == 'SESSION_APP' 
                and s['start'] < next_sleep_start
                # Simple check: timestamp must be today or early tomorrow
                and s['start'] > sleep_end 
            ]
            
            if valid_apps:
                last_app_real = max(valid_apps, key=lambda x: x['start'])
                print(f"Actual Last App: {last_app_real['pkg']} at {format_ts(last_app_real['start'])}")
                print(f"Recorded Last App: {stats['lastAppPkg']} at {format_ts(stats['lastAppEnd'])}")
                
                if stats['lastAppEnd'] != last_app_real['end']:
                    print(">>> DISCREPANCY: Last App mismatch!")
            else:
                print("No valid apps found in window?")

        # 3. Check This Day's First App
        # Should be > sleep_end
        
        if sleep_end > 0:
            first_apps = [
                s for s in sessions
                if s['type'] == 'SESSION_APP'
                and s['start'] >= sleep_end
                and s['start'] < sleep_end + (12 * 3600 * 1000) # Look ahead 12 hours
            ]
            
            if first_apps:
                first_real = min(first_apps, key=lambda x: x['start'])
                print(f"Actual First App: {first_real['pkg']} at {format_ts(first_real['start'])}")
                print(f"Recorded First App: {stats['firstAppPkg']} at {format_ts(stats['firstAppStart'])}")
                
                if abs(stats['firstAppStart'] - first_real['start']) > 1000:
                     print(">>> DISCREPANCY: First App mismatch!")
            else:
                print("No first app found after sleep?")

        # Debug specific range for user query
        if date_str == "2026-01-10" or date_str == "2026-01-11":
            print(f"\n[DEBUG] Events around 05:00 for {date_str}:")
            debug_start = stats['sleepStart'] # - 2 hours
            debug_end = stats['sleepEnd'] + (4 * 3600 * 1000)
            
            check_sessions = [
                s for s in sessions
                if s['start'] >= debug_start and s['start'] <= debug_end
            ]
            for s in check_sessions:
                print(f"  {format_ts(s['start'])} - {format_ts(s['end'])} : {s['type']} ({s['pkg']}) Duration: {(s['end']-s['start'])/1000/60:.1f}m")


if __name__ == "__main__":
    analyze()
