# JobAlert

## About
This script is designed to streamline the process of receiving job notifications from any website of your choice. It eliminates the need for manual browsing and checking for updates, especially on websites that do not offer email notification services. While not entirely automated, as the user needs to provide certain tag information, this script still significantly reduces the time and effort required to stay updated on new job listings.

[Click here](#adding-new-websites-for-parsing) to know how to add new website to parse.

> [!NOTE]
> This project is a hobby, so it might not be flawless. If you encounter problems or have ideas for improvement, please share your feedback. Your input is valuable for making this project better.

> [!IMPORTANT]
> Disclaimer: If there are any privacy or legal concerns regarding parsing a website, please inform me. As I am new to this, I want to avoid getting into trouble. It's important to ensure compliance with laws and regulations when scraping websites for data.

## Features
- Scrapping multiple websites in one click
- No more manual browsing and checking for updates.
- Alternative to websites lacking email notification services.
- Telegram notification

## Main Contents

- JobAlert_List.xlsx :window: : This file holds the list of job websites along with the tags needed for scraping. The columns are listed below.
  - **Index**: Used for numbering the entries in the Excel list.
  - **CompanyName**: Unique name of the company. This name must be unique as it is used for the storage file name. If multiple queries are made to the same company, provide a postfix to differentiate.
  - **CareerURL**: Link to the company's career page, including necessary filters if needed.
  - **ShadowRoot_XPATH**: XPATH of the relevant shadow root element (if applicable).
  - **Button_CSS_Sel**: Unique tag and value for detecting the popup button that needs to be closed to proceed with scraping (if applicable).
  - **Job_XPATH**: XPATH to locate the job title.
  - **Job_CSS_Sel**: Unique tag and value for identifying job titles.
  - **LoadTime_s**: Estimated loading time for websites in seconds. Provide a reasonable guess based on manual observation of the website's loading time. This also determines how long the script takes to execute, so not recommended to give very high values
- database :open_file_folder: : This folder stores previously parsed job information.
- JobAlert_Scrapper.py :arrow_forward: : Python script for scraping job listings.
  
## How it works
The script starts by loading the content from an Excel file containing URLs of job websites and the necessary tags for parsing. It then utilizes the Selenium package to create a webdriver and loads each website. The user needs to initially understand any dynamic behaviors of the website. If there are pop-ups such as privacy settings, the script requires the shadow root element information and the button tag information to handle them.

After attempting to close any pop-ups, the script proceeds to parse the job titles. The user should also fill the necessary tag information for scrapping in the Excel file. Once parsed, the script compares the job titles with previously stored information. Any differences are noted. This process is repeated for all websites listed in the Excel file.

Finally, once the scraping and comparison are complete, a notification is sent via Telegram to inform the user of any updates or changes in the job listings

## Adding new websites for parsing

:small_blue_diamond: Find the career website for the company you're interested in. You may need to apply the necessary filters and then copy the URL.

:small_blue_diamond: Open the website to parse, preferably in Incognito mode, to understand how the website behaves for the first time.

:small_blue_diamond: Check if there are any pop-ups blocking the view of the job list. In this case, there are none, so you don't have to mention the ShadowRoot_XPATH and Button_CSS_Sel columns in the Excel file.

:small_blue_diamond: In case there are pop-ups, like on this website, right-click the Accept button and select "Inspect." Then, navigate to the tags related to this button and right-click to copy the JS Path.
```
document.querySelector("#usercentrics-root").shadowRoot.querySelector("#focus-lock-id > div.sc-kpDqfm.ckjpsZ > div > div.sc-eBMEME.dRvQzh > div > div > div.sc-jsJBEP.iXSECa > div > button:nth-child(2)")
```
This provides a hint of the shadowRoot element we are interested in. In this case, it is a div element with the ID "#usercentrics-root". Right-click this element and copy the XPATH. Update this under ShadowRoot_XPATH column.
```
//*[@id="usercentrics-root"]
```
Next, go to the Accept button and copy the unique property in its tag. As shown below, we can choose 'data-testid'. Copy that entire tag with its value and update it in the Button_CSS_Sel column (fill -> data-testid="uc-accept-all-button").
```
<button role="button" data-testid="uc-accept-all-button" class="sc-dcJsrY jXFxaO" style="margin: 0px 6px;">Accept All</button>
```
Once this is done, close the pop-up.

:small_blue_diamond: Right-click the job title and inspect it. Copy the XPATH or identifier that works reliably. Update this in Job_XPATH	:bar_chart: or Job_CSS_Sel accordingly.

:small_blue_diamond: To run just the new website, mark 'x' in the Enable column. Additionally, set PARSE_ALL to false in the script config section.


## Adding Telegram notification

I have created a Telegram bot and a group for my personal job notifications. If you're interested, you can join them. You could also create your own Telegram bot. For more information, [click here](https://core.telegram.org/bots/features#botfather). Once created, note down the unique token. You should also find the chat ID for your user. Refer to this [page](https://diyusthad.com/2022/03/how-to-get-your-telegram-chat-id.html) for more info. Update this information in a text file as shown below:
```
{
    "FOCUX_BOT_TOKEN": "7456380:ASFLVHRUqk-N463diUCEZQ-yfsasfHan0",
    "FOCUX_BOT_CHATID": "18034635"
}
```
Add the path of this file to 'token_path' in the script config section at the beginning. Verify the macros used in bot initialization (telebot.TeleBot) and send_message function.

## Useful Links :
https://www.scrapingbee.com/blog/selenium-python/    

