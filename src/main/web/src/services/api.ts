import type { Workflow, WorkflowExecution } from '../types/workflow';

const API_BASE = '/api/v1';

export class ApiService {
  // Workflows
  static async getWorkflows(): Promise<Workflow[]> {
    const response = await fetch(`${API_BASE}/workflows`);
    if (!response.ok) throw new Error('Failed to fetch workflows');
    return response.json();
  }

  static async getWorkflow(id: number): Promise<Workflow> {
    const response = await fetch(`${API_BASE}/workflows/${id}`);
    if (!response.ok) throw new Error('Failed to fetch workflow');
    return response.json();
  }

  static async createWorkflow(workflow: Omit<Workflow, 'id'>): Promise<Workflow> {
    const response = await fetch(`${API_BASE}/workflows`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(workflow),
    });
    if (!response.ok) throw new Error('Failed to create workflow');
    return response.json();
  }

  static async updateWorkflow(id: number, workflow: Partial<Workflow>): Promise<Workflow> {
    const response = await fetch(`${API_BASE}/workflows/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(workflow),
    });
    if (!response.ok) throw new Error('Failed to update workflow');
    return response.json();
  }

  static async deleteWorkflow(id: number): Promise<void> {
    const response = await fetch(`${API_BASE}/workflows/${id}`, {
      method: 'DELETE',
    });
    if (!response.ok) throw new Error('Failed to delete workflow');
  }

  static async executeWorkflow(id: number): Promise<WorkflowExecution> {
    const response = await fetch(`${API_BASE}/workflows/${id}/execute`, {
      method: 'POST',
    });
    if (!response.ok) throw new Error('Failed to execute workflow');
    return response.json();
  }

  static async searchWorkflowsByTag(tag: string): Promise<Workflow[]> {
    const response = await fetch(`${API_BASE}/workflows/search?tag=${encodeURIComponent(tag)}`);
    if (!response.ok) throw new Error('Failed to search workflows');
    return response.json();
  }
}
