import { SiteNav } from "@/components/sections/site-nav";
import { Hero } from "@/components/sections/hero";
import { Problems } from "@/components/sections/problems";
import { Flow } from "@/components/sections/flow";
import { AemBand } from "@/components/sections/aem-band";
import { Ai } from "@/components/sections/ai";
import { Marketplace } from "@/components/sections/marketplace";
import { Suite } from "@/components/sections/suite";
import { SelfHost } from "@/components/sections/self-host";
import { CallToAction } from "@/components/sections/cta";
import { SiteFooter } from "@/components/sections/site-footer";

/**
 * The landing page. Each section answers one question a newcomer has, in the
 * order they have it, and hands depth to the page that owns it (D353):
 *
 * what it does and the first command (hero) → is this my problem (the four ways
 * an index drifts, each with the check that catches it; /trust/ has the rest) →
 * which sources and engines (one diagram; /connectors/ has each connector) →
 * is AEM covered (its band; /aem/) → what does the AI do → where do I start
 * (samples) → does it fit beside Viglet Turing and Viglet Shio, and when is it
 * the wrong tool → run it → act.
 *
 * Moved off this page, never deleted: the connector cards (/connectors/), the
 * trust cards (/trust/), the WordPress tile (/connectors/, it is Viglet
 * Turing's) and the resources grid (the footer). Their anchors still land.
 */
export function LandingPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteNav />
      <main className="flex-1">
        <Hero />
        <Problems />
        <Flow />
        <AemBand />
        <Ai />
        <Marketplace />
        <Suite />
        <SelfHost />
        <CallToAction />
      </main>
      <SiteFooter />
    </div>
  );
}
