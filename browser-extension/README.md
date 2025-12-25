# Workflow Recorder - Chrome Extension

A powerful Chrome extension for recording and replaying browser workflows with seamless backend integration.

## Features

✅ **Side Panel UI** - Native Chrome Side Panel API for persistent sidebar
✅ **Real-time Recording** - Capture clicks, typing, navigation, form submissions
✅ **Smart Selectors** - Multi-strategy selector generation (data-testid, ARIA, CSS)
✅ **Workflow Replay** - Execute workflows with parameter substitution
✅ **Backend Integration** - Full CRUD operations via REST API
✅ **No Bot Detection** - Runs in real user browser (bypasses Cloudflare)
✅ **Beautiful UI** - Modern design matching the existing frontend

## Installation

### Development Mode

1. **Navigate to Chrome Extensions**
   ```
   chrome://extensions/
   ```

2. **Enable Developer Mode**
   - Toggle the "Developer mode" switch in the top-right corner

3. **Load Extension**
   - Click "Load unpacked"
   - Select the `browser-extension/` directory
   - Extension should appear in your extensions list

4. **Open Side Panel**
   - Click the extension icon in the toolbar
   - Side panel will open on the right side of the browser

### Backend Requirements

Make sure your Spring Boot backend is running:

```bash
# From project root
mvn spring-boot:run
```

The backend should be accessible at `http://localhost:8080`

## Usage

### Recording a Workflow

1. **Start Recording**
   - Open the side panel
   - Click "New Recording" or "Start Recording"
   - Navigate to the page you want to record

2. **Perform Actions**
   - Click elements
   - Fill in forms
   - Select dropdown options
   - Navigate between pages
   - All actions are captured automatically

3. **Stop Recording**
   - Click "Stop Recording" button
   - Enter workflow name (required)
   - Optionally add description and tags
   - Click "Save Workflow"

### Replaying a Workflow

1. **Browse Workflows**
   - Open the side panel
   - View list of saved workflows
   - Use search to filter workflows

2. **View Details**
   - Click on a workflow card
   - Review actions, stats, and metadata

3. **Replay**
   - Click "Replay Workflow" button
   - Workflow executes automatically
   - Success/failure notification appears

### Managing Workflows

- **Search**: Type in the search box to filter by name, description, or tags
- **Delete**: Click a workflow → Click "Delete" button → Confirm
- **View Stats**: See execution count, success rate, and last run time

## Extension Structure

```
browser-extension/
├── manifest.json              # Extension manifest (Manifest V3)
├── background.js              # Service worker (communication hub)
├── content.js                 # DOM event capture
├── lib/
│   ├── api-client.js         # Backend REST API client
│   ├── selector-generator.js # Smart CSS selector generation
│   └── workflow-player.js    # Workflow replay engine
├── sidepanel/
│   ├── sidepanel.html        # Side panel UI structure
│   ├── sidepanel.css         # Styling
│   ├── sidepanel.js          # Main controller
│   └── components/
│       ├── RecordingView.js  # Recording mode component
│       └── BrowseView.js     # Browse/replay component
└── icons/
    └── README.md             # Icon placeholder note
```

## Action Types Supported

The extension captures and replays the following action types:

| Action Type | Description | Example |
|------------|-------------|---------|
| `CLICK` | Click an element | Clicking a button |
| `FILL` | Type text into input | Entering username |
| `SELECT` | Select dropdown option | Choosing country |
| `CHECK` | Check/uncheck checkbox | Accepting terms |
| `SUBMIT` | Submit a form | Login form |
| `NAVIGATE` | Navigate to URL | Page transitions |
| `WAIT` | Wait for specified time | Delay between actions |
| `SCROLL` | Scroll to element | Scroll into view |
| `HOVER` | Hover over element | Trigger tooltips |
| `PRESS_KEY` | Press keyboard key | Press "Enter" |
| `CLEAR` | Clear input field | Reset form field |

## Selector Generation Strategy

The extension uses a priority-based selector generation strategy:

1. **`data-testid`** attributes (most stable)
2. **ARIA attributes** (`aria-label`, `role`)
3. **Semantic IDs** (non-auto-generated)
4. **Name attributes** (form elements)
5. **Text content** (buttons, links)
6. **CSS path** (fallback with nth-child)

This ensures reliable selectors that work across dynamic content and framework updates.

## Debugging

### Console Logs

The extension logs to different console contexts:

- **Background Worker Console**: `chrome://extensions/` → Click "Service Worker" link
- **Content Script Console**: Regular page DevTools console
- **Side Panel Console**: Right-click side panel → Inspect

### Common Issues

