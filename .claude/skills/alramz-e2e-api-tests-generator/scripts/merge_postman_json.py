#!/usr/bin/env python3
"""Upsert items/variables/environment-values into an existing Postman collection or
environment JSON file, by name/key, instead of hand-splicing a large JSON tree with a
text editor (easy to produce invalid JSON or accidentally drop sibling items that way).

Re-running this with the same --add file is safe: matching entries are replaced in
place rather than duplicated, so regenerating a single endpoint's item after editing
the spec does not leave a stale copy behind.

Usage:
    python3 merge_postman_json.py --target <existing .postman_collection.json or
        .postman_environment.json> --add <partial json with the entries to upsert>
        [--dry-run]

The --add file's shape depends on what you're merging:
    {"item": [ {...one Postman request/folder item...}, ... ]}       -> matched by "name"
    {"variable": [ {"key": "...", "value": "..."}, ... ]}             -> matched by "key"
    {"values": [ {"key": "...", "value": "..."}, ... ]}               -> matched by "key"
(an --add file may contain any combination of these three keys at once)

Preserves Postman's own export formatting (tab indentation) so a re-exported diff
against this file in Postman stays minimal.
"""
import argparse
import json
import sys


def upsert(existing_list, new_list, key_field):
    existing_list = list(existing_list)
    added, updated = [], []
    for new_entry in new_list:
        key = new_entry.get(key_field)
        if key is None:
            raise ValueError(f"entry missing '{key_field}': {new_entry}")
        for i, entry in enumerate(existing_list):
            if entry.get(key_field) == key:
                existing_list[i] = new_entry
                updated.append(key)
                break
        else:
            existing_list.append(new_entry)
            added.append(key)
    return existing_list, added, updated


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--target", required=True, help="existing collection/environment JSON file to update")
    parser.add_argument("--add", required=True, help="partial JSON file with item/variable/values entries to upsert")
    parser.add_argument("--dry-run", action="store_true", help="print what would change without writing")
    args = parser.parse_args()

    with open(args.target, "r", encoding="utf-8") as f:
        target = json.load(f)
    with open(args.add, "r", encoding="utf-8") as f:
        add = json.load(f)

    merge_spec = [("item", "name"), ("variable", "key"), ("values", "key")]
    any_merged = False
    for list_key, key_field in merge_spec:
        if list_key not in add:
            continue
        if list_key not in target:
            target[list_key] = []
        target[list_key], added, updated = upsert(target[list_key], add[list_key], key_field)
        any_merged = True
        if added:
            print(f"{list_key}: added {added}")
        if updated:
            print(f"{list_key}: updated {updated}")

    if not any_merged:
        print("Nothing to merge: --add file had none of item/variable/values.", file=sys.stderr)
        sys.exit(1)

    if args.dry_run:
        print("(dry-run: not written)")
        return

    with open(args.target, "w", encoding="utf-8") as f:
        json.dump(target, f, indent="\t", ensure_ascii=False)
        f.write("\n")
    print(f"wrote {args.target}")


if __name__ == "__main__":
    main()
