/**
 * Content Script - DOM Event Capture
 * Captures user interactions and sends them to background worker
 * Note: Content scripts don't support ES modules in Manifest V3, so this is standalone
 */

// Prevent double injection of event listeners
if (!window.__WORKFLOW_RECORDER_INJECTED__) {
  window.__WORKFLOW_RECORDER_INJECTED__ = true;
  console.log('[ContentScript] Initializing...');

// Recording state
let isRecording = false;
let isPaused = false;
let inputTimeout = null;

// Event listener references for cleanup
const eventListeners = new Map();

// Visual recording indicator
let recordingIndicator = null;

/**
 * Inline Selector Generator (simplified version for content script)
 */
class SelectorGenerator {
  generate(element) {
    const selectors = [];

    // data-testid
    const testId = element.getAttribute('data-testid');
    if (testId) selectors.push(`[data-testid="${testId}"]`);

    // aria-label
    const ariaLabel = element.getAttribute('aria-label');
    if (ariaLabel && ariaLabel.length <= 50) {
      selectors.push(`[aria-label="${ariaLabel}"]`);
    }

    // ID (if semantic)
    if (element.id && !/^[0-9]/.test(element.id) && element.id.length <= 30) {
      selectors.push(`#${element.id}`);
    }

    // Name attribute
    const name = element.getAttribute('name');
    if (name) selectors.push(`[name="${name}"]`);

    // CSS path
    selectors.push(this.getCSSPath(element));

    return selectors;
  }

  getCSSPath(element) {
    const path = [];
    let current = element;

    while (current && current.nodeType === Node.ELEMENT_NODE && current.tagName.toLowerCase() !== 'body') {
      let selector = current.tagName.toLowerCase();

      if (current.id && !/^[0-9]/.test(current.id)) {
        selector = `#${current.id}`;
        path.unshift(selector);
        break;
      }

      if (current.parentElement) {
        const siblings = Array.from(current.parentElement.children);
        const sameTag = siblings.filter(s => s.tagName === current.tagName);
        if (sameTag.length > 1) {
          const index = sameTag.indexOf(current) + 1;
          selector += `:nth-of-type(${index})`;
        }
      }

      path.unshift(selector);
      current = current.parentElement;

      if (path.length > 8) break;
    }

    return path.join(' > ');
  }

  getElementDescription(element) {
    const tag = element.tagName.toLowerCase();
    const text = element.textContent?.trim().substring(0, 50);
    const type = element.type;
    const name = element.name;

    let description = tag;
    if (type) description += `[${type}]`;
    if (name) description += ` "${name}"`;
    else if (text && !['script', 'style'].includes(tag)) description += `: "${text}"`;

    return description;
  }
}

const selectorGen = new SelectorGenerator();

/**
 * Start recording
 */
function startRecording() {
  console.log('[ContentScript] Starting recording');
  isRecording = true;
  isPaused = false;

  // Attach event listeners
  attachEventListeners();

  // Show recording indicator
  showRecordingIndicator();

  // Track initial page info
  sendNavigationEvent();
}

/**
 * Stop recording
 */
function stopRecording() {
  console.log('[ContentScript] Stopping recording');
  isRecording = false;
  isPaused = false;

  // Detach event listeners
  detachEventListeners();

  // Hide recording indicator
  hideRecordingIndicator();
}

/**
 * Attach event listeners for recording
 */
function attachEventListeners() {
  // Click events
  const clickHandler = handleClick.bind(this);
  document.addEventListener('click', clickHandler, true);
  eventListeners.set('click', clickHandler);

  // Input events (typing)
  const inputHandler = handleInput.bind(this);
  document.addEventListener('input', inputHandler, true);
  eventListeners.set('input', inputHandler);

  // Change events (select, checkbox, radio)
  const changeHandler = handleChange.bind(this);
  document.addEventListener('change', changeHandler, true);
  eventListeners.set('change', changeHandler);

  // Submit events
  const submitHandler = handleSubmit.bind(this);
  document.addEventListener('submit', submitHandler, true);
  eventListeners.set('submit', submitHandler);

  // Keydown events (for Enter key on inputs)
  const keydownHandler = handleKeydown.bind(this);
  document.addEventListener('keydown', keydownHandler, true);
  eventListeners.set('keydown', keydownHandler);

  console.log('[ContentScript] Event listeners attached');
}

/**
 * Detach event listeners
 */
function detachEventListeners() {
  eventListeners.forEach((handler, eventType) => {
    document.removeEventListener(eventType, handler, true);
  });
  eventListeners.clear();
  console.log('[ContentScript] Event listeners detached');
}

/**
 * Handle click events
 */
function handleClick(event) {
  if (!isRecording || isPaused) return;

  const target = event.target;
  if (!target || target === recordingIndicator || recordingIndicator?.contains(target)) {
    return; // Don't record clicks on the indicator
  }

  const selectors = selectorGen.generate(target);
  const description = selectorGen.getElementDescription(target);

  const action = {
    type: 'CLICK',
    selector: selectors[0],
    value: null,
    waitMs: null,
    description: `Click ${description}`,
    timestamp: Date.now(),
    elementInfo: {
      tagName: target.tagName,
      text: target.textContent?.trim().substring(0, 50),
      position: { x: event.clientX, y: event.clientY }
    }
  };

  sendAction(action);
}

/**
 * Handle input events (typing)
 */
function handleInput(event) {
  if (!isRecording || isPaused) return;

  const target = event.target;

  // Debounce typing events
  clearTimeout(inputTimeout);
  inputTimeout = setTimeout(() => {
    const selectors = selectorGen.generate(target);
    const description = selectorGen.getElementDescription(target);

    const action = {
      type: 'FILL',
      selector: selectors[0],
      value: target.value,
      waitMs: null,
      description: `Type in ${description}`,
      timestamp: Date.now()
    };

    sendAction(action);
  }, 500); // Wait 500ms after last keystroke
}

/**
 * Handle change events
 */
function handleChange(event) {
  if (!isRecording || isPaused) return;

  const target = event.target;
  const selectors = selectorGen.generate(target);
  const description = selectorGen.getElementDescription(target);

  let action;

  if (target.type === 'checkbox' || target.type === 'radio') {
    action = {
      type: 'CHECK',
      selector: selectors[0],
      value: target.checked.toString(),
      waitMs: null,
      description: `${target.checked ? 'Check' : 'Uncheck'} ${description}`
    };
  } else if (target.tagName === 'SELECT') {
    action = {
      type: 'SELECT',
      selector: selectors[0],
      value: target.value,
      waitMs: null,
      description: `Select "${target.value}" in ${description}`
    };
  } else {
    return; // Skip other change events
  }

  action.timestamp = Date.now();
  sendAction(action);
}

/**
 * Handle form submit events
 */
function handleSubmit(event) {
  if (!isRecording || isPaused) return;

  const form = event.target;
  const selectors = selectorGen.generate(form);

  const action = {
    type: 'SUBMIT',
    selector: selectors[0],
    value: null,
    waitMs: null,
    description: `Submit form`,
    timestamp: Date.now()
  };

  sendAction(action);
}

/**
 * Handle keydown events (Enter key on inputs)
 */
function handleKeydown(event) {
  if (!isRecording || isPaused) return;

  // Only capture Enter key
  if (event.key !== 'Enter') return;

  const target = event.target;

  // Only on input fields and textareas
  if (target.tagName !== 'INPUT' && target.tagName !== 'TEXTAREA') return;

  // Skip if it's inside a form (will be caught by submit handler)
  const form = target.closest('form');
  if (form) return;

  // Record Enter key press for inputs without forms (e.g., search boxes)
  const selectors = selectorGen.generate(target);
  const description = selectorGen.getElementDescription(target);

  const action = {
    type: 'PRESS_ENTER',
    selector: selectors[0],
    value: target.value,
    waitMs: null,
    description: `Press Enter in ${description}`,
    timestamp: Date.now()
  };

  sendAction(action);
}

/**
 * Send captured action to background script
 */
function sendAction(action) {
  chrome.runtime.sendMessage({
    type: 'ACTION_CAPTURED',
    action: action
  }).catch(error => {
    console.warn('[ContentScript] Failed to send action:', error);
  });
}

/**
 * Send navigation event
 */
function sendNavigationEvent() {
  chrome.runtime.sendMessage({
    type: 'NAVIGATION_EVENT',
    url: window.location.href,
    title: document.title
  }).catch(error => {
    console.warn('[ContentScript] Failed to send navigation:', error);
  });
}

/**
 * Show recording indicator overlay
 */
function showRecordingIndicator() {
  if (recordingIndicator) return;

  recordingIndicator = document.createElement('div');
  recordingIndicator.id = 'workflow-recorder-indicator';
  recordingIndicator.innerHTML = `
    <div style="
      position: fixed;
      top: 20px;
      right: 20px;
      background: #ef4444;
      color: white;
      padding: 12px 20px;
      border-radius: 8px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.3);
      z-index: 999999;
      font-family: system-ui, -apple-system, sans-serif;
      font-size: 14px;
      font-weight: 500;
      display: flex;
      align-items: center;
      gap: 8px;
      pointer-events: none;
      animation: pulse 2s infinite;
    ">
      <div style="
        width: 8px;
        height: 8px;
        background: white;
        border-radius: 50%;
        animation: blink 1s infinite;
      "></div>
      <span>Recording...</span>
    </div>
    <style>
      @keyframes blink {
        0%, 100% { opacity: 1; }
        50% { opacity: 0.3; }
      }
      @keyframes pulse {
        0%, 100% { transform: scale(1); }
        50% { transform: scale(1.05); }
      }
    </style>
  `;

  document.body.appendChild(recordingIndicator);
}

/**
 * Update recording indicator state
 */
function updateRecordingIndicator(state) {
  if (!recordingIndicator) return;

  const indicator = recordingIndicator.querySelector('div');
  if (!indicator) return;

  if (state === 'paused') {
    indicator.style.background = '#f59e0b'; // Warning orange
    indicator.innerHTML = `
      <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" style="margin-right: 8px;">
        <rect x="6" y="4" width="4" height="16"/>
        <rect x="14" y="4" width="4" height="16"/>
      </svg>
      Recording Paused
    `;
  } else if (state === 'recording') {
    indicator.style.background = '#ef4444'; // Red
    indicator.innerHTML = `
      <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" style="margin-right: 8px;">
        <circle cx="12" cy="12" r="6"/>
      </svg>
      Recording...
    `;
  }
}

/**
 * Hide recording indicator
 */
function hideRecordingIndicator() {
  if (recordingIndicator) {
    recordingIndicator.remove();
    recordingIndicator = null;
  }
}

/**
 * Track navigation changes (SPA support)
 */
let lastUrl = window.location.href;
const urlObserver = new MutationObserver(() => {
  if (window.location.href !== lastUrl) {
    lastUrl = window.location.href;

    if (isRecording && !isPaused) {
      // Record navigation action
      const action = {
        type: 'NAVIGATE',
        selector: null,
        value: window.location.href,
        waitMs: null,
        description: `Navigate to ${window.location.href}`,
        timestamp: Date.now()
      };

      sendAction(action);
      sendNavigationEvent();
    }
  }
});

// Observe URL changes
urlObserver.observe(document, { subtree: true, childList: true });

// Also listen to popstate for back/forward navigation
window.addEventListener('popstate', () => {
  if (isRecording && !isPaused && window.location.href !== lastUrl) {
    lastUrl = window.location.href;

    const action = {
      type: 'NAVIGATE',
      selector: null,
      value: window.location.href,
      waitMs: null,
      description: `Navigate to ${window.location.href}`,
      timestamp: Date.now()
    };

    sendAction(action);
    sendNavigationEvent();
  }
});

console.log('[ContentScript] Event listeners and state initialized');

} // End of injection guard

