/**
 * Workflow Player - Replay Engine
 * Executes workflow actions sequentially in the browser
 */

export class WorkflowPlayer {
  constructor() {
    this.isPlaying = false;
    this.currentStep = 0;
    this.totalSteps = 0;
  }

  /**
   * Play a workflow
   * @param {Object} workflow - Workflow object with actions array
   * @param {Object} parameters - Parameters for placeholder substitution
   * @returns {Promise<Object>} Execution result
   */
  async play(workflow, parameters = {}) {
    console.log('[WorkflowPlayer] Starting workflow:', workflow.name);

    this.isPlaying = true;
    this.currentStep = 0;
    this.totalSteps = workflow.actions?.length || 0;

    if (this.totalSteps === 0) {
      return { success: false, error: 'No actions to execute' };
    }

    // Substitute parameters in actions
    const actions = this.substituteParameters(workflow.actions, parameters);

    try {
      for (let i = 0; i < actions.length; i++) {
        if (!this.isPlaying) {
          return { success: false, error: 'Playback stopped', stepsCompleted: i };
        }

        this.currentStep = i;
        const action = actions[i];

        console.log(`[WorkflowPlayer] Step ${i + 1}/${actions.length}:`, action.type, action.selector);

        // Execute action
        await this.executeAction(action);

        // Small delay between actions for stability
        await this.sleep(100);
      }

      console.log('[WorkflowPlayer] Workflow completed successfully');
      return {
        success: true,
        stepsCompleted: actions.length,
        finalUrl: window.location.href
      };

    } catch (error) {
      console.error('[WorkflowPlayer] Workflow failed at step', this.currentStep, error);
      return {
        success: false,
        error: error.message,
        failedStep: this.currentStep,
        failedAction: actions[this.currentStep],
        stepsCompleted: this.currentStep
      };
    } finally {
      this.isPlaying = false;
    }
  }

  /**
   * Execute a single action
   */
  async executeAction(action) {
    switch (action.type) {
      case 'CLICK':
        await this.click(action.selector);
        break;

      case 'FILL':
        await this.fill(action.selector, action.value);
        break;

      case 'SELECT':
        await this.select(action.selector, action.value);
        break;

      case 'SUBMIT':
        await this.submit(action.selector);
        break;

      case 'CHECK':
        await this.check(action.selector, action.value === 'true');
        break;

      case 'NAVIGATE':
        await this.navigate(action.value);
        break;

      case 'WAIT':
        await this.sleep(action.waitMs || 1000);
        break;

      case 'WAIT_NAVIGATION':
        await this.waitForNavigation(action.waitMs || 5000);
        break;

      case 'SCROLL':
        await this.scroll(action.selector);
        break;

      case 'HOVER':
        await this.hover(action.selector);
        break;

      case 'PRESS_KEY':
        await this.pressKey(action.selector, action.value);
        break;

      case 'CLEAR':
        await this.clear(action.selector);
        break;

      case 'SCREENSHOT':
        // Screenshot handled by extension
        console.log('[WorkflowPlayer] Screenshot action (skipped in replay)');
        break;

      default:
        throw new Error(`Unknown action type: ${action.type}`);
    }
  }

  /**
   * Click element
   */
  async click(selector) {
    const element = await this.waitForElement(selector);
    element.scrollIntoView({ behavior: 'smooth', block: 'center' });
    await this.sleep(200); // Wait for scroll
    element.click();
  }

  /**
   * Fill input field
   */
  async fill(selector, value) {
    const element = await this.waitForElement(selector);
    element.scrollIntoView({ behavior: 'smooth', block: 'center' });

    // Clear existing value
    element.value = '';
    element.focus();

    // Simulate typing (for better compatibility with validation)
    for (const char of value) {
      element.value += char;
      element.dispatchEvent(new Event('input', { bubbles: true }));
      await this.sleep(50); // Natural typing speed
    }

    element.dispatchEvent(new Event('change', { bubbles: true }));
    element.blur();
  }

  /**
   * Select dropdown option
   */
  async select(selector, value) {
    const element = await this.waitForElement(selector);
    element.value = value;
    element.dispatchEvent(new Event('change', { bubbles: true }));
  }

  /**
   * Submit form
   */
  async submit(selector) {
    const element = await this.waitForElement(selector);
    element.submit();
    await this.waitForNavigation();
  }

