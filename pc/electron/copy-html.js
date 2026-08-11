// 打包前把网页版 HTML 复制进 electron 目录（electron-builder 只能打包 app 目录内的文件）
const fs = require('fs');
const path = require('path');
const src = path.join(__dirname, '..', 'whale-maid-pc.html');
const dst = path.join(__dirname, 'whale-maid-pc.html');
fs.copyFileSync(src, dst);
console.log('✅ HTML copied: ' + dst);
