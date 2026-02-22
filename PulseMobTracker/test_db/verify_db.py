import sqlite3
import os

db_path = "pulse_user_db.db"

if not os.path.exists(db_path):
    print(f"Error: {db_path} not found.")
    exit(1)

conn = sqlite3.connect(db_path)
conn.row_factory = sqlite3.Row
cursor = conn.cursor()

# 1. Check schema
cursor.execute("PRAGMA table_info(app_sessions)")
columns = [row['name'] for row in cursor.fetchall()]
has_override = 'categoryOverride' in columns
print(f"Schema check: 'categoryOverride' column exists: {has_override}")

# 2. Extract App Categories
cursor.execute("SELECT packageName, category FROM app_info")
app_categories = {row['packageName']: row['category'] for row in cursor.fetchall()}
ignored_apps = {pkg for pkg, cat in app_categories.items() if cat == 'IGNORED'}

# 3. Read Daily Stats
cursor.execute("SELECT date, productiveTime, neutralTime, distractingTime, glanceCount FROM daily_stats")
daily_stats_db = {row['date']: dict(row) for row in cursor.fetchall()}

# 4. Read Sessions and calculate our own totals
query = f"""
    SELECT date, packageName, duration, type, 
           {'categoryOverride' if has_override else 'NULL as categoryOverride'}
    FROM app_sessions
    WHERE type = 'APP' AND startTime >= (
        SELECT sleepTimeEnd FROM daily_stats WHERE daily_stats.date = app_sessions.date
    )
"""
# Note: In DailySummaryProcessor, app sessions are filtered by timestamp >= dayMetricStart 
# For simplicity, we can just group by 'date' assigned by the processor!
# The 'date' field in app_sessions is already determined during extraction if it was finalized.
# To assure 100% exact matches without re-implementing sleep logic, we can just sum by the assigned `date` column.

# Simplified query: group by the 'date' string already stored on the session
simple_query = f"""
    SELECT date, packageName, duration, type, 
           {'categoryOverride' if has_override else 'NULL'} as categoryOverride
    FROM app_sessions
    WHERE type = 'SESSION_APP'
"""
sessions = cursor.execute(simple_query).fetchall()

calculated_stats = {}
PULSE_GLANCE_ESTIMATE_MS = 5000 # Assuming 5 seconds per glance (typical for tracking)

for session in sessions:
    date = session['date']
    pkg = session['packageName']
    duration = session['duration']
    override = session['categoryOverride']
    
    if pkg in ignored_apps:
        continue
        
    if date not in calculated_stats:
        # initialize
        glances = daily_stats_db.get(date, {}).get('glanceCount', 0)
        # Note: Glances add 5 seconds to neutral time theoretically
        calculated_stats[date] = {'PROD': 0, 'DIST': 0, 'NEUT': 0}
        
    # Determine category
    active_cat = override if override else app_categories.get(pkg, 'NEUTRAL')
    
    if active_cat == 'PRODUCTIVE':
        calculated_stats[date]['PROD'] += duration
    elif active_cat == 'DISTRACTING':
        calculated_stats[date]['DIST'] += duration
    else:
        calculated_stats[date]['NEUT'] += duration

# 5. Compare Results
print("\n--- Daily Verification Report ---")
for date, db_stat in sorted(daily_stats_db.items()):
    calc = calculated_stats.get(date, {'PROD': 0, 'DIST': 0, 'NEUT': 0})
    
    db_prod = db_stat['productiveTime']
    db_dist = db_stat['distractingTime']
    
    # Neutral time in DB includes glance time (glanceCount * 5000)
    # We strip it out to just compare App Usage neutral time
    db_neut_apps = db_stat['neutralTime'] - (db_stat['glanceCount'] * PULSE_GLANCE_ESTIMATE_MS)
    
    print(f"Date: {date}")
    print(f"  Productive : DB={db_prod:8d} | Calc={calc['PROD']:8d} | Diff={db_prod - calc['PROD']}")
    print(f"  Distracting: DB={db_dist:8d} | Calc={calc['DIST']:8d} | Diff={db_dist - calc['DIST']}")
    # Neutral time difference might occur due to launcher package exclusions we don't know about in python
    print(f"  Neutral    : DB={db_neut_apps:8d} | Calc={calc['NEUT']:8d} | Diff={db_neut_apps - calc['NEUT']}")

print("\n--- Detailed Debug for 2026-02-22 ---")
cursor.execute("SELECT date, packageName, duration, categoryOverride FROM app_sessions WHERE type = 'SESSION_APP' AND date = '2026-02-22'")
for row in cursor.fetchall():
    pkg = row['packageName']
    dur = row['duration']
    over = row['categoryOverride']
    cat = app_categories.get(pkg, 'NEUTRAL')
    active_cat = over if over else cat
    if "launcher" in pkg.lower() or "nexus" in pkg.lower() or "oneplus" in pkg.lower() or "sec.android" in pkg.lower():
        continue
    if active_cat != 'NEUTRAL':
        print(f"Pkg: {pkg}, Dur: {dur}, Default: {cat}, Override: {over} -> Active: {active_cat}")
conn.close()
