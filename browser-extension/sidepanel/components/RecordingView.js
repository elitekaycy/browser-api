/**
 * Recording View Component
 * Handles the recording mode UI and interactions
 */

export class RecordingView {
  constructor(app) {
    this.app = app;
    this.isRecording = false;
    this.isPaused = false;
    this.actions = [];
    this.startTime = null;
    this.durationInterval = null;
    this.currentUrl = '';
    this.currentTitle = '';
  }

  async init() {
    console.log('[RecordingView] Initializing...');

    // Bind event listeners
    this.bindEvents();

    // Check if already recording (in case of reload)
    await this.checkRecordingState();
  }

  bindEvents() {
    // Start recording button
    document.getElementById('startRecordBtn').addEventListener('click', () => {
      this.startRecording();
    });

    // Pause recording button
    document.getElementById('pauseRecordBtn').addEventListener('click', () => {
      this.pauseRecording();
    });

    // Stop recording button
    document.getElementById('stopRecordBtn').addEventListener('click', () => {
      this.stopRecording();
    });

    // Save modal buttons
    document.getElementById('closeSaveModal').addEventListener('click', () => {
      this.hideSaveModal();
    });

    document.getElementById('cancelSaveBtn').addEventListener('click', () => {
      this.hideSaveModal();
    });

    document.getElementById('confirmSaveBtn').addEventListener('click', () => {
      this.saveWorkflow();
    });

    // Close modal on background click
    document.getElementById('saveModal').addEventListener('click', (e) => {
      if (e.target.id === 'saveModal') {
        this.hideSaveModal();
      }
    });
  }

  /**
   * Check if recording is already in progress
   */
  async checkRecordingState() {
    try {
      const response = await chrome.runtime.sendMessage({
        type: 'GET_RECORDING_STATE'
      });

      if (response.success && response.recording.isRecording) {
        this.onRecordingStarted(response.recording);

        // Restore actions
        if (response.recording.actions) {
          response.recording.actions.forEach((action, index) => {
            this.onActionAdded(action, index + 1);
          });
        }
      }
    } catch (error) {
      console.error('[RecordingView] Failed to check recording state:', error);
    }
  }

  /**
   * Start recording
   */
  async startRecording() {
    try {
      // Get current active tab
      const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });

      if (!tab) {
        this.app.showToast('No active tab found', 'error');
        return;
      }

      // Send message to background to start recording
      const response = await chrome.runtime.sendMessage({
        type: 'START_RECORDING'
      });

