// 鲸鱼娘·汐汐 —— Electron 主进程
// 负责：主窗口 / 透明桌面悬浮窗 / IPC / 全局快捷键
const { app, BrowserWindow, ipcMain, globalShortcut, screen } = require('electron');
const path = require('path');
const fs = require('fs');

// 打包后 html 会复制到 electron 目录内；开发模式用上级目录的网页版
const PAGE = fs.existsSync(path.join(__dirname, 'whale-maid-pc.html'))
  ? path.join(__dirname, 'whale-maid-pc.html')
  : path.join(__dirname, '..', 'whale-maid-pc.html');

let mainWin = null;
let overlayWin = null;
let overlayDragging = false;
let dragOffset = { x: 0, y: 0 };
let dragTimer = null;

function createMainWindow(){
  mainWin = new BrowserWindow({
    width: 1240,
    height: 820,
    minWidth: 900,
    minHeight: 620,
    title: '鲸鱼娘·汐汐',
    backgroundColor: '#081426',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      webSecurity: false // 允许本地渲染进程直连 DeepSeek API（个人本机应用）
    }
  });
  mainWin.loadFile(PAGE);
  mainWin.on('closed', () => { mainWin = null; });
}

function createOverlay(){
  if (overlayWin && !overlayWin.isDestroyed()){ overlayWin.show(); return; }
  const { width: sw, height: sh } = screen.getPrimaryDisplay().workAreaSize;
  overlayWin = new BrowserWindow({
    width: 230,
    height: 360,
    x: sw - 260,
    y: sh - 420,
    transparent: true,
    frame: false,
    resizable: false,
    alwaysOnTop: true,
    skipTaskbar: true,
    hasShadow: false,
    backgroundColor: '#00000000',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      webSecurity: false
    }
  });
  overlayWin.setAlwaysOnTop(true, 'screen-saver');
  overlayWin.loadFile(PAGE, { query: { overlay: '1' } });
  overlayWin.on('closed', () => {
    overlayWin = null;
    if (dragTimer){ clearInterval(dragTimer); dragTimer = null; }
  });
}

function overlayClickThrough(on){
  if (!overlayWin || overlayWin.isDestroyed()) return;
  overlayWin.setIgnoreMouseEvents(!!on, { forward: true });
}

/* ---------- IPC ---------- */
ipcMain.on('open-overlay', () => createOverlay());
ipcMain.on('close-overlay', () => { if (overlayWin) overlayWin.close(); });
ipcMain.on('overlay-click-through', () => {
  if (!overlayWin) return;
  const ignoring = overlayWin.isIgnoringMouseEvents();
  overlayClickThrough(!ignoring);
});
ipcMain.on('overlay-speak', (e, text) => {
  if (overlayWin && overlayWin.webContents) overlayWin.webContents.send('overlay-speak', text);
});
ipcMain.on('overlay-drag-start', (e) => {
  if (!overlayWin) return;
  const cursor = screen.getCursorScreenPoint();
  const [wx, wy] = overlayWin.getPosition();
  dragOffset = { x: cursor.x - wx, y: cursor.y - wy };
  overlayDragging = true;
  if (dragTimer) clearInterval(dragTimer);
  dragTimer = setInterval(() => {
    if (!overlayDragging || !overlayWin || overlayWin.isDestroyed()) return;
    const p = screen.getCursorScreenPoint();
    overlayWin.setPosition(p.x - dragOffset.x, p.y - dragOffset.y);
  }, 12);
});
ipcMain.on('overlay-drag-end', () => {
  overlayDragging = false;
  if (dragTimer){ clearInterval(dragTimer); dragTimer = null; }
});

/* ---------- 全局快捷键 ---------- */
function registerShortcuts(){
  // Ctrl+Shift+O：切换悬浮窗鼠标穿透
  globalShortcut.register('CommandOrControl+Shift+O', () => {
    if (!overlayWin){ createOverlay(); return; }
    overlayClickThrough(!overlayWin.isIgnoringMouseEvents());
  });
}

app.whenReady().then(() => {
  createMainWindow();
  registerShortcuts();
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createMainWindow();
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
app.on('will-quit', () => globalShortcut.unregisterAll());
