const { app, BrowserWindow, ipcMain, shell , screen} = require('electron');
const path = require('path');


const renderer_path = "../renderer";
const utilis_path = "../utilis";

//IMPORT
const { setupIPCListeners } = require(utilis_path + '/ipcHandlers.js');


//////////////////////////////////
////   Creating the windows
//////////////////////////////////
let mainWindow;
function createMainWindow() {
  const mainScreen = screen.getPrimaryDisplay();
  const allScreens = screen.getAllDisplays();
  
  // Create main window
  mainWindow = new BrowserWindow({
    width: 800,
    height: 600,
    fullscreen: true,
    title: '',
    autoHideMenuBar: true,
    webPreferences: {
      nodeIntegration: true,
      preload: path.join(__dirname, 'preload.js') // Add preload script
    }
  });
  mainWindow.loadFile(path.join(__dirname, renderer_path + '/index.html'));

  // Create secondary windows for each secondary display
  allScreens.forEach((display, index) => {
    if (index === 0) return; // Skip the primary display
    const secondaryWindow = new BrowserWindow({
      width: display.size.width,
      height: display.size.height,
      x: display.bounds.x,
      y: display.bounds.y,
      fullscreen: true,
      title: '',
      autoHideMenuBar: true,
      backgroundColor: '#000000', // Set background color to white
      webPreferences: {
        nodeIntegration: true
      }
    });
    secondaryWindow.loadURL('about:blank'); // Load a blank page
  });
}

app.whenReady().then(() => {
  createMainWindow();
  setupIPCListeners(); // Set up IPC event listeners
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    createMainWindow();
  }
});

