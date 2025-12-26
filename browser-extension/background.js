/**
 * Background Service Worker for Workflow Recorder Extension
 * Handles communication between content scripts, side panel, and backend API
 */

import { WorkflowAPIClient } from './lib/api-client.js';

// Initialize API client (URL will be loaded from storage)
let apiClient = new WorkflowAPIClient('http://localhost:8080/api/v1');

// Load backend URL from storage and initialize API client
async function initializeAPIClient() {
  const result = await chrome.storage.local.get(['backendUrl']);
  const backendUrl = result.backendUrl || 'http://localhost:8080/api/v1';
  apiClient = new WorkflowAPIClient(backendUrl);
  console.log('[Background] API client initialized with URL:', backendUrl);
}

// Initialize on startup
initializeAPIClient();

// State management
let currentRecording = {
  isRecording: false,
  isPaused: false,
  actions: [],
  startTime: null,
  currentUrl: null,
  currentTitle: null
};

// Cache for workflows
let workflowCache = [];

// Initialize extension
chrome.runtime.onInstalled.addListener(() => {
  console.log('Workflow Recorder installed');

  // Enable side panel on extension icon click
  chrome.sidePanel.setPanelBehavior({ openPanelOnActionClick: true });
});

// Handle navigation during recording - re-inject content script
chrome.webNavigation.onCommitted.addListener(async (details) => {
  // Only handle main frame navigations (not iframes)
  if (details.frameId !== 0) {
    return;
  }

  // Only handle if recording is active on this specific tab
  if (!currentRecording.isRecording || currentRecording.tabId !== details.tabId) {
    return;
  }

  console.log('[Background] Page navigated during recording to:', details.url);

  // Wait for page to start loading
  await new Promise(resolve => setTimeout(resolve, 1000));

  // Re-inject content script to resume recording on the new page
  try {
    console.log('[Background] Re-injecting content script after navigation...');

    await chrome.scripting.executeScript({
      target: { tabId: details.tabId },
      files: ['content.js']
    });

    // Wait for script to initialize
    await new Promise(resolve => setTimeout(resolve, 500));

    // Restart recording on the new page
    await chrome.tabs.sendMessage(details.tabId, { type: 'START_RECORDING' });

    console.log('[Background] Recording resumed on new page');

    // Update current URL and notify side panel
    const tab = await chrome.tabs.get(details.tabId);
    currentRecording.currentUrl = tab.url;
    currentRecording.currentTitle = tab.title;

    await broadcastToSidePanel({
      type: 'PAGE_UPDATED',
      url: tab.url,
      title: tab.title
    });

  } catch (error) {
    console.error('[Background] Failed to re-inject content script after navigation:', error);
    // Don't stop recording, just log the error
  }
});

// Message handler - central hub for all communication
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  console.log('[Background] Received message:', message.type, message);

  // Handle async responses
  const handleAsync = async () => {
    try {
      switch (message.type) {
        // Recording control messages
        case 'START_RECORDING':
          // When called from side panel, sender.tab will be undefined
          // Get the actual active tab to record
          return await handleStartRecording(message.tabId);

        case 'STOP_RECORDING':
          return await handleStopRecording();

        case 'PAUSE_RECORDING':
          return await handlePauseRecording();

        case 'RESUME_RECORDING':
          return await handleResumeRecording();

        // Action captured from content script
        case 'ACTION_CAPTURED':
          return await handleActionCaptured(message.action);

        // Navigation event from content script
        case 'NAVIGATION_EVENT':
          return await handleNavigationEvent(message.url, message.title);

        // Workflow management
        case 'SAVE_WORKFLOW':
          return await handleSaveWorkflow(message.workflow);

        case 'GET_WORKFLOWS':
          return await handleGetWorkflows(message.page, message.size);

        case 'GET_WORKFLOW':
          return await handleGetWorkflow(message.id);

        case 'DELETE_WORKFLOW':
          return await handleDeleteWorkflow(message.id);

        case 'SEARCH_WORKFLOWS':
          return await handleSearchWorkflows(message.query);

        // Workflow replay
        case 'REPLAY_WORKFLOW':
          return await handleReplayWorkflow(message.workflowId, message.parameters, message.tabId);

        // Get current recording state
        case 'GET_RECORDING_STATE':
          return {
            success: true,
            recording: currentRecording
          };

        // Update backend URL
        case 'UPDATE_BACKEND_URL':
          return await handleUpdateBackendURL(message.url);

        default:
          console.warn('[Background] Unknown message type:', message.type);
          return { success: false, error: 'Unknown message type' };
      }
    } catch (error) {
      console.error('[Background] Error handling message:', error);
      return { success: false, error: error.message };
    }
  };

  // Execute async handler and send response
  handleAsync().then(sendResponse);

  // Keep channel open for async response
  return true;
});

