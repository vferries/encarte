# One-time release setup

Run these once, before the first release. Every command runs **in your own terminal**: secret values never go
through chat, logs or the repository. Repository: `vferries/encarte`.

## 1. Upload key

Create the key outside the repository and keep a copy in your password manager (the file and both passwords).
With Play App Signing, Google holds the app signing key; this key only authenticates uploads, and Play support
can reset it if it is lost or leaked.

```bash
keytool -genkeypair -v -keystore ~/encarte-upload.jks -storetype PKCS12 -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Vincent Ferries, O=Encarte"
```

`keytool` asks for the keystore password. With PKCS12, the key password is the same.

Record the certificate fingerprint (public, not a secret):

```bash
keytool -J-Duser.language=en -J-Duser.country=US -list -v -keystore ~/encarte-upload.jks -alias upload | sed -n 's/^[[:space:]]*SHA256: //p'
```

The `-J-Duser.language=en -J-Duser.country=US` flags ensure the output is in English regardless of your system language, keeping it parseable.

## 2. GitHub `release` environment, secrets and variable

The controller creates the environment (tag rule `v*`, you as required reviewer) once you agree; you can also do
it in Settings → Environments → New environment. Then set the secrets; each command prompts for the value or
reads it from a pipe:

```bash
base64 -w0 ~/encarte-upload.jks | gh secret set UPLOAD_KEYSTORE_BASE64 --env release --repo vferries/encarte
gh secret set UPLOAD_KEYSTORE_PASSWORD --env release --repo vferries/encarte   # prompts
gh secret set UPLOAD_KEY_PASSWORD --env release --repo vferries/encarte        # prompts (same as above with PKCS12)
gh secret set UPLOAD_KEY_ALIAS --env release --repo vferries/encarte --body upload
gh variable set UPLOAD_CERT_SHA256 --repo vferries/encarte --body "<fingerprint from step 1>"
```

## 3. Secret scanning and push protection

Settings → Code security → enable **Secret scanning** and **Push protection** (free on public repositories).
The controller can do it with `gh api` once you agree.

## 4. encarte.fr on GitHub Pages

1. The controller enables Pages with the **GitHub Actions** source and sets the custom domain `encarte.fr`
   once you agree. The `Pages` workflow then deploys `site/`.
2. **Verify the domain** in your account (prevents anyone else from claiming it on Pages): github.com →
   Settings (your profile) → Pages → Add a domain → `encarte.fr`. GitHub shows a `TXT` record named
   `_github-pages-challenge-vferries.encarte.fr`: add it at your registrar, then click Verify.
3. At your registrar, add the site records:

   | Name | Type | Value |
   |---|---|---|
   | `@` | A | `185.199.108.153` |
   | `@` | A | `185.199.109.153` |
   | `@` | A | `185.199.110.153` |
   | `@` | A | `185.199.111.153` |
   | `@` | AAAA | `2606:50c0:8000::153` |
   | `@` | AAAA | `2606:50c0:8001::153` |
   | `@` | AAAA | `2606:50c0:8002::153` |
   | `@` | AAAA | `2606:50c0:8003::153` |
   | `www` | CNAME | `vferries.github.io.` |

   Remove any other `A`/`AAAA` record on `@` (the registrar's parking page, for instance).
4. Once the certificate is issued (Settings → Pages shows it), tick **Enforce HTTPS**.
5. Check: `https://encarte.fr/` and `https://encarte.fr/privacy.html` load, and `https://www.encarte.fr/` redirects.
