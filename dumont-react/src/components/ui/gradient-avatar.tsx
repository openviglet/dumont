import { cva, type VariantProps } from "class-variance-authority";
import * as React from "react";

import { cn } from "@/lib/utils";

/**
 * Lightweight gradient avatar used in the header and admin user screens.
 * Falls back to initials inside a branded blue→indigo gradient when no image
 * URL is provided (or the image fails to load).
 */

const gradientAvatarFallbackVariants = cva(
  "flex size-full items-center justify-center rounded-full text-xs font-semibold text-white",
  {
    variants: {
      variant: {
        default: "bg-gradient-to-br from-blue-600 to-indigo-600 dark:from-blue-500 dark:to-indigo-500",
        secondary: "bg-gradient-to-br from-slate-600 to-slate-700 dark:from-slate-500 dark:to-slate-600",
        destructive: "bg-gradient-to-br from-red-600 to-rose-600 dark:from-red-500 dark:to-rose-500",
        success: "bg-gradient-to-br from-emerald-600 to-teal-600 dark:from-emerald-500 dark:to-teal-500",
        warning: "bg-gradient-to-br from-amber-600 to-orange-600 dark:from-amber-500 dark:to-orange-500",
        info: "bg-gradient-to-br from-cyan-600 to-blue-600 dark:from-cyan-500 dark:to-blue-500",
      },
    },
    defaultVariants: { variant: "default" },
  }
);

type AvatarContextValue = {
  imageFailed: boolean;
  setImageFailed: React.Dispatch<React.SetStateAction<boolean>>;
  hasImage: boolean;
  setHasImage: React.Dispatch<React.SetStateAction<boolean>>;
};

const AvatarContext = React.createContext<AvatarContextValue | null>(null);

function GradientAvatar({
  className,
  children,
  ...props
}: React.ComponentProps<"span">) {
  const [imageFailed, setImageFailed] = React.useState(false);
  const [hasImage, setHasImage] = React.useState(false);
  const ctx = React.useMemo(() => ({ imageFailed, setImageFailed, hasImage, setHasImage }), [imageFailed, hasImage]);
  return (
    <AvatarContext value={ctx}>
      <span
        data-slot="gradient-avatar"
        className={cn("relative flex size-8 shrink-0 overflow-hidden rounded-full", className)}
        {...props}
      >
        {children}
      </span>
    </AvatarContext>
  );
}

function GradientAvatarImage({
  className,
  src,
  alt,
  ...props
}: React.ImgHTMLAttributes<HTMLImageElement>) {
  const ctx = React.useContext(AvatarContext);
  React.useEffect(() => {
    ctx?.setHasImage(Boolean(src));
  }, [src, ctx]);
  if (!src || ctx?.imageFailed) return null;
  return (
    <img
      data-slot="gradient-avatar-image"
      src={src}
      alt={alt}
      className={cn("aspect-square size-full object-cover", className)}
      onError={() => ctx?.setImageFailed(true)}
      {...props}
    />
  );
}

function GradientAvatarFallback({
  className,
  variant = "default",
  children,
  ...props
}: React.ComponentProps<"span"> & VariantProps<typeof gradientAvatarFallbackVariants>) {
  const ctx = React.useContext(AvatarContext);
  // Show fallback when no image, or image has failed.
  const shouldShow = !ctx?.hasImage || ctx?.imageFailed;
  if (!shouldShow) return null;
  return (
    <span
      data-slot="gradient-avatar-fallback"
      className={cn(gradientAvatarFallbackVariants({ variant }), className)}
      {...props}
    >
      {children}
    </span>
  );
}

export {
  GradientAvatar,
  GradientAvatarFallback,
  gradientAvatarFallbackVariants,
  GradientAvatarImage,
};
