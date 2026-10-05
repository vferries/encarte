# Encarté — Landing Page Design Spec

- **Date:** 2026-10-05
- **Status:** Draft, pending review
- **Parent specs:** `2026-10-04-encarte-release-design.md` (§8 Site), `2026-10-04-encarte-visual-identity-design.md`
- **Mockups:** `.superpowers/brainstorm/3257109-1791182470/content/page-structure.html` (local, git-ignored)

## 1. Context and goals

`encarte.fr` is live on GitHub Pages, with a minimal home page and a privacy policy. This spec turns the home page into a real landing page. It should make everyday users want to install Encarté, and it should give privacy- and FOSS-minded visitors the facts they look for.

Decisions taken with the user (2026-10-05):

| Topic | Decision |
|---|---|
| Audience | Both: benefit first in the hero and features, then a dedicated privacy and free-software section |
| Install buttons | Official Google Play and F-Droid badges |
| Hero | Centered: icon, headline, badges, then three phones fanned out |
| Fan screens | Card display (left), card list (center), empty home screen (right) |
| Sections | Hero, Features, Privacy & free software, FAQ, closing call to action, footer |
| Support the project | Not on the page yet. A later topic; the page leaves a place for it |

Success criteria:

1. The home page (FR at `/`, EN at `/en/`) has the sections in §3, in that order, with the copy in §5.
2. Badges link to the store listings, and only for listings that exist (§4.1).
3. The site still has no JavaScript, loads nothing from another origin, and has no broken links (§6).
4. The page reads well:
   - at 390 px and 1280 px wide;
   - in light and dark themes;
   - with text contrast of at least 4.5:1.

## 2. Scope

**In scope:**
- a new `site/index.html` and `site/en/index.html`;
- a rewritten `site/style.css`;
- the shared header and footer on the privacy pages, whose content does not change;
- the store badge assets;
- web-sized screenshots;
- Open Graph tags;
- the `build-site.sh` changes in §6.

**Out of scope:**
- the "Support the project" section and any donation link (a later spec);
- new captures;
- analytics of any kind;
- a static site generator;
- changes to the app.

## 3. Page structure (home page, both locales)

1. **Header**
   - Brand (icon and "Encarté") on the left.
   - On the right: links to the Features, Privacy and FAQ anchors, plus the language switch.
   - On phones, the anchor links are hidden and only the brand and language switch remain.
2. **Hero**, centered:
   - the icon (84 px, rounded, soft shadow);
   - the headline (H1) and subtitle;
   - the store area (§4.1);
   - the phone fan (§4.2).
3. **Features** (`id="fonctionnalites"` / `id="features"`): a title, 4 feature cards with icons, then a row of barcode-format chips.
4. **Privacy & free software** (`id="vie-privee"` / `id="privacy"`): a navy band with a title, an intro line, 4 cards, and a link to the privacy policy.
5. **FAQ** (`id="faq"`): a title, then 6 questions as native `<details>`/`<summary>`. The first one is open.
6. **Closing call to action:** a title and the store area again.
7. **Footer:**
   - links: privacy policy, source code, other language;
   - the legal notice: publisher Vincent Ferries, contact through the GitHub issue tracker, host GitHub, Inc. with its postal address, "no cookies, no trackers";
   - the Google Play trademark line (§4.1).

A future "Support the project" section goes between FAQ (5) and the closing call to action (6). Nothing is rendered for it now.

The privacy pages (`privacy.html`, `en/privacy.html`) get the same header and footer. The header's anchor links point to the home page (for example `./#faq`). Their content does not change.

## 4. Components

### 4.1 Store area

**Badges**
- Official badges, FR on French pages and EN on English pages:
  - Google Play: "Disponible sur Google Play" / "Get it on Google Play";
  - F-Droid: "Disponible sur F-Droid" / "Get it on F-Droid".
