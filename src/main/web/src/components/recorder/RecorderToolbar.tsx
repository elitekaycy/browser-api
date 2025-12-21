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
    <div className="border-b border-gray-200 bg-white">
      <div className="flex items-center justify-between px-4 py-3">
        <div className="flex items-center gap-4 flex-1">
          <div className="flex items-center gap-4">
            <span className="text-xs font-medium text-gray-500">Recorder</span>
            <a
              href="/workflows"
              className="text-xs font-medium text-blue-600 hover:text-blue-700 flex items-center gap-1"
            >
              <svg
                className="w-3 h-3"
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
              View Workflows
            </a>
          </div>
          <div className="flex-1 flex items-center gap-2">
            <input
              type="url"
              value={localUrl}
              onChange={handleUrlChange}
              className="flex-1 px-3 py-1.5 text-sm border border-gray-300 rounded focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
              placeholder="Enter URL..."
              disabled={isRecording}
            />
            <button
              onClick={onLoad}
              disabled={isLoading || isRecording}
              className="px-4 py-1.5 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {isLoading ? 'Loading...' : 'Load'}
            </button>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={onToggleRecording}
            disabled={!canRecord}
            className={`px-4 py-1.5 text-sm font-medium text-white rounded focus:outline-none focus:ring-2 disabled:opacity-50 disabled:cursor-not-allowed ${
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
