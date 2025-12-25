// Recorder session and action types

export interface RecorderSession {
  sessionId: string;
  url: string;
  frameRate: number;
  isRecording: boolean;
}

// Base frame interface
export interface BaseFrameData {
  sequence: number;
  timestamp: number;
  url: string;
  sizeBytes: number;
  type: 'dom' | 'screenshot';
}

// DOM snapshot frame
export interface DOMFrameData extends BaseFrameData {
  type: 'dom';
  domHTML: string;
  stylesheets: string[];
  viewportWidth: number;
  viewportHeight: number;
  devicePixelRatio: number;
  scrollX: number;
  scrollY: number;
}

// Screenshot frame
export interface ScreenshotFrameData extends BaseFrameData {
  type: 'screenshot';
  imageData: string; // base64 encoded PNG
}

// Union type for all frame types
export type FrameData = DOMFrameData | ScreenshotFrameData;

// Legacy frame data for backward compatibility
export interface LegacyFrameData {
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
