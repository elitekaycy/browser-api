import type { RecorderSession, ClickRequest } from '../types/recorder';

const API_BASE = '/api/v1';

export class RecorderApiService {
  // Create recorder session
  static async createSession(url: string, frameRate: number = 5): Promise<RecorderSession> {
    const response = await fetch(`${API_BASE}/recorder/sessions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ url, frameRate }),
    });
    if (!response.ok) throw new Error('Failed to create session');
    return response.json();
  }

  // Start recording
  static async startRecording(sessionId: string): Promise<void> {
    const response = await fetch(`${API_BASE}/recorder/sessions/${sessionId}/start`, {
      method: 'POST',
    });
    if (!response.ok) throw new Error('Failed to start recording');
  }

  // Stop recording
  static async stopRecording(sessionId: string): Promise<{ actionCount: number }> {
    const response = await fetch(`${API_BASE}/recorder/sessions/${sessionId}/stop`, {
      method: 'POST',
    });
    if (!response.ok) throw new Error('Failed to stop recording');
    return response.json();
  }

  // Send click to backend
  static async sendClick(sessionId: string, click: ClickRequest): Promise<void> {
    const response = await fetch(`${API_BASE}/recorder/sessions/${sessionId}/click`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(click),
    });
    if (!response.ok) throw new Error('Failed to send click');
  }

  // Delete session
  static async deleteSession(sessionId: string): Promise<void> {
    const response = await fetch(`${API_BASE}/recorder/sessions/${sessionId}`, {
      method: 'DELETE',
    });
    if (!response.ok) throw new Error('Failed to delete session');
  }

  // Get session actions
  static async getActions(sessionId: string): Promise<any[]> {
    const response = await fetch(`${API_BASE}/recorder/sessions/${sessionId}/actions`);
    if (!response.ok) throw new Error('Failed to fetch actions');
    return response.json();
  }
}
