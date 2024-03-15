from selenium import webdriver 
from selenium.webdriver.chrome.service import Service as ChromeService 
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from webdriver_manager.chrome import ChromeDriverManager 
import time

 
url = 'https://jobs.bosch.com/en/?country=de&positionTypes=e4a3c0db-920d-4356-8ce0-c0b44a54e497,f524b939-fe55-407b-a0b0-e8f4eec28997,f3b92bc1-600d-4d33-9115-5aa27785f52b,8c682ec0-3d28-4e52-b53b-c63d440a32d3' 
# url = 'https://www.fev.com/en/jobs/?entryLevel=students&country=germany'
url = 'https://valeo.wd3.myworkdayjobs.com/de-DE/valeo_jobs?locationCountry=dcc5b7608d8644b3a93716604e78e995'
 
driver = webdriver.Chrome(service=ChromeService( 
	ChromeDriverManager().install())) 
 
driver.get(url) 
 
driver.implicitly_wait(20)
time.sleep(5)

# driver.execute_script("window.scrollTo(0, document.body.scrollHeight);")

# driver.find_elements(By.XPATH, '//*[@id="uc-btn-accept-banner"]').click()

 
# element = driver.find_elements(By.CLASS_NAME, 'M-JobSearchResultsGroup__list')
# element = driver.find_elements(By.CLASS_NAME, 'items')
element = driver.find_elements(By.CLASS_NAME, 'css-8j5iuw')




# print(element)


for story_element in element:
    print("\n\ntext")
    print(story_element.text)
    print("Story element whole \n")
    print(story_element)
