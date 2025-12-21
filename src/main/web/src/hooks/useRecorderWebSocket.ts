import { useEffect, useRef, useCallback } from 'react';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import type { IMessage } from '@stomp/stompjs';
import type { FrameData, Action } from '../types/recorder';

interface UseRecorderWebSocketProps {
  sessionId: string | null;
  onFrame: (frameData: FrameData) => void;
  onAction: (action: Action) => void;
  onConnect?: () => void;
  onDisconnect?: () => void;
}

export const useRecorderWebSocket = ({
  sessionId,
  onFrame,
  onAction,
  onConnect,
  onDisconnect,
}: UseRecorderWebSocketProps) => {
  const clientRef = useRef<Client | null>(null);

  const connect = useCallback(() => {
    if (!sessionId || clientRef.current?.connected) return;

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws-recorder'),
      onConnect: () => {
        console.log('WebSocket connected');

        // Subscribe to frame stream
        client.subscribe(`/topic/recorder/${sessionId}/frames`, (message: IMessage) => {
          try {
            const frameData = JSON.parse(message.body);
            onFrame(frameData);
          } catch (error) {
            console.error('Failed to parse frame data:', error);
          }
        });

        // Subscribe to action stream
        client.subscribe(`/topic/recorder/${sessionId}/actions`, (message: IMessage) => {
          try {
            const action = JSON.parse(message.body);
            onAction(action);
          } catch (error) {
            console.error('Failed to parse action:', error);
          }
        });

        onConnect?.();
      },
      onDisconnect: () => {
        console.log('WebSocket disconnected');
        onDisconnect?.();
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame);
      },
      debug: () => {
        // Uncomment for debugging
        // console.log('STOMP debug:', str);
      },
    });

    client.activate();
    clientRef.current = client;
  }, [sessionId, onFrame, onAction, onConnect, onDisconnect]);

  const disconnect = useCallback(() => {
    if (clientRef.current?.connected) {
      clientRef.current.deactivate();
      clientRef.current = null;
    }
  }, []);

  useEffect(() => {
    if (sessionId) {
      connect();
    }

    return () => {
      disconnect();
    };
  }, [sessionId, connect, disconnect]);

  return {
    connected: clientRef.current?.connected ?? false,
    disconnect,
  };
};