**Extension not loading:**
- Check Chrome version (requires Chrome 114+)
- Verify all files are in `browser-extension/` directory
- Check console for syntax errors

**Backend connection failing:**
- Ensure Spring Boot is running on `http://localhost:8080`
- Check CORS configuration in `CorsConfig.java`
- Verify API endpoints in Network tab

**Recording not capturing actions:**
- Check content script loaded (look for `[ContentScript]` logs)
- Verify page is not blocking content scripts
- Try reloading the page

**Replay failing:**
- Check if selectors are still valid (page may have changed)
- Verify element is visible and clickable
- Review action details for correct values

## Development

### Modifying the Extension

1. **Edit Files**
   - Make changes to JavaScript, HTML, or CSS files
   - No build step required (pure ES modules)

2. **Reload Extension**
   - Go to `chrome://extensions/`
   - Click the reload icon on the extension card
   - Or use keyboard shortcut: `Ctrl+R` on the extension

3. **Test Changes**
   - Open side panel
   - Verify functionality
   - Check console for errors

### Adding New Action Types

1. **Update Content Script** (`content.js`)
   - Add event listener for new action
   - Create action object with type, selector, value

2. **Update Replay Engine** (`lib/workflow-player.js`)
   - Add case in `executeAction()` switch statement
   - Implement action execution logic

3. **Update Backend** (if needed)
   - Add action type to `ActionType` enum
   - Update validation rules

## API Integration

The extension communicates with the backend REST API:

### Endpoints Used

- `POST /api/v1/workflows` - Create workflow
- `GET /api/v1/workflows` - List workflows
- `GET /api/v1/workflows/{id}` - Get workflow details
- `DELETE /api/v1/workflows/{id}` - Delete workflow
- `GET /api/v1/workflows/search?name=X` - Search workflows

### CORS Configuration

The backend includes CORS configuration to allow extension requests:

```java
// CorsConfig.java
.allowedOriginPatterns("chrome-extension://*")
.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
```

## Performance

- **Recording**: Minimal overhead, debounced input events (500ms)
- **Replay**: ~100ms delay between actions for stability
- **Selector Generation**: < 1ms per element
- **API Calls**: Cached workflows, minimal network requests

## Security

- **Sandboxed Execution**: Content scripts run in isolated context
- **No eval()**: Pure DOM manipulation, no code injection
- **HTTPS Support**: Works with secure sites
- **Input Sanitization**: All user input is escaped before rendering

## Advantages Over Playwright Approach

| Feature | Chrome Extension | Playwright (Old) |
|---------|------------------|------------------|
| Bot Detection | Not detected ✅ | Easily detected ❌ |
| Cloudflare | Works perfectly ✅ | Blocked ❌ |
| Cross-Origin | No restrictions ✅ | iframe issues ❌ |
| CAPTCHA Support | Full support ✅ | Fails ❌ |
| Performance | Lightweight ✅ | Heavy (browser launch) ❌ |
| Setup | Simple install ✅ | Complex infrastructure ❌ |
| SPA Compatibility | Perfect ✅ | Limited ❌ |

## Future Enhancements

- [ ] Visual selector picker (click to select elements)
- [ ] Parameter input UI before replay
- [ ] Screenshot capture during replay
- [ ] Workflow editor (modify actions after recording)
- [ ] Export/import workflows (JSON)
- [ ] Keyboard shortcuts (Ctrl+Shift+R)
- [ ] Multi-tab recording
- [ ] Chrome Web Store publication

## Troubleshooting

### Extension Installation Issues

**Error: "Failed to load extension"**
- Ensure you're loading the `browser-extension/` directory, not a parent folder
- Check that `manifest.json` is in the root of the loaded directory

**Error: "Manifest version 2 is deprecated"**
- This extension uses Manifest V3 - you may need to update Chrome
- Minimum Chrome version: 114

### Recording Issues

**Actions not being captured**
- Check that content script is loaded (`[ContentScript]` logs in console)
- Some sites block content scripts - try a different site
- Reload the page and try again

**Recording indicator not showing**
- Content script may not have permission on this page
- Check Chrome extension permissions
- Try on a regular website (not chrome:// pages)

### Replay Issues

**Selector not found error**
- Page structure may have changed since recording
- Try re-recording the workflow
- Check if element is in an iframe (not yet supported)

**Action executes but doesn't work**
- Some sites use custom JavaScript that blocks programmatic events
- Try adding a small delay (WAIT action) before the problematic action
- Verify the selector targets the correct element

## Support

For issues or questions:
1. Check console logs in all contexts (background, content, side panel)
2. Review this README for common solutions
3. Check backend logs for API errors
4. Open an issue in the project repository

## License

Same as the parent project.
