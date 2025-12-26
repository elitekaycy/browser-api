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

    // Pause/Resume recording button (dynamic)
    document.getElementById('pauseRecordBtn').addEventListener('click', () => {
      if (this.isPaused) {
        this.resumeRecording();
      } else {
        this.pauseRecording();
      }
    });

    // Stop recording button
    document.getElementById('stopRecordBtn').addEventListener('click', () => {
      this.stopRecording();
    });

    // Cancel recording button
    document.getElementById('cancelRecordBtn').addEventListener('click', () => {
      this.cancelRecording();
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

    // Add data extraction button
    const addExtractionBtn = document.getElementById('addExtractionBtn');
    if (addExtractionBtn) {
      addExtractionBtn.addEventListener('click', () => {
        this.enterHighlightMode();
      });
    }

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
      console.log('[RecordingView] Starting recording...');

      // Send message to background to start recording
      // Background will find the correct tab automatically
      const response = await chrome.runtime.sendMessage({
        type: 'START_RECORDING'
      });

      console.log('[RecordingView] Start recording response:', response);

      if (response && response.success) {
        this.app.showToast('Recording started!', 'success');
        this.app.showView('recording');
      } else {
        this.app.showToast('Failed to start recording: ' + (response?.error || 'Unknown error'), 'error');
      }
    } catch (error) {
      console.error('[RecordingView] Start recording failed:', error);
      this.app.showToast('Failed to start recording: ' + error.message, 'error');
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
   * Cancel recording without saving
   */
  async cancelRecording() {
    try {
      const response = await chrome.runtime.sendMessage({
        type: 'STOP_RECORDING'
      });

      if (response.success) {
        // Reset recording state without showing save modal
        this.resetRecording();
        this.app.showToast('Recording cancelled', 'info');
        this.app.showView('browse');
      }
    } catch (error) {
      console.error('[RecordingView] Cancel failed:', error);
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

    // Update pause button to show resume icon and text
    const pauseBtn = document.getElementById('pauseRecordBtn');
    pauseBtn.innerHTML = `
      <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
        <path d="M8 5v14l11-7z"/>
      </svg>
      Resume
    `;
    pauseBtn.classList.remove('btn-warning');
    pauseBtn.classList.add('btn-success');

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

    // Update pause button to show pause icon and text
    const pauseBtn = document.getElementById('pauseRecordBtn');
    pauseBtn.innerHTML = `
      <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
        <path d="M6 4h4v16H6V4zm8 0h4v16h-4V4z"/>
      </svg>
      Pause
    `;
    pauseBtn.classList.remove('btn-success');
    pauseBtn.classList.add('btn-warning');

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

    // Render extraction actions
    this.renderExtractionActions();

    // Focus name input
    setTimeout(() => {
      document.getElementById('workflowName').focus();
    }, 100);
  }

  /**
   * Render extraction actions in the save modal
   */
  renderExtractionActions() {
    // Find or create extraction container
    let extractionContainer = document.getElementById('extractionActionsContainer');

    if (!extractionContainer) {
      // Create container if it doesn't exist
      const formInfo = document.querySelector('.form-info');
      extractionContainer = document.createElement('div');
      extractionContainer.id = 'extractionActionsContainer';
      formInfo.insertAdjacentElement('afterend', extractionContainer);
    }

    // Filter extraction actions
    const extractionActions = this.actions.filter(action => action.type === 'EXTRACT');

    if (extractionActions.length === 0) {
      extractionContainer.innerHTML = '';
      return;
    }

    // Render extraction actions
    extractionContainer.innerHTML = `
      <div class="extraction-actions-header">
        <h4>📊 Data Extraction Points (${extractionActions.length})</h4>
        <p class="text-sm">These elements will be extracted during workflow execution</p>
      </div>
      <div class="extraction-actions-list">
        ${extractionActions.map((action, index) => {
          const globalIndex = this.actions.indexOf(action);
          return `
            <div class="extraction-card" data-index="${globalIndex}">
              <div class="extraction-card-header">
                <span class="extraction-type-badge ${action.extractType.toLowerCase()}">${action.extractType}</span>
                <button class="remove-extraction-btn" data-index="${globalIndex}" title="Remove extraction">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M18 6L6 18M6 6l12 12"/>
                  </svg>
                </button>
              </div>
              <div class="extraction-card-body">
                <div class="extraction-field">
                  <span class="field-label">Selector:</span>
                  <code class="field-value">${action.selector}</code>
                </div>
                ${action.extractType === 'ATTRIBUTE' ? `
                  <div class="extraction-field">
                    <span class="field-label">Attribute:</span>
                    <code class="field-value">${action.attributeName}</code>
                  </div>
                ` : ''}
                ${action.extractType === 'JSON' && action.jsonPath ? `
                  <div class="extraction-field">
                    <span class="field-label">JSON Path:</span>
                    <code class="field-value">${action.jsonPath}</code>
                  </div>
                ` : ''}
              </div>
            </div>
          `;
        }).join('')}
      </div>
    `;

    // Attach remove button event listeners
    extractionContainer.querySelectorAll('.remove-extraction-btn').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.preventDefault();
        e.stopPropagation();
        const index = parseInt(btn.dataset.index);
        this.removeExtraction(index);
      });
    });
  }

  /**
   * Remove an extraction action
   */
  removeExtraction(index) {
    if (index < 0 || index >= this.actions.length) return;

    const action = this.actions[index];
    if (action.type !== 'EXTRACT') return;

    // Remove from actions array
    this.actions.splice(index, 1);

    // Update action count
    document.getElementById('saveModalActionCount').textContent = this.actions.length.toString();

    // Re-render extraction actions
    this.renderExtractionActions();

    // Show toast
    this.app.showToast('Extraction removed', 'info');
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

  /**
   * Enter highlight mode to select element for data extraction
   */
  async enterHighlightMode() {
    try {
      // Get the current tab
      const tabs = await chrome.tabs.query({ active: true, lastFocusedWindow: true });
      const webTabs = tabs.filter(t => t.url && !t.url.startsWith('chrome-extension://'));

      if (webTabs.length === 0) {
        this.app.showToast('No active web page found. Please open a tab first.', 'error');
        return;
      }

      const tab = webTabs[0];

      // Hide save modal temporarily
      this.hideSaveModal();

      // Send message to content script to enable highlight mode
      const response = await chrome.tabs.sendMessage(tab.id, {
        type: 'ENTER_HIGHLIGHT_MODE'
      });

      if (response && response.success) {
        this.app.showToast('Click on an element to extract data from...', 'info');

        // Listen for element selection
        const listener = (message, sender) => {
          if (message.type === 'ELEMENT_SELECTED' && sender.tab.id === tab.id) {
            this.onElementSelected(message.selector, message.element);
            chrome.runtime.onMessage.removeListener(listener);
          }
        };
        chrome.runtime.onMessage.addListener(listener);
      } else {
        this.app.showToast('Failed to enter highlight mode: ' + (response?.error || 'Unknown error'), 'error');
        this.showSaveModal();
      }
    } catch (error) {
      console.error('[RecordingView] Failed to enter highlight mode:', error);
      this.app.showToast('Failed to enter highlight mode: ' + error.message, 'error');
      this.showSaveModal();
    }
  }

  /**
   * Handle element selection in highlight mode
   */
  async onElementSelected(selector, elementInfo) {
    // Show extraction type dialog
    const extractType = await this.showExtractionDialog(selector, elementInfo);

    if (extractType) {
      // Add EXTRACT action
      const extractAction = {
        type: 'EXTRACT',
        selector: selector,
        value: null,
        extractType: extractType.type,
        attributeName: extractType.attributeName || null,
        jsonPath: extractType.jsonPath || null,
        description: `Extract ${extractType.type} from ${selector}`,
        timestamp: Date.now()
      };

      this.actions.push(extractAction);

      this.app.showToast(`✅ Data extraction added: ${extractType.type}`, 'success');

      // Update the action count in the modal
      document.getElementById('saveModalActionCount').textContent = this.actions.length.toString();

      // Re-render extraction actions to show the new one
      this.renderExtractionActions();
    }

    // Show the save modal again (it was hidden during highlight mode)
    const modal = document.getElementById('saveModal');
    modal.classList.remove('hidden');
  }

  /**
   * Show extraction type selection dialog
   */
  async showExtractionDialog(selector, elementInfo) {
    return new Promise((resolve) => {
      // Create dialog overlay
      const dialog = document.createElement('div');
      dialog.className = 'extraction-dialog-overlay';
      dialog.innerHTML = `
        <div class="extraction-dialog">
          <div class="extraction-header">
            <h3>Extract Data</h3>
            <button class="dialog-close" id="closeExtraction">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" width="20" height="20">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          <div class="extraction-selector">
            <code>${selector}</code>
          </div>

          <div class="extraction-options">
            <label class="extraction-option">
              <input type="radio" name="extractType" value="TEXT" checked>
              <span>Text</span>
            </label>

            <label class="extraction-option">
              <input type="radio" name="extractType" value="HTML">
              <span>HTML</span>
            </label>

            <label class="extraction-option">
              <input type="radio" name="extractType" value="ATTRIBUTE">
              <span>Attribute</span>
            </label>

            <label class="extraction-option">
              <input type="radio" name="extractType" value="JSON">
              <span>JSON</span>
            </label>
          </div>

          <div id="attributeInput" class="attribute-input hidden">
            <input type="text" id="attributeName" class="input" placeholder="Attribute name (e.g., href, src)">
          </div>

          <div class="dialog-actions">
            <button id="confirmExtraction" class="btn btn-primary btn-large">Add Extraction</button>
          </div>
        </div>
      `;

      document.body.appendChild(dialog);

      // Show attribute input when ATTRIBUTE is selected
      const attributeInput = dialog.querySelector('#attributeInput');

      dialog.querySelectorAll('input[name="extractType"]').forEach(radio => {
        radio.addEventListener('change', () => {
          if (radio.value === 'ATTRIBUTE') {
            attributeInput.classList.remove('hidden');
            dialog.querySelector('#attributeName').focus();
          } else {
            attributeInput.classList.add('hidden');
          }
        });
      });

      // Close button
      dialog.querySelector('#closeExtraction').addEventListener('click', () => {
        dialog.remove();
        resolve(null);
      });

      // Confirm button
      dialog.querySelector('#confirmExtraction').addEventListener('click', () => {
        const selectedType = dialog.querySelector('input[name="extractType"]:checked').value;
        const result = { type: selectedType };

        if (selectedType === 'ATTRIBUTE') {
          const attrName = dialog.querySelector('#attributeName').value.trim();
          if (!attrName) {
            this.app.showToast('Please enter an attribute name', 'error');
            return;
          }
          result.attributeName = attrName;
        }

        dialog.remove();
        resolve(result);
      });

      // Close on background click
      dialog.addEventListener('click', (e) => {
        if (e.target === dialog) {
          dialog.remove();
          resolve(null);
        }
      });
    });
  }
}
