/**
 * Browse View Component
 * Handles workflow browsing, searching, and replay
 */

export class BrowseView {
  constructor(app) {
    this.app = app;
    this.workflows = [];
    this.filteredWorkflows = [];
    this.selectedWorkflow = null;
    this.searchTimeout = null;
  }

  async init() {
    console.log('[BrowseView] Initializing...');

    // Bind event listeners
    this.bindEvents();

    // Load workflows
    await this.loadWorkflows();
  }

  bindEvents() {
    // New workflow button
    document.getElementById('newWorkflowBtn').addEventListener('click', () => {
      this.app.recordingView.startRecording();
    });

    // Search input
    document.getElementById('searchInput').addEventListener('input', (e) => {
      this.handleSearch(e.target.value);
    });

    // Workflow details buttons
    document.getElementById('closeDetailsBtn').addEventListener('click', () => {
      this.hideWorkflowDetails();
    });

    document.getElementById('replayBtn').addEventListener('click', () => {
      this.replayWorkflow();
    });

    document.getElementById('deleteBtn').addEventListener('click', () => {
      this.deleteWorkflow();
    });
  }

  /**
   * Load workflows from backend
   */
  async loadWorkflows() {
    console.log('[BrowseView] Loading workflows...');

    // Show loading spinner
    document.getElementById('loadingSpinner').classList.remove('hidden');
    document.getElementById('emptyState').classList.add('hidden');

    try {
      const response = await chrome.runtime.sendMessage({
        type: 'GET_WORKFLOWS',
        page: 0,
        size: 100
      });

      console.log('[BrowseView] Response received:', response);

      if (response && response.success) {
        this.workflows = response.workflows || [];
        this.filteredWorkflows = [...this.workflows];

        console.log('[BrowseView] Loaded workflows:', this.workflows.length);

        // Render workflow list
        this.renderWorkflowList();
      } else {
        console.error('[BrowseView] Failed to load workflows:', response?.error || 'Unknown error');
        this.app.showToast('Failed to load workflows: ' + (response?.error || 'Unknown error'), 'error');
        // Show empty state on error
        document.getElementById('emptyState').classList.remove('hidden');
      }
    } catch (error) {
      console.error('[BrowseView] Load error:', error);
      this.app.showToast('Cannot connect to backend', 'error');
    } finally {
      // Hide loading spinner
      document.getElementById('loadingSpinner').classList.add('hidden');
    }
  }

  /**
   * Render workflow list
   */
  renderWorkflowList() {
    const container = document.getElementById('workflowList');
    const emptyState = document.getElementById('emptyState');

    container.innerHTML = '';

    if (this.filteredWorkflows.length === 0) {
      emptyState.classList.remove('hidden');
      return;
    }

    emptyState.classList.add('hidden');

    this.filteredWorkflows.forEach(workflow => {
      const card = this.createWorkflowCard(workflow);
      container.appendChild(card);
    });
  }

  /**
   * Create workflow card element
   */
  createWorkflowCard(workflow) {
    const card = document.createElement('div');
    card.className = 'workflow-card';
    card.dataset.workflowId = workflow.id;

    // Calculate stats
    const totalRuns = workflow.totalExecutions || 0;
    const successRate = workflow.successfulExecutions && workflow.totalExecutions
      ? Math.round((workflow.successfulExecutions / workflow.totalExecutions) * 100)
      : 0;
    const actionCount = workflow.actions?.length || 0;

    // Format last executed
    const lastExecuted = this.app.formatRelativeTime(workflow.lastExecutedAt);

    // Parse tags
    const tags = Array.isArray(workflow.tags)
      ? workflow.tags
      : (workflow.tags || '').split(',').filter(t => t.trim());

    card.innerHTML = `
      <div class="workflow-card-header">
        <div class="workflow-name">${this.escapeHtml(workflow.name)}</div>
      </div>
      <div class="workflow-description">
        ${this.escapeHtml(workflow.description || 'No description').substring(0, 80)}${workflow.description && workflow.description.length > 80 ? '...' : ''}
      </div>
      <div class="workflow-meta">
        <span>${actionCount} actions</span>
        <span>${totalRuns} runs</span>
        <span>${successRate}% success</span>
        <span>${lastExecuted}</span>
      </div>
      ${tags.length > 0 ? `
        <div class="workflow-tags">
          ${tags.map(tag => `<span class="tag">${this.escapeHtml(tag)}</span>`).join('')}
        </div>
      ` : ''}
    `;

    // Click handler
    card.addEventListener('click', () => {
      this.showWorkflowDetails(workflow);
    });

    return card;
  }