/**
 * Start recording workflow
 */
async function handleStartRecording(tabId) {
  console.log('[Background] START_RECORDING called with tabId:', tabId);

  // Get the tab to record
  let tab;
  if (typeof tabId === 'number') {
    // Tab ID was provided
    tab = await chrome.tabs.get(tabId);
  } else {
    // No tab ID provided, find the active tab in the last focused window
    const tabs = await chrome.tabs.query({ active: true, lastFocusedWindow: true });
    // Filter out side panel and extension pages
    const webTabs = tabs.filter(t => t.url && !t.url.startsWith('chrome-extension://'));

    if (webTabs.length === 0) {
      return { success: false, error: 'No active web page found. Please open a webpage first.' };
    }

    tab = webTabs[0];
  }

  if (!tab) {
    return { success: false, error: 'No active tab' };
  }

  console.log('[Background] Recording tab:', tab.id, tab.url);

  // Check if the page is a restricted page
  if (tab.url.startsWith('chrome://') || tab.url.startsWith('chrome-extension://') ||
      tab.url.startsWith('edge://') || tab.url.startsWith('about:')) {
    return {
      success: false,
      error: 'Cannot record on browser internal pages. Please navigate to a regular website.'
    };
  }

  currentRecording = {
    isRecording: true,
    isPaused: false,
    actions: [],
    startTime: Date.now(),
    currentUrl: tab.url,
    currentTitle: tab.title,
    tabId: tab.id
  };

  // Ensure content script is loaded and ready
  let contentScriptReady = false;
  let injectionAttempted = false;

  // First, try to ping the content script
  try {
    const pingResponse = await chrome.tabs.sendMessage(tab.id, { type: 'PING' });
    contentScriptReady = pingResponse && pingResponse.loaded;
    console.log('[Background] Content script already loaded:', contentScriptReady);
  } catch (pingError) {
    console.log('[Background] Content script not responding, will attempt injection');
  }

  // If content script is not ready, inject it
  if (!contentScriptReady) {
    try {
      console.log('[Background] Injecting content script into tab', tab.id);

      // Inject the content script
      await chrome.scripting.executeScript({
        target: { tabId: tab.id },
        files: ['content.js']
      });

      injectionAttempted = true;
      console.log('[Background] Content script injected, waiting for initialization...');

      // Wait longer for script to initialize
      await new Promise(resolve => setTimeout(resolve, 500));

      // Try to verify it loaded with retries
      let verified = false;
      for (let i = 0; i < 3; i++) {
        try {
          const verifyResponse = await chrome.tabs.sendMessage(tab.id, { type: 'PING' });
          if (verifyResponse && verifyResponse.loaded) {
            verified = true;
            console.log('[Background] Content script verified on attempt', i + 1);
            break;
          }
        } catch (verifyError) {
          console.log(`[Background] Verification attempt ${i + 1} failed, retrying...`);
          if (i < 2) {
            await new Promise(resolve => setTimeout(resolve, 200));
          }
        }
      }

      if (!verified) {
        throw new Error('Content script injection verification failed after 3 attempts');
      }

      contentScriptReady = true;
    } catch (injectError) {
      console.error('[Background] Failed to inject content script:', injectError);
      currentRecording.isRecording = false;

      // Provide helpful error message
      let errorMsg = 'Failed to start recording. ';
      if (injectError.message.includes('Cannot access')) {
        errorMsg += 'This page blocks extensions. Try a different website.';
      } else if (injectionAttempted) {
        errorMsg += 'Please refresh the page and try again.';
      } else {
        errorMsg += 'Extension does not have permission for this page.';
      }

      return {
        success: false,
        error: errorMsg
      };
    }
  }

  // Now that content script is confirmed ready, start recording
  try {
    const startResponse = await chrome.tabs.sendMessage(tab.id, { type: 'START_RECORDING' });

    if (!startResponse || !startResponse.success) {
      throw new Error('Content script failed to start recording');
    }

    console.log('[Background] Recording started on content script');
  } catch (startError) {
    console.error('[Background] Failed to send START_RECORDING:', startError);
    currentRecording.isRecording = false;
    return {
      success: false,
      error: 'Failed to communicate with page. Please refresh and try again.'
    };
  }

  // Notify side panel
  await broadcastToSidePanel({ type: 'RECORDING_STARTED', recording: currentRecording });

  console.log('[Background] Recording started');
  return { success: true, recording: currentRecording };
}

