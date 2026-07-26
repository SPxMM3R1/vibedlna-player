const fs = require("fs");
const path = require("path");
const sharp = require("sharp");

async function main() {
    const root = path.resolve(__dirname, "..");
    const original = path.join(__dirname, "vibem3u-banner-source.png");
    const output = path.join(
        root,
        "app/src/main/res/drawable-nodpi/vibedlna_banner.png"
    );
    const fontPath = process.env.VIBEDLNA_FONT_PATH;
    if (!fontPath) throw new Error("Falta VIBEDLNA_FONT_PATH.");

    const font = fs.readFileSync(fontPath).toString("base64");
    const preservedOriginal = await sharp(original)
        .extract({left: 11, top: 0, width: 191, height: 180})
        .png()
        .toBuffer();
    const lettering = Buffer.from(`
        <svg width="320" height="180" xmlns="http://www.w3.org/2000/svg">
          <style>
            @font-face {
              font-family: Fredoka;
              src: url(data:font/ttf;base64,${font});
            }
          </style>
          <text x="192" y="107" font-family="Fredoka" font-size="44"
                font-weight="500" letter-spacing="-1"
                fill="#02c8fd">DLNA</text>
        </svg>
    `);

    await sharp({
        create: {
            width: 320,
            height: 180,
            channels: 4,
            background: {r: 0, g: 0, b: 0, alpha: 0}
        }
    })
        .composite([
            {input: lettering, left: 0, top: 0},
            {input: preservedOriginal, left: 0, top: 0}
        ])
        .png()
        .toFile(output);
}

main().catch(error => {
    process.stderr.write(`${error.stack}\n`);
    process.exitCode = 1;
});
