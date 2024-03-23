# Focux -> JobAlert

## About
This script is designed to streamline the process of receiving job notifications from any website of your choice. It eliminates the need for manual browsing and checking for updates, especially on websites that do not offer email notification services. While not entirely automated, as the user needs to provide certain tag information, this script still significantly reduces the time and effort required to stay updated on new job listings.


## Features
- Scrapping multiple websites in one click
- No more manual browsing and checking for updates.
- Alternative to websites lacking email notification services.
- Telegram notification

> [!NOTE]
> This project is a hobby, so it might not be flawless. If you encounter problems or have ideas for improvement, please share your feedback. Your input is valuable for making this project better.

## Main Contents

- JobAlert_List.xlsx : This file holds the list of job websites along with the tags needed for scraping.
- database : This folder stores previously parsed job information.
- JobAlert_Scrapper.py : Python script for scraping job listings.
  
## How it works
The script starts by loading the content from an Excel file containing URLs of job websites and the necessary tags for parsing. It then utilizes the Selenium package to create a webdriver and loads each website. The user needs to initially understand any dynamic behaviors of the website. If there are pop-ups such as privacy settings, the script requires the shadow root element information and the button tag information to handle them.

After attempting to close any pop-ups, the script proceeds to parse the job titles. The user should also fill in this information in the Excel file. Once parsed, the script compares the job titles with previously stored information. Any differences are noted. This process is repeated for all websites listed in the Excel file.

Finally, once the scraping and comparison are complete, a notification is sent via Telegram to inform the user of any updates or changes in the job listings

Useful Links :
https://www.scrapingbee.com/blog/selenium-python/    
