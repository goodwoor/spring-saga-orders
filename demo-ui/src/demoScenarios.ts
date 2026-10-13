import type { OrderCreateRequest } from './types';

/** USB-C Cable from seed (item id 2, cost 150.00) */
const ITEM_USB_C = { itemId: 2, amount: 1, cost: '150.00' };

export const demoScenarios = {
  happyPath: {
    label: 'Happy path',
    description: 'Reserve → payment → CONFIRMED',
    request: {
      userId: 101,
      orderItems: [ITEM_USB_C],
      cost: '150.00',
    } satisfies OrderCreateRequest,
  },
  reserveFail: {
    label: 'Reserve fail',
    description: 'Not enough stock → CANCELLED',
    request: {
      userId: 101,
      orderItems: [{ itemId: 2, amount: 99999, cost: '150.00' }],
      cost: '150.00',
    } satisfies OrderCreateRequest,
  },
  paymentFail: {
    label: 'Payment fail',
    description: 'userId 9999 → revert → CANCELLED',
    request: {
      userId: 9999,
      orderItems: [ITEM_USB_C],
      cost: '150.00',
    } satisfies OrderCreateRequest,
  },
} as const;
