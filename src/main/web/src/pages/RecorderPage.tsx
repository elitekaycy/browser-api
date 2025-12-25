import { Chrome, Download, PlayCircle, CheckCircle } from 'lucide-react';

export const RecorderPage = () => {
  return (
    <div className="min-h-screen bg-gradient-to-br from-blue-50 via-white to-purple-50 p-8">
      <div className="max-w-4xl mx-auto">
        {/* Header */}
        <div className="text-center mb-12">
          <div className="inline-flex items-center justify-center w-20 h-20 bg-blue-600 rounded-full mb-6">
            <Chrome className="w-12 h-12 text-white" />
          </div>
          <h1 className="text-4xl font-bold text-gray-900 mb-4">
            Workflow Recorder Chrome Extension
          </h1>
          <p className="text-xl text-gray-600 max-w-2xl mx-auto">
            Record browser workflows directly in your browser with our powerful Chrome extension.
            No more Cloudflare blocks, no iframe restrictions!
          </p>
        </div>

        {/* Main CTA Card */}
        <div className="bg-white rounded-2xl shadow-xl p-8 mb-8 border border-gray-200">
          <div className="flex items-start gap-6">
            <div className="flex-shrink-0">
              <div className="w-16 h-16 bg-gradient-to-br from-blue-600 to-purple-600 rounded-xl flex items-center justify-center">
                <Download className="w-8 h-8 text-white" />
              </div>
            </div>
            <div className="flex-1">
              <h2 className="text-2xl font-bold text-gray-900 mb-3">
                Get the Chrome Extension
              </h2>
              <p className="text-gray-600 mb-6">
                Install our Chrome extension to start recording workflows. Works on any website,
                bypasses Cloudflare protection, and integrates seamlessly with this backend.
              </p>
              <div className="flex flex-col sm:flex-row gap-4">
                <a
                  href="chrome://extensions/"
                  className="inline-flex items-center justify-center px-6 py-3 bg-blue-600 text-white font-semibold rounded-lg hover:bg-blue-700 transition-colors shadow-md hover:shadow-lg"
                >
                  <Chrome className="w-5 h-5 mr-2" />
                  Open Chrome Extensions
                </a>
                <button
                  onClick={() => {
                    const extensionPath = window.location.origin + '/browser-extension';
                    navigator.clipboard.writeText(extensionPath);
                    alert('Extension path copied! Load it as an unpacked extension.');
                  }}
                  className="inline-flex items-center justify-center px-6 py-3 bg-gray-100 text-gray-700 font-semibold rounded-lg hover:bg-gray-200 transition-colors"
                >
                  <Download className="w-5 h-5 mr-2" />
                  Copy Extension Path
                </button>
              </div>
            </div>
          </div>
        </div>

        {/* Installation Steps */}
        <div className="bg-white rounded-2xl shadow-lg p-8 mb-8 border border-gray-200">
          <h3 className="text-2xl font-bold text-gray-900 mb-6">Installation Steps</h3>
          <div className="space-y-6">
            {/* Step 1 */}
            <div className="flex gap-4">
              <div className="flex-shrink-0">
                <div className="w-10 h-10 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center font-bold">
                  1
                </div>
              </div>
              <div className="flex-1">
                <h4 className="font-semibold text-gray-900 mb-2">Navigate to Chrome Extensions</h4>
                <p className="text-gray-600 mb-2">
                  Open Chrome and navigate to <code className="bg-gray-100 px-2 py-1 rounded">chrome://extensions/</code>
                </p>
                <p className="text-sm text-gray-500">
                  Or click the "Open Chrome Extensions" button above
                </p>
              </div>
            </div>

            {/* Step 2 */}
            <div className="flex gap-4">
              <div className="flex-shrink-0">
                <div className="w-10 h-10 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center font-bold">
                  2
                </div>
              </div>
              <div className="flex-1">
                <h4 className="font-semibold text-gray-900 mb-2">Enable Developer Mode</h4>
                <p className="text-gray-600">
                  Toggle the "Developer mode" switch in the top-right corner of the extensions page
                </p>
              </div>
            </div>

            {/* Step 3 */}
            <div className="flex gap-4">
              <div className="flex-shrink-0">
                <div className="w-10 h-10 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center font-bold">
                  3
                </div>
              </div>
              <div className="flex-1">
                <h4 className="font-semibold text-gray-900 mb-2">Load Unpacked Extension</h4>
                <p className="text-gray-600 mb-2">
                  Click "Load unpacked" and select the <code className="bg-gray-100 px-2 py-1 rounded">browser-extension/</code> directory
                </p>
                <p className="text-sm text-gray-500">
                  Location: <code className="bg-gray-50 px-2 py-1 rounded text-xs">{'{project-root}'}/browser-extension/</code>
                </p>
              </div>
            </div>

            {/* Step 4 */}
            <div className="flex gap-4">
              <div className="flex-shrink-0">
                <div className="w-10 h-10 bg-green-100 text-green-600 rounded-full flex items-center justify-center">
                  <CheckCircle className="w-6 h-6" />
                </div>
              </div>
              <div className="flex-1">
                <h4 className="font-semibold text-gray-900 mb-2">Open Side Panel</h4>
                <p className="text-gray-600">
                  Click the extension icon in Chrome toolbar to open the side panel and start recording!
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* Features */}
        <div className="grid md:grid-cols-2 gap-6 mb-8">
          <div className="bg-white rounded-xl shadow-md p-6 border border-gray-200">
            <div className="w-12 h-12 bg-green-100 rounded-lg flex items-center justify-center mb-4">
              <PlayCircle className="w-6 h-6 text-green-600" />
            </div>
            <h3 className="text-lg font-semibold text-gray-900 mb-2">Record Anywhere</h3>
            <p className="text-gray-600">
              Works on any website including Cloudflare-protected sites. No bot detection, no restrictions.
            </p>
          </div>

          <div className="bg-white rounded-xl shadow-md p-6 border border-gray-200">
            <div className="w-12 h-12 bg-purple-100 rounded-lg flex items-center justify-center mb-4">
              <CheckCircle className="w-6 h-6 text-purple-600" />
            </div>
            <h3 className="text-lg font-semibold text-gray-900 mb-2">Smart Selectors</h3>
            <p className="text-gray-600">
              Automatically generates reliable CSS selectors with multiple fallback strategies.
            </p>
          </div>

          <div className="bg-white rounded-xl shadow-md p-6 border border-gray-200">
            <div className="w-12 h-12 bg-blue-100 rounded-lg flex items-center justify-center mb-4">
              <svg className="w-6 h-6 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
            </div>
            <h3 className="text-lg font-semibold text-gray-900 mb-2">Instant Replay</h3>
            <p className="text-gray-600">
              Replay workflows instantly in your browser. No server-side browser launches needed.
            </p>
          </div>

          <div className="bg-white rounded-xl shadow-md p-6 border border-gray-200">
            <div className="w-12 h-12 bg-orange-100 rounded-lg flex items-center justify-center mb-4">
              <svg className="w-6 h-6 text-orange-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
              </svg>
            </div>
            <h3 className="text-lg font-semibold text-gray-900 mb-2">Backend Integration</h3>
            <p className="text-gray-600">
              Seamlessly saves and manages workflows through this backend API. Configure the URL in extension settings.
            </p>
          </div>
        </div>

        {/* Why Extension Banner */}
        <div className="bg-gradient-to-r from-blue-600 to-purple-600 rounded-2xl shadow-xl p-8 text-white">
          <h3 className="text-2xl font-bold mb-4">Why Use the Extension?</h3>
          <div className="grid md:grid-cols-3 gap-6 mb-6">
            <div>
              <h4 className="font-semibold mb-2">✅ No Bot Detection</h4>
              <p className="text-blue-100 text-sm">
                Runs in your real browser, not detected by Cloudflare or anti-bot systems
              </p>
            </div>
            <div>
              <h4 className="font-semibold mb-2">✅ Direct DOM Access</h4>
              <p className="text-blue-100 text-sm">
                No iframe restrictions or cross-origin issues
              </p>
            </div>
            <div>
              <h4 className="font-semibold mb-2">✅ Perfect CAPTCHAs</h4>
              <p className="text-blue-100 text-sm">
                Interact with CAPTCHAs naturally in your browser
              </p>
            </div>
          </div>
          <p className="text-blue-100 text-sm">
            The old Playwright-based recorder had limitations with modern websites. The extension approach
            solves all these issues by running directly in your browser.
          </p>
        </div>

        {/* Help */}
        <div className="mt-8 text-center text-gray-600">
          <p className="mb-2">Need help? Check the extension README for detailed documentation.</p>
          <p className="text-sm text-gray-500">
            Location: <code className="bg-gray-100 px-2 py-1 rounded">browser-extension/README.md</code>
          </p>
        </div>
      </div>
    </div>
  );
};
