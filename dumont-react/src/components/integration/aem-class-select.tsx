import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Input } from "@/components/ui/input";
import { IconKeyboard } from "@tabler/icons-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import {
  type AemClassOption,
  AEM_EXTRACTOR_CLASSES,
  AEM_MODEL_CLASSES,
  getClassLabel,
} from "./aem-class-labels";

interface AemClassSelectProps {
  value: string;
  onChange: (value: string) => void;
  category?: "extractor" | "model";
  className?: string;
}

export function AemClassSelect({
  value,
  onChange,
  category = "extractor",
  className,
}: Readonly<AemClassSelectProps>) {
  const { t } = useTranslation();
  const [manual, setManual] = useState(false);

  const options: AemClassOption[] =
    category === "model" ? AEM_MODEL_CLASSES : AEM_EXTRACTOR_CLASSES;

  const isKnown = options.some((o) => o.fqcn === value);

  // If value is set but not in the list, show manual mode
  if (value && !isKnown && !manual) {
    return (
      <div className={className}>
        <div className="flex gap-2">
          <Input
            value={value}
            onChange={(e) => onChange(e.target.value)}
            placeholder="com.example.MyExtractor"
            className="flex-1 font-mono text-xs"
          />
          <button
            type="button"
            onClick={() => {
              onChange("");
              setManual(false);
            }}
            className="text-xs text-primary hover:underline shrink-0 px-2 cursor-pointer"
          >
            {t("forms.classSelect.useList")}
          </button>
        </div>
      </div>
    );
  }

  if (manual) {
    return (
      <div className={className}>
        <div className="flex gap-2">
          <Input
            value={value}
            onChange={(e) => onChange(e.target.value)}
            placeholder="com.example.MyExtractor"
            className="flex-1 font-mono text-xs"
            autoFocus
          />
          <button
            type="button"
            onClick={() => setManual(false)}
            className="text-xs text-primary hover:underline shrink-0 px-2 cursor-pointer"
          >
            {t("forms.classSelect.useList")}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className={className}>
      <div className="flex gap-2">
        <Select
          value={value || "__none__"}
          onValueChange={(v) => onChange(v === "__none__" ? "" : v)}
        >
          <SelectTrigger className="flex-1">
            <SelectValue>
              {value ? (
                <span className="flex items-center gap-2">
                  <span className="font-medium">{getClassLabel(value)}</span>
                </span>
              ) : (
                <span className="text-muted-foreground">
                  {t("forms.classSelect.selectClass")}
                </span>
              )}
            </SelectValue>
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="__none__">
              <span className="text-muted-foreground">
                {t("forms.classSelect.none")}
              </span>
            </SelectItem>
            {options.map((opt) => (
              <SelectItem key={opt.fqcn} value={opt.fqcn}>
                <div>
                  <div className="font-medium">{opt.label}</div>
                  <div className="text-xs text-muted-foreground">
                    {opt.description}
                  </div>
                </div>
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <button
          type="button"
          onClick={() => setManual(true)}
          className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground shrink-0 px-2 cursor-pointer"
          title={t("forms.classSelect.manualEntry")}
        >
          <IconKeyboard className="size-3.5" />
        </button>
      </div>
    </div>
  );
}
