import { IconPalette, IconTrash } from "@tabler/icons-react";
import { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";

import { DiceBearAvatarPicker } from "@/components/dicebear-avatar-picker";
import { GradientAvatar, GradientAvatarFallback, GradientAvatarImage } from "@/components/ui/gradient-avatar";
import { cn } from "@/lib/utils";

interface AvatarFieldProps {
  avatarUrl?: string;
  initials: string;
  seed: string;
  size?: "sm" | "md" | "lg";
  onSelect: (url: string) => void | Promise<void>;
  onRemove?: () => void | Promise<void>;
  children?: React.ReactNode;
  className?: string;
}

const sizeMap = {
  sm: "size-10",
  md: "size-16",
  lg: "size-24",
} as const;

const textSizeMap = {
  sm: "text-xs",
  md: "text-lg",
  lg: "text-2xl",
} as const;

export function AvatarField({
  avatarUrl,
  initials,
  seed,
  size = "md",
  onSelect,
  onRemove,
  children,
  className,
}: AvatarFieldProps) {
  const { t } = useTranslation();
  const [pickerOpen, setPickerOpen] = useState(false);

  const handleSelect = useCallback(
    async (url: string) => {
      await onSelect(url);
    },
    [onSelect]
  );

  return (
    <div className={cn("flex items-center gap-4", className)}>
      <div className="relative group">
        <GradientAvatar className={sizeMap[size]}>
          {avatarUrl ? <GradientAvatarImage src={avatarUrl} alt={initials} /> : null}
          <GradientAvatarFallback className={textSizeMap[size]}>{initials}</GradientAvatarFallback>
        </GradientAvatar>
        <button
          type="button"
          title={t("forms.avatarPicker.chooseAvatar")}
          onClick={() => setPickerOpen(true)}
          className={cn(
            "absolute inset-0 rounded-full bg-black/60 text-white opacity-0 transition-opacity group-hover:opacity-100",
            "flex items-center justify-center cursor-pointer"
          )}
        >
          <IconPalette className="size-5" />
        </button>
      </div>

      <div className="flex flex-col gap-1.5">
        {children}
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setPickerOpen(true)}
            className="text-xs font-medium text-blue-600 hover:text-blue-700 dark:text-blue-400 cursor-pointer"
          >
            {t("forms.avatarPicker.chooseAvatar")}
          </button>
          {avatarUrl && onRemove && (
            <button
              type="button"
              onClick={onRemove}
              className="text-xs text-destructive hover:opacity-80 flex items-center gap-1 cursor-pointer"
            >
              <IconTrash className="size-3" />
              {t("forms.avatarPicker.remove")}
            </button>
          )}
        </div>
      </div>

      <DiceBearAvatarPicker
        open={pickerOpen}
        onOpenChange={setPickerOpen}
        onSelect={handleSelect}
        defaultSeed={seed}
        currentAvatarUrl={avatarUrl}
      />
    </div>
  );
}