- Stored under `site/img/badges/` and served by the site. They are never modified: no recoloring, cropping or added text. They are shown at least at the minimum size their guidelines set.
- Implementation fetches them from the official sources and checks each set of terms, recording source URLs and terms in `branding/README.md`:
  - Google's badge guidelines;
  - F-Droid's badge page and its license.

  If a term conflicts with this spec, implementation stops and reports.
- A badge is shown **only when its listing is published**. A listing that does not exist yet shows a plain-text line in its place ("Bientôt sur F-Droid" / "Coming soon to F-Droid"), never a dimmed badge.
- At the time of writing, the Play listing is in review and F-Droid inclusion is pending. Implementation checks each listing URL (an HTTP 200 on a public page) and shows a badge only if it answers. Switching a listing from text to badge later is a one-line change, which `docs/release-checklist.md` mentions.
- Links:
  - `https://play.google.com/store/apps/details?id=io.github.vferries.encarte`
  - `https://f-droid.org/packages/io.github.vferries.encarte/`
  - They are plain `<a href>` links, whose purpose is to leave the site.

**Trademark notice:** when the Google Play badge is shown, the footer carries the line "Google Play et le logo Google Play sont des marques de Google LLC." / "Google Play and the Google Play logo are trademarks of Google LLC.". Add F-Droid's attribution line if its badge terms require one.

### 4.2 Phone fan

- Three phones side by side, in a flex row with `align-items: flex-start` so that the side phones keep their own height.
  - The side phones are narrower and rotated by −6° and 6°, then moved down slightly.
  - The center phone is upright and in front.
- The frame is a CSS rounded rectangle (`#111111`, radius proportional to width). The image is the screenshot with a matching inner radius.
- The fan is cut off at the bottom of the hero by `overflow: hidden` on the fan container. Sizes use `clamp()`, so on phones the fan scales down and all three stay visible.
- **Images:** `site/img/<fr|en>/display.png`, `list.png` and `empty.png`.
  - They are derived from `branding/screenshots/raw/<locale>/2.png`, `1.png` and `5.png`, resized to 600 px wide by `branding/render.sh`.
  - They are committed, so the deploy needs no ImageMagick.
  - The `<img>` tags carry `width`/`height`, to avoid layout shift, plus `loading="lazy"` on the side phones.
  - Alt text: "Une carte affichée en caisse", "La liste des cartes" and "L'écran d'accueil d'Encarté" / "A card shown at checkout", "The card list" and "Encarté's home screen".

### 4.3 Feature and privacy cards

- **Feature cards** are white cards (dark theme: `#262E4F`) with a 34 px colored square holding an inline SVG icon. The icons are Material Symbols paths (Apache-2.0, already credited in the app). They are decorative, so `aria-hidden="true"`. The icon square colors are, in order: Teal, Mustard, Navy and `#D32F2F`.
- **Privacy cards** have no icons. They use `#262E4F` on the navy band.

### 4.4 FAQ

Native `<details>`/`<summary>`, with no script. Each summary is a question and a text marker shows the open or closed state.

## 5. Copy

Lines separated by " / " are FR / EN. "—" separates a card title from its text.

**Meta**
- Title: "Encarté — vos cartes de fidélité, hors-ligne" / "Encarté — your loyalty cards, offline"
- Description: "Encarté range vos cartes de fidélité sur votre téléphone et affiche leur code-barres en caisse. Hors-ligne, sans compte, logiciel libre." / "Encarté keeps your loyalty cards on your phone and shows their barcode at checkout. Offline, no account, open source."

**Header links:** "Fonctionnalités · Vie privée · FAQ · English" / "Features · Privacy · FAQ · Français"

**Hero**
- H1: "Vos cartes de fidélité. Rien d'autre." / "Your loyalty cards. Nothing else."
- Subtitle: "Toutes vos cartes dans votre téléphone, prêtes en caisse en une seconde. Sans compte, sans pub, sans Internet." / "All your cards on your phone, ready at checkout in a second. No account, no ads, no internet."

