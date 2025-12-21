import { useState } from 'react';

interface RecorderToolbarProps {
  url: string;
  isRecording: boolean;
  isLoading: boolean;
  canRecord: boolean;
  onUrlChange: (url: string) => void;
  onLoad: () => void;
  onToggleRecording: () => void;
}

export const RecorderToolbar = ({
  url,
  isRecording,
  isLoading,
  canRecord,
  onUrlChange,
  onLoad,
  onToggleRecording,
}: RecorderToolbarProps) => {
  const [localUrl, setLocalUrl] = useState(url);

  const handleUrlChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setLocalUrl(e.target.value);
    onUrlChange(e.target.value);
  };

  return (
    <div className="border-b border-gray-200 bg-white shadow-sm">
      <div className="max-w-full mx-auto px-6 py-4">
        <div className="flex items-center gap-6">
          {/* Logo and Navigation */}
          <div className="flex items-center gap-6 min-w-fit">
            <h1 className="text-lg font-semibold text-gray-900">Recorder</h1>
            <a
              href="/workflows"
              className="text-sm font-medium text-blue-600 hover:text-blue-700 flex items-center gap-1.5 whitespace-nowrap"
            >
              <svg
                className="w-4 h-4"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"
                />
              </svg>
              Workflows
            </a>
          </div>

          {/* URL Input and Load Button */}
          <div className="flex-1 flex items-center gap-3">
            <input
              type="url"
              value={localUrl}
              onChange={handleUrlChange}
              className="flex-1 px-4 py-2 text-sm border border-gray-300 rounded-md focus:outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500 focus:ring-opacity-20"
              placeholder="Enter URL (e.g., https://example.com)"
              disabled={isRecording}
            />
            <button
              onClick={onLoad}
              disabled={isLoading || isRecording}
              className="px-6 py-2 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors whitespace-nowrap"
            >
              {isLoading ? 'Loading...' : 'Load Page'}
            </button>
          </div>

          {/* Recording Button */}
          <button
            onClick={onToggleRecording}
            disabled={!canRecord}
            className={`px-6 py-2 text-sm font-medium text-white rounded-md focus:outline-none focus:ring-2 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition-colors whitespace-nowrap ${
              isRecording
                ? 'bg-gray-700 hover:bg-gray-800 focus:ring-gray-500'
                : 'bg-red-600 hover:bg-red-700 focus:ring-red-500'
            }`}
          >
            <span className="flex items-center gap-2">
              <span
                className={`w-2 h-2 rounded-full bg-white ${
                  isRecording ? 'animate-pulse' : ''
                }`}
              />
              <span>{isRecording ? 'Stop Recording' : 'Start Recording'}</span>
            </span>
          </button>
        </div>
      </div>
    </div>
  );
};
