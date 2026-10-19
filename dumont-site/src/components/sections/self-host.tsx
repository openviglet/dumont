import { Database, Gauge, Lock } from "lucide-react";
import { Container, SectionHeader } from "@/components/brand";
import { CopyCommand } from "@/components/copy-command";
import { CodeBlock } from "@/components/code-block";
import { DOCKER_RUN, LINKS } from "@/lib/site-content";

const FACTS = [
  {
    icon: Database,
    title: "One container, your database",
    description:
      "Spring Boot 4 on Java 21, every connector in the same JVM, an embedded queue. H2 out of the box for a trial, PostgreSQL when it matters.",
  },
  {
    icon: Lock,
    title: "Your sign-in",
    description:
      "Local users, groups and roles, Keycloak or an OAuth2 provider for the console; an API key for machine calls. Multi-tenant mode if you run it for several teams.",
  },
  {
    icon: Gauge,
    title: "Observable from day one",
    description:
      "Prometheus metrics on the management port (30131), a webhook for warnings, and an optional MongoDB or Redis store for the indexing log.",
  },
];

// The two-service shape from the installation guide, trimmed to what a first
// run needs: Turing for the index, Dumont pointed at it.
const COMPOSE = `services:
  dumont:
    image: ghcr.io/openviglet/dumont-ce:latest
    ports:
      - 30130:30130
    volumes:
      - ./store:/app/store
    environment:
      TURING_URL: http://turing:2700
      TURING_APIKEY: <your Turing API key>
  turing:
    image: ghcr.io/openviglet/turing-ce:latest
    ports:
      - 2700:2700`;

export function SelfHost() {
  return (
    <section id="self-host" className="py-20">
      <Container>
        <SectionHeader
          eyebrow="Run it"
          title={
            <>
              On your infrastructure,{" "}
              <span className="text-primary">next to your content</span>
            </>
          }
          description="Connectors need to reach the systems they read, which usually means inside your network. Dumont is one image you run there; there is no hosted tier."
        />

        <div className="grid items-start gap-8 lg:grid-cols-[1fr_1fr] [&>*]:min-w-0">
          <div className="space-y-4">
            {FACTS.map((f) => (
              <div key={f.title} className="flex gap-4 rounded-xl border border-border bg-card p-5">
                <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                  <f.icon className="size-5" />
                </div>
                <div className="min-w-0">
                  <h3 className="font-bold">{f.title}</h3>
                  <p className="mt-1 text-sm leading-relaxed text-muted-foreground">{f.description}</p>
                </div>
              </div>
            ))}
          </div>

          <div>
            <p className="text-sm font-semibold">Try it in one line:</p>
            <CopyCommand command={DOCKER_RUN} className="mt-2 border-border bg-card text-foreground" />
            <p className="mt-6 text-sm font-semibold">Or with Turing beside it:</p>
            <CodeBlock caption="docker-compose.yaml" code={COMPOSE} className="mt-2" />
            <p className="mt-3 text-xs leading-relaxed text-muted-foreground">
              Then open <code className="font-mono">http://localhost:30130</code>. Standalone
              jars, a Windows installer and an AppImage are described in the{" "}
              <a href={LINKS.install} className="font-semibold text-primary hover:underline">
                installation guide
              </a>
              .
            </p>
          </div>
        </div>
      </Container>
    </section>
  );
}