      if (response.success) {
        this.app.showToast('Recording started!', 'success');
        this.app.showView('recording');
      } else {
        this.app.showToast('Failed to start recording: ' + response.error, 'error');
      }
    } catch (error) {
      console.error('[RecordingView] Start recording failed:', error);
      this.app.showToast('Failed to start recording', 'error');
    }
  }

  /**
   * Pause recording
   */
  async pauseRecording() {
    try {
      const response = await chrome.runtime.sendMessage({
        type: 'PAUSE_RECORDING'
      });

      if (response.success) {
        this.app.showToast('Recording paused', 'info');
      }
    } catch (error) {
      console.error('[RecordingView] Pause failed:', error);
    }
  }

  /**
   * Resume recording (unpause)
   */
  async resumeRecording() {
    try {
      const response = await chrome.runtime.sendMessage({
        type: 'RESUME_RECORDING'
      });

      if (response.success) {
        this.app.showToast('Recording resumed', 'success');
      }
    } catch (error) {
      console.error('[RecordingView] Resume failed:', error);
    }
  }

  /**
   * Stop recording
   */
  async stopRecording() {
    try {
      const response = await chrome.runtime.sendMessage({
        type: 'STOP_RECORDING'
      });

      if (response.success) {
        // Will be handled by onRecordingStopped callback
      }
    } catch (error) {
      console.error('[RecordingView] Stop failed:', error);
    }
  }

  /**
   * Save workflow to backend
   */
  async saveWorkflow() {
    const name = document.getElementById('workflowName').value.trim();
    const description = document.getElementById('workflowDescription').value.trim();
    const tags = document.getElementById('workflowTags').value
      .split(',')
      .map(t => t.trim())
      .filter(t => t.length > 0);

    if (!name) {
      this.app.showToast('Please enter a workflow name', 'error');
      return;
    }

    if (this.actions.length === 0) {
      this.app.showToast('No actions to save', 'error');
      return;
    }

    const workflowData = {
      name: name,
      description: description || '',
      url: this.currentUrl,
      actions: this.actions,
      tags: tags,
      createdBy: 'extension-user'
    };

    try {
      const response = await chrome.runtime.sendMessage({
        type: 'SAVE_WORKFLOW',
        workflow: workflowData
      });

      if (response.success) {
        this.app.showToast('Workflow saved successfully!', 'success');
        this.hideSaveModal();
        this.resetRecording();
        this.app.showView('browse');

        // Refresh browse view
        if (this.app.browseView) {
          await this.app.browseView.loadWorkflows();
        }
      } else {
        this.app.showToast('Failed to save: ' + response.error, 'error');
      }
    } catch (error) {
      console.error('[RecordingView] Save failed:', error);
      this.app.showToast('Failed to save workflow', 'error');
    }
  }

  /**
   * Event handler: Recording started
   */
  onRecordingStarted(recording) {
    console.log('[RecordingView] Recording started');

    this.isRecording = true;
    this.isPaused = false;
    this.actions = [];
    this.startTime = recording.startTime || Date.now();
    this.currentUrl = recording.currentUrl || '';
    this.currentTitle = recording.currentTitle || '';

    // Update UI
    document.getElementById('startRecordBtn').classList.add('hidden');
    document.getElementById('recordingButtons').classList.remove('hidden');
    document.getElementById('recordingStatus').classList.remove('hidden');
    document.getElementById('pageInfo').classList.remove('hidden');
    document.getElementById('actionListContainer').classList.remove('hidden');

    // Update page info
    this.updatePageInfo(this.currentUrl, this.currentTitle);

    // Start duration timer
    this.startDurationTimer();

    // Clear action list
    document.getElementById('actionList').innerHTML = '';
    document.getElementById('actionCount').textContent = '0';
  }

  /**
   * Event handler: Recording stopped
   */
  onRecordingStopped(actions, duration) {
    console.log('[RecordingView] Recording stopped', actions.length, 'actions');

    this.isRecording = false;
    this.isPaused = false;
    this.actions = actions || [];

    // Stop duration timer
    this.stopDurationTimer();

    // Show save modal
    this.showSaveModal();
  }

  /**
   * Event handler: Recording paused
   */
  onRecordingPaused() {
    console.log('[RecordingView] Recording paused');

    this.isPaused = true;

    // Update UI
    document.getElementById('recordingText').textContent = 'Paused';
    document.getElementById('pauseRecordBtn').textContent = '▶️ Resume';
    document.getElementById('pauseRecordBtn').onclick = () => this.resumeRecording();

    // Stop duration timer
    this.stopDurationTimer();
  }

  /**
   * Event handler: Recording resumed
   */
  onRecordingResumed() {
    console.log('[RecordingView] Recording resumed');

    this.isPaused = false;

    // Update UI
    document.getElementById('recordingText').textContent = 'Recording...';
    document.getElementById('pauseRecordBtn').textContent = '⏸️ Pause';
    document.getElementById('pauseRecordBtn').onclick = () => this.pauseRecording();

    // Restart duration timer
    this.startDurationTimer();
  }

  /**
   * Event handler: Action added
   */
  onActionAdded(action, totalActions) {
    console.log('[RecordingView] Action added:', action.type);

    // Add to local array
    this.actions.push(action);

    // Update counter
    document.getElementById('actionCount').textContent = totalActions.toString();

    // Add to action list UI
    this.addActionToList(action);
  }

  /**
   * Event handler: Page updated
   */
  onPageUpdated(url, title) {
    console.log('[RecordingView] Page updated:', url);

    this.currentUrl = url;
    this.currentTitle = title;

    this.updatePageInfo(url, title);
  }

  /**
   * Update page info display
   */
  updatePageInfo(url, title) {
    document.getElementById('pageTitle').textContent = title || 'Untitled';
    document.getElementById('pageUrl').textContent = url || '';
  }

  /**
   * Add action to the list UI
   */
  addActionToList(action) {
    const actionList = document.getElementById('actionList');
    const actionItem = document.createElement('div');
    actionItem.className = 'action-item';
    actionItem.dataset.actionIndex = this.actions.length - 1;

    const icon = this.app.getActionIcon(action.type);
    const description = action.description || `${action.type} action`;
    const selector = action.selector || '—';

    actionItem.innerHTML = `
      <div class="action-icon">${icon}</div>
      <div class="action-content">
        <div class="action-type">${action.type}</div>
        <div class="action-description">${description}</div>
        <div class="action-selector">${selector}</div>
      </div>
      <button class="action-delete" title="Delete action">🗑️</button>
    `;

    // Delete button handler
    actionItem.querySelector('.action-delete').addEventListener('click', (e) => {
      e.stopPropagation();
      this.deleteAction(parseInt(actionItem.dataset.actionIndex));
    });

    actionList.appendChild(actionItem);

    // Scroll to bottom
    actionList.scrollTop = actionList.scrollHeight;
  }

  /**
   * Delete an action from the list
   */
  deleteAction(index) {
    if (index < 0 || index >= this.actions.length) return;

    this.actions.splice(index, 1);

    // Re-render action list
    this.renderActionList();

    // Update counter
    document.getElementById('actionCount').textContent = this.actions.length.toString();
  }

  /**
   * Re-render the entire action list
   */
  renderActionList() {
    const actionList = document.getElementById('actionList');
    actionList.innerHTML = '';

    this.actions.forEach((action, index) => {
      this.addActionToList(action);
    });
  }

  /**
   * Start duration timer
   */
  startDurationTimer() {
    this.stopDurationTimer(); // Clear any existing interval

    this.durationInterval = setInterval(() => {
      const elapsed = Date.now() - this.startTime;
      document.getElementById('recordingDuration').textContent = this.app.formatDuration(elapsed);
    }, 1000);
  }

  /**
   * Stop duration timer
   */
  stopDurationTimer() {
    if (this.durationInterval) {
      clearInterval(this.durationInterval);
      this.durationInterval = null;
    }
  }

  /**
   * Show save workflow modal
   */
  showSaveModal() {
    const modal = document.getElementById('saveModal');
    modal.classList.remove('hidden');

    // Populate modal
    document.getElementById('saveModalActionCount').textContent = this.actions.length.toString();
    document.getElementById('saveModalUrl').textContent = this.currentUrl;

    // Clear form
    document.getElementById('workflowName').value = '';
    document.getElementById('workflowDescription').value = '';
    document.getElementById('workflowTags').value = '';

    // Focus name input
    setTimeout(() => {
      document.getElementById('workflowName').focus();
    }, 100);
  }

  /**
   * Hide save workflow modal
   */
  hideSaveModal() {
    const modal = document.getElementById('saveModal');
    modal.classList.add('hidden');
  }

  /**
   * Reset recording state
   */
  resetRecording() {
    this.isRecording = false;
    this.isPaused = false;
    this.actions = [];
    this.startTime = null;
    this.currentUrl = '';
    this.currentTitle = '';

    // Reset UI
    document.getElementById('startRecordBtn').classList.remove('hidden');
    document.getElementById('recordingButtons').classList.add('hidden');
    document.getElementById('recordingStatus').classList.add('hidden');
    document.getElementById('pageInfo').classList.add('hidden');
    document.getElementById('actionListContainer').classList.add('hidden');

    document.getElementById('actionList').innerHTML = '';
    document.getElementById('actionCount').textContent = '0';
    document.getElementById('recordingDuration').textContent = '00:00';
  }
}
