// ipcHandlers.js
const { ipcMain, shell, app, BrowserWindow, screen } = require('electron');
const { spawn } = require('child_process');

const path = require('path');

const { createWindows } = require('./windowHandler'); // Import window handling functions

const main_path = "D:\\Learn\\Projects\\Focux\\Focux\\Dashboard\\lib\\main";
const renderer_path = "D:\\Learn\\Projects\\Focux\\Focux\\Dashboard\\lib\\renderer";



function setupIPCListeners() 
{

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
    // Call a function to handle learning German
    createWindows('learnGerman');
  });


        
  ipcMain.on('readGermanFunc', () => {
    createWindows('readGerman');
  });

  
  ipcMain.on('watchGermanFunc', () => {
    createWindows('watchGerman');    
    });

    ipcMain.on('openVocabPracFunc', () => {
    
    

      // Create full screen secondary window
      const allScreens = screen.getAllDisplays();
      allScreens.forEach((display, index) => {
          if (index === 0) return; // Skip the primary display
  
  
          const secondaryWindow = new BrowserWindow({
            width: display.size.width,
            height: display.size.height,
            x: display.bounds.x,
            y: display.bounds.y,
  
      
          fullscreen: true,
          title: '',
          //autoHideMenuBar: true,
          alwaysOnTop: true, // Keep window always on top
          webPreferences: {
              nodeIntegration: true
          }
          });
          secondaryWindow.loadFile(renderer_path+ '\\Open3pdf.html');
  
        })
  
  
      });
      

    ipcMain.on('openLocalHTMLFunc', () => {    
    

      // Create full screen secondary window
      const allScreens = screen.getAllDisplays();
      allScreens.forEach((display, index) => {
          if (index === 0) return; // Skip the primary display
  
  
          const secondaryWindow = new BrowserWindow({
            width: display.size.width,
            height: display.size.height,
            x: display.bounds.x,
            y: display.bounds.y,
  
      
          fullscreen: true,
          title: '',
          //autoHideMenuBar: true,
          alwaysOnTop: true, // Keep window always on top
          webPreferences: {
              nodeIntegration: true
          }
          });
          
          // Set the default zoom level (adjust the value as needed)
          secondaryWindow.webContents.setZoomLevel(1.6);

          secondaryWindow.loadFile(renderer_path+ '\\readDBZ_Manga.html');
  
        })
  
  
      });




     
  ipcMain.on('jobApplyFunc', () => { 

        
    shell.openPath("D:\\My\\Personal Files\\Job\\Applying\\ApplyShortcut");
    shell.openPath("D:\\Learn\\Anhalt\\0_Study\\5_Thesis\\Applications\\Applied.xlsx");
    shell.openExternal('https://www.linkedin.com/notifications/?filter=all');
    shell.openExternal('https://embeddedsysteminfoshare.wordpress.com/');


    
         scriptPath = "D:\\Learn\\Projects\\Focux\\Focux\\JobAlert\\runJobAlert.bat"
        // Run the batch script
            const bat = spawn(scriptPath);
             // Spawn a new process to run the batch script
            // const bat = spawn(scriptPath, [], { detached: true, stdio: 'ignore' });

            // // Detach the child process to let it run independently from the parent process
            // bat.unref();


            // Log output from the batch script
            bat.stdout.on('data', (data) => {
                console.log(data.toString());
                
                // event.reply('batch-script-output', data.toString());
            });

            // Log errors from the batch script
            bat.stderr.on('data', (data) => {
                console.error(data.toString());
                // event.reply('batch-script-error', data.toString());
            });

            // Handle script completion
            bat.on('exit', (code) => {
                console.log(`Batch script exited with code ${code}`);
                // event.reply('batch-script-exit', code);
            });




          app.quit();
            


            

    });





}

module.exports = { setupIPCListeners };
