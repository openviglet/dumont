import { ArrowRight, ShoppingBag } from "lucide-react";
import { Container } from "@/components/brand";
import { sitePath } from "@/lib/site-links";

/**
 * The WordPress plugin, on the landing and connectors pages. A tile of its own rather than a
 * connector card: it runs inside WordPress and sends to Turing directly, so the queue, safe
 * finish and audit the connector cards promise are not its to claim.
 */
export function WordPressTile() {
  return (
    <section className="pb-20">
      <Container>
        <a
          href={sitePath("/wordpress/")}
          className="group flex flex-col gap-5 rounded-2xl border border-border bg-card p-6 transition-all hover:border-primary/40 hover:shadow-lg sm:flex-row sm:items-center"
        >
          <div className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-[#0f766e] to-[#0e7490] text-white shadow-sm">
            <ShoppingBag className="size-5" />
          </div>
          <div className="flex-1">
            <h3 className="text-lg font-bold">WordPress and WooCommerce</h3>
            <p className="mt-1 text-sm leading-relaxed text-muted-foreground">
              A plugin that indexes posts, pages and products into Turing and puts search,
              filters from a sentence, cited answers and a shopping copilot on the site. See it
              as one shopper's visit to a demo store.
            </p>
          </div>
          <span className="inline-flex shrink-0 items-center gap-1 text-sm font-semibold text-primary">
            The story
            <ArrowRight className="size-3.5 transition-transform group-hover:translate-x-0.5" />
          </span>
        </a>
      </Container>
    </section>
  );
}
