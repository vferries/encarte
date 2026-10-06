`sample/` is a Wallet store card written for Encarté's tests: `pass.json` and two `pass.strings` tables, unsigned
(Encarté does not check signatures). `PassReaderTest` zips it the way a `.pkpass` is built.

For a manual check, zip it from `sample/`: `python3 -m zipfile -c sample.pkpass pass.json en.lproj fr.lproj`.
