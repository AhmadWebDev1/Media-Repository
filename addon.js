const { addonBuilder } = require("stremio-addon-sdk");
const cloudscraper = require("cloudscraper");
const cheerio = require("cheerio");

const mainUrl = "https://ak.sv";

const manifest = {
    id: "org.akwam.stremio",
    version: "1.0.0",
    name: "Akwam",
    description: "Akwam streaming addon with Cloudflare bypass",
    types: ["movie", "series"],
    catalogs: [
        { type: "movie", id: "akwam_movies", name: "Akwam Movies", extra: [{ name: "search" }] },
        { type: "series", id: "akwam_series", name: "Akwam Series", extra: [{ name: "search" }] }
    ],
    resources: ["catalog", "meta", "stream"]
};

const builder = new addonBuilder(manifest);

// --------------------------- Helper: Cloudflare Bypass ---------------------------
async function fetchHTML(url) {
    const html = await cloudscraper.get(url, {
        headers: {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
        }
    });
    return cheerio.load(html);
}

function cleanTitle(t) {
    return t
        .replace(/الفيلم|فيلم|مترجم|مترجمة|مسلسل|مشاهدة|حصرياًً|كامل|اونلاين|اون لاين|والاخيرة/g, "")
        .replace(/انمي|برنامج/g, "")
        .replace(/الحلقة\s+(\d+)/g, "")
        .replace(/حلقة\s+(\d+)/g, "")
        .trim();
}

// --------------------------- Catalog ---------------------------
builder.defineCatalogHandler(async (args) => {
    let url = "";

    if (args.id === "akwam_movies") url = `${mainUrl}/movies`;
    if (args.id === "akwam_series") url = `${mainUrl}/series`;

    if (args.extra && args.extra.search) {
        url = `${mainUrl}/search?q=${args.extra.search.replace(/ /g, "+")}`;
    }

    const $ = await fetchHTML(url);
    const items = [];

    $(".page-archive .entry-box, .page-search .entry-box").each((i, el) => {
        const elem = $(el);
        const link = elem.find("a").attr("href");
        const title = cleanTitle(elem.find(".entry-title a").text());
        const poster = elem.find("img[data-src]").attr("data-src");

        if (link && title) {
            items.push({
                id: link,
                type: link.includes("series") ? "series" : "movie",
                name: title,
                poster
            });
        }
    });

    return { metas: items };
});

// --------------------------- Meta ---------------------------
builder.defineMetaHandler(async (args) => {
    const id = args.id;
    const $ = await fetchHTML(id);

    const title = cleanTitle($("h1.entry-title").text());
    const poster = $("picture img").attr("src");
    const plot = $(".text-white p").text();
    const isMovie = $(".qualities").length > 0;

    let episodes = [];

    if (!isMovie) {
        $("#series-episodes .bg-primary2").each((i, el) => {
            const e = $(el);
            const epLink = e.find("a").attr("href");
            const epTitle = e.find(".text-white a").text();

            episodes.push({
                id: epLink,
                title: epTitle,
                season: 1,
                episode: i + 1
            });
        });
    }

    return {
        meta: {
            id,
            type: isMovie ? "movie" : "series",
            name: title,
            poster,
            description: plot,
            videos: episodes
        }
    };
});

// --------------------------- Stream ---------------------------
builder.defineStreamHandler(async (args) => {
    const id = args.id;

    const $page = await fetchHTML(id);
    const linkShow = $page(".link-show").attr("href");

    const watchID = /\/watch\/(\d+)/.exec(linkShow)?.[1];
    const baseID = /\/(?:movie|series|episode)\/(\d+)\//.exec(id)?.[1];

    if (!watchID || !baseID) return { streams: [] };

    const $ = await fetchHTML(`${mainUrl}/watch/${watchID}/${baseID}`);
    const streams = [];

    $("video source").each((i, el) => {
        const url = $(el).attr("src");
        const size = $(el).attr("size") || "Auto";

        streams.push({
            title: `Akwam ${size}`,
            url,
            behaviorHints: { notWebReady: false }
        });
    });

    return { streams };
});

module.exports = builder.getInterface();
