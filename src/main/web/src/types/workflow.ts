// Workflow and Action types

export interface Action {
  type: 'click' | 'fill' | 'navigate' | 'select' | 'wait' | 'screenshot';
  selector?: string;
  value?: string;
  timestamp?: number;
}

export interface Workflow {
  id?: number;
  name: string;
  description?: string;
  url: string;
  actions: Action[];
  tags?: string[];
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
  executionCount?: number;
  lastExecutedAt?: string;
}

export interface WorkflowExecution {
  id: number;
  workflowId: number;
  status: 'running' | 'success' | 'failed';
  startedAt: string;
  completedAt?: string;
  error?: string;
  screenshot?: string;
}
