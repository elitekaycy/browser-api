import { useEffect } from 'react';
import { useRecorderStore } from '../stores/recorderStore';
import { RecorderApiService } from '../services/recorderApi';
import { useRecorderWebSocket } from '../hooks/useRecorderWebSocket';
import { RecorderToolbar } from '../components/recorder/RecorderToolbar';
import { BrowserCanvas, renderFrameOnCanvas } from '../components/recorder/BrowserCanvas';
import { ActionsList } from '../components/recorder/ActionsList';
import { ApiService } from '../services/api';

export const RecorderPage = () => {
  const {
    sessionId,
    url,
    isRecording,
    actions,
    isLoading,
    setSessionId,
    setUrl,
    setRecording,
    addAction,
    clearActions,
    setLoading,
  } = useRecorderStore();

  // WebSocket connection for frame streaming and action updates
  useRecorderWebSocket({
    sessionId,
    onFrame: (frameData) => {
      renderFrameOnCanvas(frameData);
    },
    onAction: (action) => {
      addAction(action);
    },
  });

  // Load page and create session
  const handleLoad = async () => {
    if (!url.trim()) {
      alert('Please enter a URL');
      return;
    }

    try {
      setLoading(true);
      const session = await RecorderApiService.createSession(url, 5);
      setSessionId(session.sessionId);
    } catch (error) {
      console.error('Failed to load page:', error);
      alert('Failed to load page: ' + (error instanceof Error ? error.message : 'Unknown error'));
    } finally {
      setLoading(false);
    }
  };

  // Toggle recording
  const handleToggleRecording = async () => {
    if (!sessionId) return;

    try {
      if (isRecording) {
        await RecorderApiService.stopRecording(sessionId);
        setRecording(false);
      } else {
        await RecorderApiService.startRecording(sessionId);
        setRecording(true);
      }
    } catch (error) {
      console.error('Failed to toggle recording:', error);
      alert('Failed to toggle recording: ' + (error instanceof Error ? error.message : 'Unknown error'));
    }
  };

  // Handle canvas click
  const handleCanvasClick = async (x: number, y: number) => {
    if (!sessionId) return;

    try {
      await RecorderApiService.sendClick(sessionId, { x, y });
    } catch (error) {
      console.error('Failed to send click:', error);
    }
  };

  // Save as workflow
  const handleSaveWorkflow = async () => {
    if (actions.length === 0) return;

    const workflowName = prompt('Enter workflow name:', 'Recorded Workflow');
    if (!workflowName) return;

    try {
      await ApiService.createWorkflow({
        name: workflowName,
        url,
        actions,
        tags: ['recorded'],
        createdBy: 'recorder',
      });
      alert(`Workflow "${workflowName}" saved successfully!`);
      clearActions();
    } catch (error) {
      console.error('Failed to save workflow:', error);
      alert('Failed to save workflow: ' + (error instanceof Error ? error.message : 'Unknown error'));
    }
  };

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (sessionId) {
        RecorderApiService.deleteSession(sessionId).catch(console.error);
      }
    };
  }, [sessionId]);

  return (
    <div className="h-screen overflow-hidden flex flex-col bg-white">
      <RecorderToolbar
        url={url}
        isRecording={isRecording}
        isLoading={isLoading}
        canRecord={!!sessionId}
        onUrlChange={setUrl}
        onLoad={handleLoad}
        onToggleRecording={handleToggleRecording}
      />

      <div className="flex h-[calc(100vh-57px)]">
        <ActionsList
          actions={actions}
          onClear={clearActions}
          onSaveWorkflow={handleSaveWorkflow}
          sessionUrl={url}
        />

        <div className="flex-1 flex flex-col bg-white">
          <div className="px-4 py-2 border-b border-gray-200 bg-gray-50">
            <div className="flex items-center justify-between text-xs text-gray-500">
              <div className="flex items-center gap-2">
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
                    d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
                  />
                </svg>
                <span>
                  {sessionId
                    ? isRecording
                      ? 'Recording actions...'
                      : `Loaded: ${url}`
                    : 'Click "Load" to open URL in recorder'}
                </span>
              </div>
            </div>
          </div>

          <BrowserCanvas
            onCanvasClick={handleCanvasClick}
            hasSession={!!sessionId}
          />
        </div>
      </div>
    </div>
  );
};
