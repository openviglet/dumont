import { SiteNav } from "@/components/sections/site-nav";
import { Hero } from "@/components/sections/hero";
import { Problems } from "@/components/sections/problems";
import { Flow } from "@/components/sections/flow";
import { Connectors } from "@/components/sections/connectors";
import { AemBand } from "@/components/sections/aem-band";
import { WordPressTile } from "@/components/sections/wordpress-tile";
import { Trust } from "@/components/sections/trust";
import { Ai } from "@/components/sections/ai";
import { Marketplace } from "@/components/sections/marketplace";
import { Suite } from "@/components/sections/suite";
import { SelfHost } from "@/components/sections/self-host";
import { Resources } from "@/components/sections/resources";
import { CallToAction } from "@/components/sections/cta";
import { SiteFooter } from "@/components/sections/site-footer";

/**
 * The landing page. The order is an argument, not a feature list:
 *
 * the outcome (an index that stays correct) → the four ways an index drifts →
 * how the pieces fit → where content comes from → the deepest connector, AEM →
 * what is checked after a run → AI, bounded → samples to start from → the
 * suite, and where Dumont is the wrong tool → run it → convert.
 *
 * Connectors are table stakes, so they come after the problem rather than
 * before it. What a buyer cannot get from a weekend script is the audit, the
 * safe finish and the review before deletion, and the page says so first.
 */
export function LandingPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteNav />
      <main className="flex-1">
        <Hero />
        <Problems />
        <Flow />
        <Connectors />
        <WordPressTile />
        <AemBand />
        <Trust />
        <Ai />
        <Marketplace />
        <Suite />
        <SelfHost />
        <Resources />
        <CallToAction />
      </main>
      <SiteFooter />
    </div>
  );
}
