import { useState } from "react";
import { ArrowUpRight, Play, ShoppingBag } from "lucide-react";
import { Container } from "@/components/brand";
import { LINKS, WORDPRESS_TILE } from "@/lib/site-content";

/**
 * The WordPress plugin, on the landing and connectors pages. A tile of its own rather than a
 * connector card: the plugin is part of Viglet Turing, runs inside WordPress and sends to
 * Viglet Turing directly, so the queue, safe finish and audit the connector cards promise are
 * not its to claim. Its page lives on turing.viglet.org; this tile points there.
 */
export function WordPressTile() {
  const { title, body, partOf, cta } = WORDPRESS_TILE;
  return (
    <section className="pb-20">
      <Container>
        <div className="grid items-center gap-6 rounded-2xl border border-border bg-card p-6 lg:grid-cols-5 [&>*]:min-w-0">
          <div className="lg:col-span-2">
            <div className="flex size-11 items-center justify-center rounded-xl bg-gradient-to-br from-[#0f766e] to-[#0e7490] text-white shadow-sm">
              <ShoppingBag className="size-5" />
            </div>
            <h3 className="mt-4 text-lg font-bold">{title}</h3>
            <p className="mt-1 text-sm leading-relaxed text-muted-foreground">{body}</p>
            <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
              {partOf}{" "}
              <a
                href={LINKS.wordpress}
                className="inline-flex items-center gap-1 font-semibold text-primary hover:underline"
              >
                {cta}
                <ArrowUpRight className="size-3.5" />
              </a>
            </p>
          </div>
          <div className="lg:col-span-3">
            <VideoFacade />
          </div>
        </div>
      </Container>
    </section>
  );
}

/**
 * The plugin's demo video as a click-to-load facade: the poster is a local image and no
 * request reaches YouTube until the visitor presses play, so no third-party cookie loads
 * before consent. The embed is youtube-nocookie.
 */
function VideoFacade() {
  const [playing, setPlaying] = useState(false);
  const { id, title, poster } = WORDPRESS_TILE.video;

  return (
    <div>
      <div className="relative aspect-video overflow-hidden rounded-xl border border-border bg-black shadow-sm">
        {playing ? (
          <iframe
            className="absolute inset-0 size-full"
            src={`https://www.youtube-nocookie.com/embed/${id}?autoplay=1&rel=0`}
            title={title}
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
            allowFullScreen
          />
        ) : (
          <button
            type="button"
            onClick={() => setPlaying(true)}
            className="group absolute inset-0 size-full"
            aria-label={`Play the video: ${title}`}
          >
            <img
              src={poster}
              alt=""
              width={1280}
              height={720}
              loading="lazy"
              className="size-full object-cover transition-opacity group-hover:opacity-90"
            />
            <span className="absolute top-1/2 left-1/2 flex size-14 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-lg transition-transform group-hover:scale-110">
              <Play className="ml-1 size-6" />
            </span>
          </button>
        )}
      </div>
      <p className="mt-2 text-xs text-muted-foreground">
        In Portuguese. Plays from YouTube (youtube-nocookie.com) after you press play.
      </p>
    </div>
  );
}
