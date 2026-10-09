#!/usr/bin/env python3
"""List every endpoint in an alramz-mw-oss service's OpenAPI spec with the facts
needed to build a Postman item: method, declared path, resolved security, request
body requiredness, and which response status codes are documented.

Usage:
    python3 list_endpoints.py <path-to-spec.yaml>

This only reads the spec; it never writes anything. It exists because hand-parsing
a 500-900 line OpenAPI YAML file for "every endpoint" requests is slow and error-prone
compared to a few lines of PyYAML.
"""
import sys
import yaml

HTTP_METHODS = ("get", "put", "post", "delete", "patch", "options", "head")


def resolve_security(top_level_security, operation):
    """An operation's own `security:` key overrides the document's top-level one.
    `security: []` explicitly means "no auth required" for that operation."""
    if "security" in operation:
        op_sec = operation["security"]
        return "none" if not op_sec else ",".join(sorted(k for s in op_sec for k in s))
    if not top_level_security:
        return "none"
    return ",".join(sorted(k for s in top_level_security for k in s))


def main():
    if len(sys.argv) != 2:
        print("usage: list_endpoints.py <path-to-spec.yaml>", file=sys.stderr)
        sys.exit(1)

    with open(sys.argv[1], "r", encoding="utf-8") as f:
        spec = yaml.safe_load(f)

    top_security = spec.get("security")
    paths = spec.get("paths", {})

    rows = []
    for path, path_item in paths.items():
        path_params = [
            p["name"] for p in path_item.get("parameters", []) if p.get("in") == "path"
        ]
        for method in HTTP_METHODS:
            if method not in path_item:
                continue
            op = path_item[method]
            op_path_params = path_params + [
                p["name"] for p in op.get("parameters", []) if p.get("in") == "path"
            ]
            query_params = [
                p["name"] for p in op.get("parameters", []) if p.get("in") == "query"
            ]
            request_body = op.get("requestBody")
            responses = sorted(op.get("responses", {}).keys())
            rows.append(
                {
                    "method": method.upper(),
                    "path": path,
                    "operationId": op.get("operationId", ""),
                    "summary": op.get("summary", ""),
                    "security": resolve_security(top_security, op),
                    "requestBodyRequired": bool(request_body and request_body.get("required")),
                    "pathParams": ",".join(op_path_params) or "-",
                    "queryParams": ",".join(query_params) or "-",
                    "responses": ",".join(responses) or "-",
                }
            )

    if not rows:
        print("No paths found in spec.", file=sys.stderr)
        sys.exit(1)

    widths = {
        k: max(len(k), max(len(str(r[k])) for r in rows))
        for k in rows[0].keys()
    }
    header = "  ".join(k.ljust(widths[k]) for k in rows[0].keys())
    print(header)
    print("-" * len(header))
    for r in rows:
        print("  ".join(str(r[k]).ljust(widths[k]) for k in r.keys()))


if __name__ == "__main__":
    main()
