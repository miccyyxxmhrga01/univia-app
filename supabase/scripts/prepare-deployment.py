"""Prepare atomic, versioned Univia SQL bundles and the matching Edge Function.

This script writes local review artifacts only. It does not contact Supabase.
"""
from pathlib import Path
import argparse
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "supabase"
OUTPUT = ROOT / "build/univia-database/deployment"


def sql_string(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def bundle(paths: list[Path], filename: str) -> None:
    parts = [
        "-- Univia: generated from the checked-in migrations.\n"
        "-- Review the target project and expected migration state before running.\n"
        "begin;\n"
        "create schema if not exists supabase_migrations;\n"
        "create table if not exists supabase_migrations.schema_migrations (\n"
        "    version text primary key, statements text[], name text\n"
        ");\n"
    ]
    for path in paths:
        version, name = path.stem.split("_", 1)
        sql = path.read_text(encoding="utf-8").strip()
        parts.append(f"\n-- {path.name}\n{sql}\n")
        parts.append(
            "insert into supabase_migrations.schema_migrations (version, name, statements)\n"
            f"values ({sql_string(version)}, {sql_string(name)}, array[{sql_string(sql)}]);\n"
        )
    parts.append("\ncommit;\n")
    (OUTPUT / filename).write_text("".join(parts), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--after", help="Last migration version already applied to the target database.")
    args = parser.parse_args()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    migrations = sorted((SOURCE / "migrations").glob("*.sql"))
    versions = [path.stem.split("_", 1)[0] for path in migrations]
    baseline = args.after or versions[1]
    if baseline not in versions:
        parser.error(f"Unknown migration version: {baseline}")
    pending = migrations[versions.index(baseline) + 1:]
    bundle(migrations, "Univia-full.sql")
    bundle(pending, "Univia-upgrade.sql")
    function_dir = OUTPUT / "bbco-data"
    function_dir.mkdir(exist_ok=True)
    function_source = SOURCE / "functions/bbco-data"
    function_files = sorted(path for path in function_source.iterdir()
                            if (path.suffix == ".ts" and not path.name.endswith(".test.ts"))
                            or path.name in ("deno.json", "deno.lock"))
    for path in function_files:
        shutil.copyfile(path, function_dir / path.name)
    manifest = {
        "application": "Univia",
        "project_ref": "cwruxuwcoxldpyseavbb",
        "migrations": [path.stem for path in migrations],
        "upgrade_after": baseline,
        "pending_migrations": [path.stem for path in pending],
        "edge_function": "bbco-data",
        "function_files": {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in function_files},
        "verify_jwt": False,
        "authentication": "Firebase RS256 tokens verified inside the function",
    }
    (OUTPUT / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (OUTPUT / "README.md").write_text(
        "# Univia deployment\n\n"
        "Target project: `cwruxuwcoxldpyseavbb`. No deployment occurs when this bundle is generated.\n\n"
        "1. In Supabase Project Settings > General, set the project display name to **Univia**.\n"
        f"2. For the existing project with migrations applied through `{baseline}`, run `Univia-upgrade.sql` in the SQL Editor. "
        "For a fresh Supabase project, run `Univia-full.sql` instead. These bundles preserve existing data "
        "and record migration history inside the same transaction. Check the live migration history first; "
        "regenerate with `--after VERSION` if its last applied version differs. Do not run both bundles.\n"
        "3. Deploy the `bbco-data` Edge Function with every file in `bbco-data/`, including OTP, SMTP and the dependency lockfile. "
        "Keep gateway JWT verification disabled: the handler verifies Firebase tokens itself. "
        "The existing Supabase runtime provides `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY`; "
        "no service key belongs in the Android app.\n"
        "4. Run `python supabase/tests/smoke.py` from the repository root.\n"
        "5. For new email-code verification, the coordinator must configure the sending email account through "
        "the app's Email sender screen. Existing Firebase-verified accounts retain access.\n\n"
        "The Android app already points to the target project. A different project requires updating "
        "its publishable URL/key in `app/build.gradle.kts` and deploying the function there.\n",
        encoding="utf-8",
    )
    print(f"Prepared Univia deployment: {OUTPUT}")


if __name__ == "__main__":
    main()
