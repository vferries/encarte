FidMe export fixture written for Encarté, in the format of Catima's FidMe importer and test fixture (2021):
`;`-separated, header `Retailer;Program;Added At;Reference;Firstname;Lastname`, stored as `loyalty_programs.csv`
in a ZIP. The Décathlon row has no reference, as FidMe exports expired cards.

Unverified against a real 2026 FidMe export: when one is available and differs, it becomes the new fixture.

For a manual check, zip it: `python3 -m zipfile -c fidme-export.zip loyalty_programs.csv` (from this folder).
