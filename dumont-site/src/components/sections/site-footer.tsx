import { Container, Logo } from "@/components/brand";
import { LINKS } from "@/lib/site-content";
import { reopenConsent } from "@/lib/analytics";

const COLUMNS: { heading: string; links: { label: string; href: string }[] }[] = [
  {
    heading: "Connectors",
    links: [
      { label: "Adobe Experience Manager", href: "/aem/" },
      { label: "Edge Delivery Services", href: "/connectors/#eds" },
      { label: "Databases (JDBC)", href: "/connectors/#database" },
      { label: "Web crawler", href: "/connectors/#web-crawler" },
      { label: "Files and documents", href: "/connectors/#files" },
      { label: "Catalogs and JSON APIs", href: "/connectors/#catalog" },
    ],
  },
  {
    heading: "The product",
    links: [
      { label: "How it fits", href: "/#how" },
      { label: "An index you can trust", href: "/trust/" },
      { label: "AI, where it helps", href: "/#ai" },
      { label: "Marketplace", href: "/#marketplace" },
      { label: "Self-host", href: "/#self-host" },
    ],
  },
  {
    heading: "Open and inspectable",
    links: [
      { label: "Documentation", href: LINKS.docs },
      { label: "Source", href: LINKS.repo },
      { label: "Roadmap", href: LINKS.roadmap },
      { label: "Changelog", href: LINKS.changelog },
      { label: "Viglet Turing", href: LINKS.turing },
      { label: "Viglet Shio", href: LINKS.shio },
    ],
  },
];

export function SiteFooter() {
  return (
    <footer className="bg-[#061110] text-[#86a5a1]">
      <Container className="py-14">
        <div className="grid gap-8 sm:grid-cols-2 md:grid-cols-[1.4fr_1fr_1fr_1fr]">
          <div>
            <Logo className="text-[#e8f5f3]" />
            <p className="mt-3 max-w-xs text-sm leading-relaxed">
              Open-source connectors from AEM, Edge Delivery Services, databases, files,
              web sites and JSON APIs into Turing ES, Solr or Elasticsearch, with an audit
              that keeps the index true to the source.
            </p>
          </div>
          {COLUMNS.map((col) => (
            <div key={col.heading}>
              <h3 className="text-xs font-bold tracking-[0.06em] text-[#e8f5f3] uppercase">
                {col.heading}
              </h3>
              <ul className="mt-4 space-y-2.5">
                {col.links.map((link) => (
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
              docs.viglet.org
            </a>
            <a href="/llms.txt" className="font-mono hover:text-[#e8f5f3]">
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
