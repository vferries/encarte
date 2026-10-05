# Encarté — Support the Project Design Spec

- **Date:** 2026-10-05
- **Status:** Draft, pending review
- **Parent spec:** `2026-10-05-encarte-landing-page-design.md` (§3 reserves the slot between FAQ and the closing call to action)

## 1. Context and goals

The user wants people who value Encarté, and his future apps, to be able to support his work. That means donations, plus ways to help that cost nothing. Everything is in French and English.

Decisions taken with the user (2026-10-05):

| Topic | Decision |
|---|---|
| Kinds of support | Donations and free "nudges" (spread the word, report bugs, contribute, review) |
| Platform | **Liberapay** only, account `vferries` (`https://liberapay.com/vferries`) |
| Why Liberapay | Most used donation channel among F-Droid apps: 440 of 6,823 apps, versus 39 for Patreon (fdroiddata, 2026-10-05). French non-profit, free software, 0 % platform commission, dedicated F-Droid metadata field |
| Not chosen | Patreon (membership/perk model, 10 % commission for new creators since August 2025), Ko-fi, GitHub Sponsors, PayPal, Buy Me a Coffee, Open Collective |
| The app | **No change.** No donation link in the app or in the Play listing, to stay clear of Google Play's payments policy. Links live on the site, GitHub and F-Droid |

Success criteria:

1. `encarte.fr` (FR and EN) has a "Soutenir Encarté" / "Support Encarté" section between the FAQ and the closing call to action. It holds a donation link to Liberapay and a list of free ways to help (§3).
2. The GitHub repository shows a "Sponsor" button that points to Liberapay.
3. The F-Droid metadata carries `Liberapay: vferries`, so F-Droid's client shows its donate button.
4. The Liberapay profile texts (FR and EN) are versioned in the repository (§4).
5. Nothing that links to Liberapay is published before `https://liberapay.com/vferries` answers HTTP 200.

Liberapay handles **recurring** donations only. The donor picks the amount and the rhythm, from a few cents a week. That is accepted.

## 2. Scope

**In scope:**
- the site section, header and footer links, and one FAQ answer;
- `.github/FUNDING.yml`;
- the F-Droid metadata, both the draft in this repository and the user's unpushed `fdroiddata` branch;
- `docs/release/liberapay.md`.

**Out of scope:**
- creating the Liberapay account (the user does it);
- tax matters;
- any change to the app or its store listings;
- other platforms;
- donation goals or counters (they would need a widget loaded from liberapay.com).

## 3. Site section

**Placement and anchors**
- The section goes in both home pages, between `#faq` and the closing call to action.
- Anchors: `id="soutenir"` (FR) and `id="support"` (EN).
- The header menu gets a "Soutenir" / "Support" anchor link after FAQ, on all four pages. On the privacy pages it points to `./#soutenir` or `./#support`. Like the other anchor links, it is hidden on phones.
- The home pages' footer links become:
  - "Confidentialité · Soutenir · Code source · English"
  - "Privacy · Support · Source code · Français"

**Layout**
- A centered card, max-width about 40 rem, on the page background. It holds:
  - the title (H2);
  - an intro paragraph;
  - the donation button;
  - a small note under the button;
  - a subtitle (H3);
  - a list of free ways to help.
- **Donation button:** a plain link styled as a button.
  - Light theme: background `var(--link)` (`#1F6F5C`) with Cream text, 5.62:1.
  - Dark theme: `var(--link)` (`#7FD1C7`) with Navy text, 8.59:1.
  - It has a visible focus style.
  - No Liberapay logo, image or widget is loaded, so the "nothing from another origin" rule holds.
- **Link target:** `https://liberapay.com/vferries/donate`.

**Copy, FR / EN**

