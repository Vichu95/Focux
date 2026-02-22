import sqlite3

DB_PATH = "pulse_user_db.db"

def connect_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def test_algorithm():
    conn = connect_db()
    cursor = conn.cursor()
    
    cursor.execute("SELECT packageName, category FROM app_info")
    app_categories = {r['packageName']: r['category'] for r in cursor.fetchall()}
    
    # Get last 10 days
    cursor.execute("SELECT date FROM daily_stats ORDER BY date DESC LIMIT 10")
    dates = [r['date'] for r in cursor.fetchall()]
    
    print("=====================================================================")
    print(" ALGORITHM SIMULATION: HOLISTIC FOCUS SCORE ")
    print("=====================================================================")
    print(f"{'Date':<12} | {'Time(40)':<8} | {'Habit(20)':<9} | {'Morn(10)':<8} | {'Deep(15)':<8} | {'Slp(15)':<7} || TOTAL / 100")
    print("-" * 80)
    
    for date in dates:
        # Load DB Stats for existing aggregated metrics
        cursor.execute("SELECT * FROM daily_stats WHERE date = ?", (date,))
        stats = dict(cursor.fetchone())
        
        # Load App Sessions for first app & precise overrides
        cursor.execute("SELECT packageName, type, duration, categoryOverride FROM app_sessions WHERE date = ? ORDER BY startTime ASC", (date,))
        sessions = cursor.fetchall()
        
        # Determine First App Category
        first_app_cat = "NEUTRAL"
        for s in sessions:
            if s['type'] == 'SESSION_APP':
                pkg = s['packageName']
                if 'launcher' in pkg.lower() or 'nexus' in pkg.lower() or 'oneplus' in pkg.lower() or 'sec.android' in pkg.lower():
                    continue
                over = s['categoryOverride']
                first_app_cat = over if over else app_categories.get(pkg, 'NEUTRAL')
                break
                
        # 1. Screen Time Quality (Max 40)
        p_min = stats['productiveTime'] / 60000
        d_min = stats['distractingTime'] / 60000
        n_min = stats['neutralTime'] / 60000
        
        time_score = max(0, min(40, 20 + (p_min * 0.2) - (d_min * 0.5) - (n_min * 0.1)))
        
        # 2. Habits (Max 20)
        unlocks = stats['unlockAppCount'] + stats['unlockNoAppCount']
        glances = stats['glanceCount']
        # Relaxed penalty: 80 unlocks = -8, 50 glances = -5. Leaves 7/20 points
        habit_score = max(0, min(20, 20 - (unlocks * 0.1) - (glances * 0.1)))
        
        # 3. Morning Ritual (Max 10)
        morn_score = 0 if first_app_cat == "DISTRACTING" else 10
        
        # 4. Deep Work (Max 15)
        streak_min = stats['offlineStreakDuration'] / 60000
        deep_score = max(0, min(15, (streak_min / 120.0) * 15))
        
        # 5. Sleep (Max 15)
        # Handle cases where sleep wasn't fully logged yet (e.g., today)
        sleep_dur = stats['sleepTimeEnd'] - stats['sleepTimeStart']
        if sleep_dur <= 0:
            sleep_score = 15 # Default to perfect if unknown
        else:
            sleep_min = sleep_dur / 60000
            base_sleep = 15 if sleep_min >= 360 else (15 - (360 - sleep_min) * 0.05)
            sleep_score = max(0, min(15, base_sleep - (stats['sleepBreakCount'] * 3)))
        
        # TOTAL
        total = time_score + habit_score + morn_score + deep_score + sleep_score
        
        print(f"{date:<12} | {time_score:>6.1f}   | {habit_score:>7.1f}   | {morn_score:>6.1f}   | {deep_score:>6.1f}   | {sleep_score:>5.1f}   || {total:>5.1f} / 100")

    print("=====================================================================")

test_algorithm()
