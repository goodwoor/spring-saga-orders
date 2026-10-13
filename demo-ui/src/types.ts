export type OrderStatus =
  | 'CREATED'
  | 'AWAITING_PAYMENT'
  | 'CONFIRMED'
  | 'CANCELLED';

export interface OrderItemCreateRequest {
  itemId: number;
  amount: number;
  cost: string;
}

export interface OrderCreateRequest {
  userId: number;
  orderItems: OrderItemCreateRequest[];
  cost: string;
}

export interface OrderItemResponse {
  id: number;
  itemId: number;
  amount: number;
  cost: string;
}

export interface OrderResponse {
  id: number;
  userId: number;
  orderItems: OrderItemResponse[];
  cost: string;
  status: OrderStatus;
  statusChangedDate: string;
  createdAt: string;
}

export interface ItemResponse {
  id: number;
  cost: string;
  amount: number;
  name: string;
  description: string;
}

export interface PaymentResponse {
  id: number;
  userId: number;
  orderId: number;
  status: string;
  statusChangedDate: string;
  createdAt: string;
  cost: string;
}

export const TERMINAL_STATUSES: OrderStatus[] = ['CONFIRMED', 'CANCELLED'];

export function isTerminalStatus(status: OrderStatus): boolean {
  return TERMINAL_STATUSES.includes(status);
}
