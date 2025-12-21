import { useEffect, useState } from 'react';
import { useWorkflowStore } from '../stores/workflowStore';
import { ApiService } from '../services/api';
import { WorkflowDialog } from '../components/workflows/WorkflowDialog';
import { ActionsDialog } from '../components/workflows/ActionsDialog';
import { Button } from '../components/ui/button';
import type { Workflow } from '../types/workflow';
import type { Action } from '../types/workflow';

export const WorkflowsPage = () => {
  const { workflows, setWorkflows, setLoading, setError } = useWorkflowStore();
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingWorkflow, setEditingWorkflow] = useState<Workflow | null>(null);
  const [actionsDialogOpen, setActionsDialogOpen] = useState(false);
  const [viewingWorkflow, setViewingWorkflow] = useState<Workflow | null>(null);

  useEffect(() => {
    loadWorkflows();
  }, []);

  const loadWorkflows = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await ApiService.getWorkflows();
      setWorkflows(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load workflows');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateWorkflow = async (workflow: Omit<Workflow, 'id'>) => {
    if (editingWorkflow?.id) {
      await ApiService.updateWorkflow(editingWorkflow.id, workflow);
    } else {
      await ApiService.createWorkflow(workflow);
    }
    await loadWorkflows();
    setEditingWorkflow(null);
  };

  const handleEditClick = (workflow: Workflow) => {
    setEditingWorkflow(workflow);
    setDialogOpen(true);
  };

  const handleNewClick = () => {
    setEditingWorkflow(null);
    setDialogOpen(true);
  };

  const handleExecute = async (workflowId: number) => {
    try {
      await ApiService.executeWorkflow(workflowId);
      alert('Workflow execution started!');
    } catch (error) {
      alert('Failed to execute workflow: ' + (error instanceof Error ? error.message : 'Unknown error'));
    }
  };

  const handleDelete = async (workflowId: number) => {
    if (!confirm('Are you sure you want to delete this workflow?')) return;

    try {
      await ApiService.deleteWorkflow(workflowId);
      await loadWorkflows();
    } catch (error) {
      alert('Failed to delete workflow: ' + (error instanceof Error ? error.message : 'Unknown error'));
    }
  };

  const handleViewActions = (workflow: Workflow) => {
    setViewingWorkflow(workflow);
    setActionsDialogOpen(true);
  };

  const handleSaveActions = async (actions: Action[]) => {
    if (!viewingWorkflow?.id) return;

    await ApiService.updateWorkflow(viewingWorkflow.id, {
      ...viewingWorkflow,
      actions,
    });
    await loadWorkflows();
  };

  const { isLoading: storeLoading, error: storeError } = useWorkflowStore();

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <header className="border-b bg-card">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between items-center py-4">
            <div className="flex items-center gap-4">
              <h1 className="text-2xl font-bold">Browser API</h1>
              <nav className="flex gap-4">
                <a
                  href="/recorder"
                  className="text-sm font-medium text-muted-foreground hover:text-foreground"
                >
                  Recorder
                </a>
                <a
                  href="/workflows"
                  className="text-sm font-medium text-primary"
                >
                  Workflows
                </a>
              </nav>
            </div>
            <Button onClick={handleNewClick}>
              New Workflow
            </Button>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Error Message */}
        {storeError && (
          <div className="mb-6 p-4 bg-red-50 border border-red-200 rounded-lg">
            <div className="flex items-center gap-2">
              <svg className="w-5 h-5 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <p className="text-sm font-medium text-red-800">{storeError}</p>
            </div>
          </div>
        )}

        {/* Loading State */}
        {storeLoading && workflows.length === 0 && (
          <div className="flex justify-center items-center py-12">
            <div className="text-center">
              <div className="w-12 h-12 border-4 border-primary border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
              <p className="text-sm text-muted-foreground">Loading workflows...</p>
            </div>
          </div>
        )}
        {!storeLoading && (
          <>
            <div className="mb-6">
              <h2 className="text-xl font-semibold">Workflows</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                Manage and execute your browser automation workflows
              </p>
            </div>

            {/* Workflows Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {workflows.length === 0 ? (
            <div className="col-span-full flex flex-col items-center justify-center py-12 text-center">
              <svg
                className="w-16 h-16 text-muted mb-4"
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
              <h3 className="text-lg font-medium mb-1">
                No workflows yet
              </h3>
              <p className="text-sm text-muted-foreground mb-4">
                Get started by creating your first workflow or recording one
              </p>
              <div className="flex gap-3">
                <Button variant="outline" asChild>
                  <a href="/recorder">Go to Recorder</a>
                </Button>
                <Button onClick={handleNewClick}>
                  Create Workflow
                </Button>
              </div>
            </div>
          ) : (
            workflows.map((workflow) => (
              <div
                key={workflow.id}
                className="bg-card border rounded-lg p-6 hover:shadow-lg transition-shadow"
              >
                <div className="flex items-start justify-between mb-3">
                  <h3 className="text-lg font-semibold">
                    {workflow.name}
                  </h3>
                </div>

                {workflow.description && (
                  <p className="text-sm text-muted-foreground mb-4">
                    {workflow.description}
                  </p>
                )}

                <div className="flex items-center gap-2 mb-4 flex-wrap">
                  <button
                    onClick={() => handleViewActions(workflow)}
                    className="px-2 py-1 text-xs font-medium bg-secondary text-secondary-foreground rounded hover:bg-secondary/80 transition-colors cursor-pointer"
                  >
                    {workflow.actions.length} steps
                  </button>
                  {workflow.tags?.map((tag) => (
                    <span
                      key={tag}
                      className="px-2 py-1 text-xs font-medium bg-primary/10 text-primary rounded"
                    >
                      {tag}
                    </span>
                  ))}
                </div>

                <div className="text-xs text-muted-foreground mb-4 truncate">
                  URL: {workflow.url}
                </div>

                <div className="flex gap-2">
                  <Button
                    className="flex-1"
                    onClick={() => workflow.id && handleExecute(workflow.id)}
                  >
                    Execute
                  </Button>
                  <Button
                    variant="outline"
                    onClick={() => handleEditClick(workflow)}
                  >
                    Edit
                  </Button>
                  <Button
                    variant="destructive"
                    size="icon"
                    onClick={() => workflow.id && handleDelete(workflow.id)}
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
                        d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                      />
                    </svg>
                  </Button>
                </div>
              </div>
            ))
          )}
            </div>
          </>
        )}
      </main>

      <WorkflowDialog
        open={dialogOpen}
        onOpenChange={(open) => {
          setDialogOpen(open);
          if (!open) setEditingWorkflow(null);
        }}
        workflow={editingWorkflow}
        onSave={handleCreateWorkflow}
      />

      <ActionsDialog
        open={actionsDialogOpen}
        onOpenChange={(open) => {
          setActionsDialogOpen(open);
          if (!open) setViewingWorkflow(null);
        }}
        workflowName={viewingWorkflow?.name || ''}
        actions={viewingWorkflow?.actions || []}
        onSave={handleSaveActions}
      />
    </div>
  );
};
