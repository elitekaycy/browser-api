/**
 * Workflow API Client
 * Handles all HTTP communication with the Spring Boot backend
 */

export class WorkflowAPIClient {
  constructor(baseUrl = 'http://localhost:8080/api/v1') {
    this.baseUrl = baseUrl;
  }

  /**
   * Create a new workflow
   * @param {Object} workflowData - Workflow creation data
   * @returns {Promise<Object>} Created workflow
   */
  async createWorkflow(workflowData) {
    const response = await this.request('/workflows', {
      method: 'POST',
      body: JSON.stringify(workflowData)
    });

    return response;
  }

  /**
   * Get all workflows (paginated)
   * @param {number} page - Page number (0-indexed)
   * @param {number} size - Page size
   * @returns {Promise<Array>} Array of workflows
   */
  async getWorkflows(page = 0, size = 100) {
    const response = await this.request(`/workflows?page=${page}&size=${size}`);
    return response;
  }

  /**
   * Get workflow by ID
   * @param {string} id - Workflow ID
   * @returns {Promise<Object>} Workflow object
   */
  async getWorkflow(id) {
    const response = await this.request(`/workflows/${id}`);
    return response;
  }

  /**
   * Update workflow
   * @param {string} id - Workflow ID
   * @param {Object} updates - Fields to update
   * @returns {Promise<Object>} Updated workflow
   */
  async updateWorkflow(id, updates) {
    const response = await this.request(`/workflows/${id}`, {
      method: 'PUT',
      body: JSON.stringify(updates)
    });

    return response;
  }

  /**
   * Delete workflow
   * @param {string} id - Workflow ID
   * @returns {Promise<void>}
   */
  async deleteWorkflow(id) {
    await this.request(`/workflows/${id}`, {
      method: 'DELETE'
    });
  }

  /**
   * Search workflows by name
   * @param {string} name - Search query
   * @returns {Promise<Array>} Matching workflows
   */
  async searchWorkflows(name) {
    const encoded = encodeURIComponent(name);
    const response = await this.request(`/workflows/search?name=${encoded}`);
    return response;
  }

  /**
   * Filter workflows by tag
   * @param {string} tag - Tag to filter by
   * @returns {Promise<Array>} Matching workflows
   */
  async filterByTag(tag) {
    const encoded = encodeURIComponent(tag);
    const response = await this.request(`/workflows/by-tag?tag=${encoded}`);
    return response;
  }

  /**
   * Filter workflows by creator
   * @param {string} creator - Creator username
   * @returns {Promise<Array>} Matching workflows
   */
  async filterByCreator(creator) {
    const encoded = encodeURIComponent(creator);
    const response = await this.request(`/workflows/by-creator?creator=${encoded}`);
    return response;
  }

  /**
   * Get workflow statistics
   * @returns {Promise<Object>} Statistics object
   */
  async getStatistics() {
    const response = await this.request('/workflows/statistics');
    return response;
  }

  /**
   * Get most executed workflows
   * @returns {Promise<Array>} Top 10 workflows
   */
  async getMostExecuted() {
    const response = await this.request('/workflows/most-executed');
    return response;
  }

  /**
   * Get most successful workflows
   * @param {number} page - Page number
   * @param {number} size - Page size
   * @returns {Promise<Array>} Workflows sorted by success rate
   */
  async getMostSuccessful(page = 0, size = 10) {
    const response = await this.request(`/workflows/most-successful?page=${page}&size=${size}`);
    return response;
  }

  /**
   * Get recently executed workflows
   * @returns {Promise<Array>} Top 10 recent workflows
   */
  async getRecentlyExecuted() {
    const response = await this.request('/workflows/recently-executed');
    return response;
  }

  /**
   * Get recently created workflows
   * @returns {Promise<Array>} Top 10 recent workflows
   */
  async getRecentlyCreated() {
    const response = await this.request('/workflows/recently-created');
    return response;
  }

  /**
   * Get workflows that were never executed
   * @returns {Promise<Array>} Workflows with 0 executions
   */
  async getNeverExecuted() {
    const response = await this.request('/workflows/never-executed');
    return response;
  }

  /**
   * Execute workflow on backend (optional - extension replays locally)
   * @param {string} id - Workflow ID
   * @param {Object} parameters - Execution parameters
   * @returns {Promise<Object>} Execution result
   */
  async executeWorkflow(id, parameters = {}) {
    const response = await this.request(`/workflows/${id}/execute`, {
      method: 'POST',
      body: JSON.stringify(parameters)
    });

    return response;
  }

  /**
   * Make HTTP request to backend
   * @private
   */
  async request(endpoint, options = {}) {
    const url = `${this.baseUrl}${endpoint}`;

    const defaultHeaders = {
      'Content-Type': 'application/json',
      'Accept': 'application/json'
    };

    const config = {
      ...options,
      headers: {
        ...defaultHeaders,
        ...options.headers
      }
    };

    try {
      console.log(`[APIClient] ${options.method || 'GET'} ${url}`);

      const response = await fetch(url, config);

      // Handle non-JSON responses (e.g., DELETE returns 204 No Content)
      if (response.status === 204 || response.status === 205) {
        return null;
      }

      // Parse JSON response
      const data = await response.json();

      // Check for HTTP errors
      if (!response.ok) {
        throw new Error(
          data.message || data.error || `HTTP ${response.status}: ${response.statusText}`
        );
      }

      return data;

    } catch (error) {
      console.error('[APIClient] Request failed:', error);

      // Enhance error message
      if (error.message.includes('Failed to fetch')) {
        throw new Error(
          'Cannot connect to backend API. Make sure the server is running at ' + this.baseUrl
        );
      }

      throw error;
    }
  }

  /**
   * Check if backend is reachable
   * @returns {Promise<boolean>}
   */
  async ping() {
    try {
      await this.getStatistics();
      return true;
    } catch (error) {
      return false;
    }
  }

  /**
   * Set base URL
   * @param {string} url - New base URL
   */
  setBaseUrl(url) {
    this.baseUrl = url;
    console.log('[APIClient] Base URL updated:', url);
  }

  /**
   * Get base URL
   * @returns {string}
   */
  getBaseUrl() {
    return this.baseUrl;
  }
}

// Export default instance
export const apiClient = new WorkflowAPIClient();
