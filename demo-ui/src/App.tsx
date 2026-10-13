import { useCallback, useEffect, useState } from 'react';
import {
  createOrder,
  fetchInventory,
  fetchOrders,
  fetchPayments,
} from './api';
import { DataTables } from './components/DataTables';
import { DemoButtons } from './components/DemoButtons';
import { OrderPanel } from './components/OrderPanel';
import { SagaStepper } from './components/SagaStepper';
import { demoScenarios } from './demoScenarios';
import { useOrderPolling } from './hooks/useOrderPolling';
import type { ItemResponse, OrderResponse, PaymentResponse } from './types';

const LIST_REFRESH_MS = 2500;

export default function App() {
  const [selectedOrderId, setSelectedOrderId] = useState<number | null>(null);
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [inventory, setInventory] = useState<ItemResponse[]>([]);
  const [payments, setPayments] = useState<PaymentResponse[]>([]);
  const [listsError, setListsError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const { order, loading, error: pollError } = useOrderPolling(selectedOrderId);

  const refreshLists = useCallback(async () => {
    try {
      const [o, i, p] = await Promise.all([
        fetchOrders(),
        fetchInventory(),
        fetchPayments(),
      ]);
      setOrders(o);
      setInventory(i);
      setPayments(p);
      setListsError(null);
    } catch (e) {
      setListsError(
        e instanceof Error ? e.message : 'Failed to load lists (is Gateway up?)',
      );
    }
  }, []);

  useEffect(() => {
    void refreshLists();
    const id = setInterval(() => void refreshLists(), LIST_REFRESH_MS);
    return () => clearInterval(id);
  }, [refreshLists]);

  const runDemo = async (key: keyof typeof demoScenarios) => {
    setCreating(true);
    setActionError(null);
    try {
      const created = await createOrder(demoScenarios[key].request);
      setSelectedOrderId(created.id);
      await refreshLists();
    } catch (e) {
      setActionError(
        e instanceof Error ? e.message : 'Failed to create order',
      );
    } finally {
      setCreating(false);
    }
  };

  const displayError = actionError ?? pollError;

  return (
    <div className="app">
      <header className="app-header">
        <div>
          <p className="eyebrow">saga-orders</p>
          <h1>Order saga demo</h1>
        </div>
        <p className="header-note">
          API via Vite proxy → Gateway :8081
        </p>
      </header>

      {displayError && (
        <div className="banner banner--error" role="alert">
          {displayError}
        </div>
      )}

      <div className="layout-top">
        <DemoButtons busy={creating} onRun={(k) => void runDemo(k)} />
        <SagaStepper status={order?.status ?? null} />
        <OrderPanel
          order={order}
          loading={loading}
          error={pollError}
          selectedId={selectedOrderId}
        />
      </div>

      <DataTables
        orders={orders}
        inventory={inventory}
        payments={payments}
        selectedOrderId={selectedOrderId}
        onSelectOrder={setSelectedOrderId}
        listsError={listsError}
      />
    </div>
  );
}
