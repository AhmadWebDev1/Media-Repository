const { addonBuilder, serveHTTP } = require("stremio-addon-sdk");
const axios = require("axios");
const cheerio = require("cheerio");

const BASE_URL = "https://www.faselhds.biz";

// إعدادات لتجاوز الحماية (User-Agent ضروري جداً)
const HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36",
    "Referer": BASE_URL
};

const manifest = {
    id: "org.faselhd.unofficial",
    version: "1.0.2",
    name: "FaselHD Pro",
    description: "Watch movies from FaselHD directly in Stremio",
    resources: ["catalog", "meta", "stream"],
    types: ["movie", "series"],
    idPrefixes: ["fasel:"],
    catalogs: [
        {
            type: "movie",
            id: "faselhd_search",
            name: "FaselHD Search",
            extra: [{ name: "search", isRequired: true }]
        }
    ]
};

const builder = new addonBuilder(manifest);

// 1. البحث (Catalog Handler)
builder.defineCatalogHandler(async ({ type, id, extra }) => {
    let results = [];
    if (extra.search) {
        // رابط البحث بناءً على ملف HTML الأول
        const searchUrl = `${BASE_URL}/?s=${encodeURIComponent(extra.search)}`;
        try {
            const { data } = await axios.get(searchUrl, { headers: HEADERS });
            const $ = cheerio.load(data);

            $("div.postDiv").each((i, element) => {
                const link = $(element).find("a").attr("href");
                const title = $(element).find(".postInner .h1").text().trim();
                // محاولة جلب الصورة من src أو data-src (للتحميل الكسول)
                const poster = $(element).find(".imgdiv-class img").attr("src") || $(element).find(".imgdiv-class img").attr("data-src");
                
                if (link && title) {
                    // نستخدم جزء الرابط كـ ID (مثال: hindi/movie-name)
                    const pathId = link.replace(BASE_URL, "").replace(/^\//, "");
                    results.push({
                        id: `fasel:${pathId}`,
                        type: type,
                        name: title,
                        poster: poster
                    });
                }
            });
        } catch (error) {
            console.error("Search Error:", error.message);
        }
    }
    return { metas: results };
});

// 2. التفاصيل (Meta Handler)
builder.defineMetaHandler(async ({ type, id }) => {
    const urlPath = id.replace("fasel:", "");
    const url = `${BASE_URL}/${urlPath}`;

    try {
        const { data } = await axios.get(url, { headers: HEADERS });
        const $ = cheerio.load(data);

        const title = $(".singleInfo .title").text().trim();
        const description = $(".singleDesc p").text().trim();
        const poster = $(".posterImg img").attr("src");
        
        // استخراج صورة الخلفية من الستايل
        let background = $(".singlePage").css("background-image");
        if (background) {
            background = background.replace(/^url\(['"]?/, '').replace(/['"]?\)$/, '');
        }

        return {
            meta: {
                id: id,
                type: type,
                name: title,
                poster: poster,
                background: background,
                description: description,
            }
        };
    } catch (error) {
        console.error("Meta Error:", error.message);
        return { meta: {} };
    }
});

// 3. التشغيل (Stream Handler) - الجزء الأهم
builder.defineStreamHandler(async ({ type, id }) => {
    const urlPath = id.replace("fasel:", "");
    const pageUrl = `${BASE_URL}/${urlPath}`;
    
    try {
        console.log("Fetching page:", pageUrl);
        // الخطوة 1: جلب صفحة الفيلم لاستخراج Iframe
        const pageResponse = await axios.get(pageUrl, { headers: HEADERS });
        const $ = cheerio.load(pageResponse.data);

        // البحث عن Iframe الذي اسمه player_iframe (موجود في ملفك الثاني)
        const iframeSrc = $("iframe[name='player_iframe']").attr("src");

        if (!iframeSrc) {
            console.log("No iframe found");
            return { streams: [] };
        }

        console.log("Fetching player:", iframeSrc);
        
        // الخطوة 2: جلب محتوى الـ Iframe (المشغل)
        const iframeResponse = await axios.get(iframeSrc, { headers: HEADERS });
        const $$ = cheerio.load(iframeResponse.data);

        let streams = [];

        // الخطوة 3: استخراج الروابط من الأزرار (موجود في ملف FaselHD Player.html)
        // الأزرار تحمل الكلاس "hd_btn" ولديها خاصية "data-url"
        $$("button.hd_btn").each((i, btn) => {
            const streamUrl = $$(btn).attr("data-url");
            let qualityText = $$(btn).text().trim(); 

            // تحسين اسم الجودة
            if (qualityText === "auto") qualityText = "Auto (M3U8)";
            else qualityText += " HD";

            if (streamUrl) {
                streams.push({
                    name: "FaselHD",
                    title: qualityText,
                    url: streamUrl
                });
            }
        });

        // ترتيب الجودات (1080p في الأعلى)
        streams.sort((a, b) => {
            const getVal = (s) => s.title.includes("1080") ? 1080 : (s.title.includes("720") ? 720 : 0);
            return getVal(b) - getVal(a);
        });

        if (streams.length === 0) {
            // إذا فشل الاستخراج، نضع رابطاً لفتح الصفحة في المتصفح كحل بديل
            streams.push({
                title: "Open Website (Extraction Failed)",
                externalUrl: pageUrl
            });
        }

        return { streams: streams };

    } catch (error) {
        console.error("Stream Error:", error.message);
        return { streams: [] };
    }
});

// تشغيل السيرفر
serveHTTP(builder.getInterface(), { port: 7000 });
console.log("Addon is running at http://127.0.0.1:7000/manifest.json");