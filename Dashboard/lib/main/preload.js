// In the preload.js module, we define the functionality to bridge communication between the renderer process and the main process.

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
  openFolder: (path) => ipcRenderer.send('open-folder',path),
  openLink: () => ipcRenderer.send('open-link'),
  quitApp: () => ipcRenderer.send('quit-app'),
  learnGermanButton: () => ipcRenderer.send('learn-german'),
  readGermanFunc: () => ipcRenderer.send('readGermanFunc'),
  watchGermanFunc: () => ipcRenderer.send('watchGermanFunc'),
  jobApplyFunc: () => ipcRenderer.send('jobApplyFunc')
});

