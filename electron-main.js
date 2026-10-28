const { app, BrowserWindow, session, globalShortcut, shell } = require('electron');
const path = require('path');

let mainWindow = null;

// Linux AppImage & Wayland/PipeWire compatibility flags
if (process.platform === 'linux') {
  app.commandLine.appendSwitch('no-sandbox');
  app.commandLine.appendSwitch('disable-setuid-sandbox');
}
app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required');

function configureTidalCdnInterceptor() {
  const ses = session.defaultSession;

  // 1. Rewrite outgoing Origin & Referer headers for all Tidal CDN & manifest requests
  // so sp-ad-cf.audio.tidal.com, sp-ad-fa.audio.tidal.com, lgf.audio.tidal.com, and
  // im-fa.manifest.tidal.com treat every fetch() as coming from https://listen.tidal.com
  ses.webRequest.onBeforeSendHeaders(
    {
      urls: [
        '*://*.tidal.com/*',
        '*://*.audio.tidal.com/*',
        '*://*.manifest.tidal.com/*'
      ]
    },
    (details, callback) => {
      const requestHeaders = { ...details.requestHeaders };
      requestHeaders['Origin'] = 'https://listen.tidal.com';
      requestHeaders['Referer'] = 'https://listen.tidal.com/';
      callback({ requestHeaders });
    }
  );

  // 2. Inject permissive CORS response headers on Tidal CDN & h.shy.cx responses
  // so MediaSource Extensions (MSE) can read raw fMP4 FLAC/AAC arrayBuffers directly
  ses.webRequest.onHeadersReceived(
    {
      urls: [
        '*://*.tidal.com/*',
        '*://*.audio.tidal.com/*',
        '*://*.manifest.tidal.com/*',
        '*://h.shy.cx/*'
      ]
    },
    (details, callback) => {
      const responseHeaders = { ...(details.responseHeaders || {}) };
      delete responseHeaders['access-control-allow-origin'];
      delete responseHeaders['Access-Control-Allow-Origin'];
      responseHeaders['Access-Control-Allow-Origin'] = ['*'];
      responseHeaders['Access-Control-Allow-Methods'] = ['GET, HEAD, POST, DELETE, OPTIONS'];
      responseHeaders['Access-Control-Allow-Headers'] = ['*'];
      responseHeaders['Access-Control-Expose-Headers'] = ['Content-Length, Content-Range, Content-Type, Accept-Ranges, ETag'];
      callback({ responseHeaders });
    }
  );
}

function createMainWindow() {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 880,
    minHeight: 600,
    backgroundColor: '#030604',
    title: 'hifi-tui // h.shy.cx',
    icon: path.join(__dirname, 'build', 'icon.png'),
    autoHideMenuBar: true,
    webPreferences: {
      nodeIntegration: false,
      contextIsolation: true,
      webSecurity: false, // Bypasses renderer-level CORS checks inside the desktop wrapper
      spellcheck: false,
      backgroundThrottling: false // Keep gapless queue & MSE pumping active when minimized
    }
  });

  mainWindow.loadFile(path.join(__dirname, 'index.html'));

  // Open external links or direct file downloads in the default system browser
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('http://') || url.startsWith('https://')) {
      shell.openExternal(url);
    }
    return { action: 'deny' };
  });

  mainWindow.on('closed', () => {
    mainWindow = null;
  });
}

function registerMediaKeys() {
  try {
    globalShortcut.register('MediaPlayPause', () => {
      mainWindow?.webContents.executeJavaScript('typeof togglePlayPause === "function" && togglePlayPause();');
    });
    globalShortcut.register('MediaNextTrack', () => {
      mainWindow?.webContents.executeJavaScript('typeof playNextTrack === "function" && playNextTrack(false);');
    });
    globalShortcut.register('MediaPreviousTrack', () => {
      mainWindow?.webContents.executeJavaScript('typeof playPrevTrack === "function" && playPrevTrack();');
    });
  } catch (e) {
    console.warn('Media shortcut registration skipped:', e.message);
  }
}

app.whenReady().then(() => {
  configureTidalCdnInterceptor();
  createMainWindow();
  registerMediaKeys();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createMainWindow();
    }
  });
});

app.on('will-quit', () => {
  globalShortcut.unregisterAll();
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});
