const { ipcMain, shell, app, BrowserWindow, screen } = require('electron');
const { spawn } = require('child_process');

const path = require('path');

const main_path = "D:\\Learn\\Projects\\Focux\\Focux\\Dashboard\\lib\\main";
const renderer_path = "D:\\Learn\\Projects\\Focux\\Focux\\Dashboard\\lib\\renderer";



// const { setupIPCListeners } = require(utilis_path + '/ipcHandlers.js');


function createWindows(action) {
  // Logic for creating windows based on the action
  switch (action) {
    case 'learnGerman':
      createLearnGermanWindows();
      break;
    case 'readGerman':
      createReadGermanWindows();
      break;
    case 'watchGerman':
        createWatchGermanWindows();
        break;
    // Add more cases for other actions
  }
}

function createLearnGermanWindows() {
 // Get the primary display's size
 const primaryDisplay = screen.getPrimaryDisplay();
 const screenWidth = primaryDisplay.size.width;
 const screenHeight = primaryDisplay.size.height;

 // Calculate the width for each window (half of the screen's width)
 const windowWidth = Math.floor(screenWidth / 2);

 // Create the first BrowserWindow instance for the first website
 const mainWindow1 = new BrowserWindow({
     width: windowWidth -50,
     height: screenHeight * 0.85,
     x: 0, // Position at the left edge of the screen
     y: 0, // Position at the top edge of the screen
     frame: false, // Hide window frame (including title bar)
     title: '',
     autoHideMenuBar: true, 
     alwaysOnTop: true, // Keep window always on top
     webPreferences: {
         nodeIntegration: false // Disable Node.js integration for security
     }
 });
 // Load the first website
 mainWindow1.loadURL('https://chat.openai.com/c/1147723c-43af-44fd-a65d-4545b37fa2b3');

 
 // Create the second BrowserWindow instance for the second website
 const mainWindow2 = new BrowserWindow({
     width: windowWidth + 50,
     height: screenHeight * 0.85,
     x: windowWidth - 50, // Position at the right edge of the screen
     y: 0, // Position at the top edge of the screen
     frame: false, // Hide window frame (including title bar)
     title: '',
     autoHideMenuBar: true, 
     alwaysOnTop: true, // Keep window always on top
     webPreferences: {
         nodeIntegration: false // Disable Node.js integration for security
     }
 });
 // Load the second website
 mainWindow2.loadURL('https://www.deepl.com/translator');

 
 // Create the second BrowserWindow instance for the second website
 const mainWindow3 = new BrowserWindow({
    width: screenWidth,
    height: screenHeight * 0.25,
    x: 0, // Position at the right edge of the screen
    y: 600, // Position at the top edge of the screen
    frame: false, // Hide window frame (including title bar)
    title: '',
    autoHideMenuBar: true, 
    alwaysOnTop: true, // Keep window always on top
    webPreferences: {
                nodeIntegration: true,
                preload: path.join(main_path, 'preload.js') // Add preload script
    }
});
// Load the second website
mainWindow3.loadFile(renderer_path + '\\learnGermanHome.html');


   // Create secondary windows for each secondary display
 const allScreens = screen.getAllDisplays();
 allScreens.forEach((display, index) => {
     if (index === 0) return; // Skip the primary display


     // const secondaryWindowTL = new BrowserWindow({
     // width: display.size.width - 450,
     // height: display.size.height + 100,
     // x: display.bounds.x,
     // y: display.bounds.y,

 
     // // fullscreen: true,
     // title: '',
     // autoHideMenuBar: true,
     // webPreferences: {
     //     nodeIntegration: true
     // }
     // });
     // secondaryWindowTL.loadURL('https://german.net/reading/tom/');


// //--------------
//      const scaleFactor = display.scaleFactor;
//      const displayWidth = display.size.width;
//      const displayHeight = display.size.height;

//      console.log(`Display ${index}:`);
//      console.log(`  Scale Factor: ${scaleFactor}`);
//      console.log(`  Width: ${displayWidth}, Adjusted Width: ${displayWidth}`);
//      console.log(`  Height: ${displayHeight}, Adjusted Height: ${displayHeight}`);
//      console.log(`  Bounds: ${JSON.stringify(display.bounds)}`);

//      // Create the left window
//      const secondaryWindowLeft = new BrowserWindow({
//          width: displayWidth / 2,
//          height: displayHeight,
//          x: display.bounds.x,
//          y: display.bounds.y,
//          frame: false,
//          title: '',
//          autoHideMenuBar: true,
//          alwaysOnTop: true,
//          webPreferences: {
//              nodeIntegration: true
//          }
//      });
//      secondaryWindowLeft.loadURL('https://example-left.com'); // Replace with your URL

//      // Create the right window
//      const secondaryWindowRight = new BrowserWindow({
//          width: displayWidth / 2,
//          height: displayHeight,
//          x: display.bounds.x + displayWidth / 2,
//          y: display.bounds.y,
//          frame: false,
//          title: '',
//          autoHideMenuBar: true,
//          alwaysOnTop: true,
//          webPreferences: {
//              nodeIntegration: true
//          }
//      });
//      secondaryWindowRight.loadURL('https://example-right.com'); // Replace with your URL

//      // Adjust the bounds using setBounds to consider the scaling factor
//      secondaryWindowLeft.setBounds({
//          x: display.bounds.x,
//          y: display.bounds.y,
//          width: Math.floor(displayWidth / 2 * scaleFactor),
//          height: Math.floor(displayHeight * scaleFactor)
//      });

//      secondaryWindowRight.setBounds({
//          x: Math.floor(display.bounds.x + displayWidth / 2 * scaleFactor),
//          y: display.bounds.y,
//          width: Math.floor(displayWidth / 2 * scaleFactor),
//          height: Math.floor(displayHeight * scaleFactor)
//      });


// //--------------
     
     // const secondaryWindowTR = new BrowserWindow({
     //     width: display.size.width/2 - 25,
     //     height: display.size.height,
     //     x: display.bounds.x + display.size.width/2 + 220,
     //     y: display.bounds.y,            
     //     frame: false, // Hide window frame (including title bar)
     //     title: '',
     //     autoHideMenuBar: true, 
     //     alwaysOnTop: true, // Keep window always on top
     //     webPreferences: {
     //         nodeIntegration: true
     //     }
     //     });
     //     secondaryWindowTR.loadURL('https://chat.openai.com/c/1147723c-43af-44fd-a65d-4545b37fa2b3');


         

     
     // const secondaryWindowBL = new BrowserWindow({
     //     width: display.size.width/2 + 200 ,// + display.size.width/2,
     //     height: display.size.height/2 - 100,
     //     x: display.bounds.x,
     //     y: display.workArea.height -display.size.height/2 - 100,        
     //     frame: false, // Hide window frame (including title bar)
     //     title: '',
     //     autoHideMenuBar: true, 
     //     // backgroundColor: '#FFFFFF', // Set background color to white
     //     transparent: true, // Make window transparent
     //     resizable: false, // Disable window resizing
     //     alwaysOnTop: true, // Keep window always on top
     //     webPreferences: {
     //         nodeIntegration: true,
     //         preload: path.join(main_path, 'preload.js') // Add preload script
     //     }
     //     });
     //     // secondaryWindowBL.loadURL('https://chat.openai.com/c/1147723c-43af-44fd-a65d-4545b37fa2b3');
     //     secondaryWindowBL.loadFile(renderer_path + '\\learnGermanHome.html');



          
     // const secondaryWindowBR = new BrowserWindow({
     //     width: display.size.width/2 - 200,
     //     height: display.size.height/2 - 110,
     //     x: display.bounds.x + display.size.width/2 + 200,
     //     y: display.workArea.height -display.size.height/2 - 100, 
     //     // fullscreen: true,
     //     frame: false, // Hide window frame (including title bar)
     //     title: '',
     //     autoHideMenuBar: true, 
     //     backgroundColor: '#FFFFFF', // Set background color to white
     //     transparent: true, // Make window transparent
     //     resizable: false, // Disable window resizing
     //     alwaysOnTop: true, // Keep window always on top
     //     webPreferences: {
     //         nodeIntegration: true,
     //         preload: path.join(main_path, 'preload.js') // Add preload script
     //     }
     //     });
     //     secondaryWindowBR.loadFile(renderer_path+ '\\TimingDetail.html');


 }); //foreach disaply

}

