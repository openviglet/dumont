import { IconTrash } from "@tabler/icons-react";
import React, { type Dispatch, type SetStateAction, useState } from "react";
import { useTranslation } from "react-i18next";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "./ui/dialog";
import { GradientButton } from "./ui/gradient-button";
import { Input } from "./ui/input";

interface Props {
  feature: string;
  name: string;
  onDelete: () => void;
  open: boolean;
  setOpen: Dispatch<SetStateAction<boolean>>;
  trigger?: React.ReactNode;
  title?: string;
  description?: string;
  confirmLabel?: string;
}

export const DialogDelete: React.FC<Props> = ({
  feature, name, onDelete, open, setOpen,
  trigger, title, description, confirmLabel,
}) => {
  const { t } = useTranslation();
  const [confirmText, setConfirmText] = useState("");
  const canDelete = confirmText === name;

  function handleOpenChange(value: boolean) {
    setOpen(value);
    if (!value) setConfirmText("");
  }

  const defaultTrigger = (
    <GradientButton variant="ghost" size="icon-sm">
      <IconTrash className="size-5!" />
    </GradientButton>
  );

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        {trigger ?? defaultTrigger}
      </DialogTrigger>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-red-500/10 ring-1 ring-red-500/20 mb-2">
            <IconTrash className="size-6 text-red-500" />
          </div>
          <DialogTitle className="text-center">{title ?? t("dialog.deleteFeature", { feature })}</DialogTitle>
          <DialogDescription className="text-center">
            {description ?? t("dialog.permanentDelete", { name })}
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          <label className="text-sm text-muted-foreground">
            {t("dialog.typeToConfirm")} <span className="font-mono font-semibold text-foreground bg-muted px-1.5 py-0.5 rounded">{name}</span> {t("dialog.toConfirm")}
          </label>
          <Input
            value={confirmText}
            onChange={(e) => setConfirmText(e.target.value)}
            placeholder={name}
            className="font-mono text-sm"
            autoFocus
          />
        </div>
        <DialogFooter className="flex-col gap-2 sm:flex-col">
          <GradientButton
            onClick={onDelete}
            variant="destructive"
            disabled={!canDelete}
            className="w-full"
          >
            <IconTrash className="size-4" />
            {confirmLabel ?? t("dialog.deleteFeature", { feature })}
          </GradientButton>
          <GradientButton
            onClick={() => handleOpenChange(false)}
            variant="ghost"
            className="w-full"
          >
            {t("common.cancel")}
          </GradientButton>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
