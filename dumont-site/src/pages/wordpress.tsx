import { ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Container, SectionHeader } from "@/components/brand";
import { PageHero } from "@/components/sections/page-hero";
import { SiteNav } from "@/components/sections/site-nav";
import { SiteFooter } from "@/components/sections/site-footer";
import { CallToAction } from "@/components/sections/cta";
import { LINKS, WORDPRESS_STORY } from "@/lib/site-content";
import { sitePath } from "@/lib/site-links";

/**
 * The WordPress and WooCommerce plugin, told as the demo store's story: one shopper, one
 * visit, a chapter per feature. Every picture is a still of `npm run e2e:story` running
 * against Turing on the demo stack, so the page shows nothing the story does not play.
 */
export function WordPressPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteNav />
      <main className="flex-1">
        <PageHero
          eyebrow="Dumont for WordPress and WooCommerce"
          title={
            <>
              Search that understands what a shopper means,{" "}
              <span className="text-gradient">answered from your own catalog</span>
            </>
          }
          lead="A WordPress plugin that indexes posts, pages and WooCommerce products into Turing, then puts search as you type, filters, cited answers, a shopping copilot and search insights on the site. Below, one shopper's visit to the demo store, from a vague need to a paid-for cart."
        >
          <div className="flex flex-wrap gap-3">
            <Button asChild size="lg">
              <a href={LINKS.wordpressDocs}>
                Install the plugin <ArrowRight className="size-4" />
              </a>
            </Button>
            <Button
              asChild
              size="lg"
              variant="outline"
              className="border-white/20 bg-white/10 text-white hover:bg-white/20 hover:text-white dark:bg-white/10"
            >
              <a href={LINKS.wordpressDemo}>Run the demo store</a>
            </Button>
          </div>
        </PageHero>

        <section id="story" className="py-20">
          <Container>
            <SectionHeader
              eyebrow="One shopper, one visit"
              title={
                <>
                  Her first two days on the fells,{" "}
                  <span className="text-primary">in eight steps</span>
                </>
              }
              description="Tarnfell is a demo outdoor shop: every product and guide is invented, and everything else is the plugin and Turing running. Each picture is taken from the recorded story."
            />
            <ol className="space-y-16">
              {WORDPRESS_STORY.map((c, i) => (
                <li
                  key={c.title}
                  className="grid items-center gap-8 lg:grid-cols-5 [&>*]:min-w-0"
                >
                  <div className={i % 2 ? "lg:order-2 lg:col-span-2" : "lg:col-span-2"}>
                    <p className="font-mono text-sm font-bold text-primary">
                      Chapter {i + 1} · {c.title}
                    </p>
                    <h3 className="mt-2 text-2xl font-extrabold tracking-tight">{c.feature}</h3>
                    <p className="mt-3 leading-relaxed text-muted-foreground">{c.body}</p>
                  </div>
                  <img
                    src={sitePath(`/wordpress/chapter-${i + 1}.webp`)}
                    alt={c.alt}
                    width={1280}
                    height={720}
                    loading={i ? "lazy" : "eager"}
                    className="w-full rounded-2xl border border-border shadow-sm lg:col-span-3"
                  />
                </li>
              ))}
            </ol>
          </Container>
        </section>

        <section className="border-y border-border bg-muted/40 py-20">
          <Container className="max-w-3xl text-center">
            <SectionHeader
              eyebrow="On your own store"
              title="Install it, point it at Turing, reindex"
              description="The plugin indexes posts, pages, custom post types and WooCommerce products, provisions their fields as facets, and keeps the index in step as content changes. The answers, the copilot and the assistant use the language model configured in Turing, and stay hidden on a Turing that has none."
            />
            <div className="flex flex-wrap justify-center gap-3">
              <Button asChild size="lg">
                <a href={LINKS.wordpressDocs}>
                  Read the plugin's docs <ArrowRight className="size-4" />
                </a>
              </Button>
              <Button asChild size="lg" variant="outline">
                <a href={LINKS.turing} target="_blank" rel="noreferrer">
                  About Viglet Turing
                </a>
              </Button>
            </div>
          </Container>
        </section>

        <CallToAction />
      </main>
      <SiteFooter />
    </div>
  );
}
