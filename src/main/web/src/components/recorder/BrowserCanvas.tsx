import { useRef, useEffect, useImperativeHandle, forwardRef, useState } from 'react';
import type { FrameData } from '../../types/recorder';

interface BrowserCanvasProps {
  onCanvasClick: (x: number, y: number) => void;
  hasSession: boolean;
  onFrameRender?: (frameData: FrameData) => void;
}

export interface BrowserCanvasHandle {
  renderFrame: (frameData: FrameData) => void;
}

export const BrowserCanvas = forwardRef<BrowserCanvasHandle, BrowserCanvasProps>(
  ({ onCanvasClick, hasSession, onFrameRender }, ref) => {
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const iframeRef = useRef<HTMLIFrameElement>(null);
    const ctxRef = useRef<CanvasRenderingContext2D | null>(null);
    const [renderMode, setRenderMode] = useState<'dom' | 'screenshot'>('dom');
    const [currentFrameData, setCurrentFrameData] = useState<FrameData | null>(null);

    useEffect(() => {
      if (canvasRef.current) {
        ctxRef.current = canvasRef.current.getContext('2d');
      }
    }, []);

    const renderDOMFrame = (frameData: FrameData) => {
      if (frameData.type !== 'dom') return;

      const iframe = iframeRef.current;
      if (!iframe) {
        console.warn('iframe not available for DOM rendering');
        return;
      }

      try {
        // Create a complete HTML document with styles
        const fullHTML = `
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=${frameData.viewportWidth}, initial-scale=1.0">
  <style>
    * {
      margin: 0;
      padding: 0;
      box-sizing: border-box;
    }
    body {
      overflow: hidden;
      width: ${frameData.viewportWidth}px;
      height: ${frameData.viewportHeight}px;
    }
  </style>
  ${frameData.stylesheets.map((css) => {
    if (css.startsWith('http')) {
      return `<link rel="stylesheet" href="${css}">`;
    } else {
      return `<style>${css}</style>`;
    }
  }).join('\n')}
  <script>
    // Restore scroll position
    window.addEventListener('DOMContentLoaded', () => {
      window.scrollTo(${frameData.scrollX}, ${frameData.scrollY});

      // Apply computed styles from data attributes
      document.querySelectorAll('[data-computed-style]').forEach(el => {
        try {
          const styles = JSON.parse(atob(el.getAttribute('data-computed-style')));
          Object.assign(el.style, styles);
        } catch (e) {
          console.error('Failed to apply computed styles:', e);
        }
      });
    });
  </script>
</head>
<body>
  ${frameData.domHTML}
</body>
</html>
        `;

        // Write to iframe
        const iframeDoc = iframe.contentDocument || iframe.contentWindow?.document;
        if (iframeDoc) {
          iframeDoc.open();
          iframeDoc.write(fullHTML);
          iframeDoc.close();
        }

        console.log('DOM frame rendered:', {
          viewport: `${frameData.viewportWidth}x${frameData.viewportHeight}`,
          size: `${(frameData.sizeBytes / 1024).toFixed(2)}KB`,
          stylesheets: frameData.stylesheets.length,
        });

        setCurrentFrameData(frameData);
        onFrameRender?.(frameData);
      } catch (error) {
        console.error('Failed to render DOM frame:', error);
        // Fall back to canvas rendering on error
        setRenderMode('screenshot');
      }
    };

    const renderScreenshotFrame = (frameData: FrameData) => {
      if (frameData.type !== 'screenshot') return;

      const canvas = canvasRef.current;
      const ctx = ctxRef.current;
      if (!canvas || !ctx) {
        console.warn('Canvas or context not available for rendering');
        return;
      }

      const img = new Image();
      img.onload = () => {
        canvas.width = img.width;
        canvas.height = img.height;
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.drawImage(img, 0, 0);
        console.log('Screenshot frame rendered:', {
          size: `${img.width}x${img.height}`,
          bytes: `${(frameData.sizeBytes / 1024).toFixed(2)}KB`,
        });

        setCurrentFrameData(frameData);
        onFrameRender?.(frameData);
      };
      img.onerror = (err) => {
        console.error('Failed to load screenshot frame:', err);
      };
      img.src = 'data:image/png;base64,' + frameData.imageData;
    };

    const renderFrame = (frameData: FrameData) => {
      if (frameData.type === 'dom') {
        setRenderMode('dom');
        renderDOMFrame(frameData);
      } else if (frameData.type === 'screenshot') {
        setRenderMode('screenshot');
        renderScreenshotFrame(frameData);
      }
    };

    // Expose renderFrame via ref
    useImperativeHandle(ref, () => ({
      renderFrame,
    }));

    const handleClick = (e: React.MouseEvent<HTMLCanvasElement | HTMLIFrameElement>) => {
      if (!hasSession || !currentFrameData) return;

      let displayX: number;
      let displayY: number;
      let scaleX: number;
      let scaleY: number;

      if (renderMode === 'dom' && iframeRef.current && currentFrameData.type === 'dom') {
        // DOM mode: calculate coordinates from iframe
        const iframe = iframeRef.current;
        const rect = iframe.getBoundingClientRect();

        displayX = e.clientX - rect.left;
        displayY = e.clientY - rect.top;

        // Scale to viewport dimensions
        scaleX = currentFrameData.viewportWidth / rect.width;
        scaleY = currentFrameData.viewportHeight / rect.height;
      } else if (renderMode === 'screenshot' && canvasRef.current) {
        // Screenshot mode: calculate coordinates from canvas
        const canvas = canvasRef.current;
        const rect = canvas.getBoundingClientRect();

        displayX = e.clientX - rect.left;
        displayY = e.clientY - rect.top;

        scaleX = canvas.width / rect.width;
        scaleY = canvas.height / rect.height;
      } else {
        return;
      }

      // Convert to actual page coordinates
      const pageX = displayX * scaleX;
      const pageY = displayY * scaleY;

      onCanvasClick(pageX, pageY);
    };

    return (
      <div className="w-full h-full relative bg-gray-900 flex items-center justify-center">
        {!hasSession ? (
          <div className="text-center p-8">
            <svg
              className="mx-auto h-24 w-24 text-gray-600 mb-4"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={1}
                d="M21 12a9 9 0 01-9 9m9-9a9 9 0 00-9-9m9 9H3m9 9a9 9 0 01-9-9m9 9c1.657 0 3-4.03 3-9s-1.343-9-3-9m0 18c-1.657 0-3-4.03-3-9s1.343-9 3-9m-9 9a9 9 0 019-9"
              />
            </svg>
            <h3 className="text-lg font-medium text-gray-300 mb-2">No Browser Session</h3>
            <p className="text-sm text-gray-500">Enter a URL above and click "Load Page" to begin</p>
          </div>
        ) : (
          <>
            {/* DOM Renderer (iframe) */}
            {renderMode === 'dom' && (
              <iframe
                ref={iframeRef}
                onClick={handleClick}
                className="w-full h-full border-0 bg-white cursor-crosshair"
                sandbox="allow-same-origin allow-scripts allow-forms allow-popups allow-modals"
                title="Browser Preview"
              />
            )}

            {/* Screenshot Renderer (canvas) */}
            {renderMode === 'screenshot' && (
              <canvas
                ref={canvasRef}
                onClick={handleClick}
                className="max-w-full max-h-full object-contain cursor-crosshair shadow-2xl"
                style={{ imageRendering: 'crisp-edges' }}
              />
            )}

            {/* Frame info overlay */}
            {currentFrameData && (
              <div className="absolute bottom-4 right-4 bg-black/70 text-white text-xs px-3 py-2 rounded-lg font-mono">
                <div className="flex items-center gap-2">
                  <span
                    className={`w-2 h-2 rounded-full ${
                      renderMode === 'dom' ? 'bg-green-500' : 'bg-blue-500'
                    }`}
                  />
                  <span>{renderMode === 'dom' ? 'DOM' : 'Screenshot'}</span>
                  <span className="opacity-50">|</span>
                  <span>{(currentFrameData.sizeBytes / 1024).toFixed(1)}KB</span>
                </div>
              </div>
            )}
          </>
        )}
      </div>
    );
  }
);
