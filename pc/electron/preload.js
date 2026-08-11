// 鲸鱼娘·汐汐 —— 预加载脚本（安全暴露 IPC 给页面）
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('whalemaid', {
  isElectron: true,

  openOverlay: () => ipcRenderer.send('open-overlay'),
  closeOverlay: () => ipcRenderer.send('close-overlay'),
  toggleClickThrough: () => ipcRenderer.send('overlay-click-through'),

  // 把汐汐说的话转发到悬浮窗显示
  overlaySpeak: (text) => ipcRenderer.send('overlay-speak', String(text).slice(0, 120)),

  // 悬浮窗拖动
  dragStart: () => ipcRenderer.send('overlay-drag-start'),
  dragEnd: () => ipcRenderer.send('overlay-drag-end'),

  // 悬浮窗内监听“说话”事件
  onOverlaySpeak: (cb) => {
    const listener = (_e, text) => cb(text);
    ipcRenderer.on('overlay-speak', listener);
    return () => ipcRenderer.removeListener('overlay-speak', listener);
  }
});
