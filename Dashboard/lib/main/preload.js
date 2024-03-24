// In the preload.js module, we define the functionality to bridge communication between the renderer process and the main process.

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
  openFolder: () => ipcRenderer.send('open-folder'),
  openLink: () => ipcRenderer.send('open-link'),
  quitApp: () => ipcRenderer.send('quit-app')
});

