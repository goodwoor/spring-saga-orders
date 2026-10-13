import { useEffect, useState } from 'react';
import { fetchOrder } from '../api';
import type { OrderResponse } from '../types';
import { isTerminalStatus } from '../types';

const POLL_MS = 1000;

export function useOrderPolling(selectedOrderId: number | null) {
  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (selectedOrderId == null) {
      setOrder(null);
      setError(null);
      return;
    }

    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const tick = async () => {
      setLoading(true);
      try {
        const next = await fetchOrder(selectedOrderId);
        if (cancelled) {
          return;
        }
        setOrder(next);
        setError(next ? null : 'Order not found');
        if (next && !isTerminalStatus(next.status)) {
          timer = setTimeout(tick, POLL_MS);
        }
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : 'Failed to load order');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    };

    void tick();

    return () => {
      cancelled = true;
      if (timer) {
        clearTimeout(timer);
      }
    };
  }, [selectedOrderId]);

  return { order, loading, error };
}
