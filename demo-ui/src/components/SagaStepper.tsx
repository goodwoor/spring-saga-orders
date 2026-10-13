import type { OrderStatus } from '../types';

const STEPS: { status: OrderStatus; label: string }[] = [
  { status: 'CREATED', label: 'Created' },
  { status: 'AWAITING_PAYMENT', label: 'Awaiting payment' },
  { status: 'CONFIRMED', label: 'Confirmed' },
];

type Props = {
  status: OrderStatus | null;
};

function stepIndex(status: OrderStatus): number {
  if (status === 'CANCELLED') {
    return 1;
  }
  const idx = STEPS.findIndex((s) => s.status === status);
  return idx >= 0 ? idx : 0;
}

export function SagaStepper({ status }: Props) {
  if (!status) {
    return (
      <section className="panel stepper-panel">
        <h2>Saga</h2>
        <p className="muted">Select or create an order to see the flow.</p>
      </section>
    );
  }

  const active = stepIndex(status);
  const cancelled = status === 'CANCELLED';

  return (
    <section className="panel stepper-panel">
      <h2>Saga</h2>
      <ol className={`stepper ${cancelled ? 'stepper--cancelled' : ''}`}>
        {STEPS.map((step, i) => {
          let state: 'done' | 'active' | 'pending' | 'cancelled' = 'pending';
          if (cancelled && i === active) {
            state = 'cancelled';
          } else if (i < active) {
            state = 'done';
          } else if (i === active && !cancelled) {
            state = 'active';
          } else if (cancelled && i > active) {
            state = 'pending';
          }

          return (
            <li key={step.status} className={`step step--${state}`}>
              <span className="step-dot" />
              <span className="step-label">{step.label}</span>
            </li>
          );
        })}
      </ol>
      {cancelled && (
        <p className="cancel-banner">Compensation path — order CANCELLED</p>
      )}
    </section>
  );
}
