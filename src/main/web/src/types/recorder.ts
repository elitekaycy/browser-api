// Recorder session and action types

export interface RecorderSession {
  sessionId: string;
  url: string;
  frameRate: number;
  isRecording: boolean;
}

export interface FrameData {
  imageData: string; // base64 encoded PNG
  timestamp: number;
}

export interface ClickRequest {
  x: number;
  y: number;
}

export interface Action {
  type: 'click' | 'fill' | 'navigate' | 'select' | 'wait' | 'screenshot';
  selector?: string;
  value?: string;
  timestamp?: number;
}