**Features:** "Simple, rapide, en caisse" / "Simple, fast, at the checkout"
- Toutes vos cartes au même endroit — Favorites en haut, recherche instantanée, tri par nom ou par usage récent. / All your cards in one place — Favorites on top, instant search, sort by name or recent use.
- Prête en caisse — Code-barres en grand, luminosité au maximum, écran qui reste allumé. / Ready at checkout — Big barcode, full brightness, the screen stays on.
- Ajoutez une carte en un instant — Scannez-la avec l'appareil photo ou depuis une capture d'écran, ou tapez son numéro. / Add a card in seconds — Scan it with the camera or from a screenshot, or type its number.
- Recto, verso, notes — Gardez une photo de chaque face et une note pour les infos utiles. / Front, back, notes — Keep a photo of each side and a note for useful details.
- Chips, the same in both locales: QR code · EAN-13 / EAN-8 · UPC-A / UPC-E · Code 128 / 39 / 93 · Codabar · ITF · Aztec · Data Matrix · PDF417

**Privacy & free software:** "Vos cartes restent chez vous" / "Your cards stay with you"
- Intro: "Pour celles et ceux qui ne veulent pas qu'une app de cartes de fidélité en sache plus que nécessaire." / "For anyone who doesn't want a loyalty-card app to know more than it should."
- Aucun accès à Internet — L'app ne demande pas la permission réseau : elle ne pourrait rien envoyer, même si elle le voulait. / No internet access — The app doesn't request network permission: it couldn't send anything even if it wanted to.
- Ni compte, ni pub, ni pistage — Aucun outil de publicité ou de statistiques. Vous n'avez rien à créer. / No account, no ads, no tracking — No advertising or analytics tools. Nothing to sign up for.
- Compatible Catima — Sauvegardes au format Catima, chiffrées si vous le voulez : changez d'app quand vous voulez. / Catima-compatible — Backups in Catima's format, encrypted if you like: switch apps whenever you want.
- Logiciel libre — Code source ouvert sous licence GPL-3.0 ou ultérieure, sur GitHub. / Open source — Source code under GPL-3.0-or-later, on GitHub.
- Link: "Lire la politique de confidentialité" / "Read the privacy policy"

**FAQ:** "Questions fréquentes" / "Frequently asked questions"

1. **"C'est vraiment gratuit ?" / "Is it really free?"**
   - FR: Oui. Pas de publicité, pas d'achat intégré, pas d'abonnement. Encarté est un logiciel libre.
   - EN: Yes. No ads, no in-app purchases, no subscription. Encarté is free software.
2. **"Comment récupérer mes cartes sur un nouveau téléphone ?" / "How do I move my cards to a new phone?"**
   - FR: Dans les réglages, exportez une sauvegarde (avec un mot de passe pour la chiffrer), copiez le fichier sur le nouveau téléphone et importez-le. Le transfert direct d'Android d'un téléphone à l'autre peut aussi copier vos cartes.
   - EN: In the settings, export a backup (add a password to encrypt it), copy the file to the new phone and import it there. Android's direct phone-to-phone transfer can also copy your cards.
3. **"Je viens de Catima, comment faire ?" / "I use Catima. How do I switch?"**
   - FR: Exportez vos cartes depuis Catima, puis importez le fichier dans Encarté (Réglages → Importer des cartes). Les cartes et leurs photos sont reprises. L'inverse marche aussi.
   - EN: Export your cards from Catima, then import the file in Encarté (Settings → Import cards). Cards and their photos come along. It works the other way round too.
4. **"Et sur iPhone ?" / "What about iPhone?"**
   - FR: Encarté n'existe que sur Android pour l'instant.
   - EN: Encarté is Android-only for now.
