/**
 * Content Script - DOM Event Capture
 * Captures user interactions and sends them to background worker
 * Note: Content scripts don't support ES modules in Manifest V3, so this is standalone
 */

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

/**
 * Message listener for commands from background script
 */
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  console.log('[ContentScript] Received message:', message.type);

  switch (message.type) {
    case 'START_RECORDING':
      startRecording();
      sendResponse({ success: true });
      break;

    case 'STOP_RECORDING':
      stopRecording();
      sendResponse({ success: true });
      break;

    case 'PAUSE_RECORDING':
      isPaused = true;
      sendResponse({ success: true });
      break;

    case 'RESUME_RECORDING':
      isPaused = false;
      sendResponse({ success: true });
      break;

    case 'REPLAY_WORKFLOW':
      // Import and execute workflow player
      import(chrome.runtime.getURL('lib/workflow-player.js'))
        .then(module => {
          const player = new module.WorkflowPlayer();
          return player.play(message.workflow, message.parameters);
        })
        .then(result => sendResponse(result))
        .catch(error => sendResponse({ success: false, error: error.message }));

      return true; // Keep channel open for async response

    default:
      console.warn('[ContentScript] Unknown message type:', message.type);
      sendResponse({ success: false, error: 'Unknown message type' });
  }

  return false;
});

console.log('[ContentScript] Workflow recorder content script loaded');
