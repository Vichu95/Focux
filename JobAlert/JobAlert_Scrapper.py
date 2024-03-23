
##############################
##        I M P O R T S 
##############################
from selenium import webdriver 
from selenium.webdriver.chrome.service import Service as ChromeService 
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from webdriver_manager.chrome import ChromeDriverManager 
import time
import os
import telebot
import pandas as pd
import json



##############################
##     c o n f i g
##############################
## Mention the path to the json file that contains the token values.
token_path="../../.focux_params"
## Mention path to the excel
parserList_path = 'JobAlert_List.xlsx'



##############################
##     F U N C T I O N S 
##############################

# Function to read existing data from a file
def read_existing_data(file_path):
    if os.path.exists(file_path):
        with open(file_path, 'r') as file:
            existing_data = file.read().splitlines()
        return existing_data
    else:
        return []



######
## Telegram Bot
######

# Read the tokens: Bot token, Chat ID
def read_token_from_file( token_key):
    with open(token_path, 'r') as file:
        tokens = json.load(file)
    return tokens.get(token_key)

FOCUX_BOT_TOKEN=read_token_from_file('FOCUX_BOT_TOKEN')
FOCUX_GROUP_CHATID=read_token_from_file('FOCUX_GROUP_CHATID')
# Initialize bot
bot = telebot.TeleBot(FOCUX_BOT_TOKEN)

### Use below code to get the user or group id. 
### Uncomment and run the script. Text the bot or text the group having bot.
# @bot.message_handler(func=lambda message: True)
# def handle_message(message):
#     print("Chat ID:", message.chat.id)
# # Start the bot
# bot.polling()


######
## Chrome Parser
######
options = webdriver.ChromeOptions()
options.add_argument("--disable-cookies")
options.add_argument('--headless')


jobalert_msg = ""
# Read the Excel file into a DataFrame
df = pd.read_excel(parserList_path)
# Display the DataFrame
print(df)


# Main driver loop
with webdriver.Chrome(service=ChromeService(ChromeDriverManager().install()), options=options) as driver: 
   
   ## Iterate between each company career loop
    for index, jobalert in df.iterrows():
        
        # Open the link
        driver.get(jobalert['CareerURL'])

        print("\nWaiting for sometime...")
        if pd.notnull(jobalert['LoadTime_s']):
            time.sleep(jobalert['LoadTime_s'])
        driver.implicitly_wait(100)


        ## This part handles the cases where there is a shadowroot wrapping the popup like privacy or cookies settings
        if pd.notnull(jobalert['ShadowRoot_XPATH']):

            shadow_host1 = WebDriverWait(driver, 60).until(
                EC.presence_of_element_located((By.XPATH, jobalert['ShadowRoot_XPATH']))
            )

            shadow_root1 = driver.execute_script('return arguments[0].shadowRoot', shadow_host1)

            print("Extracted the shadow host and root")
            print(shadow_host1)
            print(shadow_root1)


            # Find the button element inside the shadow root
            button_element = shadow_root1.find_element(By.CSS_SELECTOR, "[" + jobalert['Button_CSS_Sel'] + "]")
            # Scroll to the button element to ensure it's in view
            driver.execute_script("arguments[0].scrollIntoView();", button_element)
            # Click on the button using JavaScript to bypass any potential visibility issues
            driver.execute_script("arguments[0].click();", button_element)


        ## Scrap the data via XPATH or CSS_SELECTOR
        new_data = []
        if pd.notnull(jobalert['Job_XPATH']):            
            element = WebDriverWait(driver, 60).until(EC.presence_of_element_located((By.XPATH,  jobalert['Job_XPATH'])))
            # Scrape new data
            new_data = [element.text for element in driver.find_elements(By.XPATH, jobalert['Job_XPATH'])]
        
        elif pd.notnull(jobalert['Job_CSS_Sel']):
            element = WebDriverWait(driver, 60).until(EC.presence_of_element_located((By.CSS_SELECTOR, "[" + jobalert['Job_CSS_Sel'] + "]")))
            # Scrape new data
            new_data = [element.text for element in driver.find_elements(By.CSS_SELECTOR,"[" + jobalert['Job_CSS_Sel'] + "]")]

        
        print("\n\nScrapped Data:\n",new_data)


        # File path to store existing data
        file_path = 'database/data_' + jobalert['CompanyName'] + '.txt'

        # Read existing job data
        existing_data = read_existing_data(file_path)


        ## Handle case when the entire parsed data is in one string separated by \n
        if(len(new_data)==1):
            split_elements = new_data[0]
            # Add each split element to the new set
            new_data = split_elements.split('\n')

        # Compare new data with existing data
        difference = set(new_data) - set(existing_data)

        
        # If there is a difference, update the existing data file and store it in final message
        if difference:
            print("\n\nThere is difference!!\n\n", difference)
            
            # Add the differences to differences_str
            jobalert_msg+=f"New openings in <a href='{jobalert['CareerURL']}'>{jobalert['CompanyName']}</a>\n* "

            jobalert_msg += "\n* ".join(difference) + "\n\n\n"


            # Write new data to the file
            with open(file_path, 'w') as file:
                file.write('\n'.join(new_data))
        else:
            print("\n\nThere are no difference!!\n\n")



if(jobalert_msg != ''):
    print("\n\n\nJob Alert!!\n\n", jobalert_msg)

    jobalert_msg = "JOB ALERTS!!\n\n" + jobalert_msg


    ## Splitting the message if it is greater than 4k characters. Its said telegram has limit of 4096.
    if len(jobalert_msg) > 4000:
        for x in range(0, len(jobalert_msg), 4000):
            msg = bot.send_message(FOCUX_GROUP_CHATID, jobalert_msg[x:x+4000], parse_mode = 'HTML')
    else:
        msg = bot.send_message(FOCUX_GROUP_CHATID, jobalert_msg, parse_mode = 'HTML')