5. **"Pourquoi je ne vois pas les logos des enseignes ?" / "Why don't I see store logos?"**
   - FR: Les logos appartiennent aux enseignes : les intégrer poserait des problèmes de droits et rendrait l'app non libre. Les enseignes connues reçoivent leur couleur, et vous pouvez ajouter une photo de la vraie carte.
   - EN: Logos belong to the stores: bundling them would raise rights issues and make the app non-free. Well-known stores get their color, and you can add a photo of the real card.
6. **"Et si je perds mon téléphone ?" / "What if I lose my phone?"**
   - FR: Vos cartes ne sont que sur votre téléphone : exportez une sauvegarde de temps en temps. Le verrouillage facultatif protège l'app par empreinte, visage ou code de l'appareil.
   - EN: Your cards live only on your phone: export a backup now and then. The optional app lock protects the app with your fingerprint, face or screen lock.

**Closing call to action:** "Prêt à alléger votre portefeuille ?" / "Ready to lighten your wallet?"

**Footer**
- "Confidentialité · Code source · English" / "Privacy · Source code · Français"
- Legal notice: as on today's pages, plus the trademark line from §4.1.

## 6. Constraints and build

**Inherited from the release spec §8:**
- no JavaScript;
- nothing loaded from another origin;
- Nunito served by the site;
- relative asset paths;
- `CNAME` written by the build.

**Own-origin absolute URLs:** Open Graph and canonical tags need them:
- `<link rel="canonical">`;
- `og:url`;
- `og:image`;
- `hreflang` alternates.

`scripts/build-site.sh`'s external-resource guard therefore treats `https://encarte.fr/...` as same-origin. It removes those URLs before looking for URLs, the same way it already removes `<a>` tags. Every other scheme URL or `//` URL left over still fails the build.

**Open Graph:** `og:title`, `og:description`, `og:type=website`, `og:locale`, `og:url` and `og:image`. The image is `https://encarte.fr/og-fr.png` or `og-en.png`, copied by `build-site.sh` from `fastlane/metadata/android/<locale>/images/featureGraphic.png` (1024 × 500), so there is one source.

**In-page anchors:** the link check also verifies that every `href="#id"` and `href="./#id"` targets an existing `id` in the target page.

**Theme**
- Light: Cream background, Navy text.
- Accents:
  - Teal `#2E8C83` for decoration;
  - links and text accents in `#1F6F5C`, which keeps contrast of at least 4.5:1 on Cream.
- Dark (`prefers-color-scheme: dark`):
  - Navy page;
  - cards in `#262E4F`;
  - Cream text;
  - links in `#7FD1C7`;
  - the privacy band in `#141A33`, so it stays distinct from the page.

**Layout**
- Content width at most 72 rem, with 1 rem side gutters.
- Feature and privacy cards: 2 columns from 600 px up, 1 column below.
- Readable at 390 px and 1280 px, with no horizontal scroll.

**Accessibility**
- `lang` on `<html>`.
- One H1 per page, with a heading order that follows the sections.
- Visible focus styles.
- Decorative SVGs have `aria-hidden`.
- Screenshots have alt text.
- The language switch has `hreflang` and `lang`.

## 7. Verification

**Build guard**
- `scripts/build-site.sh` passes.
- It still fails on:
  - an injected CDN font;
  - a `<script>`;
  - a broken relative link.
- It now also fails on an anchor link with no matching `id`.
- It accepts `https://encarte.fr/og-fr.png` in a meta tag.

**Visual check:** headless Chrome screenshots of the 4 pages (home and privacy, FR and EN) at 390 and 1280 px, in light (`--blink-settings=preferredColorScheme=1`) and dark (`=0`). That makes 16 images, and the user reviews them before the commit that publishes the redesign.

**Contrast:** the text/background pairs in §6 are computed with the WCAG formula. Body text and links must be at least 4.5:1, and large titles at least 3:1.

**Store links:** each badge shown links to a listing URL that answered HTTP 200 during implementation.
