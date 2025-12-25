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

// Message handler - central hub for all communication
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  console.log('[Background] Received message:', message.type, message);

  // Handle async responses
  const handleAsync = async () => {
    try {
      switch (message.type) {
        // Recording control messages
        case 'START_RECORDING':
          return await handleStartRecording(sender.tab);

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
          return await handleReplayWorkflow(message.workflowId, message.parameters, sender.tab);

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
async function handleStartRecording(tab) {
  if (!tab) {
    return { success: false, error: 'No active tab' };
  }

  currentRecording = {
    isRecording: true,
    isPaused: false,
    actions: [],
    startTime: Date.now(),
    currentUrl: tab.url,
    currentTitle: tab.title
  };

  // Send message to content script to start capturing events
  await chrome.tabs.sendMessage(tab.id, { type: 'START_RECORDING' });

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
  const tabs = await chrome.tabs.query({ active: true, currentWindow: true });
  if (tabs[0]) {
    await chrome.tabs.sendMessage(tabs[0].id, { type: 'STOP_RECORDING' });
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
    // Return cached workflows if available
    if (workflowCache.length > 0 && page === 0) {
      return { success: true, workflows: workflowCache };
    }

    const workflows = await apiClient.getWorkflows(page, size);

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
async function handleReplayWorkflow(workflowId, parameters, tab) {
  try {
    // Get workflow from backend
    const workflowResponse = await handleGetWorkflow(workflowId);
    if (!workflowResponse.success) {
      return workflowResponse;
    }

    const workflow = workflowResponse.workflow;

    // Navigate to workflow starting URL
    if (tab.url !== workflow.url) {
      await chrome.tabs.update(tab.id, { url: workflow.url });

      // Wait for navigation to complete
      await new Promise(resolve => {
        const listener = (tabId, changeInfo) => {
          if (tabId === tab.id && changeInfo.status === 'complete') {
            chrome.tabs.onUpdated.removeListener(listener);
            resolve();
          }
        };
        chrome.tabs.onUpdated.addListener(listener);
      });
    }

    // Send workflow to content script for execution
    const result = await chrome.tabs.sendMessage(tab.id, {
      type: 'REPLAY_WORKFLOW',
      workflow: workflow,
      parameters: parameters || {}
    });

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
