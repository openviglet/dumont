import { Input } from "@/components/ui/input";
import { TurIntegrationAemSourceService } from "@/services/integration/integration-aem-source.service";
import { IconCheck, IconChevronDown, IconX } from "@tabler/icons-react";
import { useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import {
  type AemClassOption,
  AEM_EXTRACTOR_CLASSES,
  AEM_MODEL_CLASSES,
  getClassOption,
} from "./aem-class-labels";

interface AemClassSelectProps {
  value: string;
  onChange: (value: string) => void;
  category?: "extractor" | "model";
  integrationId?: string;
  className?: string;
}

interface DiscoveredClass {
  fqcn: string;
  simpleName: string;
  packageName: string;
}

interface DropdownPos {
  left: number;
  top: number;
  width: number;
}

export function AemClassSelect({
  value,
  onChange,
  category = "extractor",
  integrationId,
  className,
}: Readonly<AemClassSelectProps>) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [discovered, setDiscovered] = useState<DiscoveredClass[]>([]);
  const [loadingDiscovered, setLoadingDiscovered] = useState(false);
  const [pos, setPos] = useState<DropdownPos | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const builtIn: AemClassOption[] =
    category === "model" ? AEM_MODEL_CLASSES : AEM_EXTRACTOR_CLASSES;

  const known = getClassOption(value);

  const builtInFiltered = useMemo(() => {
    if (!value) return builtIn;
    const q = value.toLowerCase();
    return builtIn.filter(
      (o) => o.fqcn.toLowerCase().includes(q) || o.label.toLowerCase().includes(q),
    );
  }, [builtIn, value]);

  const builtInFqcns = useMemo(() => new Set(builtIn.map((b) => b.fqcn)), [builtIn]);
  const discoveredFiltered = useMemo(
    () => discovered.filter((d) => !builtInFqcns.has(d.fqcn)),
    [discovered, builtInFqcns],
  );

  // Position the dropdown below the input
  const updatePosition = () => {
    if (!containerRef.current) return;
    const rect = containerRef.current.getBoundingClientRect();
    setPos({
      left: rect.left,
      top: rect.bottom + 4,
      width: rect.width,
    });
  };

  useLayoutEffect(() => {
    if (!open) return;
    updatePosition();
    window.addEventListener("scroll", updatePosition, true);
    window.addEventListener("resize", updatePosition);
    return () => {
      window.removeEventListener("scroll", updatePosition, true);
      window.removeEventListener("resize", updatePosition);
    };
  }, [open]);

  // Close on outside click (check both container and dropdown)
  useEffect(() => {
    if (!open) return;
    const handleClick = (e: MouseEvent) => {
      const target = e.target as Node;
      if (
        containerRef.current && !containerRef.current.contains(target)
        && dropdownRef.current && !dropdownRef.current.contains(target)
      ) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [open]);

  // Debounced fetch of JVM classes
  useEffect(() => {
    if (!open || !integrationId) return;
    const handle = setTimeout(() => {
      setLoadingDiscovered(true);
      const service = new TurIntegrationAemSourceService(integrationId);
      const apiCategory = category === "model" ? "content" : "attribute";
      service
        .listExtractors(apiCategory, value || undefined)
        .then(setDiscovered)
        .catch(() => setDiscovered([]))
        .finally(() => setLoadingDiscovered(false));
    }, 200);
    return () => clearTimeout(handle);
  }, [open, value, category, integrationId]);

  const handleSelect = (fqcn: string) => {
    onChange(fqcn);
    setOpen(false);
  };

  const totalResults = builtInFiltered.length + discoveredFiltered.length;

  const dropdownContent = open && pos && (
    <div
      ref={dropdownRef}
      className="fixed z-[9999] max-h-80 overflow-y-auto rounded-md border bg-popover shadow-lg"
      style={{ left: pos.left, top: pos.top, width: pos.width }}
    >
      {builtInFiltered.length > 0 && (
        <>
          <div className="px-3 py-1.5 text-[10px] font-semibold uppercase text-muted-foreground bg-muted/50 border-b">
            {t("forms.classSelect.recommended")}
          </div>
          {builtInFiltered.map((opt) => {
            const isSelected = opt.fqcn === value;
            return (
              <button
                key={opt.fqcn}
                type="button"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => handleSelect(opt.fqcn)}
                className={[
                  "flex w-full items-start gap-2 px-3 py-2 text-left text-sm transition-colors cursor-pointer",
                  isSelected
                    ? "bg-accent text-accent-foreground"
                    : "hover:bg-accent/50",
                ].join(" ")}
              >
                <IconCheck
                  className={`size-4 mt-0.5 shrink-0 ${isSelected ? "opacity-100 text-primary" : "opacity-0"}`}
                />
                <div className="min-w-0 flex-1">
                  <div className="font-medium truncate">{opt.label}</div>
                  <div className="text-xs text-muted-foreground truncate">
                    {opt.description}
                  </div>
                  <div className="text-[10px] text-muted-foreground/70 font-mono truncate mt-0.5">
                    {opt.fqcn}
                  </div>
                </div>
              </button>
            );
          })}
        </>
      )}

      {discoveredFiltered.length > 0 && (
        <>
          <div className="px-3 py-1.5 text-[10px] font-semibold uppercase text-muted-foreground bg-muted/50 border-b border-t">
            {t("forms.classSelect.fromClasspath")}
          </div>
          {discoveredFiltered.map((opt) => {
            const isSelected = opt.fqcn === value;
            return (
              <button
                key={opt.fqcn}
                type="button"
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => handleSelect(opt.fqcn)}
                className={[
                  "flex w-full items-start gap-2 px-3 py-2 text-left text-sm transition-colors cursor-pointer",
                  isSelected
                    ? "bg-accent text-accent-foreground"
                    : "hover:bg-accent/50",
                ].join(" ")}
              >
                <IconCheck
                  className={`size-4 mt-0.5 shrink-0 ${isSelected ? "opacity-100 text-primary" : "opacity-0"}`}
                />
                <div className="min-w-0 flex-1">
                  <div className="font-medium truncate">{opt.simpleName}</div>
                  <div className="text-[10px] text-muted-foreground/70 font-mono truncate">
                    {opt.fqcn}
                  </div>
                </div>
              </button>
            );
          })}
        </>
      )}

      {totalResults === 0 && !loadingDiscovered && (
        <div className="px-3 py-3 text-xs text-muted-foreground">
          {t("forms.classSelect.noMatch")}
        </div>
      )}

      {loadingDiscovered && totalResults === 0 && (
        <div className="px-3 py-3 text-xs text-muted-foreground italic">
          {t("forms.classSelect.scanning")}
        </div>
      )}
    </div>
  );

  return (
    <div ref={containerRef} className={`relative ${className ?? ""}`}>
      <div className="relative">
        <Input
          value={value}
          onChange={(e) => {
            onChange(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          placeholder={t("forms.classSelect.placeholder")}
          className={`font-mono text-xs ${value ? "pr-16" : "pr-9"}`}
        />
        {value && (
          <button
            type="button"
            onClick={() => {
              onChange("");
              setOpen(true);
            }}
            className="absolute right-8 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground cursor-pointer"
            aria-label={t("forms.classSelect.clear")}
            title={t("forms.classSelect.clear")}
          >
            <IconX className="size-4" />
          </button>
        )}
        <button
          type="button"
          onClick={() => setOpen((o) => !o)}
          className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground cursor-pointer"
          aria-label="Toggle suggestions"
        >
          <IconChevronDown className={`size-4 transition-transform ${open ? "rotate-180" : ""}`} />
        </button>
      </div>

      {known && !open && (
        <p className="text-xs text-muted-foreground mt-1">
          <span className="font-medium text-foreground">{known.label}</span>
          {" — "}
          {known.description}
        </p>
      )}

      {dropdownContent && createPortal(dropdownContent, document.body)}
    </div>
  );
}