/**
 * Message listener for commands from background script
 * This is OUTSIDE the guard so it's always available
 */
if (!window.__WORKFLOW_RECORDER_LISTENER_ADDED__) {
  window.__WORKFLOW_RECORDER_LISTENER_ADDED__ = true;

  chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    console.log('[ContentScript] Received message:', message.type);

    switch (message.type) {
      case 'PING':
        // Health check to see if content script is loaded
        sendResponse({ success: true, loaded: true });
        break;

      case 'CHECK_WORKFLOW_PLAYER':
        // Check if WorkflowPlayer is loaded
        if (typeof WorkflowPlayer !== 'undefined') {
          sendResponse({ success: true, loaded: true });
        } else {
          sendResponse({ success: false, loaded: false });
        }
        break;

      case 'START_RECORDING':
        startRecording();
        sendResponse({ success: true });
        break;

      case 'STOP_RECORDING':
        stopRecording();
        sendResponse({ success: true });
        break;

      case 'PAUSE_RECORDING':
        console.log('[ContentScript] Pausing recording');
        isPaused = true;
        updateRecordingIndicator('paused');
        sendResponse({ success: true });
        break;

      case 'RESUME_RECORDING':
        console.log('[ContentScript] Resuming recording');
        isPaused = false;
        updateRecordingIndicator('recording');
        sendResponse({ success: true });
        break;

      case 'REPLAY_WORKFLOW':
        // Execute workflow player
        (async () => {
          try {
            // Check for WorkflowPlayer in both places
            let PlayerClass = null;

            if (typeof window.WorkflowPlayer !== 'undefined') {
              console.log('[ContentScript] WorkflowPlayer found on window');
              PlayerClass = window.WorkflowPlayer;
            } else if (typeof WorkflowPlayer !== 'undefined') {
              console.log('[ContentScript] WorkflowPlayer found in global scope');
              PlayerClass = WorkflowPlayer;
            } else {
              console.error('[ContentScript] WorkflowPlayer not found anywhere');
              console.log('[ContentScript] window.WorkflowPlayer:', typeof window.WorkflowPlayer);
              console.log('[ContentScript] WorkflowPlayer:', typeof WorkflowPlayer);
              console.log('[ContentScript] Window keys with Workflow:', Object.keys(window).filter(k => k.includes('Workflow')));
              sendResponse({
                success: false,
                error: 'WorkflowPlayer not loaded. Please reload the page and try again.'
              });
              return;
            }

            console.log('[ContentScript] Starting workflow replay...');
            const player = new PlayerClass();
            const result = await player.play(message.workflow, message.parameters);
            sendResponse(result);
          } catch (error) {
            console.error('[ContentScript] Workflow replay error:', error);
            sendResponse({ success: false, error: error.message });
          }
        })();

        return true; // Keep channel open for async response

      case 'ENTER_HIGHLIGHT_MODE':
        enterHighlightMode();
        sendResponse({ success: true });
        break;

      default:
        console.warn('[ContentScript] Unknown message type:', message.type);
        sendResponse({ success: false, error: 'Unknown message type' });
    }

    return false;
  });

  console.log('[ContentScript] Message listener registered');
}

