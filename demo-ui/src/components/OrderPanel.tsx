import type { OrderResponse } from '../types';

type Props = {
  order: OrderResponse | null;
  loading: boolean;
  error: string | null;
  selectedId: number | null;
};

function formatInstant(iso: string): string {
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}

export function OrderPanel({ order, loading, error, selectedId }: Props) {
  return (
    <section className="panel order-panel">
      <h2>Selected order</h2>
      {!selectedId && (
        <p className="muted">No order selected — click a row or run a demo.</p>
      )}
      {selectedId && loading && !order && !error && (
        <p className="muted">Loading…</p>
      )}
      {error && <p className="error-text">{error}</p>}
      {order && (
        <dl className="order-details">
          <div>
            <dt>ID</dt>
            <dd>{order.id}</dd>
          </div>
          <div>
            <dt>User</dt>
            <dd>{order.userId}</dd>
          </div>
          <div>
            <dt>Status</dt>
            <dd>
              <span className={`status-pill status-pill--${order.status}`}>
                {order.status}
              </span>
            </dd>
          </div>
          <div>
            <dt>Cost</dt>
            <dd>{order.cost}</dd>
          </div>
          <div>
            <dt>Status changed</dt>
            <dd>{formatInstant(order.statusChangedDate)}</dd>
          </div>
          <div className="order-items-block">
            <dt>Items</dt>
            <dd>
              <ul className="order-items-list">
                {order.orderItems.map((line) => (
                  <li key={line.id}>
                    item #{line.itemId} × {line.amount} — {line.cost}
                  </li>
                ))}
              </ul>
            </dd>
          </div>
        </dl>
      )}
    </section>
  );
}
