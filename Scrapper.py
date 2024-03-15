from selenium import webdriver 
from selenium.webdriver.chrome.service import Service as ChromeService 
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from webdriver_manager.chrome import ChromeDriverManager 
import time

 
url = 'https://jobs.bosch.com/en/?country=de&positionTypes=e4a3c0db-920d-4356-8ce0-c0b44a54e497,f524b939-fe55-407b-a0b0-e8f4eec28997,f3b92bc1-600d-4d33-9115-5aa27785f52b,8c682ec0-3d28-4e52-b53b-c63d440a32d3' 
 
driver = webdriver.Chrome(service=ChromeService( 
	ChromeDriverManager().install())) 
 
driver.get(url) 
 

time.sleep(5)

 
element = driver.find_elements(By.CLASS_NAME, 'M-JobSearchResultsGroup__list')


for story_element in element:
    print('hi')
    print(story_element.text)
    print(story_element)
