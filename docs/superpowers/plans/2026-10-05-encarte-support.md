# Encarté Support the Project Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let people support the user's work. That means a "Soutenir / Support" section on encarte.fr with a Liberapay donation link and free ways to help, a GitHub Sponsor button, an F-Droid donate field, and the Liberapay profile texts versioned in the repo.

**Architecture:**
- **Site:** static HTML/CSS only. One new section on each home page, menu and footer links, one FAQ answer, and a little CSS: the support card, a button style, and a `play-live` rule that reuses the existing `play-soon` body class.
- **Repository files:** `.github/FUNDING.yml` and `docs/release/liberapay.md`.
- **F-Droid metadata:** both in this repository's draft and in the user's unpushed `~/projects/fdroiddata` branch, whose single commit is amended.

**Tech Stack:** HTML, CSS, YAML, fdroidserver (already installed in the scratchpad virtualenv), headless Chrome for checks.

**Spec:** `docs/superpowers/specs/2026-10-05-encarte-support-design.md`

## Global Constraints

**Liberapay**
- Account `vferries`. Donation link: `https://liberapay.com/vferries/donate`. Profile: `https://liberapay.com/vferries`.
- Nothing that links to Liberapay is pushed before `https://liberapay.com/vferries` answers HTTP 200.

**Site**
- No JavaScript. Nothing loaded from another origin, so no Liberapay widget, logo or image. `<a href>` links may point elsewhere.
- French and English everywhere. Copy exactly as in spec §3.
- Anchors: `#soutenir` (FR) and `#support` (EN).
- The Google Play review item carries class `play-live` and is hidden while `<body>` has `play-soon`.

**App and Play listing**
- No change to the app or to the store listings: no donation link there.

**Repository**
- Commit directly on `main`, with no `Co-Authored-By` trailer. Stage files by name.
- Pushing (this repo) and the fdroiddata branch are the user's or the controller's business, with the user's consent.

## Review Focus

1. **A dead donation link goes live** before the Liberapay account exists. This is pinned by the gate in Task 5.
2. **"Leave a review on Google Play" shows** while the listing is still 404. This is pinned by the `play-live` rule (Task 3) and by the Task 5 screenshots.
3. **The donation button loses contrast or focus visibility in dark mode.** This is pinned by the Task 3 contrast numbers and the Task 5 dark screenshots.
4. **A new anchor breaks on the privacy pages** (`./#soutenir`). This is pinned by `build-site.sh`'s anchor check (Task 4).
5. **The fdroiddata branch ends up with two commits or fails lint.** This is pinned by the Task 2 checks.

---

### Task 1: Liberapay texts and GitHub funding file

**Files:**
- Create: `docs/release/liberapay.md`
- Create: `.github/FUNDING.yml`

- [ ] **Step 1: Write `docs/release/liberapay.md`**

````markdown
# Liberapay profile

Account: **vferries**. The public page is https://liberapay.com/vferries and the donation page https://liberapay.com/vferries/donate.
Liberapay is a French non-profit. It takes no commission; only the payment processor's fees apply. It handles recurring
donations. The donor picks the amount and the rhythm.

## Setup (once)

1. Create the account at https://liberapay.com/sign-up with the username `vferries`.
2. In the profile, add a **French** and an **English** description, and paste the texts below.
3. In the payment settings, connect **Stripe** and/or **PayPal** to receive payments, and accept EUR.
4. Check that https://liberapay.com/vferries opens while logged out. The site's support section goes live only after
   that (see `docs/release-checklist.md`).

## Summary

- FR: crée des applications Android libres, sans pub ni traqueur
- EN: makes free and open-source Android apps, with no ads and no trackers

## Description (FR)

```markdown
## Des applis qui ne vous espionnent pas

Je développe des applications Android libres, à commencer par **[Encarté](https://encarte.fr)**, un portefeuille de cartes de fidélité hors-ligne.

Mes règles sont simples :

- **aucun traqueur**, aucune statistique, aucune publicité ;
- **aucun compte** à créer : vos données restent sur votre téléphone ;
- **du code ouvert** (GPL) que chacun peut vérifier, modifier et redistribuer ;
- **gratuit**, sans achat intégré ni abonnement.

Ce choix a un coût : le temps de développement et de maintenance, les frais de publication (compte développeur Google Play, nom de domaine), les tests sur de vrais appareils. Les applis « gratuites » se rémunèrent souvent en revendant votre attention ou vos données. Je préfère que les miennes soient financées par celles et ceux qui les utilisent et qui tiennent à ce qu'elles existent.

Vos dons me permettent de :

- maintenir Encarté et corriger les bugs rapidement ;
- publier d'autres applis sur le même principe ;
- garder tout cela gratuit, sans pub et sans traqueur, pour tout le monde, y compris pour celles et ceux qui ne peuvent pas donner.

Même quelques centimes par semaine comptent. Merci !

*Vous ne pouvez pas donner ? Parler de mes applis autour de vous, signaler un bug ou laisser un avis m'aide aussi beaucoup.*
```

