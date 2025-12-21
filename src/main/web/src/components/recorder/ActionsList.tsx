import { useState, useEffect } from 'react';
import type { Action } from '../../types/recorder';
import { ApiService } from '../../services/api';

interface ActionsListProps {
  actions: Action[];
  onClear: () => void;
  onSaveWorkflow: () => void;
  sessionUrl: string;
}

export const ActionsList = ({
  actions,
  onClear,
  onSaveWorkflow,
  sessionUrl,
}: ActionsListProps) => {
  const [newActionId, setNewActionId] = useState<number | null>(null);

  // Highlight new actions briefly
  useEffect(() => {
    if (actions.length > 0) {
      setNewActionId(actions.length - 1);
      const timer = setTimeout(() => setNewActionId(null), 1000);
      return () => clearTimeout(timer);
    }
  }, [actions.length]);

  const exportAsJSON = () => {
    const json = JSON.stringify(actions, null, 2);
    const blob = new Blob([json], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'recording.json';
    a.click();
    URL.revokeObjectURL(url);
  };

  const copyToClipboard = async () => {
    const json = JSON.stringify(actions, null, 2);
    await navigator.clipboard.writeText(json);
  };

  return (
    <div className="w-96 border-r border-gray-200 flex flex-col bg-gray-50">
      {/* Header */}
      <div className="px-4 py-3 border-b border-gray-200 bg-white">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-semibold text-gray-900">Recording</h3>
            <span className="px-2 py-0.5 text-xs font-medium text-gray-600 bg-gray-100 rounded">
              {actions.length} steps
            </span>
          </div>
          <button
            onClick={onClear}
            disabled={actions.length === 0}
            className="text-xs text-gray-500 hover:text-gray-700 disabled:opacity-50 disabled:cursor-not-allowed"
            title="Clear all"
          >
            Clear
          </button>
        </div>
      </div>

      {/* Actions List */}
      <div className="flex-1 overflow-y-auto">
        {actions.length === 0 ? (
          <div className="px-4 py-12 text-center">
            <svg
              className="mx-auto h-12 w-12 text-gray-300"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={1}
                d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2"
              />
            </svg>
            <p className="mt-2 text-sm text-gray-500">No actions recorded</p>
            <p className="mt-1 text-xs text-gray-400">
              Load a page and start recording
            </p>
          </div>
        ) : (
          <div className="divide-y divide-gray-100">
            {actions.map((action, idx) => (
              <div
                key={idx}
                className={`px-4 py-3 hover:bg-gray-50 border-l-2 transition-all duration-300 ${
                  idx === newActionId
                    ? 'border-blue-500 bg-blue-50'
                    : 'border-transparent'
                }`}
              >
                <div className="flex items-start gap-3">
                  <span className="flex-shrink-0 w-5 h-5 flex items-center justify-center text-xs font-medium text-gray-400">
                    {idx + 1}
                  </span>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-medium text-gray-900">
                        {action.type}
                      </span>
                    </div>
                    <div className="mt-0.5 text-xs font-mono text-gray-500 truncate">
                      {action.selector || 'N/A'}
                    </div>
                    {action.value && (
                      <div className="mt-0.5 text-xs text-gray-600">
                        "{action.value}"
                      </div>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Bottom Actions */}
      <div className="border-t border-gray-200 bg-white p-3 space-y-2">
        <button
          onClick={onSaveWorkflow}
          disabled={actions.length === 0}
          className="w-full px-3 py-2 text-sm font-medium text-white bg-blue-600 rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          Save as Workflow
        </button>
        <div className="flex gap-2">
          <button
            onClick={exportAsJSON}
            disabled={actions.length === 0}
            className="flex-1 px-3 py-1.5 text-xs font-medium text-gray-700 bg-white border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Export JSON
          </button>
          <button
            onClick={copyToClipboard}
            disabled={actions.length === 0}
            className="flex-1 px-3 py-1.5 text-xs font-medium text-gray-700 bg-white border border-gray-300 rounded hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Copy
          </button>
        </div>
      </div>
    </div>
  );
};