/**
 * Stop recording workflow
 */
async function handleStopRecording() {
  if (!currentRecording.isRecording) {
    return { success: false, error: 'Not recording' };
  }

  const finalActions = [...currentRecording.actions];
  const duration = Date.now() - currentRecording.startTime;

  // Stop recording
  currentRecording.isRecording = false;
  currentRecording.isPaused = false;

  // Send message to content script to stop capturing
  if (currentRecording.tabId) {
    try {
      await chrome.tabs.sendMessage(currentRecording.tabId, { type: 'STOP_RECORDING' });
    } catch (error) {
      console.warn('[Background] Failed to send STOP_RECORDING to tab:', error);
    }
  }

  // Notify side panel
  await broadcastToSidePanel({
    type: 'RECORDING_STOPPED',
    actions: finalActions,
    duration: duration
  });

  console.log('[Background] Recording stopped. Actions:', finalActions.length);
  return {
    success: true,
    actions: finalActions,
    duration: duration
  };
}

/**
 * Pause recording
 */
async function handlePauseRecording() {
  if (!currentRecording.isRecording || currentRecording.isPaused) {
    return { success: false, error: 'Cannot pause' };
  }

  currentRecording.isPaused = true;

  // Send pause message to content script
  if (currentRecording.tabId) {
    try {
      await chrome.tabs.sendMessage(currentRecording.tabId, { type: 'PAUSE_RECORDING' });
    } catch (error) {
      console.warn('[Background] Failed to send PAUSE_RECORDING to tab:', error);
    }
  }

  // Notify side panel
  await broadcastToSidePanel({ type: 'RECORDING_PAUSED' });

  console.log('[Background] Recording paused');
  return { success: true };
}

/**
 * Resume recording
 */
async function handleResumeRecording() {
  if (!currentRecording.isRecording || !currentRecording.isPaused) {
    return { success: false, error: 'Cannot resume' };
  }

  currentRecording.isPaused = false;

  // Send resume message to content script
  if (currentRecording.tabId) {
    try {
      await chrome.tabs.sendMessage(currentRecording.tabId, { type: 'RESUME_RECORDING' });
    } catch (error) {
      console.warn('[Background] Failed to send RESUME_RECORDING to tab:', error);
    }
  }

  // Notify side panel
  await broadcastToSidePanel({ type: 'RECORDING_RESUMED' });

  console.log('[Background] Recording resumed');
  return { success: true };
}

/**
 * Handle action captured from content script
 */
async function handleActionCaptured(action) {
  if (!currentRecording.isRecording || currentRecording.isPaused) {
    return { success: false, error: 'Not recording or paused' };
  }

  // Add action to current recording
  currentRecording.actions.push(action);

  // Notify side panel of new action
  await broadcastToSidePanel({
    type: 'ACTION_ADDED',
    action: action,
    totalActions: currentRecording.actions.length
  });

  console.log('[Background] Action captured:', action.type);
  return { success: true };
}

/**
 * Handle navigation event
 */
