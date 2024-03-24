// ipcHandlers.js
const { ipcMain, shell, app } = require('electron');

function setupIPCListeners() {
  ipcMain.on('open-folder', () => {
    shell.openPath('/path/to/your/folder');
  });

  ipcMain.on('open-link', () => {
    shell.openExternal('https://example.com');
  });

  ipcMain.on('quit-app', () => {
    app.quit();
  });
}

module.exports = { setupIPCListeners };
