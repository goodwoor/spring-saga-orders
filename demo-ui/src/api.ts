import type {
  ItemResponse,
  OrderCreateRequest,
  OrderResponse,
  PaymentResponse,
} from './types';

async function handleJson<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `${response.status} ${response.statusText}`);
  }
  return response.json() as Promise<T>;
}

export async function fetchOrders(): Promise<OrderResponse[]> {
  return handleJson(await fetch('/order'));
}

export async function fetchOrder(id: number): Promise<OrderResponse | null> {
  const response = await fetch(`/order/${id}`);
  if (response.status === 404) {
    return null;
  }
  return handleJson(response);
}

export async function createOrder(
  body: OrderCreateRequest,
): Promise<OrderResponse> {
  const response = await fetch('/order', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return handleJson(response);
}

export async function fetchInventory(): Promise<ItemResponse[]> {
  return handleJson(await fetch('/inventory'));
}

export async function fetchPayments(): Promise<PaymentResponse[]> {
  return handleJson(await fetch('/payment'));
}
