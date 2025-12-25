/**
 * Side Panel Main Controller
 * Manages view routing and communication with background worker
 */

import { RecordingView } from './components/RecordingView.js';
import { BrowseView } from './components/BrowseView.js';

class SidePanelApp {
  constructor() {
    this.currentView = 'browse';
    this.recordingView = null;
    this.browseView = null;
    this.isConnected = false;
  }

  async init() {
    console.log('[SidePanel] Initializing...');

    // Initialize views
    this.recordingView = new RecordingView(this);
    this.browseView = new BrowseView(this);

    await this.recordingView.init();
    await this.browseView.init();

    // Show browse view by default
    this.showView('browse');

    // Check backend connection
    await this.checkConnection();

    // Listen for messages from background worker
    this.listenForMessages();

    console.log('[SidePanel] Initialized');
  }

  /**
   * Show a specific view
   */
  showView(viewName) {
    console.log('[SidePanel] Switching to view:', viewName);

    const recordingView = document.getElementById('recordingView');
    const browseView = document.getElementById('browseView');

    if (viewName === 'recording') {
      recordingView.classList.remove('hidden');
      browseView.classList.add('hidden');
      this.currentView = 'recording';
    } else {
      recordingView.classList.add('hidden');
      browseView.classList.remove('hidden');
      this.currentView = 'browse';
    }
  }

  /**
   * Check backend connection status
   */
  async checkConnection() {
    try {
      const response = await chrome.runtime.sendMessage({
        type: 'GET_WORKFLOWS',
        page: 0,
        size: 1
      });

      this.isConnected = response.success;
      this.updateConnectionIndicator(this.isConnected);
    } catch (error) {
      console.error('[SidePanel] Connection check failed:', error);
      this.isConnected = false;
      this.updateConnectionIndicator(false);
    }
  }

  /**
   * Update connection indicator UI
   */
  updateConnectionIndicator(connected) {
    const indicator = document.getElementById('connectionIndicator');
    if (indicator) {
      if (connected) {
        indicator.classList.remove('offline');
        indicator.classList.add('online');
        indicator.title = 'Connected to backend';
      } else {
        indicator.classList.remove('online');
        indicator.classList.add('offline');
        indicator.title = 'Disconnected from backend';
      }
    }
  }

  /**
   * Listen for messages from background worker
   */
  listenForMessages() {
    chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
      console.log('[SidePanel] Received message:', message.type);

      switch (message.type) {
        case 'RECORDING_STARTED':
          this.recordingView.onRecordingStarted(message.recording);
          break;

        case 'RECORDING_STOPPED':
          this.recordingView.onRecordingStopped(message.actions, message.duration);
          break;

        case 'RECORDING_PAUSED':
          this.recordingView.onRecordingPaused();
          break;

        case 'RECORDING_RESUMED':
          this.recordingView.onRecordingResumed();
          break;

        case 'ACTION_ADDED':
          this.recordingView.onActionAdded(message.action, message.totalActions);
          break;

        case 'PAGE_UPDATED':
          this.recordingView.onPageUpdated(message.url, message.title);
          break;

        default:
          console.debug('[SidePanel] Unhandled message:', message.type);
      }

      sendResponse({ received: true });
    });
  }

  /**
   * Show toast notification
   */
  showToast(message, type = 'info') {
    const container = document.getElementById('toastContainer');
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;

    const icon = type === 'success' ? '✅' : type === 'error' ? '❌' : 'ℹ️';

    toast.innerHTML = `
      <div class="toast-icon">${icon}</div>
      <div class="toast-message">${message}</div>
    `;

    container.appendChild(toast);

    // Remove after 3 seconds
    setTimeout(() => {
      toast.style.animation = 'slideOutRight 0.3s';
      setTimeout(() => toast.remove(), 300);
    }, 3000);
  }

  /**
   * Format duration in mm:ss
   */
  formatDuration(ms) {
    const totalSeconds = Math.floor(ms / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  }

  /**
   * Format relative time
   */
  formatRelativeTime(dateString) {
    if (!dateString) return 'Never';

    const date = new Date(dateString);
    const now = new Date();
    const diffMs = now - date;
    const diffSecs = Math.floor(diffMs / 1000);
    const diffMins = Math.floor(diffSecs / 60);
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffSecs < 60) return 'Just now';
    if (diffMins < 60) return `${diffMins}m ago`;
    if (diffHours < 24) return `${diffHours}h ago`;
    if (diffDays < 7) return `${diffDays}d ago`;

    return date.toLocaleDateString();
  }

  /**
   * Get action icon emoji
   */
  getActionIcon(actionType) {
    const icons = {
      CLICK: '🖱️',
      FILL: '⌨️',
      SELECT: '📋',
      SUBMIT: '📤',
      CHECK: '☑️',
      NAVIGATE: '🔗',
      WAIT: '⏱️',
      WAIT_NAVIGATION: '⏳',
      SCROLL: '📜',
      HOVER: '👆',
      PRESS_KEY: '⌨️',
      CLEAR: '🗑️',
      SCREENSHOT: '📸'
    };

    return icons[actionType] || '•';
  }
}

// Initialize app when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
  const app = new SidePanelApp();
  app.init().catch(error => {
    console.error('[SidePanel] Failed to initialize:', error);
  });

  // Make app globally available for debugging
  window.sidePanelApp = app;
});

// Add CSS animation for toast exit
const style = document.createElement('style');
style.textContent = `
  @keyframes slideOutRight {
    from {
      transform: translateX(0);
      opacity: 1;
    }
    to {
      transform: translateX(100%);
      opacity: 0;
    }
  }
`;
document.head.appendChild(style);