  /**
   * Show workflow details panel
   */
  showWorkflowDetails(workflow) {
    console.log('[BrowseView] Showing workflow details:', workflow.id);

    this.selectedWorkflow = workflow;

    const detailsPanel = document.getElementById('workflowDetails');
    detailsPanel.classList.remove('hidden');

    // Populate details
    document.getElementById('detailsName').textContent = workflow.name;
    document.getElementById('detailsUrl').textContent = workflow.url;
    document.getElementById('detailsActionCount').textContent = `${workflow.actions?.length || 0} actions`;

    const successRate = workflow.successfulExecutions && workflow.totalExecutions
      ? Math.round((workflow.successfulExecutions / workflow.totalExecutions) * 100)
      : 0;
    document.getElementById('detailsSuccessRate').textContent = `${successRate}%`;

    // Description
    document.getElementById('detailsDescription').textContent =
      workflow.description || 'No description provided';

    // Tags
    const tagsContainer = document.getElementById('detailsTags');
    tagsContainer.innerHTML = '';

    const tags = Array.isArray(workflow.tags)
      ? workflow.tags
      : (workflow.tags || '').split(',').filter(t => t.trim());

    if (tags.length > 0) {
      tags.forEach(tag => {
        const tagEl = document.createElement('span');
        tagEl.className = 'tag';
        tagEl.textContent = tag;
        tagsContainer.appendChild(tagEl);
      });
    }

    // Actions list
    this.renderActionsList(workflow.actions || []);
  }

  /**
   * Hide workflow details panel
   */
  hideWorkflowDetails() {
    document.getElementById('workflowDetails').classList.add('hidden');
    this.selectedWorkflow = null;
  }

  /**
   * Render actions list in details panel
   */
  renderActionsList(actions) {
    const container = document.getElementById('detailsActionsList');
    container.innerHTML = '';

    if (actions.length === 0) {
      container.innerHTML = '<p class="text-center" style="color: var(--gray-500);">No actions</p>';
      return;
    }

    actions.forEach((action, index) => {
      const item = document.createElement('div');
      item.className = 'action-item';

      const icon = this.app.getActionIcon(action.type);
      const description = action.description || action.type;
      const selector = action.selector || '—';

      item.innerHTML = `
        <div class="action-icon">${icon}</div>
        <div class="action-content">
          <div class="action-type">${action.type}</div>
          <div class="action-description">${this.escapeHtml(description)}</div>
          <div class="action-selector">${this.escapeHtml(selector)}</div>
        </div>
      `;

      container.appendChild(item);
    });
  }

  /**
   * Replay selected workflow
   */
  async replayWorkflow() {
    if (!this.selectedWorkflow) return;

    console.log('[BrowseView] Replaying workflow:', this.selectedWorkflow.id);

    try {
      // Get current active web tab (not the side panel)
      const tabs = await chrome.tabs.query({ active: true, lastFocusedWindow: true });
      const webTabs = tabs.filter(t => t.url && !t.url.startsWith('chrome-extension://'));

      if (webTabs.length === 0) {
        this.app.showToast('No active web page found. Please open a tab first.', 'error');
        return;
      }

      const tab = webTabs[0];

      // Send replay message to background with tab ID
      const response = await chrome.runtime.sendMessage({
        type: 'REPLAY_WORKFLOW',
        workflowId: this.selectedWorkflow.id,
        tabId: tab.id,
        parameters: {} // TODO: Add parameter input UI
      });

      if (response && response.success) {
        // Check if there's extracted data
        if (response.extractedData && response.extractedData.length > 0) {
          this.showExtractionResults(response.extractedData);
        } else {
          this.app.showToast('Workflow completed successfully!', 'success');
        }

        // Refresh workflow to get updated stats
        await this.loadWorkflows();
      } else {
        this.app.showToast('Workflow failed: ' + (response?.error || 'Unknown error'), 'error');
      }
    } catch (error) {
      console.error('[BrowseView] Replay error:', error);
      this.app.showToast('Failed to replay workflow: ' + error.message, 'error');
    }
  }

  /**
   * Delete selected workflow
   */
  async deleteWorkflow() {
    if (!this.selectedWorkflow) return;

    const confirmed = confirm(`Are you sure you want to delete "${this.selectedWorkflow.name}"?`);
    if (!confirmed) return;

    console.log('[BrowseView] Deleting workflow:', this.selectedWorkflow.id);

    try {
      const response = await chrome.runtime.sendMessage({
        type: 'DELETE_WORKFLOW',
        id: this.selectedWorkflow.id
      });

      if (response.success) {
        this.app.showToast('Workflow deleted', 'success');

        // Hide details panel
        this.hideWorkflowDetails();

        // Reload workflows
        await this.loadWorkflows();
      } else {
        this.app.showToast('Failed to delete: ' + response.error, 'error');
      }
    } catch (error) {
      console.error('[BrowseView] Delete error:', error);
      this.app.showToast('Failed to delete workflow', 'error');
    }
  }

