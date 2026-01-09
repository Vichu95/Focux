
import csv

file_path = r'd:\Learn\Projects\Focux\Focux\PulseMobTracker\logic_test\pulse_user_db-raw_data.csv'

# Constants
EVENT_APP_OPEN = '1'
EVENT_APP_CLOSE = '2'
EVENT_SCREEN_ON = '15'
EVENT_SCREEN_OFF = '16'
EVENT_LOCK = '17'
EVENT_UNLOCK = '18'

LAUNCHERS = {'com.google.android.apps.nexuslauncher', 'com.android.launcher3', 'com.sec.android.app.launcher'}
IGNORED = {'com.android.systemui', 'android'}

events = []

print("Reading file...")
with open(file_path, 'r') as f:
    reader = csv.reader(f, delimiter=';')
    header = next(reader)
    for row in reader:
        if not row: continue
        try:
            # id;timestamp;eventType;packageName...
            events.append({
                'ts': int(row[1]),
                'type': row[2],
                'pkg': row[3] if len(row) > 3 else '',
                'time': row[5] if len(row) > 5 else ''
            })
        except ValueError:
            continue

events.sort(key=lambda x: x['ts'])

# Session State
screen_on_ts = None
has_unlocked = False
apps_opened = set()

# Metrics
glance_count = 0
glance_duration = 0

unlock_no_app_count = 0
unlock_no_app_duration = 0

unlock_app_count = 0
unlock_app_duration_total = 0 # Total time ON->OFF for these sessions

total_screen_time = 0

for e in events:
    etype = e['type']
    ts = e['ts']
    pkg = e['pkg']

    if etype == EVENT_SCREEN_ON:
        screen_on_ts = ts
        has_unlocked = False
        apps_opened = set()
        
    elif etype == EVENT_UNLOCK:
        if screen_on_ts is not None:
            has_unlocked = True
            
    elif etype == EVENT_APP_OPEN:
        if screen_on_ts is not None:
             if pkg not in LAUNCHERS and pkg not in IGNORED and pkg != "":
                 apps_opened.add(pkg)

    elif etype == EVENT_SCREEN_OFF:
        if screen_on_ts is not None:
            duration = ts - screen_on_ts
            
            if duration < 24 * 60 * 60 * 1000:
                total_screen_time += duration
                
                if not has_unlocked:
                    # GLANCE
                    glance_count += 1
                    glance_duration += duration
                else:
                    # UNLOCKED SESSION
                    if len(apps_opened) == 0:
                        # UNLOCK NO APP
                        unlock_no_app_count += 1
                        unlock_no_app_duration += duration
                    else:
                        # UNLOCK WITH APP
                        unlock_app_count += 1
                        unlock_app_duration_total += duration
            
            screen_on_ts = None
            has_unlocked = False
            apps_opened = set()


print(f"{'Metric':<25} | {'Count':<8} | {'Duration (h)':<12}")
print("-" * 50)
print(f"{'Glances':<25} | {glance_count:<8} | {glance_duration/1000/3600:<12.2f}")
print(f"{'Unlock No App':<25} | {unlock_no_app_count:<8} | {unlock_no_app_duration/1000/3600:<12.2f}")
print(f"{'Unlock With App':<25} | {unlock_app_count:<8} | {unlock_app_duration_total/1000/3600:<12.2f}")
print("-" * 50)
calculated_total = glance_duration + unlock_no_app_duration + unlock_app_duration_total
print(f"{'Calculated Sum':<25} | {'-':<8} | {calculated_total/1000/3600:<12.2f}")
print(f"{'Actual Total Screen Time':<25} | {'-':<8} | {total_screen_time/1000/3600:<12.2f}")

if calculated_total == total_screen_time:
    print("\nSUCCESS: Sum equals Total Screen Time.")
else:
    print(f"\nDiff: {total_screen_time - calculated_total} ms")
