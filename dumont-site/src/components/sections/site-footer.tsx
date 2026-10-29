import { Container, Logo } from "@/components/brand";
import { FOOTER_BLURB, FOOTER_COLUMNS, LINKS, sitePath } from "@/lib/site-links";
import { reopenConsent } from "@/lib/analytics";

export function SiteFooter() {
  return (
    <footer className="bg-[#061110] text-[#86a5a1]">
      <Container className="py-14">
        <div className="grid gap-8 sm:grid-cols-2 md:grid-cols-[1.4fr_1fr_1fr_1fr]">
          <div>
            <Logo className="text-[#e8f5f3]" />
            <p className="mt-3 max-w-xs text-sm leading-relaxed">{FOOTER_BLURB}</p>
          </div>
          {FOOTER_COLUMNS.map((col) => (
            <div key={col.label}>
              <h3 className="text-xs font-bold tracking-[0.06em] text-[#e8f5f3] uppercase">
                {col.label}
              </h3>
              <ul className="mt-4 space-y-2.5">
                {col.items.map((link) => (
                  <li key={link.label}>
                    <a
                      href={link.href}
                      target={link.href.startsWith("http") ? "_blank" : undefined}
                      rel={link.href.startsWith("http") ? "noreferrer" : undefined}
                      className="text-sm transition-colors hover:text-[#e8f5f3]"
                    >
                      {link.label}
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
        <div className="mt-12 flex flex-col justify-between gap-2 border-t border-[#16302d] pt-6 text-xs text-[#5f7e7a] sm:flex-row">
          <span>
            &copy; {new Date().getFullYear()} Viglet Dumont &middot;{" "}
            <a href={LINKS.license} target="_blank" rel="noreferrer" className="hover:text-[#e8f5f3]">
              Apache 2.0
            </a>
          </span>
          <span className="space-x-3">
            <a href={LINKS.viglet} target="_blank" rel="noreferrer" className="hover:text-[#e8f5f3]">
              viglet.org
            </a>
            <a href={LINKS.docs} className="hover:text-[#e8f5f3]">
              Docs
            </a>
            <a href={sitePath("/llms.txt")} className="font-mono hover:text-[#e8f5f3]">
              llms.txt
            </a>
            <button type="button" onClick={reopenConsent} className="hover:text-[#e8f5f3]">
              Cookie settings
            </button>
          </span>
        </div>
      </Container>
    </footer>
  );
}
