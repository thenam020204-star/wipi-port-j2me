#!/usr/bin/env python3
"""Phase A inventory: unpack COPIES of the original archives and hash every file.

Usage:
    python3 -I tools/inventory.py --ktf <KTF zip> --ref <HL4_project zip> --out work/orig --docs docs

- Input archives are opened read-only; nothing is written next to them.
- Nested archives (zip -> jar, HL4_project.zip -> LGT.zip -> jar) are expanded in memory.
- File types are classified from content (magic), never from the name.
- Output manifests are deterministic (sorted, no timestamps) so two runs must diff empty.
"""
import argparse
import hashlib
import io
import os
import sys
import zipfile


def sha256(b):
    return hashlib.sha256(b).hexdigest()


def classify(b):
    """Classify by magic bytes only."""
    if len(b) == 0:
        return 'empty'
    if b[:8] == b'\x89PNG\r\n\x1a\n':
        return 'png'
    if b[:4] == b'MMMD':
        return 'smaf'
    if b[:4] == b'PK\x03\x04':
        return 'zip'
    if b[:4] == b'\x7fELF':
        return 'elf'
    if b[:4] == bytes.fromhex('04e0c046'):
        return 'thumb-code'  # b.n +8 ; mov r8,r8 (KTF client.bin prologue)
    if b[:16].startswith(b'Manifest-Version'):
        return 'manifest'
    if all(32 <= c < 127 or c in (9, 10, 13) or c >= 0xa1 for c in b[:64]) and b[:3] in (b'PID', b'AID'):
        return 'adf-text'
    return 'bin:' + b[:4].hex()


def walk_zip(data, prefix, rows, out_dir, depth=0):
    """Expand a zip (bytes) recursively; record every member."""
    z = zipfile.ZipFile(io.BytesIO(data))
    cd_total = 0
    for info in z.infolist():
        if info.is_dir():
            continue
        b = z.read(info.filename)
        if len(b) != info.file_size:
            raise SystemExit('size mismatch vs central directory: %s' % info.filename)
        cd_total += info.file_size
        path = prefix + info.filename
        kind = classify(b)
        rows.append((path, len(b), sha256(b), b[:16].hex(), kind, '%08x' % info.CRC))
        if out_dir is not None:
            dst = os.path.join(out_dir, path)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            with open(dst, 'wb') as f:
                f.write(b)
        if kind == 'zip' and depth < 3:
            walk_zip(b, path + '!/', rows, out_dir, depth + 1)
    return cd_total


def write_tsv(path, rows):
    rows = sorted(rows)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write('path\tsize\tsha256\tmagic16\tkind\tcrc32\n')
        for r in rows:
            f.write('\t'.join(str(x) for x in r) + '\n')
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--ktf', required=True, help='original KTF zip (영웅서기 4_KTF_wipiX호환)')
    ap.add_argument('--ref', required=True, help='HL4_project zip (reference project, contains LGT.zip)')
    ap.add_argument('--out', default=None, help='directory for extracted copies (gitignored)')
    ap.add_argument('--docs', default='docs')
    ap.add_argument('--android', default=None, help='Android build zip (apk + kickass save), optional')
    a = ap.parse_args()
    os.makedirs(a.docs, exist_ok=True)

    inputs = []
    for p in [x for x in (a.ktf, a.ref, a.android) if x]:
        with open(p, 'rb') as f:
            b = f.read()
        inputs.append((os.path.basename(p), len(b), sha256(b), b[:16].hex(), classify(b), '-'))
    write_tsv(os.path.join(a.docs, 'manifest_inputs.tsv'), inputs)

    # KTF target
    with open(a.ktf, 'rb') as f:
        ktf = f.read()
    rows = []
    walk_zip(ktf, '', rows, os.path.join(a.out, 'ktf') if a.out else None)
    rows = write_tsv(os.path.join(a.docs, 'manifest_ktf.tsv'), rows)

    # LGT cross-check copy lives inside the reference project
    with open(a.ref, 'rb') as f:
        ref = zipfile.ZipFile(io.BytesIO(f.read()))
    lgt = ref.read('HL4_project/LGT.zip')
    ktf_in_ref = ref.read('HL4_project/KTF.zip')
    lrows = []
    walk_zip(lgt, '', lrows, os.path.join(a.out, 'lgt') if a.out else None)
    lrows = write_tsv(os.path.join(a.docs, 'manifest_lgt.tsv'), lrows)

    arows = []
    if a.android:
        with open(a.android, 'rb') as f:
            walk_zip(f.read(), '', arows, os.path.join(a.out, 'android') if a.out else None)
        arows = write_tsv(os.path.join(a.docs, 'manifest_android.tsv'), arows)

    # summary
    from collections import Counter
    for name, rs in (('KTF', rows), ('LGT', lrows), ('ANDROID', arows)):
        c = Counter(r[4] if not r[4].startswith('bin:') else 'bin' for r in rs)
        print('%s: %d files, %d bytes, kinds=%s' % (name, len(rs), sum(r[1] for r in rs), dict(sorted(c.items()))))
    print('LGT.zip sha256', sha256(lgt))
    print('KTF.zip(in ref) == KTF input:', sha256(ktf_in_ref) == sha256(ktf))


if __name__ == '__main__':
    sys.exit(main())