/**
 * Highlight mode state and functions (outside injection guard)
 */
let isHighlightMode = false;
let highlightedElement = null;
let highlightOverlay = null;

function enterHighlightMode() {
  console.log('[ContentScript] Entering highlight mode');
  isHighlightMode = true;

  // Create highlight overlay
  highlightOverlay = document.createElement('div');
  highlightOverlay.id = 'workflow-highlight-overlay';
  highlightOverlay.style.cssText = `
    position: absolute;
    pointer-events: none;
    border: 3px solid #10b981;
    background: rgba(16, 185, 129, 0.1);
    z-index: 999998;
    transition: all 0.1s ease;
  `;
  document.body.appendChild(highlightOverlay);

  // Add instruction banner
  const banner = document.createElement('div');
  banner.id = 'workflow-highlight-banner';
  banner.style.cssText = `
    position: fixed;
    top: 20px;
    left: 50%;
    transform: translateX(-50%);
    background: #10b981;
    color: white;
    padding: 12px 24px;
    border-radius: 8px;
    box-shadow: 0 4px 12px rgba(0,0,0,0.3);
    z-index: 999999;
    font-family: system-ui, -apple-system, sans-serif;
    font-size: 14px;
    font-weight: 500;
    pointer-events: none;
  `;
  banner.textContent = 'Hover over an element and click to extract data from it';
  document.body.appendChild(banner);

  // Add event listeners for highlighting
  document.addEventListener('mouseover', highlightOnHover, true);
  document.addEventListener('click', selectElement, true);
  document.addEventListener('keydown', exitHighlightModeOnEscape, true);
}

