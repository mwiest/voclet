# Bundled font licenses

`app/src/main/res/font/` ships the app's typefaces so Voclet needs no Google Play
Services font provider at runtime. Full license texts live next to this file.

| Font | License | Upstream |
| --- | --- | --- |
| Nunito Sans (Regular, Bold) | OFL 1.1 | [google/fonts/ofl/nunitosans](https://github.com/google/fonts/tree/main/ofl/nunitosans) |
| Sniglet (Regular) | OFL 1.1 | [google/fonts/ofl/sniglet](https://github.com/google/fonts/tree/main/ofl/sniglet) |
| OpenMoji | CC BY-SA 4.0 | [openmoji.org](https://openmoji.org) |

Neither OFL declares a Reserved Font Name, so the instanced files below keep the
upstream family names.

## Regenerating the Nunito Sans statics

Upstream ships only a variable font, and the Google Fonts CSS API serves a
latin-only subset - useless for a language-agnostic vocabulary app. The statics
here are instanced from the full variable font (939 codepoints, incl. Cyrillic
and Vietnamese):

```bash
pip install fonttools
curl -sSLO "https://raw.githubusercontent.com/google/fonts/main/ofl/nunitosans/NunitoSans%5BYTLC,opsz,wdth,wght%5D.ttf"
fonttools varLib.instancer "NunitoSans[YTLC,opsz,wdth,wght].ttf" \
  wght=400 wdth=100 opsz=12 YTLC=500 --update-name-table -o nunito_sans_regular.ttf
fonttools varLib.instancer "NunitoSans[YTLC,opsz,wdth,wght].ttf" \
  wght=700 wdth=100 opsz=12 YTLC=500 --update-name-table -o nunito_sans_bold.ttf
```

`--update-name-table` writes "Nunito Sans Normal" as the family (the pinned
`wdth` leaks into the name); the checked-in files have name IDs 1/2/4/6 reset to
`Nunito Sans` / `Regular`|`Bold`.
