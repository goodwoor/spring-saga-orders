import type {
  ItemResponse,
  OrderResponse,
  PaymentResponse,
} from '../types';

type Props = {
  orders: OrderResponse[];
  inventory: ItemResponse[];
  payments: PaymentResponse[];
  selectedOrderId: number | null;
  onSelectOrder: (id: number) => void;
  listsError: string | null;
};

function sortByIdDesc<T extends { id: number }>(rows: T[]): T[] {
  return [...rows].sort((a, b) => b.id - a.id);
}

export function DataTables({
  orders,
  inventory,
  payments,
  selectedOrderId,
  onSelectOrder,
  listsError,
}: Props) {
  const recentOrders = sortByIdDesc(orders).slice(0, 12);

  return (
    <div className="tables-grid">
      {listsError && <p className="error-text tables-error">{listsError}</p>}

      <section className="panel table-panel">
        <h2>Orders</h2>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>User</th>
                <th>Status</th>
                <th>Cost</th>
              </tr>
            </thead>
            <tbody>
              {recentOrders.map((o) => (
                <tr
                  key={o.id}
                  className={
                    o.id === selectedOrderId ? 'row-selected' : 'row-clickable'
                  }
                  onClick={() => onSelectOrder(o.id)}
                >
                  <td>{o.id}</td>
                  <td>{o.userId}</td>
                  <td>
                    <span className={`status-pill status-pill--${o.status}`}>
                      {o.status}
                    </span>
                  </td>
                  <td>{o.cost}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="panel table-panel">
        <h2>Inventory</h2>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Name</th>
                <th>Stock</th>
                <th>Cost</th>
              </tr>
            </thead>
            <tbody>
              {inventory.map((item) => (
                <tr key={item.id}>
                  <td>{item.id}</td>
                  <td>{item.name}</td>
                  <td>{item.amount}</td>
                  <td>{item.cost}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="panel table-panel table-panel--wide">
        <h2>Payments</h2>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Order</th>
                <th>User</th>
                <th>Status</th>
                <th>Cost</th>
              </tr>
            </thead>
            <tbody>
              {sortByIdDesc(payments)
                .slice(0, 15)
                .map((p) => (
                  <tr key={p.id}>
                    <td>{p.id}</td>
                    <td>{p.orderId}</td>
                    <td>{p.userId}</td>
                    <td>{p.status}</td>
                    <td>{p.cost}</td>
                  </tr>
                ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
