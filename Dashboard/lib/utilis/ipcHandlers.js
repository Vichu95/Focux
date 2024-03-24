// ipcHandlers.js
const { ipcMain, shell, app } = require('electron');

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
    shell.openPath("D:\\Learn\\Infoshare\\LearnGerman");
    shell.openExternal('https://konjugator.reverso.net/konjugation-deutsch.html');
    app.quit();
  });

}

module.exports = { setupIPCListeners };