Title, intro and button:
- H2: "Soutenir Encarté" / "Support Encarté"
- Intro: "Encarté est gratuit, sans pub et sans traqueur, et le restera. Ce sont celles et ceux qui l'utilisent qui le font vivre." / "Encarté is free, ad-free and tracker-free, and it will stay that way. It lives on thanks to the people who use it."
- Button: "Faire un don sur Liberapay" / "Donate on Liberapay"
- Note: "Don récurrent, dès quelques centimes par semaine. Liberapay est une association et ne prend aucune commission." / "A recurring donation, from a few cents a week. Liberapay is a non-profit and takes no commission."

Free ways to help, under the H3 "Sans dépenser un centime" / "Without spending a cent":
- "Parlez d'Encarté autour de vous." / "Tell people about Encarté."
- "Signalez un bug ou proposez une idée sur GitHub." / "Report a bug or suggest an idea on GitHub." The word "GitHub" links to `https://github.com/vferries/encarte/issues`.
- "Contribuez au code ou à une traduction : le projet est ouvert." / "Contribute code or a translation: the project is open." The word "ouvert" / "open" links to `https://github.com/vferries/encarte`.
- "Laissez un avis sur Google Play." / "Leave a review on Google Play." The words "Google Play" link to the store listing. This item is shown **only while the Play listing is live**. It carries class `play-live`, and a new CSS rule `.play-soon .play-live { display: none; }` hides it. It switches with the same `play-soon` body class as the badge.

**FAQ "C'est vraiment gratuit ?" / "Is it really free?"** The answer becomes:
- FR: "Oui. Pas de publicité, pas d'achat intégré, pas d'abonnement. Encarté est un logiciel libre ; si vous voulez aider, voyez <a href="#soutenir">comment le soutenir</a>."
- EN: "Yes. No ads, no in-app purchases, no subscription. Encarté is free software; if you'd like to help, see <a href="#support">how to support it</a>."

**Build guard:** the existing `scripts/build-site.sh` checks still apply:
- the anchors must resolve;
- `<a href>` links may leave the site;
- nothing may be loaded from another origin.

## 4. Liberapay profile texts

`docs/release/liberapay.md` holds what the user pastes into Liberapay:
- the username `vferries`;
- the FR and EN summaries;
- the FR and EN descriptions (Markdown).

These texts were agreed in conversation on 2026-10-05:
- Summary: "crée des applications Android libres, sans pub ni traqueur" / "makes free and open-source Android apps, with no ads and no trackers".
- Description: the "Des applis qui ne vous espionnent pas" / "Apps that don't spy on you" texts, word for word. The plan copies them in full.

The file also lists the setup steps:
- create the account;
- add both languages;
- connect Stripe and/or PayPal to receive payments;
- check that the public page answers.

## 5. GitHub and F-Droid

**`.github/FUNDING.yml`:**

```yaml
liberapay: vferries
```

GitHub then shows "Sponsor" on the repository.

**F-Droid:** add `Liberapay: vferries` to two places:
- `docs/release/fdroid/io.github.vferries.encarte.yml`;
- the user's local `~/projects/fdroiddata` branch `io.github.vferries.encarte`. It is not pushed yet, so its single "New app: Encarté" commit is amended.

The field goes where `fdroid rewritemeta` places it, and `fdroid lint` must pass.

## 6. Verification

- `scripts/build-site.sh` passes. The new anchors (`#soutenir`, `#support`, `./#soutenir`, `./#support`) resolve.
- There is no horizontal scroll at 320, 360 and 390 px. Use the same iframe harness as for the landing page.
- Screenshots at 360 and 1280 px, in light and dark, of both home pages' new section, reviewed by the user.
- The button's text/background contrast is at least 4.5:1 in both themes.
- `.github/FUNDING.yml` parses as YAML.
- `fdroid rewritemeta` and `fdroid lint io.github.vferries.encarte` pass on the `fdroiddata` branch.
- **Publication gate:** `curl -s -o /dev/null -w '%{http_code}' https://liberapay.com/vferries` returns `200` before anything is pushed.
