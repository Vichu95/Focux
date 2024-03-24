// ipcHandlers.js
const { ipcMain, shell, app, BrowserWindow, screen } = require('electron');
const path = require('path');



const main_path = "D:\\Learn\\Projects\\Focux\\Focux\\Dashboard\\lib\\main";
const renderer_path = "D:\\Learn\\Projects\\Focux\\Focux\\Dashboard\\lib\\renderer";

function setupIPCListeners() {
  ipcMain.on('open-folder', (event,path) => {
    shell.openPath(path);
  });

  ipcMain.on('open-link', () => {
    shell.openExternal('https://example.com');
    shell.openExternal('https://google.com');
  });

  ipcMain.on('quit-app', () => {
    app.quit();
  });

  
    ipcMain.on('learn-german', () => {
        // Get the primary display's size
        const primaryDisplay = screen.getPrimaryDisplay();
        const screenWidth = primaryDisplay.size.width;
        const screenHeight = primaryDisplay.size.height;

        // Calculate the width for each window (half of the screen's width)
        const windowWidth = Math.floor(screenWidth / 2);

        // Create the first BrowserWindow instance for the first website
        const mainWindow1 = new BrowserWindow({
            width: windowWidth,
            height: screenHeight,
            x: 0, // Position at the left edge of the screen
            y: 0, // Position at the top edge of the screen
            webPreferences: {
                nodeIntegration: false // Disable Node.js integration for security
            }
        });
        // Load the first website
        mainWindow1.loadURL('https://konjugator.reverso.net/konjugation-deutsch.html');

        // Create the second BrowserWindow instance for the second website
        const mainWindow2 = new BrowserWindow({
            width: windowWidth + 20,
            height: screenHeight,
            x: windowWidth - 20, // Position at the right edge of the screen
            y: 0, // Position at the top edge of the screen
            webPreferences: {
                nodeIntegration: false // Disable Node.js integration for security
            }
        });
        // Load the second website
        mainWindow2.loadURL('https://www.deepl.com/translator');



          // Create secondary windows for each secondary display
        const allScreens = screen.getAllDisplays();
        allScreens.forEach((display, index) => {
            if (index === 0) return; // Skip the primary display


            const secondaryWindowTL = new BrowserWindow({
            width: display.size.width,
            height: display.size.height + 100,
            x: display.bounds.x,
            y: display.bounds.y,

        
            // fullscreen: true,
            title: '',
            autoHideMenuBar: true,
            webPreferences: {
                nodeIntegration: true
            }
            });
            secondaryWindowTL.loadURL('https://german.net/reading/tom/');



            
            const secondaryWindowTR = new BrowserWindow({
                width: display.size.width/2,
                height: display.size.height,
                x: display.bounds.x + display.size.width/2 + 200,
                y: display.bounds.y,            
                // fullscreen: true,
                title: '',
                autoHideMenuBar: true,
                webPreferences: {
                    nodeIntegration: true
                }
                });
                secondaryWindowTR.loadURL('https://chat.openai.com/c/1147723c-43af-44fd-a65d-4545b37fa2b3');


                

            
            const secondaryWindowBL = new BrowserWindow({
                width: display.size.width/2 + 200 ,// + display.size.width/2,
                height: display.size.height/2 - 100,
                x: display.bounds.x,
                y: display.workArea.height -display.size.height/2 - 100,        
                frame: false, // Hide window frame (including title bar)
                title: '',
                autoHideMenuBar: true, 
                // backgroundColor: '#FFFFFF', // Set background color to white
                transparent: true, // Make window transparent
                resizable: false, // Disable window resizing
                alwaysOnTop: true, // Keep window always on top
                webPreferences: {
                    nodeIntegration: true,
                    preload: path.join(main_path, 'preload.js') // Add preload script
                }
                });
                // secondaryWindowBL.loadURL('https://chat.openai.com/c/1147723c-43af-44fd-a65d-4545b37fa2b3');
                secondaryWindowBL.loadFile(renderer_path + '\\learnGermanHome.html');



                 
            const secondaryWindowBR = new BrowserWindow({
                width: display.size.width/2 - 200,
                height: display.size.height/2 - 110,
                x: display.bounds.x + display.size.width/2 + 200,
                y: display.workArea.height -display.size.height/2 - 100, 
                // fullscreen: true,
                frame: false, // Hide window frame (including title bar)
                title: '',
                autoHideMenuBar: true, 
                backgroundColor: '#FFFFFF', // Set background color to white
                transparent: true, // Make window transparent
                resizable: false, // Disable window resizing
                alwaysOnTop: true, // Keep window always on top
                webPreferences: {
                    nodeIntegration: true,
                    preload: path.join(main_path, 'preload.js') // Add preload script
                }
                });
                secondaryWindowBR.loadFile(renderer_path+ '\\TimingDetail.html');


        });




        
  ipcMain.on('readGermanFunc', () => {
    shell.openPath("D:\\Learn\\Infoshare\\LearnGerman");
  });





    });

}

module.exports = { setupIPCListeners };
