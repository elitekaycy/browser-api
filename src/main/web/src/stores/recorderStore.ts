import { create } from 'zustand';
import { devtools } from 'zustand/middleware';
import type { Action } from '../types/recorder';

interface RecorderState {
  // Session state
  sessionId: string | null;
  url: string;
  isRecording: boolean;

  // Actions
  actions: Action[];

  // UI state
  frameRate: number;
  fps: number;
  isLoading: boolean;

  // Actions
  setSessionId: (id: string | null) => void;
  setUrl: (url: string) => void;
  setRecording: (isRecording: boolean) => void;
  addAction: (action: Action) => void;
  clearActions: () => void;
  setActions: (actions: Action[]) => void;
  setFps: (fps: number) => void;
  setLoading: (isLoading: boolean) => void;
}

export const useRecorderStore = create<RecorderState>()(
  devtools(
    (set) => ({
      sessionId: null,
      url: 'https://example.com',
      isRecording: false,
      actions: [],
      frameRate: 5,
      fps: 0,
      isLoading: false,

      setSessionId: (id) => set({ sessionId: id }),
      setUrl: (url) => set({ url }),
      setRecording: (isRecording) => set({ isRecording }),

      addAction: (action) =>
        set((state) => ({
          actions: [...state.actions, action],
        })),

      clearActions: () => set({ actions: [] }),
      setActions: (actions) => set({ actions }),
      setFps: (fps) => set({ fps }),
      setLoading: (isLoading) => set({ isLoading }),
    }),
    { name: 'RecorderStore' }
  )
);
