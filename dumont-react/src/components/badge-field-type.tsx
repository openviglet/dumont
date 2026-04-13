import { Badge } from "@/components/ui/badge";
import {
    AlignLeft,
    Binary,
    Calendar,
    CheckCircle2,
    CircleDollarSign,
    CircleDot,
    Hash,
    List,
    Sigma,
    Type,
} from "lucide-react";

type FieldTypeBadgeProps = {
    type?: string | null;
    variation?: "short" | "long";
};

const typeConfig = {
    INT: {
        label: "Integer",
        icon: Hash,
        className:
            "bg-blue-200 text-blue-900 border-blue-300 dark:bg-blue-900/60 dark:text-blue-100 dark:border-blue-700",
    },
    LONG: {
        label: "Long",
        icon: Binary,
        className:
            "bg-slate-200 text-slate-900 border-slate-300 dark:bg-slate-800 dark:text-slate-100 dark:border-slate-600",
    },
    FLOAT: {
        label: "Float",
        icon: CircleDot,
        className:
            "bg-cyan-200 text-cyan-900 border-cyan-300 dark:bg-cyan-900/60 dark:text-cyan-100 dark:border-cyan-700",
    },
    DOUBLE: {
        label: "Double",
        icon: Sigma,
        className:
            "bg-rose-200 text-rose-900 border-rose-300 dark:bg-rose-900/60 dark:text-rose-100 dark:border-rose-700",
    },
    CURRENCY: {
        label: "Currency",
        icon: CircleDollarSign,
        className:
            "bg-teal-200 text-teal-900 border-teal-300 dark:bg-teal-900/60 dark:text-teal-100 dark:border-teal-700",
    },
    STRING: {
        label: "String",
        icon: Type,
        className:
            "bg-emerald-200 text-emerald-900 border-emerald-300 dark:bg-emerald-900/60 dark:text-emerald-100 dark:border-emerald-700",
    },
    TEXT: {
        label: "Text",
        icon: AlignLeft,
        className:
            "bg-amber-200 text-amber-900 border-amber-300 dark:bg-amber-900/60 dark:text-amber-100 dark:border-amber-700",
    },
    ARRAY: {
        label: "Array",
        icon: List,
        className:
            "bg-violet-200 text-violet-900 border-violet-300 dark:bg-violet-900/60 dark:text-violet-100 dark:border-violet-700",
    },
    DATE: {
        label: "Date",
        icon: Calendar,
        className:
            "bg-orange-200 text-orange-900 border-orange-300 dark:bg-orange-900/60 dark:text-orange-100 dark:border-orange-700",
    },
    BOOL: {
        label: "Boolean",
        icon: CheckCircle2,
        className:
            "bg-lime-200 text-lime-900 border-lime-300 dark:bg-lime-900/60 dark:text-lime-100 dark:border-lime-700",
    },
} as const;

export const BadgeFieldType: React.FC<FieldTypeBadgeProps> = ({ type, variation = "long" }) => {
    const typeValue = type ?? "";
    const cleanType = typeValue.split("(")[0].toUpperCase();
    const config = typeConfig[cleanType as keyof typeof typeConfig] ?? {
        label: typeValue,
        icon: Type,
        className: "bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300",
    };

    const Icon = config.icon;
    const isShort = variation === "short";

    return (
        <Badge
            variant="outline"
            className={`justify-center inline-flex items-center font-semibold whitespace-nowrap transition-colors ${isShort ? "w-8 px-0 py-1" : "w-auto md:w-24 gap-1 md:gap-2 py-1 px-2 md:px-3"} ${config.className}`}
        >
            {Icon && <Icon className="h-3.5 w-3.5 shrink-0" />}
            {!isShort && <span className="hidden md:inline">{config.label}</span>}
        </Badge>
    );
};
