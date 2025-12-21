import type { Action } from '../types/workflow';

export type CodeLanguage = 'typescript' | 'javascript' | 'python' | 'java';

export class PlaywrightCodeGenerator {
  /**
   * Generate Playwright code from actions
   */
  static generate(
    actions: Action[],
    url: string,
    language: CodeLanguage = 'typescript'
  ): string {
    switch (language) {
      case 'typescript':
        return this.generateTypeScript(actions, url);
      case 'javascript':
        return this.generateJavaScript(actions, url);
      case 'python':
        return this.generatePython(actions, url);
      case 'java':
        return this.generateJava(actions, url);
      default:
        throw new Error(`Unsupported language: ${language}`);
    }
  }

  private static generateTypeScript(actions: Action[], url: string): string {
    const actionCode = actions
      .map((action) => this.generateTSActionCode(action))
      .join('\n');

    return `import { test, expect } from '@playwright/test';

test('recorded workflow', async ({ page }) => {
  // Navigate to page
  await page.goto('${url}');

  // Perform recorded actions
${actionCode}
});
`;
  }

  private static generateJavaScript(actions: Action[], url: string): string {
    const actionCode = actions
      .map((action) => this.generateTSActionCode(action))
      .join('\n');

    return `const { test, expect } = require('@playwright/test');

test('recorded workflow', async ({ page }) => {
  // Navigate to page
  await page.goto('${url}');

  // Perform recorded actions
${actionCode}
});
`;
  }

  private static generatePython(actions: Action[], url: string): string {
    const actionCode = actions
      .map((action) => this.generatePythonActionCode(action))
      .join('\n');

    return `from playwright.sync_api import Page, expect

def test_recorded_workflow(page: Page):
    # Navigate to page
    page.goto("${url}")

    # Perform recorded actions
${actionCode}
`;
  }

  private static generateJava(actions: Action[], url: string): string {
    const actionCode = actions
      .map((action) => this.generateJavaActionCode(action))
      .join('\n');

    return `import com.microsoft.playwright.*;

public class RecordedWorkflow {
    public static void main(String[] args) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch();
            Page page = browser.newPage();

            // Navigate to page
            page.navigate("${url}");

            // Perform recorded actions
${actionCode}

            browser.close();
        }
    }
}
`;
  }

  private static generateTSActionCode(action: Action): string {
    const indent = '  ';
    switch (action.type) {
      case 'click':
        return `${indent}await page.locator('${this.escapeSelector(action.selector)}').click();`;
      case 'fill':
        return `${indent}await page.locator('${this.escapeSelector(action.selector)}').fill('${this.escapeValue(action.value)}');`;
      case 'navigate':
        return `${indent}await page.goto('${action.value}');`;
      case 'select':
        return `${indent}await page.locator('${this.escapeSelector(action.selector)}').selectOption('${this.escapeValue(action.value)}');`;
      case 'wait':
        return `${indent}await page.locator('${this.escapeSelector(action.selector)}').waitFor();`;
      case 'screenshot':
        return `${indent}await page.screenshot({ path: 'screenshot.png' });`;
      default:
        return `${indent}// ${action.type}: ${action.selector || 'N/A'}`;
    }
  }

  private static generatePythonActionCode(action: Action): string {
    const indent = '    ';
    switch (action.type) {
      case 'click':
        return `${indent}page.locator("${this.escapeSelector(action.selector)}").click()`;
      case 'fill':
        return `${indent}page.locator("${this.escapeSelector(action.selector)}").fill("${this.escapeValue(action.value)}")`;
      case 'navigate':
        return `${indent}page.goto("${action.value}")`;
      case 'select':
        return `${indent}page.locator("${this.escapeSelector(action.selector)}").select_option("${this.escapeValue(action.value)}")`;
      case 'wait':
        return `${indent}page.locator("${this.escapeSelector(action.selector)}").wait_for()`;
      case 'screenshot':
        return `${indent}page.screenshot(path="screenshot.png")`;
      default:
        return `${indent}# ${action.type}: ${action.selector || 'N/A'}`;
    }
  }

  private static generateJavaActionCode(action: Action): string {
    const indent = '            ';
    switch (action.type) {
      case 'click':
        return `${indent}page.locator("${this.escapeSelector(action.selector)}").click();`;
      case 'fill':
        return `${indent}page.locator("${this.escapeSelector(action.selector)}").fill("${this.escapeValue(action.value)}");`;
      case 'navigate':
        return `${indent}page.navigate("${action.value}");`;
      case 'select':
        return `${indent}page.locator("${this.escapeSelector(action.selector)}").selectOption("${this.escapeValue(action.value)}");`;
      case 'wait':
        return `${indent}page.locator("${this.escapeSelector(action.selector)}").waitFor();`;
      case 'screenshot':
        return `${indent}page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get("screenshot.png")));`;
      default:
        return `${indent}// ${action.type}: ${action.selector || 'N/A'}`;
    }
  }

  private static escapeSelector(selector?: string): string {
    if (!selector) return '';
    return selector.replace(/'/g, "\\'").replace(/"/g, '\\"');
  }

  private static escapeValue(value?: string): string {
    if (!value) return '';
    return value.replace(/'/g, "\\'").replace(/"/g, '\\"');
  }
}