async function handleNavigationEvent(url, title) {
  if (currentRecording.isRecording) {
    currentRecording.currentUrl = url;
    currentRecording.currentTitle = title;

    // Notify side panel
    await broadcastToSidePanel({
      type: 'PAGE_UPDATED',
      url: url,
      title: title
    });
  }

  return { success: true };
}

/**
 * Save workflow to backend
 */
async function handleSaveWorkflow(workflowData) {
  try {
    const result = await apiClient.createWorkflow(workflowData);

    // Clear cache to force refresh
    workflowCache = [];

    console.log('[Background] Workflow saved:', result.id);
    return { success: true, workflow: result };
  } catch (error) {
    console.error('[Background] Failed to save workflow:', error);
    return { success: false, error: error.message };
  }
}

/**
 * Get workflows from backend (with caching)
 */
async function handleGetWorkflows(page = 0, size = 100) {
  try {
    console.log('[Background] Fetching workflows from backend...');

    // Return cached workflows if available
    if (workflowCache.length > 0 && page === 0) {
      console.log('[Background] Returning cached workflows:', workflowCache.length);
      return { success: true, workflows: workflowCache };
    }

    const workflows = await apiClient.getWorkflows(page, size);

    console.log('[Background] Received workflows from API:', workflows);

    // Ensure workflows is an array
    if (!Array.isArray(workflows)) {
      console.error('[Background] Expected array, got:', typeof workflows);
      return { success: false, error: 'Invalid response format from backend' };
    }

    // Cache workflows
    if (page === 0) {
      workflowCache = workflows;
    }

    console.log('[Background] Loaded workflows:', workflows.length);
    return { success: true, workflows: workflows };
  } catch (error) {
    console.error('[Background] Failed to get workflows:', error);
    return { success: false, error: error.message };
  }
}

/**
 * Get single workflow by ID
 */
async function handleGetWorkflow(id) {
  try {
    const workflow = await apiClient.getWorkflow(id);
    console.log('[Background] Loaded workflow:', workflow.name);
    return { success: true, workflow: workflow };
  } catch (error) {
    console.error('[Background] Failed to get workflow:', error);
    return { success: false, error: error.message };
  }
}

/**
 * Delete workflow
 */
async function handleDeleteWorkflow(id) {
  try {
    await apiClient.deleteWorkflow(id);

    // Clear cache
    workflowCache = [];

    console.log('[Background] Workflow deleted:', id);
    return { success: true };
  } catch (error) {
    console.error('[Background] Failed to delete workflow:', error);
    return { success: false, error: error.message };
  }
}

/**
 * Search workflows
 */
async function handleSearchWorkflows(query) {
  try {
    const workflows = await apiClient.searchWorkflows(query);
    console.log('[Background] Search results:', workflows.length);
    return { success: true, workflows: workflows };
  } catch (error) {
    console.error('[Background] Search failed:', error);
    return { success: false, error: error.message };
  }
}

/**
 * Replay workflow in content script
 */
