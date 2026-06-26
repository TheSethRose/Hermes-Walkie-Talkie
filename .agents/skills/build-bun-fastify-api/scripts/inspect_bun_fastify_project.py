#!/usr/bin/env python3
import json
import pathlib
import sys


def read(path):
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except FileNotFoundError:
        return ""


def main():
    root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    package_path = root / "package.json"
    package = json.loads(read(package_path) or "{}")
    src = root / "src"
    files = sorted(p.relative_to(root).as_posix() for p in src.rglob("*.ts")) if src.exists() else []
    index_text = read(src / "index.ts")
    config_text = read(src / "config.ts")

    print(f"project={root}")
    print(f"package={package_path if package_path.exists() else 'missing'}")
    print(f"name={package.get('name', 'unknown')}")
    print(f"type={package.get('type', 'unknown')}")
    print(f"bun_lock={'yes' if (root / 'bun.lock').exists() or (root / 'bun.lockb').exists() else 'no'}")
    print(f"typescript={'yes' if (root / 'tsconfig.json').exists() else 'no'}")
    print(f"fastify={'yes' if 'fastify' in (package.get('dependencies') or {}) else 'no'}")
    print(f"zod={'yes' if 'zod' in (package.get('dependencies') or {}) else 'no'}")
    print(f"multipart={'yes' if '@fastify/multipart' in (package.get('dependencies') or {}) else 'no'}")
    print(f"dotenv={'yes' if 'dotenv' in (package.get('dependencies') or {}) else 'no'}")
    print(f"scripts={','.join(sorted((package.get('scripts') or {}).keys())) or 'none'}")
    print(f"routes={','.join(p for p in files if p.startswith('src/routes/')) or 'none'}")
    print(f"services={','.join(p for p in files if p.startswith('src/services/')) or 'none'}")
    print(f"auth={'yes' if (src / 'auth.ts').exists() or 'requireAuth' in index_text else 'unknown'}")
    print(f"env_schema={'yes' if 'z.object' in config_text else 'unknown'}")
    print("suggested_checks=bun run typecheck")


if __name__ == "__main__":
    main()
