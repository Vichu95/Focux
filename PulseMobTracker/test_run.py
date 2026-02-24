import sqlite3
import pandas as pd
pd.set_option('display.max_columns', None)
pd.set_option('display.width', 1000)
conn=sqlite3.connect('test_db/pulse_user_db.db')

# eventType 1 = APP_OPEN, 2 = APP_CLOSE (based on our tracking, not Android's accessibility event type constants)
# The AccessibilityEvent payload is not stored in the DB, only our abstracted generic 'eventLabel' (APP_OPEN/APP_CLOSE).
# So I need to use the AccessibilityEvent directly in the service to inspect what comes through when navigating the launcher.
