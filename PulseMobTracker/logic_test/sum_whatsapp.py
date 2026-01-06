
import csv

file_path = r'd:\Learn\Projects\Focux\Focux\PulseMobTracker\logic_test\pulse_user_db-app_sessions.csv'
target_date = '2026-01-06'
target_pkg = 'com.whatsapp'

total_duration = 0
count = 0
sessions = []

with open(file_path, 'r') as f:
    # Skip header presumably? The file viewed didn't show a header in the first few lines, 
    # but line 1 was "id;pkg;..." in previous views? 
    # Let's check line 15500 view again. It was data.
    # I'll assume no header or handle it.
    for line in f:
        parts = line.strip().split(';')
        if len(parts) < 7:
            continue
        
        # Format: id;package;start;end;duration;type;date
        pkg = parts[1]
        try:
            duration = int(parts[4])
        except ValueError:
            continue
        date = parts[6]
        
        if date == target_date and pkg == target_pkg:
            start = int(parts[2])
            end = int(parts[3])
            sessions.append((start, end, duration))

sessions.sort(key=lambda x: x[0])

overlap_duration = 0
prev_end = 0
for start, end, dur in sessions:
    total_duration += dur
    count += 1
    
    if start < prev_end:
        overlap = prev_end - start
        print(f"Overlap detected! Start: {start}, PrevEnd: {prev_end}, Overlap: {overlap} ms")
        overlap_duration += overlap
    prev_end = max(prev_end, end)

print(f"Total WhatsApp Duration (Sum): {total_duration} ms")
print(f"Total Hours: {total_duration / 1000 / 3600:.2f} hours")
print(f"Total Overlap Duration: {overlap_duration} ms")
print(f"Session Count: {count}")
