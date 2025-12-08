const { addonBuilder, serveHTTP } = require("stremio-addon-sdk");
const axios = require("axios");
const cheerio = require("cheerio");
const jsdom = require("jsdom");
const { JSDOM, VirtualConsole } = jsdom;

const BASE_URL = "https://www.faselhds.biz";

const HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36",
    "Referer": BASE_URL
};

const manifest = {
    id: "org.faselhd.pro",
    version: "1.2.0",
    name: "FaselHD Pro",
    description: "Movies & Series from FaselHD (Search Fixed + All Seasons)",
    resources: ["catalog", "meta", "stream"],
    types: ["movie", "series"],
    idPrefixes: ["fasel:"],
    catalogs: [
        {
            type: "movie",
            id: "faselhd_search",
            name: "FaselHD Search",
            // إضافة خاصية "skip" لدعم تصفح المزيد من النتائج
            extra: [{ name: "search", isRequired: true }, { name: "skip" }]
        },
        {
            type: "series",
            id: "faselhd_series",
            name: "FaselHD Series",
            extra: [{ name: "search", isRequired: true }, { name: "skip" }]
        }
    ]
};

const builder = new addonBuilder(manifest);

// 1. معالج البحث (Catalog) - تم التعديل لدعم تعدد الصفحات
builder.defineCatalogHandler(async ({ type, id, extra }) => {
    let results = [];
    if (extra.search) {
        try {
            // حساب رقم الصفحة بناءً على عدد النتائج التي تم تخطيها
            // نفترض أن كل صفحة تحتوي على 20 عنصر تقريباً
            const page = Math.floor((extra.skip || 0) / 20) + 1;
        
            let searchUrl;
            if (page === 1) {
                searchUrl = `${BASE_URL}/?s=${encodeURIComponent(extra.search.replace(" ","+"))}`;
            } else {
                searchUrl = `${BASE_URL}/page/${page}?s=${encodeURIComponent(extra.search.replace(" ","+"))}`;
            }

            console.log(`🔍 Searching: ${extra.search} | Page: ${page}`);
            
            const { data } = await axios.get(searchUrl, { headers: HEADERS });
            const $ = cheerio.load(data);

            $("div.postDiv").each((i, element) => {
                const linkAnchor = $(element).find("a").first();
                const link = linkAnchor.attr("href");
                const title = $(element).find(".postInner .h1").text().replace(new RegExp("الموسم الأول|برنامج|فيلم|مترجم|اون لاين|مسلسل|مشاهدة|انمي|أنمي", "g"), "").trim();
                const poster = $(element).find(".imgdiv-class img").attr("data-src") || $(element).find(".imgdiv-class img").attr("src");

                if (link && title) {
                    const isSeries = title.includes("مسلسل") || title.includes("موسم") || link.includes("/series/") || link.includes("/seasons/");
                    
                    // فلترة النتائج حسب النوع (فيلم أو مسلسل)
                    if ((type === "series" && isSeries) || (type === "movie" && !isSeries)) {
                        const urlObj = new URL(link);
                        const pathId = urlObj.pathname.replace(/^\//, ""); 

                        results.push({
                            id: `fasel:${pathId}`,
                            type: type,
                            name: title,
                            poster: poster,
                            description: title
                        });
                    }
                }
            });
        } catch (error) {
            // تجاهل الخطأ إذا كانت الصفحة غير موجودة (نهاية النتائج)
            if (error.response && error.response.status === 404) {
                console.log("End of search results.");
            } else {
                console.error("Search Error:", error.message);
            }
        }
    }
    return { metas: results };
});

// 2. معالج التفاصيل (Meta) - (يدعم جلب جميع المواسم)
builder.defineMetaHandler(async ({ type, id }) => {
    const urlPath = id.replace("fasel:", "");
    const cleanUrl = urlPath.split(":")[0];
    const url = `${BASE_URL}/${cleanUrl}`;

    console.log(`ℹ️ Fetching Meta: ${url}`);

    try {
        const { data } = await axios.get(url, { headers: HEADERS });
        const $ = cheerio.load(data);

        const title = $(".singleInfo .title").text().replace(new RegExp("الموسم الأول|برنامج|فيلم|مترجم|اون لاين|مسلسل|مشاهدة|انمي|أنمي", "g"), "").trim();
        const description = $(".singleDesc p").text().trim();
        const poster = $(".posterImg img").attr("src");
        
        let background = null;
        const bgStyle = $(".singlePage").css("background-image");
        if (bgStyle && bgStyle.includes("url")) {
            background = bgStyle.replace(/^url\(['"]?/, '').replace(/['"]?\)$/, '');
        }

        let metaObj = {
            id: id,
            type: type,
            name: title,
            poster: poster,
            background: background,
            description: description,
        };

        if (type === "series") {
            let seasonsList = [];
            const seasonDivs = $("#seasonList .seasonDiv");

            if (seasonDivs.length > 0) {
                seasonDivs.each((i, el) => {
                    let seasonLink = $(el).find("a").attr("href");
                    if (!seasonLink) {
                        const onclick = $(el).attr("onclick");
                        if (onclick) {
                            const match = onclick.match(/href\s*=\s*['"]([^'"]+)['"]/);
                            if (match) seasonLink = match[1];
                        }
                    }
                    const seasonTitle = $(el).find(".title").text().trim();
                    const seasonMatch = seasonTitle.match(/(\d+)/);
                    const seasonNumber = seasonMatch ? parseInt(seasonMatch[1]) : (i + 1);

                    if (seasonLink) {
                        if (seasonLink.startsWith("/")) seasonLink = BASE_URL + seasonLink;
                        seasonsList.push({ num: seasonNumber, url: seasonLink });
                    }
                });
            } else {
                seasonsList.push({ num: 1, url: url });
            }

            const seasonsPromises = seasonsList.map(async (season) => {
                try {
                    let $$ = $;
                    if (season.url !== url && !season.url.includes(cleanUrl)) {
                        const seasonRes = await axios.get(season.url, { headers: HEADERS });
                        $$ = cheerio.load(seasonRes.data);
                    }

                    let seasonEpisodes = [];
                    $$("#epAll a").each((j, el) => {
                        const epLink = $$(el).attr("href");
                        const epTitle = $$(el).text().trim();
                        const epNumMatch = epTitle.match(/(\d+)/);
                        const epNum = epNumMatch ? parseInt(epNumMatch[1]) : (j + 1);

                        if (epLink) {
                            const epUrlObj = new URL(epLink);
                            const epPathId = epUrlObj.pathname.replace(/^\//, "");
                            
                            seasonEpisodes.push({
                                id: `fasel:${epPathId}`,
                                title: epTitle,
                                season: season.num,
                                episode: epNum,
                                released: new Date().toISOString()
                            });
                        }
                    });
                    return seasonEpisodes;
                } catch (err) {
                    return [];
                }
            });

            const allEpisodesArrays = await Promise.all(seasonsPromises);
            let allVideos = allEpisodesArrays.flat();
            allVideos.sort((a, b) => (a.season - b.season) || (a.episode - b.episode));
            metaObj.videos = allVideos;
        }

        return { meta: metaObj };

    } catch (error) {
        console.error("Meta Error:", error.message);
        return { meta: { id: id, type: type, name: "Error Loading Metadata" } }; 
    }
});

// 3. معالج الروابط (Stream) - (يدعم JSDOM)
builder.defineStreamHandler(async ({ type, id }) => {
    const urlPath = id.replace("fasel:", "");
    const pageUrl = `${BASE_URL}/${urlPath}`;
    
    try {
        console.log(`[Stream] Fetching Page: ${pageUrl}`);
        const pageResponse = await axios.get(pageUrl, { headers: HEADERS });
        const $ = cheerio.load(pageResponse.data);

        let iframeSrc = $("iframe[name='player_iframe']").attr("src");
        if (!iframeSrc) iframeSrc = $("iframe").first().attr("src");

        if (!iframeSrc) {
            console.log("❌ No iframe found.");
            return { streams: [] };
        }

        const iframeResponse = await axios.get(iframeSrc, { headers: HEADERS });
        let htmlContent = iframeResponse.data;

        const $iframe = cheerio.load(htmlContent);
        $iframe('script[src]').remove();
        htmlContent = $iframe.html();

        console.log(`[Stream] Decrypting with JSDOM...`);
        
        const virtualConsole = new VirtualConsole();
        virtualConsole.on("error", () => {}); 
        const dom = new JSDOM(htmlContent, {
            url: iframeSrc,
            runScripts: "dangerously",
            resources: "usable",
            virtualConsole,
            beforeParse(window) {
                window.TextEncoder = TextEncoder;
                window.TextDecoder = TextDecoder;
                window.Uint8Array = Uint8Array;
                window.fetch = fetch;
                window.jwplayer = () => ({
                    setup: () => {}, on: () => {}, load: () => {},
                    play: () => {}, seek: () => {}, getPosition: () => 0
                });
                window.jwplayer.key = "fake-key";
                window.open = () => {}; 
                window.indexedDB = { open: () => ({}) };
            }
        });

        await new Promise(resolve => setTimeout(resolve, 3000));

        const { window } = dom;
        let streams = [];

        if (window.hlsPlaylist && window.hlsPlaylist.file) {
            streams.push({
                name: "FaselHD",
                title: "⚡ Auto (Master)",
                url: window.hlsPlaylist.file
            });
        }

        const buttons = window.document.querySelectorAll('.hd_btn');
        buttons.forEach(btn => {
            const quality = btn.textContent.trim(); 
            const streamUrl = btn.getAttribute('data-url');
            if (streamUrl) {
                streams.push({
                    name: "FaselHD",
                    title: `${quality} HD`,
                    url: streamUrl
                });
            }
        });

        window.close();

        if (streams.length === 0) {
            return { streams: [{ title: "Open in browser", externalUrl: iframeSrc }] };
        }

        streams.sort((a, b) => {
            const val = s => s.title.includes("1080") ? 100 : s.title.includes("720") ? 50 : 0;
            return val(b) - val(a);
        });

        return { streams: streams };

    } catch (error) {
        console.error("Stream Error:", error.message);
        return { streams: [] };
    }
});

const port = process.env.PORT || 7000;
serveHTTP(builder.getInterface(), { port: port });
console.log(`Addon running on http://127.0.0.1:${port}/manifest.json`);