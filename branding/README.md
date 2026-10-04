# Branding

Sources for Encarté's identity and store graphics. Spec: `docs/superpowers/specs/2026-10-04-encarte-visual-identity-design.md`.

Palette (icon, empty-state illustration and store graphics only; the app UI keeps Material You):

| Name | Hex |
|---|---|
| Cream | `#FFF6E8` |
| Teal | `#2E8C83` |
| Mustard | `#F2A93B` |
| Navy | `#1D2440` |
| White | `#FFFFFF` |

## Font

`app/src/main/res/font/nunito.ttf` is the Nunito variable font (`Nunito[wght].ttf`, weights 200–1000), taken unmodified
from `github.com/google/fonts`, commit `8b0a1d0f5983c89bc2b93f1b5fb55f9e252744b5`, folder `ofl/nunito`
(SHA-256 `bb55a5ca5c2042335b3991af27c4d0705d0ef41cac6164ac737fd8f2a1e85207`).
It is licensed under the SIL Open Font License 1.1: see `fonts/Nunito-OFL.txt`.
The store templates load the same file, so the app and its graphics never drift apart.
