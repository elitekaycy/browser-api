/**
 * Smart Selector Generator
 * Generates reliable CSS selectors for DOM elements with multiple fallback strategies
 */

export class SelectorGenerator {
  /**
   * Generate multiple selectors for an element (primary + alternatives)
   * @param {HTMLElement} element - Target element
   * @returns {string[]} Array of selectors (priority order)
   */
  generate(element) {
    if (!element || !(element instanceof Element)) {
      console.warn('[SelectorGenerator] Invalid element');
      return ['body'];
    }

    const selectors = [];

    // Priority 1: data-testid (most stable)
    const testId = this.getTestIdSelector(element);
    if (testId) selectors.push(testId);

    // Priority 2: ARIA attributes
    const ariaLabel = this.getAriaLabelSelector(element);
    if (ariaLabel) selectors.push(ariaLabel);

    const ariaRole = this.getAriaRoleSelector(element);
    if (ariaRole) selectors.push(ariaRole);

    // Priority 3: Semantic ID (not auto-generated)
    const id = this.getIdSelector(element);
    if (id) selectors.push(id);

    // Priority 4: Name attribute (form elements)
    const name = this.getNameSelector(element);
    if (name) selectors.push(name);

    // Priority 5: Semantic text-based selectors
    const textSelector = this.getTextSelector(element);
    if (textSelector) selectors.push(textSelector);

    // Priority 6: CSS path (fallback)
    const cssPath = this.getCSSPath(element);
    if (cssPath) selectors.push(cssPath);

    // Ensure we have at least one selector
    if (selectors.length === 0) {
      selectors.push('body');
    }

    return selectors;
  }

  /**
   * Get primary selector (highest priority available)
   */
  getPrimarySelector(element) {
    return this.generate(element)[0];
  }

  /**
   * Get data-testid selector
   */
  getTestIdSelector(element) {
    const testId = element.getAttribute('data-testid') ||
                   element.getAttribute('data-test-id') ||
                   element.getAttribute('data-test');

    if (testId) {
      return `[data-testid="${this.escapeSelector(testId)}"]`;
    }
    return null;
  }

  /**
   * Get ARIA label selector
   */
  getAriaLabelSelector(element) {
    const ariaLabel = element.getAttribute('aria-label');
    if (ariaLabel && ariaLabel.length <= 50) {
      return `[aria-label="${this.escapeSelector(ariaLabel)}"]`;
    }
    return null;
  }

  /**
   * Get ARIA role selector (combined with tag for specificity)
   */
  getAriaRoleSelector(element) {
    const role = element.getAttribute('role');
    if (role) {
      const tag = element.tagName.toLowerCase();
      return `${tag}[role="${this.escapeSelector(role)}"]`;
    }
    return null;
  }

  /**
   * Get ID selector (only if semantic, not auto-generated)
   */
  getIdSelector(element) {
    const id = element.id;
    if (!id) return null;

    // Skip auto-generated IDs (contain numbers, long random strings)
    if (/^[0-9]/.test(id) || id.length > 30 || /[0-9]{5,}/.test(id)) {
      return null;
    }

    return `#${this.escapeSelector(id)}`;
  }

  /**
   * Get name attribute selector
   */
  getNameSelector(element) {
    const name = element.getAttribute('name');
    if (name && ['INPUT', 'SELECT', 'TEXTAREA', 'BUTTON'].includes(element.tagName)) {
      return `${element.tagName.toLowerCase()}[name="${this.escapeSelector(name)}"]`;
    }
    return null;
  }

