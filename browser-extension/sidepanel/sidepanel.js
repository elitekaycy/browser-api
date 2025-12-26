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

    // Initialize settings
    this.initSettings();

    // Show browse view by default
    this.showView('browse');

    // Check backend connection
    await this.checkConnection();

    // Listen for messages from background worker
    this.listenForMessages();

    console.log('[SidePanel] Initialized');
  }

  /**
   * Initialize settings UI and handlers
   */
  initSettings() {
    // Settings button
    document.getElementById('settingsBtn').addEventListener('click', () => {
      this.showSettingsModal();
    });

    // Close settings modal
    document.getElementById('closeSettingsModal').addEventListener('click', () => {
      this.hideSettingsModal();
    });

    // Save settings
    document.getElementById('saveSettingsBtn').addEventListener('click', () => {
      this.saveSettings();
    });

    // Reset to default
    document.getElementById('resetSettingsBtn').addEventListener('click', () => {
      this.resetSettings();
    });

    // Close modal on background click
    document.getElementById('settingsModal').addEventListener('click', (e) => {
      if (e.target.id === 'settingsModal') {
        this.hideSettingsModal();
      }
    });
  }

  /**
   * Show settings modal
   */
  async showSettingsModal() {
    const modal = document.getElementById('settingsModal');
    const input = document.getElementById('backendUrl');

    // Load current backend URL
    const result = await chrome.storage.local.get(['backendUrl']);
    input.value = result.backendUrl || 'http://localhost:8080/api/v1';

    modal.classList.remove('hidden');
    setTimeout(() => input.focus(), 100);
  }

  /**
   * Hide settings modal
   */
  hideSettingsModal() {
    document.getElementById('settingsModal').classList.add('hidden');
  }

  /**
   * Save settings
   */
  async saveSettings() {
    const input = document.getElementById('backendUrl');
    const url = input.value.trim();

    // Validate URL
    if (!url) {
      this.showToast('Please enter a valid URL', 'error');
      return;
    }

    try {
      new URL(url); // Validate URL format
    } catch (error) {
      this.showToast('Invalid URL format', 'error');
      return;
    }

    try {
      // Save to storage
      await chrome.storage.local.set({ backendUrl: url });

      // Notify background worker to update API client
      await chrome.runtime.sendMessage({
        type: 'UPDATE_BACKEND_URL',
        url: url
      });

      this.showToast('Settings saved successfully!', 'success');
      this.hideSettingsModal();

      // Recheck connection with new URL
      await this.checkConnection();

      // Reload workflows with new backend
      if (this.browseView) {
        await this.browseView.loadWorkflows();
      }
    } catch (error) {
      console.error('[SidePanel] Failed to save settings:', error);
      this.showToast('Failed to save settings', 'error');
    }
  }

  /**
   * Reset settings to default
   */
  async resetSettings() {
    const defaultUrl = 'http://localhost:8080/api/v1';
    document.getElementById('backendUrl').value = defaultUrl;
    await this.saveSettings();
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
      CLICK: '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" width="18" height="18"><path stroke-linecap="round" stroke-linejoin="round" d="M15.042 21.672L13.684 16.6m0 0l-2.51 2.225.569-9.47 5.227 7.917-3.286-.672zM12 2.25V4.5m5.834.166l-1.591 1.591M20.25 10.5H18M7.757 14.743l-1.59 1.59M6 10.5H3.75m4.007-4.243l-1.59-1.59" /></svg>',
      FILL: '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" width="18" height="18"><path stroke-linecap="round" stroke-linejoin="round" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931zm0 0L19.5 7.125M18 14v4.75A2.25 2.25 0 0115.75 21H5.25A2.25 2.25 0 013 18.75V8.25A2.25 2.25 0 015.25 6H10" /></svg>',
      SELECT: '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" width="18" height="18"><path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>',
      SUBMIT: '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" width="18" height="18"><path stroke-linecap="round" stroke-linejoin="round" d="M6 12L3.269 3.126A59.768 59.768 0 0121.485 12 59.77 59.77 0 013.27 20.876L5.999 12zm0 0h7.5" /></svg>',
      CHECK: '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" width="18" height="18"><path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z" /></svg>',
      NAVIGATE: '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" width="18" height="18"><path stroke-linecap="round" stroke-linejoin="round" d="M13.5 6H5.25A2.25 2.25 0 003 8.25v10.5A2.25 2.25 0 005.25 21h10.5A2.25 2.25 0 0018 18.75V10.5m-10.5 6L21 3m0 0h-5.25M21 3v5.25" /></svg>',
      WAIT: '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" width="18" height="18"><path stroke-linecap="round" stroke-linejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>',
      WAIT_NAVIGATION: '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" width="18" height="18"><path stroke-linecap="round" stroke-linejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0013.803-3.7M4.031 9.865a8.25 8.25 0 0113.803-3.7l3.181 3.182m0-4.991v4.99" /></svg>',
      SCROLL: '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" width="18" height="18"><path stroke-linecap="round" stroke-linejoin="round" d="M8.25 15L12 18.75 15.75 15m-7.5-6L12 5.25 15.75 9" /></svg>',
      HOVER: '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" width="18" height="18"><path stroke-linecap="round" stroke-linejoin="round" d="M15.042 21.672L13.684 16.6m0 0l-2.51 2.225.569-9.47 5.227 7.917-3.286-.672z" /></svg>',
      PRESS_KEY: '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" width="18" height="18"><path stroke-linecap="round" stroke-linejoin="round" d="M7.5 8.25h9m-9 3H12m-9.75 1.51c0 1.6 1.123 2.994 2.707 3.227 1.129.166 2.27.293 3.423.379.35.026.67.21.865.501L12 21l2.755-4.133a1.14 1.14 0 01.865-.501 48.172 48.172 0 003.423-.379c1.584-.233 2.707-1.626 2.707-3.228V6.741c0-1.602-1.123-2.995-2.707-3.228A48.394 48.394 0 0012 3c-2.392 0-4.744.175-7.043.513C3.373 3.746 2.25 5.14 2.25 6.741v6.018z" /></svg>',
      CLEAR: '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" width="18" height="18"><path stroke-linecap="round" stroke-linejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" /></svg>',
      SCREENSHOT: '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" width="18" height="18"><path stroke-linecap="round" stroke-linejoin="round" d="M6.827 6.175A2.31 2.31 0 015.186 7.23c-.38.054-.757.112-1.134.175C2.999 7.58 2.25 8.507 2.25 9.574V18a2.25 2.25 0 002.25 2.25h15A2.25 2.25 0 0021.75 18V9.574c0-1.067-.75-1.994-1.802-2.169a47.865 47.865 0 00-1.134-.175 2.31 2.31 0 01-1.64-1.055l-.822-1.316a2.192 2.192 0 00-1.736-1.039 48.774 48.774 0 00-5.232 0 2.192 2.192 0 00-1.736 1.039l-.821 1.316z" /><path stroke-linecap="round" stroke-linejoin="round" d="M16.5 12.75a4.5 4.5 0 11-9 0 4.5 4.5 0 019 0zM18.75 10.5h.008v.008h-.008V10.5z" /></svg>',
      EXTRACT: '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" width="18" height="18"><path stroke-linecap="round" stroke-linejoin="round" d="M3 16.5v2.25A2.25 2.25 0 005.25 21h13.5A2.25 2.25 0 0021 18.75V16.5M16.5 12L12 16.5m0 0L7.5 12m4.5 4.5V3" /></svg>'
    };

    return icons[actionType] || '<svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" width="18" height="18"><path stroke-linecap="round" stroke-linejoin="round" d="M9.879 7.519c1.171-1.025 3.071-1.025 4.242 0 1.172 1.025 1.172 2.687 0 3.712-.203.179-.43.326-.67.442-.745.361-1.45.999-1.45 1.827v.75M21 12a9 9 0 11-18 0 9 9 0 0118 0zm-9 5.25h.008v.008H12v-.008z" /></svg>';
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