## Description (EN)

```markdown
## Apps that don't spy on you

I build free and open-source Android apps, starting with **[Encarté](https://encarte.fr/en/)**, an offline loyalty-card wallet.

My rules are simple:

- **no trackers**, no analytics, no ads;
- **no account** to create: your data stays on your phone;
- **open source** (GPL), so anyone can check, change and share the code;
- **free**, with no in-app purchases or subscriptions.

That choice has a cost: development and maintenance time, publishing fees (Google Play developer account, domain name), testing on real devices. "Free" apps often pay for themselves by selling your attention or your data. I'd rather mine be funded by the people who use them and want them to exist.

Your donations help me:

- keep Encarté maintained and fix bugs quickly;
- publish more apps built the same way;
- keep all of it free, ad-free and tracker-free for everyone, including people who can't donate.

Even a few cents a week make a difference. Thank you!

*Can't donate? Telling people about my apps, reporting a bug or leaving a review helps a lot too.*
```

## Where the donation link appears

- encarte.fr, in the "Soutenir Encarté" / "Support Encarté" section.
- GitHub's "Sponsor" button, from `.github/FUNDING.yml`.
- F-Droid's donate button, from the `Liberapay:` metadata field.
- **Not** in the app or its Play listing: Google Play's payments policy is unclear about developer tips linked from the
  app, so the link stays on the site, GitHub and F-Droid.
````

- [ ] **Step 2: Write `.github/FUNDING.yml`**

```yaml
liberapay: vferries
```

Run `python3 -c "import yaml; print(yaml.safe_load(open('.github/FUNDING.yml')))"`. Expected: `{'liberapay': 'vferries'}`.

- [ ] **Step 3: Commit**

```bash
git add docs/release/liberapay.md .github/FUNDING.yml
git commit -m "docs: add the Liberapay profile texts and GitHub funding link"
```

---

### Task 2: F-Droid donate field

**Files:**
- Modify: `docs/release/fdroid/io.github.vferries.encarte.yml`, in this repository
- Modify: `~/projects/fdroiddata/metadata/io.github.vferries.encarte.yml`, on branch `io.github.vferries.encarte`, by amending its single commit

- [ ] **Step 1: Update the user's fdroiddata branch**

```bash
cd ~/projects/fdroiddata
git switch io.github.vferries.encarte
git status --short                       # expected: clean
sed -i 's|^IssueTracker: \(.*\)$|IssueTracker: \1\nLiberapay: vferries|' metadata/io.github.vferries.encarte.yml
V=/tmp/claude-1000/-home-vincent-projects-fidelio/7ce4fcb8-3839-46e0-bf9d-d98ea692082e/scratchpad/fdroid-venv
$V/bin/fdroid rewritemeta io.github.vferries.encarte
$V/bin/fdroid lint io.github.vferries.encarte; echo "lint exit $?"
cat metadata/io.github.vferries.encarte.yml
git add metadata/io.github.vferries.encarte.yml
git commit --amend --no-edit
git log --oneline upstream/master..HEAD   # expected: exactly one commit, "New app: Encarté"
```

Expected: `lint exit 0`, and the file contains `Liberapay: vferries` wherever `rewritemeta` placed it. If the virtualenv is missing, recreate it with `python3 -m venv "$V" && "$V/bin/pip" install -q fdroidserver`, and say so in the report. Never push this branch.

- [ ] **Step 2: Mirror the field into the draft in this repository**

In `docs/release/fdroid/io.github.vferries.encarte.yml`, add `Liberapay: vferries` at the same position as in the fdroiddata file after `rewritemeta`. The rest of the draft stays as it is, including the `FULL_COMMIT_HASH_OF_TAG_v1.0.0` marker. Check that the YAML parses:

```bash
python3 -c "import yaml; m=yaml.safe_load(open('docs/release/fdroid/io.github.vferries.encarte.yml')); assert m['Liberapay']=='vferries'; print('ok')"
```

- [ ] **Step 3: Commit (this repository)**

