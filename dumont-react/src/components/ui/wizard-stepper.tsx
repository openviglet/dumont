import { IconCheck } from "@tabler/icons-react";

export interface WizardStepperStep {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
}

interface WizardStepperProps {
  steps: WizardStepperStep[];
  currentStep: number;
  completedSteps: boolean[];
  onStepClick: (index: number) => void;
}

export function WizardStepper({
  steps,
  currentStep,
  completedSteps,
  onStepClick,
}: Readonly<WizardStepperProps>) {
  return (
    <nav>
      <ol className="flex items-center">
        {steps.map((step, index) => {
          const Icon = step.icon;
          const isActive = index === currentStep;
          const isDone = completedSteps[index];
          const isPast = index < currentStep;
          const isClickable = isPast || isDone;

          return (
            <li
              key={step.label}
              className="flex items-center"
              style={{ flex: index === 0 ? "0 0 auto" : "1 1 0%" }}
            >
              {index > 0 && (
                <div
                  className={[
                    "h-px flex-1 mx-2 transition-colors",
                    isPast || isActive ? "bg-primary" : "bg-border",
                  ].join(" ")}
                />
              )}
              <button
                type="button"
                onClick={() => {
                  if (isClickable) onStepClick(index);
                }}
                disabled={!isClickable && !isActive}
                className={[
                  "flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-medium transition-all whitespace-nowrap shrink-0",
                  isActive
                    ? "bg-primary text-primary-foreground shadow-sm"
                    : isClickable
                      ? "bg-primary/10 text-primary cursor-pointer hover:bg-primary/20"
                      : "bg-muted text-muted-foreground",
                ].join(" ")}
              >
                {isDone && !isActive ? (
                  <IconCheck className="size-3.5" />
                ) : (
                  <Icon className="size-3.5" />
                )}
                <span className="hidden sm:inline">{step.label}</span>
              </button>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
