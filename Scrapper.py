
from selenium import webdriver 
from selenium.webdriver.chrome.service import Service as ChromeService 
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from webdriver_manager.chrome import ChromeDriverManager 
import time
import os
import smtplib
from email.message import EmailMessage
import pandas as pd



# Function to read existing data from a file
def read_existing_data(file_path):
    if os.path.exists(file_path):
        with open(file_path, 'r') as file:
            existing_data = file.read().splitlines()
        return existing_data
    else:
        return []

# Function to send email notification
def send_email(new_data):
    EMAIL_ADDRESS = 'your_email@gmail.com'
    EMAIL_PASSWORD = 'your_email_password'

    msg = EmailMessage()
    msg['Subject'] = 'New Job Listings Available'
    msg['From'] = EMAIL_ADDRESS
    msg['To'] = 'recipient_email@example.com'

    msg.set_content('\n'.join(new_data))

    with smtplib.SMTP_SSL('smtp.gmail.com', 465) as smtp:
        smtp.login(EMAIL_ADDRESS, EMAIL_PASSWORD)
        smtp.send_message(msg)


 
# url = 'https://jobs.bosch.com/en/?country=de&positionTypes=e4a3c0db-920d-4356-8ce0-c0b44a54e497,f524b939-fe55-407b-a0b0-e8f4eec28997,f3b92bc1-600d-4d33-9115-5aa27785f52b,8c682ec0-3d28-4e52-b53b-c63d440a32d3' 
# url = 'https://www.fev.com/en/jobs/?entryLevel=students&country=germany'
# url = 'https://valeo.wd3.myworkdayjobs.com/de-DE/valeo_jobs?locationCountry=dcc5b7608d8644b3a93716604e78e995'
# url = 'https://karriere.volkswagen.de/sap/bc/bsp/sap/zvw_hcmx_ui_ext/desktop.html#/SEARCH/SIMPLE/'
 

options = webdriver.ChromeOptions() #newly added 
options.add_argument("--disable-cookies")
# options.add_argument('--headless')


# Read the Excel file into a DataFrame
df = pd.read_excel('JobAlert.xlsx')
# Display the DataFrame
print(df)


# driver = webdriver.Chrome(service=ChromeService(ChromeDriverManager().install())) 

with webdriver.Chrome(service=ChromeService(ChromeDriverManager().install()), options=options) as driver: 
   
    for index, jobalert in df.iterrows():
        
        driver.get(jobalert['CareerURL'])

        print("Timer on)")
        if pd.notnull(jobalert['LoadTime_s']):
            time.sleep(jobalert['LoadTime_s'])
        driver.implicitly_wait(100)


        if pd.notnull(jobalert['ShadowRoot_XPATH']):
            shadow_host1 = WebDriverWait(driver, 60).until(
                EC.presence_of_element_located((By.XPATH, jobalert['ShadowRoot_XPATH']))
            )


            
            # shadow_host1 = driver.find_element(By.ID, "usercentrics-root")
            shadow_root1 = driver.execute_script('return arguments[0].shadowRoot', shadow_host1)

            print("Extracted shadow host and root")
            print(shadow_host1)
            print(shadow_root1)


            # Find the button element inside the shadow root
            button_element = shadow_root1.find_element(By.CSS_SELECTOR, "[" + jobalert['Button_CSS_Sel'] + "]")

            # Scroll to the button element to ensure it's in view
            driver.execute_script("arguments[0].scrollIntoView();", button_element)

            # Click on the button using JavaScript to bypass any potential visibility issues
            driver.execute_script("arguments[0].click();", button_element)


        
        # element = driver.find_elements(By.CLASS_NAME, 'M-JobSearchResultsGroup__list')
        new_data = []
        if pd.notnull(jobalert['Job_XPATH']):            
            element = WebDriverWait(driver, 60).until(EC.presence_of_element_located((By.XPATH,  jobalert['Job_XPATH'])))
            # Scrape new data
            new_data = [element.text for element in driver.find_elements(By.XPATH, jobalert['Job_XPATH'])]
        
        elif pd.notnull(jobalert['Job_CSS_Sel']):
            element = WebDriverWait(driver, 60).until(EC.presence_of_element_located((By.CSS_SELECTOR, "[" + jobalert['Job_CSS_Sel'] + "]")))
            # Scrape new data
            new_data = [element.text for element in driver.find_elements(By.CSS_SELECTOR,"[" + jobalert['Job_CSS_Sel'] + "]")]


        # element = driver.find_elements(By.CLASS_NAME, 'items')
        # element = driver.find_elements(By.CLASS_NAME, 'css-8j5iuw')
        # element = driver.find_elements(By.CLASS_NAME, 'item-title')
        # element = driver.find_elements(By.ID, 'JOBRESULTLIST--jobList')

        
        print("\n\nScrapped Data:\n",new_data)


        # File path to store existing data
        file_path = 'database/data_' + jobalert['CompanyName'] + '.txt'

        # Read existing data
        existing_data = read_existing_data(file_path)


        # Compare new data with existing data
        difference = set(new_data) - set(existing_data)


        # If there is a difference, update the existing data file and send an email
        if difference:
            print("\n\nThere is difference!!\n\n", difference)
            # Write new data to the file
            with open(file_path, 'w') as file:
                file.write('\n'.join(new_data))
        else:
            print("\n\nThere are no difference!!\n\n")