  /**
   * Handle search input
   */
  handleSearch(query) {
    // Debounce search
    clearTimeout(this.searchTimeout);

    this.searchTimeout = setTimeout(() => {
      this.performSearch(query);
    }, 300);
  }

  /**
   * Perform search filtering
   */
  performSearch(query) {
    const lowerQuery = query.toLowerCase().trim();

    if (!lowerQuery) {
      // Show all workflows
      this.filteredWorkflows = [...this.workflows];
    } else {
      // Filter workflows
      this.filteredWorkflows = this.workflows.filter(workflow => {
        const name = (workflow.name || '').toLowerCase();
        const description = (workflow.description || '').toLowerCase();
        const tags = Array.isArray(workflow.tags)
          ? workflow.tags.join(' ').toLowerCase()
          : (workflow.tags || '').toLowerCase();

        return name.includes(lowerQuery) ||
               description.includes(lowerQuery) ||
               tags.includes(lowerQuery);
      });
    }

    // Re-render list
    this.renderWorkflowList();
  }

  /**
   * Show extraction results dialog
   */
  showExtractionResults(extractedData) {
    console.log('[BrowseView] Showing extraction results:', extractedData);

    // Create dialog
    const dialog = document.createElement('div');
    dialog.className = 'extraction-dialog-overlay';

    // Build extraction items HTML
    const extractionsHtml = extractedData.map((item, index) => `
      <div class="extraction-result-item">
        <div class="extraction-result-header">
          <strong>Extraction ${index + 1}</strong>
          <span class="extraction-type-badge">${item.type}</span>
        </div>
        <div class="extraction-result-meta">
          <div><strong>Selector:</strong> <code>${this.escapeHtml(item.selector)}</code></div>
          <div><strong>Timestamp:</strong> ${new Date(item.timestamp).toLocaleString()}</div>
        </div>
        <div class="extraction-result-value">
          <label>Extracted Value:</label>
          <pre>${this.escapeHtml(item.value || '(empty)')}</pre>
          <button class="btn btn-sm btn-secondary copy-extraction-btn" data-value="${this.escapeHtml(item.value || '')}">
            Copy to Clipboard
          </button>
        </div>
      </div>
    `).join('');

    dialog.innerHTML = `
      <div class="extraction-dialog" style="max-width: 700px;">
        <h3>Workflow Completed with Data Extraction</h3>
        <p class="dialog-subtitle">
          ${extractedData.length} data point${extractedData.length > 1 ? 's' : ''} extracted
        </p>

        <div class="extraction-results-list">
          ${extractionsHtml}
        </div>

        <div class="dialog-buttons">
          <button id="downloadExtractionsJson" class="btn btn-secondary">Download JSON</button>
          <button id="closeExtractionResults" class="btn btn-primary">Close</button>
        </div>
      </div>
    `;

    document.body.appendChild(dialog);

    // Close button
    dialog.querySelector('#closeExtractionResults').addEventListener('click', () => {
      dialog.remove();
      this.app.showToast('Workflow completed successfully!', 'success');
    });

    // Download JSON button
    dialog.querySelector('#downloadExtractionsJson').addEventListener('click', () => {
      const dataStr = JSON.stringify(extractedData, null, 2);
      const dataBlob = new Blob([dataStr], { type: 'application/json' });
      const url = URL.createObjectURL(dataBlob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `extraction-${Date.now()}.json`;
      link.click();
      URL.revokeObjectURL(url);
      this.app.showToast('Extraction data downloaded', 'success');
    });

    // Copy to clipboard buttons
    dialog.querySelectorAll('.copy-extraction-btn').forEach(btn => {
      btn.addEventListener('click', async (e) => {
        const value = e.target.dataset.value;
        try {
          await navigator.clipboard.writeText(value);
          this.app.showToast('Copied to clipboard', 'success');
        } catch (error) {
          console.error('Failed to copy:', error);
          this.app.showToast('Failed to copy to clipboard', 'error');
        }
      });
    });

    // Close on background click
    dialog.addEventListener('click', (e) => {
      if (e.target === dialog) {
        dialog.remove();
        this.app.showToast('Workflow completed successfully!', 'success');
      }
    });
  }

  /**
   * Escape HTML to prevent XSS
   */
  escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  }
}