```bash
git add docs/release/fdroid/io.github.vferries.encarte.yml
git commit -m "docs: add the Liberapay field to the F-Droid metadata draft"
```

---

### Task 3: Support section styles

**Files:**
- Modify: `site/style.css`, appending a block after the `.faq`/`details` rules and before `.closing`

- [ ] **Step 1: Add the styles**

```css
/* Support section: a card with the donation button and free ways to help. */
.support .wrap { max-width: 42rem; }
.support-card { background: var(--surface); border-radius: 1rem; padding: clamp(1.5rem, 4vw, 2.5rem); text-align: center; }
.support-card h2 { margin-bottom: 0.75rem; }
.support-card .intro { color: var(--muted); margin: 0 0 1.5rem; }
.support-card .note { font-size: 0.9375rem; color: var(--muted); margin: 0.75rem 0 0; }
.support-card h3 { font-size: 1.125rem; font-weight: 800; margin: 2rem 0 0.5rem; }
.helps { list-style: none; padding: 0; margin: 0; display: grid; gap: 0.4rem; }
/* The page background as text color keeps 5.6:1 (light) and 8.6:1 (dark) on --link. */
.button { display: inline-block; background: var(--link); color: var(--bg); font-weight: 800; text-decoration: none; padding: 0.8rem 1.6rem; border-radius: 999px; }
.button:focus-visible { outline-color: var(--text); }
/* Play-only items appear once the Play listing is live, like the Play badge. */
.play-soon .play-live { display: none; }
```

- [ ] **Step 2: Check contrast and build**

```bash
python3 - <<'EOF'
def L(h):
    c=[int(h.lstrip('#')[i:i+2],16)/255 for i in (0,2,4)]
    c=[x/12.92 if x<=0.03928 else ((x+0.055)/1.055)**2.4 for x in c]
    return 0.2126*c[0]+0.7152*c[1]+0.0722*c[2]
def r(a,b):
    hi,lo=sorted([L(a),L(b)],reverse=True); return (hi+0.05)/(lo+0.05)
print("light button", round(r("#FFF6E8","#1F6F5C"),2), "dark button", round(r("#1D2440","#7FD1C7"),2))
print("light muted on card", round(r("#4A5170","#FFFFFF"),2), "dark muted on card", round(r("#C9C3B8","#262E4F"),2))
EOF
scripts/build-site.sh
```

Expected: every ratio is at least 4.5, and the build passes.

- [ ] **Step 3: Commit**

```bash
git add site/style.css
git commit -m "feat: style the support section on encarte.fr"
```

---

### Task 4: Support section, menu, footer and FAQ link

**Files:**
- Modify: `site/index.html`, `site/en/index.html`, `site/privacy.html`, `site/en/privacy.html`

- [ ] **Step 1: French home page (`site/index.html`)**

1. In the header `<nav>`, after `<a class="anchor" href="#faq">FAQ</a>`, add:
   `<a class="anchor" href="#soutenir">Soutenir</a>`
2. In the FAQ, replace the first answer's `<p>` with:
   `<p>Oui. Pas de publicité, pas d'achat intégré, pas d'abonnement. Encarté est un logiciel libre ; si vous voulez aider, voyez <a href="#soutenir">comment le soutenir</a>.</p>`
3. Between the end of the FAQ `</section>` and `<section class="closing">`, insert:

```html
  <section class="support" id="soutenir">
    <div class="wrap">
      <div class="support-card">
        <h2>Soutenir Encarté</h2>
        <p class="intro">Encarté est gratuit, sans pub et sans traqueur, et le restera. Ce sont celles et ceux qui l'utilisent qui le font vivre.</p>
        <a class="button" href="https://liberapay.com/vferries/donate">Faire un don sur Liberapay</a>
        <p class="note">Don récurrent, dès quelques centimes par semaine. Liberapay est une association et ne prend aucune commission.</p>
        <h3>Sans dépenser un centime</h3>
        <ul class="helps">
          <li>Parlez d'Encarté autour de vous.</li>
          <li>Signalez un bug ou proposez une idée sur <a href="https://github.com/vferries/encarte/issues">GitHub</a>.</li>
          <li>Contribuez au code ou à une traduction : le projet est <a href="https://github.com/vferries/encarte">ouvert</a>.</li>
          <li class="play-live">Laissez un avis sur <a href="https://play.google.com/store/apps/details?id=io.github.vferries.encarte">Google Play</a>.</li>
        </ul>
      </div>
    </div>
  </section>
```