function highlightOnHover(event) {
  if (!isHighlightMode) return;

  const target = event.target;

  // Ignore our own overlays
  if (target.id === 'workflow-highlight-overlay' ||
      target.id === 'workflow-highlight-banner' ||
      target === highlightOverlay) {
    return;
  }

  highlightedElement = target;

  // Update overlay position
  const rect = target.getBoundingClientRect();
  highlightOverlay.style.top = (rect.top + window.scrollY) + 'px';
  highlightOverlay.style.left = (rect.left + window.scrollX) + 'px';
  highlightOverlay.style.width = rect.width + 'px';
  highlightOverlay.style.height = rect.height + 'px';
  highlightOverlay.style.display = 'block';
}

function selectElement(event) {
  if (!isHighlightMode) return;

  event.preventDefault();
  event.stopPropagation();

  const target = event.target;

  // Ignore our own overlays
  if (target.id === 'workflow-highlight-overlay' ||
      target.id === 'workflow-highlight-banner') {
    return;
  }

  console.log('[ContentScript] Element selected for extraction');

  // Generate selector for the element (use global selectorGen if available)
  let selector = target.tagName.toLowerCase();
  if (target.id) {
    selector = '#' + target.id;
  } else if (target.className) {
    const classes = target.className.split(' ').filter(c => c).slice(0, 2).join('.');
    if (classes) selector = target.tagName.toLowerCase() + '.' + classes;
  }

  const elementInfo = {
    tagName: target.tagName,
    text: target.textContent?.trim().substring(0, 50),
    attributes: Array.from(target.attributes).map(attr => ({
      name: attr.name,
      value: attr.value
    }))
  };

  // Exit highlight mode
  exitHighlightModeCleanup();

  // Send selected element back to extension
  chrome.runtime.sendMessage({
    type: 'ELEMENT_SELECTED',
    selector: selector,
    element: elementInfo
  }).catch(error => {
    console.warn('[ContentScript] Failed to send element selection:', error);
  });
}

function exitHighlightModeOnEscape(event) {
  // Exit on Escape key
  if (event.key === 'Escape') {
    exitHighlightModeCleanup();
  }
}

function exitHighlightModeCleanup() {
  console.log('[ContentScript] Exiting highlight mode');
  isHighlightMode = false;
  highlightedElement = null;

  // Remove overlay
  if (highlightOverlay) {
    highlightOverlay.remove();
    highlightOverlay = null;
  }

  // Remove banner
  const banner = document.getElementById('workflow-highlight-banner');
  if (banner) {
    banner.remove();
  }

  // Remove event listeners
  document.removeEventListener('mouseover', highlightOnHover, true);
  document.removeEventListener('click', selectElement, true);
  document.removeEventListener('keydown', exitHighlightModeOnEscape, true);
}

console.log('[ContentScript] Workflow recorder content script loaded');
