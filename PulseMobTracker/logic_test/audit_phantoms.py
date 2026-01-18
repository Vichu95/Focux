import csv
import datetime

SESSION_FILE = "pulse_user_db-app_sessions.csv"
RAW_FILE = "pulse_user_db-raw_data.csv"

def run_audit():
    print("--- RAW DATA DUMP (Jan 18 Morning) ---")
    events = []
    with open(RAW_FILE, 'r') as f:
        reader = csv.DictReader(f, delimiter=';')
        for row in reader:
            ts = int(row['timestamp'])
            dt = datetime.datetime.fromtimestamp(ts / 1000.0)
            if dt.day == 18 and dt.hour < 10:
                events.append({
                    'id': row['id'],
                    'ts': dt.strftime('%H:%M:%S'),
                    'type': row['eventType'],
                    'pkg': row['packageName']
                })
    
    for e in events:
        print(f"{e['ts']} | {e['type']:<15} | {e['pkg']}")


if __name__ == "__main__":
    run_audit()
