const { serveHTTP } = require("stremio-addon-sdk");
const addonInterface = require("./addon");

serveHTTP(addonInterface, { port: process.env.PORT || 3000 });

console.log("Addon running on: http://localhost:3000/manifest.json");
