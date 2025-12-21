import { create } from 'zustand';
import { devtools } from 'zustand/middleware';
import type { Workflow } from '../types/workflow';

interface WorkflowState {
  workflows: Workflow[];
  selectedWorkflow: Workflow | null;
  isLoading: boolean;
  error: string | null;

  // Actions
  setWorkflows: (workflows: Workflow[]) => void;
  addWorkflow: (workflow: Workflow) => void;
  updateWorkflow: (id: number, workflow: Partial<Workflow>) => void;
  deleteWorkflow: (id: number) => void;
  selectWorkflow: (workflow: Workflow | null) => void;
  setLoading: (isLoading: boolean) => void;
  setError: (error: string | null) => void;
}

export const useWorkflowStore = create<WorkflowState>()(
  devtools(
    (set) => ({
      workflows: [],
      selectedWorkflow: null,
      isLoading: false,
      error: null,

      setWorkflows: (workflows) => set({ workflows }),

      addWorkflow: (workflow) =>
        set((state) => ({
          workflows: [...state.workflows, workflow],
        })),

      updateWorkflow: (id, updatedWorkflow) =>
        set((state) => ({
          workflows: state.workflows.map((w) =>
            w.id === id ? { ...w, ...updatedWorkflow } : w
          ),
        })),

      deleteWorkflow: (id) =>
        set((state) => ({
          workflows: state.workflows.filter((w) => w.id !== id),
          selectedWorkflow:
            state.selectedWorkflow?.id === id ? null : state.selectedWorkflow,
        })),

      selectWorkflow: (workflow) => set({ selectedWorkflow: workflow }),

      setLoading: (isLoading) => set({ isLoading }),

      setError: (error) => set({ error }),
    }),
    { name: 'WorkflowStore' }
  )
);
