import { useState, useEffect } from 'react';
import type { Action } from '../../types/recorder';
import { Button } from '../ui/button';
import { PlaywrightCodeGenerator, type CodeLanguage } from '../../services/codeGenerator';

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
  const [showCodeMenu, setShowCodeMenu] = useState(false);

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
    downloadFile('recording.json', json, 'application/json');
  };

  const exportAsCode = (language: CodeLanguage) => {
    const code = PlaywrightCodeGenerator.generate(actions, sessionUrl, language);
    const extension = language === 'typescript' ? 'ts' : language === 'python' ? 'py' : language === 'java' ? 'java' : 'js';
    downloadFile(`workflow.${extension}`, code, 'text/plain');
    setShowCodeMenu(false);
  };

  const copyToClipboard = async () => {
    const json = JSON.stringify(actions, null, 2);
    await navigator.clipboard.writeText(json);
  };

  const downloadFile = (filename: string, content: string, type: string) => {
    const blob = new Blob([content], { type });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="w-96 border-r border-border flex flex-col bg-muted/40">
      {/* Header */}
      <div className="px-4 py-3 border-b border-border bg-card">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-semibold">Recording</h3>
            <span className="px-2 py-0.5 text-xs font-medium text-muted-foreground bg-secondary rounded">
              {actions.length} steps
            </span>
          </div>
          <Button
            onClick={onClear}
            disabled={actions.length === 0}
            variant="ghost"
            size="sm"
            className="h-7 text-xs"
          >
            Clear
          </Button>
        </div>
      </div>

      {/* Actions List */}
      <div className="flex-1 overflow-y-auto">
        {actions.length === 0 ? (
          <div className="px-4 py-12 text-center">
            <svg
              className="mx-auto h-12 w-12 text-muted"
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
            <p className="mt-2 text-sm text-muted-foreground">No actions recorded</p>
            <p className="mt-1 text-xs text-muted-foreground">
              Load a page and start recording
            </p>
          </div>
        ) : (
          <div className="divide-y divide-border">
            {actions.map((action, idx) => (
              <div
                key={idx}
                className={`px-4 py-3 hover:bg-muted/60 border-l-2 transition-all duration-300 ${
                  idx === newActionId
                    ? 'border-primary bg-primary/10'
                    : 'border-transparent'
                }`}
              >
                <div className="flex items-start gap-3">
                  <span className="flex-shrink-0 w-5 h-5 flex items-center justify-center text-xs font-medium text-muted-foreground">
                    {idx + 1}
                  </span>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-medium">
                        {action.type}
                      </span>
                    </div>
                    <div className="mt-0.5 text-xs font-mono text-muted-foreground truncate">
                      {action.selector || 'N/A'}
                    </div>
                    {action.value && (
                      <div className="mt-0.5 text-xs text-foreground/70">
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
      <div className="border-t border-border bg-card p-3 space-y-2">
        <Button
          onClick={onSaveWorkflow}
          disabled={actions.length === 0}
          className="w-full"
          size="sm"
        >
          Save as Workflow
        </Button>
        <div className="flex gap-2">
          <Button
            onClick={exportAsJSON}
            disabled={actions.length === 0}
            variant="outline"
            size="sm"
            className="flex-1 text-xs"
          >
            Export JSON
          </Button>
          <div className="relative flex-1">
            <Button
              onClick={() => setShowCodeMenu(!showCodeMenu)}
              disabled={actions.length === 0}
              variant="outline"
              size="sm"
              className="w-full text-xs"
            >
              Export Code
            </Button>
            {showCodeMenu && (
              <div className="absolute bottom-full left-0 right-0 mb-1 bg-card border border-border rounded-md shadow-lg overflow-hidden z-10">
                <button
                  onClick={() => exportAsCode('typescript')}
                  className="w-full px-3 py-2 text-left text-xs hover:bg-muted/60 transition-colors"
                >
                  TypeScript
                </button>
                <button
                  onClick={() => exportAsCode('javascript')}
                  className="w-full px-3 py-2 text-left text-xs hover:bg-muted/60 transition-colors"
                >
                  JavaScript
                </button>
                <button
                  onClick={() => exportAsCode('python')}
                  className="w-full px-3 py-2 text-left text-xs hover:bg-muted/60 transition-colors"
                >
                  Python
                </button>
                <button
                  onClick={() => exportAsCode('java')}
                  className="w-full px-3 py-2 text-left text-xs hover:bg-muted/60 transition-colors"
                >
                  Java
                </button>
              </div>
            )}
          </div>
        </div>
        <Button
          onClick={copyToClipboard}
          disabled={actions.length === 0}
          variant="ghost"
          size="sm"
          className="w-full text-xs"
        >
          Copy JSON
        </Button>
      </div>
    </div>
  );
};
