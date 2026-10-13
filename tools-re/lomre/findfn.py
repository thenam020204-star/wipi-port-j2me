import sys, io, contextlib, re
import inv
from inv import starts
import ann
fs = sorted(starts)
def text(a, e):
    buf = io.StringIO()
    with contextlib.redirect_stdout(buf):
        ann.ann(a, e)
    return buf.getvalue()
def func_of(addr):
    import bisect
    i = bisect.bisect_right(fs, addr)-1
    return fs[i], (fs[i+1] if i+1 < len(fs) else 0xf4a00)
if __name__ == '__main__':
    pat = sys.argv[1]; limit = int(sys.argv[2]) if len(sys.argv)>2 else 3
    hits = []
    for i,a in enumerate(fs):
        e = fs[i+1] if i+1 < len(fs) else 0xf4a00
        t = text(a, e)
        if re.search(pat, t):
            hits.append((a,e,len(t.splitlines())))
    print(len(hits), 'functions match;', [(hex(a),n) for a,e,n in hits[:25]])