function createReadGermanWindows() {
 
          // Create secondary windows for each secondary display
          const allScreens = screen.getAllDisplays();
          allScreens.forEach((display, index) => {
              if (index === 0) return; // Skip the primary display
  
              const scaleFactor = display.scaleFactor;
                 const displayWidth = display.size.width;
                 const displayHeight = display.size.height;

  
              const secondaryWindowTL = new BrowserWindow({
       
              width: display.size.width,
              height: display.size.height,
              x: display.bounds.x,
              y: display.bounds.y,
            //   fullscreen: true,
              title: '',
              autoHideMenuBar: false,
              webPreferences: {
                  nodeIntegration: true
              }
              });
              secondaryWindowTL.loadFile(renderer_path + '\\readGermanHome.html');
              
              // Open the window maximized
              secondaryWindowTL.maximize();


              
                 // Adjust the bounds using setBounds to consider the scaling factor
            secondaryWindowTL.setBounds({
            x: display.bounds.x,
            y: display.bounds.y,
            width: Math.floor(displayWidth  * scaleFactor),
            height: Math.floor(displayHeight * scaleFactor)
                });

            })


}



function createWatchGermanWindows() {

  shell.openExternal(renderer_path + '\\watchGermanHome.html');
}

module.exports = { createWindows };