async function handleReplayWorkflow(workflowId, parameters, tabId) {
  try {
    console.log('[Background] Replaying workflow:', workflowId, 'on tab:', tabId);

    // Get workflow from backend
    const workflowResponse = await handleGetWorkflow(workflowId);
    if (!workflowResponse.success) {
      return workflowResponse;
    }

    const workflow = workflowResponse.workflow;

    if (!workflow) {
      return { success: false, error: 'Workflow not found' };
    }

    console.log('[Background] Workflow loaded:', workflow.name, 'URL:', workflow.url);

    // Get the tab
    let tab;
    try {
      tab = await chrome.tabs.get(tabId);
    } catch (error) {
      return { success: false, error: 'Tab not found. Please open a webpage first.' };
    }

    // Check if we need to navigate
    const needsNavigation = tab.url !== workflow.url;

    if (needsNavigation) {
      console.log('[Background] Workflow requires navigation from', tab.url, 'to', workflow.url);
      console.log('[Background] Navigating tab...');

      // Navigate the tab
      await chrome.tabs.update(tab.id, { url: workflow.url });

      // Wait for navigation to complete with proper status checking
      await new Promise((resolve, reject) => {
        const timeout = setTimeout(() => {
          chrome.tabs.onUpdated.removeListener(listener);
          console.log('[Background] Navigation timeout, proceeding anyway');
          resolve(); // Don't reject, just proceed
        }, 30000);

        const listener = (listenTabId, changeInfo, updatedTab) => {
          if (listenTabId === tab.id) {
            console.log('[Background] Tab update:', changeInfo.status, updatedTab.url);

            if (changeInfo.status === 'complete' && updatedTab.url === workflow.url) {
              clearTimeout(timeout);
              chrome.tabs.onUpdated.removeListener(listener);
              console.log('[Background] Navigation confirmed complete');
              resolve();
            }
          }
        };

        chrome.tabs.onUpdated.addListener(listener);
      });

      // Wait for page to fully settle and DOM to be ready
      console.log('[Background] Waiting for page to settle...');
      await new Promise(resolve => setTimeout(resolve, 2000));
    }

    // Always inject scripts fresh
    console.log('[Background] Injecting content.js...');
    try {
      await chrome.scripting.executeScript({
        target: { tabId: tab.id },
        files: ['content.js']
      });
      console.log('[Background] Content.js injected');
      await new Promise(resolve => setTimeout(resolve, 500));
    } catch (error) {
      console.error('[Background] Failed to inject content.js:', error);
      return {
        success: false,
        error: 'Failed to inject content script: ' + error.message
      };
    }

    // Inject WorkflowPlayer
    console.log('[Background] Injecting WorkflowPlayer...');
    try {
      await chrome.scripting.executeScript({
        target: { tabId: tab.id },
        files: ['lib/workflow-player.js']
      });
      console.log('[Background] WorkflowPlayer injected');
      await new Promise(resolve => setTimeout(resolve, 500));

      // Verify it's loaded
      const checkResult = await chrome.scripting.executeScript({
        target: { tabId: tab.id },
        func: () => {
          return {
            hasWorkflowPlayer: typeof window.WorkflowPlayer !== 'undefined',
            hasWorkflowPlayerGlobal: typeof WorkflowPlayer !== 'undefined'
          };
        }
      });

      console.log('[Background] WorkflowPlayer check:', checkResult[0].result);

      if (!checkResult[0].result.hasWorkflowPlayer && !checkResult[0].result.hasWorkflowPlayerGlobal) {
        return {
          success: false,
          error: 'WorkflowPlayer failed to load. Please try again.'
        };
      }
    } catch (error) {
      console.error('[Background] Failed to inject WorkflowPlayer:', error);
      return {
        success: false,
        error: 'Failed to inject WorkflowPlayer: ' + error.message
      };
    }

    // Send workflow to content script for execution
    console.log('[Background] Sending workflow to content script...');
    let result;
    try {
      result = await chrome.tabs.sendMessage(tab.id, {
        type: 'REPLAY_WORKFLOW',
        workflow: workflow,
        parameters: parameters || {}
      });
    } catch (error) {
      console.error('[Background] Failed to send message:', error);
      return {
        success: false,
        error: 'Failed to communicate with page: ' + error.message
      };
    }

    console.log('[Background] Workflow replay result:', result);
    return result;
  } catch (error) {
    console.error('[Background] Replay failed:', error);
    return { success: false, error: error.message };
  }
}

/**
 * Update backend URL
 */
async function handleUpdateBackendURL(url) {
  try {
    // Update API client
    apiClient.setBaseUrl(url);

    // Clear workflow cache
    workflowCache = [];

    console.log('[Background] Backend URL updated to:', url);
    return { success: true };
  } catch (error) {
    console.error('[Background] Failed to update backend URL:', error);
    return { success: false, error: error.message };
  }
}

/**
 * Broadcast message to side panel
 */
async function broadcastToSidePanel(message) {
  try {
    await chrome.runtime.sendMessage(message);
  } catch (error) {
    // Side panel might not be open, ignore error
    console.debug('[Background] Side panel not available:', error.message);
  }
}

console.log('[Background] Service worker initialized');
