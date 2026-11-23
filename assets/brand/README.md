# The LinkHub mark

The Ecosystem Link: two interconnected loops representing mobile and desktop devices,
joining seamlessly around the central LinkHub core, on a Dark Navy tile with a
Royal Blue mesh glow.

| File | What it is |
|---|---|
| `icon.svg` | The full-color app icon: Dark Navy tile, mesh glow, royal blue hub, accent rim |
| `icon-flat.svg` | The same without the glow and highlights, for flat contexts |
| `icon-mono.svg` | The mark alone in white, for one-color uses (notifications, themed icons) |
| `icon-512.png` | `icon.svg` at 512 × 512 |

The apps draw the mark themselves from the same geometry:
`desktop/app/src/mark.rs` (the Windows icon, tray and notifications at every
size) and `android/app/src/main/res/drawable/ic_launcher_*.xml` and
`ic_notification.xml`.

Colors (LinkHub Blue palette, `docs/design/tokens.json`): tile `#111827`, glow
`#1E40AF`, phone loop `#F8FAFC`, PC loop `#DBEAFE`, central hub `#2563EB`, rim `#60A5FA`.
