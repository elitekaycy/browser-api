import { useRef, useEffect } from 'react';
import type { FrameData } from '../../types/recorder';

interface BrowserCanvasProps {
  onCanvasClick: (x: number, y: number) => void;
  hasSession: boolean;
}

export const BrowserCanvas = ({ onCanvasClick, hasSession }: BrowserCanvasProps) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const ctxRef = useRef<CanvasRenderingContext2D | null>(null);

  useEffect(() => {
    if (canvasRef.current) {
      ctxRef.current = canvasRef.current.getContext('2d');
    }
  }, []);

  const renderFrame = (frameData: FrameData) => {
    const canvas = canvasRef.current;
    const ctx = ctxRef.current;
    if (!canvas || !ctx) return;

    const img = new Image();
    img.onload = () => {
      canvas.width = img.width;
      canvas.height = img.height;
      ctx.drawImage(img, 0, 0);
    };
    img.src = 'data:image/png;base64,' + frameData.imageData;
  };

  // Expose renderFrame method via ref (will be called by parent)
  useEffect(() => {
    (window as any).__renderFrame = renderFrame;
    return () => {
      delete (window as any).__renderFrame;
    };
  }, []);

  const handleClick = (e: React.MouseEvent<HTMLCanvasElement>) => {
    if (!hasSession) return;

    const canvas = canvasRef.current;
    if (!canvas) return;

    const rect = canvas.getBoundingClientRect();

    // Calculate click position relative to displayed canvas
    const displayX = e.clientX - rect.left;
    const displayY = e.clientY - rect.top;

    // Calculate scaling factors
    const scaleX = canvas.width / rect.width;
    const scaleY = canvas.height / rect.height;

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
        <canvas
          ref={canvasRef}
          onClick={handleClick}
          className="max-w-full max-h-full object-contain cursor-crosshair shadow-2xl"
          style={{ imageRendering: 'crisp-edges' }}
        />
      )}
    </div>
  );
};

// Export the renderFrame function type for parent components
export const renderFrameOnCanvas = (frameData: FrameData) => {
  if ((window as any).__renderFrame) {
    (window as any).__renderFrame(frameData);
  }
};