  /**
   * Get text-based selector (for buttons, links)
   */
  getTextSelector(element) {
    const tag = element.tagName.toLowerCase();
    const text = element.textContent?.trim();

    if (!text || text.length > 50) return null;

    // Only for interactive elements
    if (['button', 'a', 'label'].includes(tag)) {
      // Escape special characters in text
      const escapedText = text.replace(/['"]/g, '\\$&');
      return `${tag}:contains("${escapedText}")`;
    }

    return null;
  }

  /**
   * Get CSS path selector (comprehensive fallback)
   */
  getCSSPath(element) {
    if (element.tagName.toLowerCase() === 'html') {
      return 'html';
    }

    if (element.tagName.toLowerCase() === 'body') {
      return 'body';
    }

    const path = [];
    let current = element;

    while (current && current.nodeType === Node.ELEMENT_NODE) {
      let selector = current.tagName.toLowerCase();

      // If element has unique ID, use it and stop
      if (current.id && this.isUniqueSelector(`#${current.id}`)) {
        selector = `#${this.escapeSelector(current.id)}`;
        path.unshift(selector);
        break;
      }

      // Add class names if available
      if (current.classList.length > 0) {
        const classes = Array.from(current.classList)
          .filter(cls => !this.isUtilityClass(cls))
          .map(cls => `.${this.escapeSelector(cls)}`)
          .join('');

        if (classes) {
          selector += classes;
        }
      }

      // Add nth-child if needed for uniqueness
      if (current.parentElement) {
        const siblings = Array.from(current.parentElement.children);
        const similarSiblings = siblings.filter(
          sibling => sibling.tagName === current.tagName
        );

        if (similarSiblings.length > 1) {
          const index = similarSiblings.indexOf(current) + 1;
          selector += `:nth-of-type(${index})`;
        }
      }

      path.unshift(selector);

      current = current.parentElement;

      // Stop at body or if we've gone too deep
      if (!current || current.tagName.toLowerCase() === 'body' || path.length > 10) {
        break;
      }
    }

    return path.join(' > ');
  }

  /**
   * Check if a class is likely a utility class (Tailwind, Bootstrap, etc.)
   */
  isUtilityClass(className) {
    // Common utility class patterns
    const utilityPatterns = [
      /^(mt|mb|ml|mr|mx|my|pt|pb|pl|pr|px|py)-/,  // Margin/padding
      /^(w|h|min-w|min-h|max-w|max-h)-/,          // Width/height
      /^(text|font|bg|border|rounded|shadow)-/,    // Typography/styling
      /^(flex|grid|inline|block|hidden)-/,         // Layout
      /^(hover|focus|active|disabled):/,           // State classes
      /^[a-z]+-\d+$/                               // Generic utility pattern
    ];

    return utilityPatterns.some(pattern => pattern.test(className));
  }

  /**
   * Check if selector is unique in document
   */
  isUniqueSelector(selector) {
    try {
      return document.querySelectorAll(selector).length === 1;
    } catch (error) {
      return false;
    }
  }

  /**
   * Escape special characters in selector strings
   */
  escapeSelector(str) {
    if (!str) return '';
    // Escape special CSS characters
    return str.replace(/[!"#$%&'()*+,.\/:;<=>?@[\\\]^`{|}~]/g, '\\$&');
  }

  /**
   * Validate if selector can find the element
   */
  validateSelector(selector, element) {
    try {
      const found = document.querySelector(selector);
      return found === element;
    } catch (error) {
      console.warn('[SelectorGenerator] Invalid selector:', selector);
      return false;
    }
  }

  /**
   * Get element description for display
   */
  getElementDescription(element) {
    const tag = element.tagName.toLowerCase();
    const text = element.textContent?.trim().substring(0, 30);
    const type = element.type;
    const name = element.name;
    const id = element.id;
    const ariaLabel = element.getAttribute('aria-label');

    // Build description
    let description = tag;

    if (type) description += `[${type}]`;
    if (name) description += ` "${name}"`;
    else if (id) description += ` #${id}`;
    else if (ariaLabel) description += ` "${ariaLabel}"`;
    else if (text) description += ` "${text}"`;

    return description;
  }
}

// Export singleton instance
export const selectorGenerator = new SelectorGenerator();
