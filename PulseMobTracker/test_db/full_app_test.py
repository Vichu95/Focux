import sqlite3
import datetime

DB_PATH = "pulse_user_db.db"

def connect_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def ms_to_str(ms):
    if ms < 0: return "0m"
    hours = ms // 3600000
    minutes = (ms % 3600000) // 60000
    return f"{hours}h {minutes}m" if hours > 0 else f"{minutes}m"

def run_tests():
    conn = connect_db()
    cursor = conn.cursor()
    
    # --- GET DATES ---
    cursor.execute("SELECT date FROM daily_stats ORDER BY date DESC LIMIT 10")
    dates = [r['date'] for r in cursor.fetchall()]
    if not dates:
        print("Error: No data in daily_stats.")
        return
        
    print("=" * 70)
    print(" FOCUX COMPREHENSIVE 10-DAY DATA INTEGRITY REPORT ")
    print("=" * 70)
    
    # --- GET BASE REFS ---
    cursor.execute("SELECT packageName, category FROM app_info")
    app_categories = {r['packageName']: r['category'] for r in cursor.fetchall()}
    ignored_apps = set(pkg for pkg, cat in app_categories.items() if cat == 'IGNORED')
    launchers = ['launcher', 'nexus', 'oneplus', 'sec.android']
    
    def is_valid_app(pkg):
        if pkg in ignored_apps: return False
        pkg_lower = pkg.lower()
        if any(l in pkg_lower for l in launchers): return False
        return True

    for target_date in dates:
        print(f"\n[{target_date}] DATA VERIFICATION")
        print("-" * 50)
        
        cursor.execute("SELECT * FROM daily_stats WHERE date = ?", (target_date,))
        db_stats_row = cursor.fetchone()
        if not db_stats_row: continue
        db_stats = dict(db_stats_row)
        
        # Calculate from Sessions
        cursor.execute('''
            SELECT type, packageName, duration, categoryOverride 
            FROM app_sessions 
            WHERE date = ?
        ''', (target_date,))
        sessions = cursor.fetchall()
        
        calc_prod, calc_dist, calc_neut = 0, 0, 0
        app_durations = {}
        app_count = 0
        unlock_app, unlock_noapp, glance = 0, 0, 0
        offline_sessions = []
        time_offline = 0
        
        for row in sessions:
            stype = row['type']
            dur = row['duration']
            
            if stype == 'SESSION_GLANCE':
                glance += 1
            elif stype == 'SESSION_UNLOCK_APP':
                unlock_app += 1
            elif stype == 'SESSION_UNLOCK_NOAPP':
                unlock_noapp += 1
            elif stype == 'SESSION_OFFLINE':
                offline_sessions.append(dur)
                time_offline += dur
            elif stype == 'SESSION_APP':
                pkg = row['packageName']
                if not is_valid_app(pkg): continue
                
                app_count += 1
                app_durations[pkg] = app_durations.get(pkg, 0) + dur
                
                over = row['categoryOverride']
                cat = over if over else app_categories.get(pkg, 'NEUTRAL')
                
                if cat == 'PRODUCTIVE': calc_prod += dur
                elif cat == 'DISTRACTING': calc_dist += dur
                else: calc_neut += dur
                
        # --- PREPARE DATA ---
        db_neut_apps = db_stats['neutralTime'] - (db_stats['glanceCount'] * 5000)
        calc_total_screen = calc_prod + calc_dist + calc_neut + (glance * 5000)
        
        avg_session = (calc_prod + calc_dist + calc_neut) // app_count if app_count > 0 else 0
        max_offline = max(offline_sessions) if offline_sessions else 0
        
        sorted_apps = sorted(app_durations.items(), key=lambda x: x[1], reverse=True)[:3]
        
        db_total_unlocks = db_stats['unlockAppCount'] + db_stats['unlockNoAppCount']
        calc_total_unlocks = unlock_app + unlock_noapp
        
        print(f"  * TIME BREAKDOWN")
        print(f"    Productive : DB: {ms_to_str(db_stats['productiveTime']):<8} | Calc: {ms_to_str(calc_prod):<8} | {'[PASS]' if abs(db_stats['productiveTime'] - calc_prod) <= 60000 else '[FAIL]'}")
        print(f"    Distracting: DB: {ms_to_str(db_stats['distractingTime']):<8} | Calc: {ms_to_str(calc_dist):<8} | {'[PASS]' if abs(db_stats['distractingTime'] - calc_dist) <= 60000 else '[FAIL]'}")
        print(f"    Neutral(app):DB: {ms_to_str(db_neut_apps):<8} | Calc: {ms_to_str(calc_neut):<8} | {'[PASS]' if abs(db_neut_apps - calc_neut) <= 60000 else '[WARN]'}")
        print(f"    TotalScreen: DB: {ms_to_str(db_stats['totalScreenTime']):<8} | Calc: {ms_to_str(calc_total_screen):<8} | {'[PASS]' if abs(db_stats['totalScreenTime'] - calc_total_screen) <= 60000 else '[WARN]'}")
        
        print(f"\n  * HABITS & ACTIONS")
        print(f"    Unlocks    : DB: {db_total_unlocks:<8} | Calc: {calc_total_unlocks:<8} | {'[PASS]' if db_total_unlocks == calc_total_unlocks else '[FAIL]'}")
        print(f"    Glances    : DB: {db_stats['glanceCount']:<8} | Calc: {glance:<8} | {'[PASS]' if db_stats['glanceCount'] == glance else '[FAIL]'}")
        
        print(f"\n  * OFFLINE & SLEEP")
        print(f"    Offline Streak (Deep Work): DB: {ms_to_str(db_stats['offlineStreakDuration'])} | Calc Base: {ms_to_str(max_offline)}")
        print(f"    Total Time Offline: {ms_to_str(time_offline)}")
        print(f"    Sleep Logged: {db_stats['sleepReadableStart']} to {db_stats['sleepReadableEnd']} (Duration: {ms_to_str(db_stats['sleepTimeEnd'] - db_stats['sleepTimeStart'])})")
        
        print(f"\n  * INSIGHTS (App Averages)")
        print(f"    Avg Session Length: {ms_to_str(avg_session)}")
        print(f"    App Count (valid instances): {app_count}")
        
        print(f"\n  * TOP APPS")
        top_db_pkgs = [db_stats['topApp1Package'], db_stats['topApp2Package'], db_stats['topApp3Package']]
        top_db_durs = [db_stats['topApp1Duration'], db_stats['topApp2Duration'], db_stats['topApp3Duration']]
        for i in range(3):
            calc_pkg, calc_dur = sorted_apps[i] if i < len(sorted_apps) else (None, 0)
            db_pkg, db_dur = top_db_pkgs[i], top_db_durs[i]
            match = "[PASS]" if db_pkg == calc_pkg else "[FAIL]"
            if db_dur > 0 or calc_dur > 0:
                print(f"    Top {i+1}: DB[{db_pkg} ({ms_to_str(db_dur)})] vs Calc[{calc_pkg} ({ms_to_str(calc_dur)})] {match}")

    print("\n" + "=" * 70)
    print(" WEEKLY TOTAL TIME (LAST 7 FULL DAYS) ")
    print("=" * 70)
    cursor.execute("SELECT SUM(totalScreenTime) as tst, SUM(productiveTime) as tpt, SUM(distractingTime) as tdt, AVG(focusScore) as afs FROM daily_stats WHERE date > date(?, '-8 days') AND date < ?", (dates[0], dates[0]))
    last_week = cursor.fetchone()
    if last_week and last_week['tst']:
        print(f"  Total Screen: {ms_to_str(last_week['tst'])}")
        print(f"  Total Productive: {ms_to_str(last_week['tpt'])}")
        print(f"  Total Distracting: {ms_to_str(last_week['tdt'])}")
        print(f"  Avg Focus Score: {last_week['afs']:.1f}")

run_tests()
