import { DumontLogo } from "@/components/logo/dumont-logo"

export function AppFooter() {
  return (
    <footer className="mt-auto">
      <div className="mx-6 border-t" />
      <div className="px-6 py-3 flex items-center justify-center gap-2 flex-wrap">
        <DumontLogo className="size-4 opacity-50" />
        <span className="text-xs text-muted-foreground/70">
          Viglet Dumont
        </span>
        <span className="text-xs text-muted-foreground/40">·</span>
        <a href="https://docs.viglet.org/dumont/" target="_blank" rel="noopener noreferrer" className="text-[11px] text-muted-foreground/50 hover:text-muted-foreground transition-colors">
          Docs
        </a>
        <span className="text-xs text-muted-foreground/40">·</span>
        <a href="https://github.com/openviglet/dumont" target="_blank" rel="noopener noreferrer" className="text-[11px] text-muted-foreground/50 hover:text-muted-foreground transition-colors">
          GitHub
        </a>
        <span className="text-xs text-muted-foreground/40">·</span>
        <a href="https://www.linkedin.com/company/viglet.com" target="_blank" rel="noopener noreferrer" className="text-[11px] text-muted-foreground/50 hover:text-muted-foreground transition-colors">
          LinkedIn
        </a>
      </div>
    </footer>
  )
}