  /**
   * Check/uncheck checkbox or radio
   */
  async check(selector, checked) {
    const element = await this.waitForElement(selector);

    if (element.checked !== checked) {
      element.click();
    }
  }

  /**
   * Navigate to URL
   */
  async navigate(url) {
    window.location.href = url;
    await this.waitForNavigation();
  }

  /**
   * Scroll to element
   */
  async scroll(selector) {
    const element = await this.waitForElement(selector);
    element.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  /**
   * Hover over element
   */
  async hover(selector) {
    const element = await this.waitForElement(selector);
    const mouseEnter = new MouseEvent('mouseenter', { bubbles: true });
    const mouseOver = new MouseEvent('mouseover', { bubbles: true });
    element.dispatchEvent(mouseEnter);
    element.dispatchEvent(mouseOver);
  }

  /**
   * Press keyboard key
   */
  async pressKey(selector, key) {
    const element = selector ? await this.waitForElement(selector) : document.activeElement;

    const keyDownEvent = new KeyboardEvent('keydown', { key, bubbles: true });
    const keyPressEvent = new KeyboardEvent('keypress', { key, bubbles: true });
    const keyUpEvent = new KeyboardEvent('keyup', { key, bubbles: true });

    element.dispatchEvent(keyDownEvent);
    element.dispatchEvent(keyPressEvent);
    element.dispatchEvent(keyUpEvent);
  }

  /**
   * Clear input field
   */
  async clear(selector) {
    const element = await this.waitForElement(selector);
    element.value = '';
    element.dispatchEvent(new Event('input', { bubbles: true }));
    element.dispatchEvent(new Event('change', { bubbles: true }));
  }

  /**
   * Wait for element to appear and be visible
   */
  async waitForElement(selector, timeout = 10000) {
    const startTime = Date.now();
    let lastError = null;

    while (Date.now() - startTime < timeout) {
      try {
        const element = document.querySelector(selector);

        if (element && this.isElementVisible(element)) {
          return element;
        }

        lastError = 'Element found but not visible';
      } catch (error) {
        lastError = error.message;
      }

      await this.sleep(100);
    }

    throw new Error(`Element not found: ${selector} (waited ${timeout}ms, last error: ${lastError})`);
  }

  /**
   * Check if element is visible
   */
  isElementVisible(element) {
    const rect = element.getBoundingClientRect();
    const style = window.getComputedStyle(element);

    return (
      rect.width > 0 &&
      rect.height > 0 &&
      style.visibility !== 'hidden' &&
      style.display !== 'none' &&
      parseFloat(style.opacity) > 0
    );
  }

  /**
   * Wait for navigation to complete
   */
  async waitForNavigation(timeout = 10000) {
    const startUrl = window.location.href;
    const startTime = Date.now();

    return new Promise((resolve, reject) => {
      const checkInterval = setInterval(() => {
        // Navigation occurred
        if (window.location.href !== startUrl) {
          clearInterval(checkInterval);
          resolve();
        }

        // Timeout
        if (Date.now() - startTime > timeout) {
          clearInterval(checkInterval);
          resolve(); // Resolve anyway (navigation might not be needed)
        }
      }, 100);

      // Also listen for load event
      window.addEventListener('load', () => {
        clearInterval(checkInterval);
        resolve();
      }, { once: true });
    });
  }

  /**
   * Substitute parameters in actions
   */
  substituteParameters(actions, parameters) {
    return actions.map(action => ({
      ...action,
      value: action.value ? this.replaceParams(action.value, parameters) : null
    }));
  }

  /**
   * Replace ${paramName} placeholders with actual values
   */
  replaceParams(text, parameters) {
    return text.replace(/\$\{(\w+)\}/g, (match, key) => {
      if (parameters[key] !== undefined) {
        return parameters[key];
      }
      console.warn(`[WorkflowPlayer] Parameter not provided: ${key}`);
      return match; // Keep placeholder if parameter not provided
    });
  }

  /**
   * Sleep for specified milliseconds
   */
  sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  /**
   * Stop playback
   */
  stop() {
    console.log('[WorkflowPlayer] Stopping playback');
    this.isPlaying = false;
  }

  /**
   * Get current progress
   */
  getProgress() {
    return {
      currentStep: this.currentStep,
      totalSteps: this.totalSteps,
      percentage: this.totalSteps > 0 ? (this.currentStep / this.totalSteps) * 100 : 0
    };
  }
}

// Export default instance
export const workflowPlayer = new WorkflowPlayer();
