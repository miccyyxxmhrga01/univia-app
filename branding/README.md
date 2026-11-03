# Brand sources

- `bisu-logo.ico`: original BISU seal supplied with this task, preserved here because Android drawable resources do not support hyphenated ICO filenames.
- `../app/src/main/res/drawable/bisu_logo.png`: lossless conversion of that seal, 256 × 256.
- `../app/src/main/res/drawable/univia_logo.png`: existing Univia App application artwork.
- `../app/src/main/res/drawable/univia_launcher_foreground.png` and `mipmap-*/ic_launcher*.webp`: launcher variants derived from the existing Univia App artwork, with Android safe-zone padding.
- `login-design-reference.png`: the user's supplied login design, preserved unchanged.
- `login-glass-detail-reference.png`: the user's follow-up crop emphasizing the campus seal in the backdrop and the frosted card.
- `login-bg.jpg`: original campus entrance photo supplied in the project; `drawable/login_bg.jpg` is the byte-identical Android resource used by the login background and its frosted glass layer. Android resource names require underscores instead of hyphens. Decoding disables density scaling to keep blur work on the original small bitmap.
- `fonts/`: attribution and the SIL Open Font License for the bundled Poppins files from the official Google Fonts repository.

All artwork comes from the project or the user; no AI-generated assets were introduced.
