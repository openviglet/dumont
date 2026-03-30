interface DumontLogoProps {
    readonly size?: number;
    readonly className?: string;
}

export function DumontLogo({ size = 48, className }: DumontLogoProps) {
    return (
        <svg
            className={className}
            width={size}
            height={size}
            viewBox="0 0 549 549"
        >
            <defs>
                <style>{`
                    .dumont-logo-bg {
                        fill: #0f766e;
                        stroke: #ccfbf1;
                        stroke-width: 20px;
                        opacity: 1.0;
                    }
                    .dumont-logo-text {
                        font-size: 98.505px;
                        fill: #ccfbf1;
                        font-family: "Proxima Nova", system-ui, sans-serif;
                        font-weight: 500;
                    }
                `}</style>
            </defs>
            <rect
                className="dumont-logo-bg"
                x="0.063"
                width="548"
                height="548.188"
                rx="100"
                ry="100"
            />
            <text
                className="dumont-logo-text"
                transform="translate(64.825 442.418) scale(2.74 2.741)"
            >
                Du
            </text>
        </svg>
    );
}