4. Footer: replace the `<p class="links">` line with:
   `<p class="links"><a href="privacy.html">Confidentialité</a> · <a href="#soutenir">Soutenir</a> · <a href="https://github.com/vferries/encarte">Code source</a> · <a href="en/" hreflang="en" lang="en">English</a></p>`

- [ ] **Step 2: English home page (`site/en/index.html`)**

1. Header `<nav>`: after `<a class="anchor" href="#faq">FAQ</a>`, add `<a class="anchor" href="#support">Support</a>`.
2. Replace the first FAQ answer's `<p>` with:
   `<p>Yes. No ads, no in-app purchases, no subscription. Encarté is free software; if you'd like to help, see <a href="#support">how to support it</a>.</p>`
3. Between the FAQ `</section>` and `<section class="closing">`, insert:

```html
  <section class="support" id="support">
    <div class="wrap">
      <div class="support-card">
        <h2>Support Encarté</h2>
        <p class="intro">Encarté is free, ad-free and tracker-free, and it will stay that way. It lives on thanks to the people who use it.</p>
        <a class="button" href="https://liberapay.com/vferries/donate">Donate on Liberapay</a>
        <p class="note">A recurring donation, from a few cents a week. Liberapay is a non-profit and takes no commission.</p>
        <h3>Without spending a cent</h3>
        <ul class="helps">
          <li>Tell people about Encarté.</li>
          <li>Report a bug or suggest an idea on <a href="https://github.com/vferries/encarte/issues">GitHub</a>.</li>
          <li>Contribute code or a translation: the project is <a href="https://github.com/vferries/encarte">open</a>.</li>
          <li class="play-live">Leave a review on <a href="https://play.google.com/store/apps/details?id=io.github.vferries.encarte">Google Play</a>.</li>
        </ul>
      </div>
    </div>
  </section>
```

4. Footer `<p class="links">`:
   `<p class="links"><a href="privacy.html">Privacy</a> · <a href="#support">Support</a> · <a href="https://github.com/vferries/encarte">Source code</a> · <a href="../" hreflang="fr" lang="fr">Français</a></p>`

- [ ] **Step 3: Privacy pages menu**

- In `site/privacy.html`'s header `<nav>`, after `<a class="anchor" href="./#faq">FAQ</a>`, add `<a class="anchor" href="./#soutenir">Soutenir</a>`.
- In `site/en/privacy.html`'s header `<nav>`, after `<a class="anchor" href="./#faq">FAQ</a>`, add `<a class="anchor" href="./#support">Support</a>`.
- Nothing else changes on those pages.

- [ ] **Step 4: Build**

Run: `scripts/build-site.sh`. Expected: success, which also means the new anchors resolve on all four pages.

Then confirm that the copy matches spec §3: `grep -c 'Liberapay est une association' site/index.html` and `grep -c 'Liberapay is a non-profit' site/en/index.html` each print `1`.

- [ ] **Step 5: Commit**

```bash
git add site/index.html site/en/index.html site/privacy.html site/en/privacy.html
git commit -m "feat: add a support section to encarte.fr"
```

---

### Task 5: Review, gate and publish (controller and user)

The controller runs this task.

- [ ] **Step 1: Layout checks**
  - Build.
  - Run the iframe overflow harness at 320, 360 and 390 px on a `mktemp -d` copy, the same method as the landing page fix wave. `scrollWidth` must stay within `innerWidth`, apart from the scrollbar.
  - Take screenshots of the support section on both home pages, at 360 and 1280 px, in light and dark.
  - Check that the "Google Play" review item is **not** visible, because `play-soon` is still set.

- [ ] **Step 2: User review.** Show the screenshots, or the local preview (`python3 -m http.server --directory build/site`), and wait for the user's approval. Any change goes back to Task 3 or 4 as a new commit.

- [ ] **Step 3: Liberapay gate**

```bash
curl -s -o /dev/null -w '%{http_code}\n' https://liberapay.com/vferries
```

If the answer is not `200`, the account is not live yet. Stop and tell the user. **Do not push.** `docs/release/liberapay.md` lists the setup steps.

- [ ] **Step 4: Publish (with the user's consent).**
  - Run `git push origin main`.
  - Watch the Pages and CI runs.
  - Check `https://encarte.fr/#soutenir` and `https://encarte.fr/en/#support`: HTTP 200 and the section present.
  - Check that the repository shows the Sponsor button: `gh api repos/vferries/encarte --jq .has_sponsorships`, or simply look at the page.
  - Remind the user that the fdroiddata branch now carries `Liberapay: vferries` and is still theirs to push.
