import { demoScenarios } from '../demoScenarios';

type Props = {
  busy: boolean;
  onRun: (key: keyof typeof demoScenarios) => void;
};

export function DemoButtons({ busy, onRun }: Props) {
  return (
    <section className="panel demo-panel">
      <h2>Demo scenarios</h2>
      <p className="muted">
        Creates a new order via Gateway. Status updates poll every second.
      </p>
      <div className="demo-buttons">
        {(Object.keys(demoScenarios) as (keyof typeof demoScenarios)[]).map(
          (key) => {
            const scenario = demoScenarios[key];
            return (
              <button
                key={key}
                type="button"
                className="demo-btn"
                disabled={busy}
                onClick={() => onRun(key)}
              >
                <span className="demo-btn-label">{scenario.label}</span>
                <span className="demo-btn-desc">{scenario.description}</span>
              </button>
            );
          },
        )}
      </div>
    </section>
  );
}
