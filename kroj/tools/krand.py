# krand.py <seed> <n keys> [prefix spec]: random key walk (seq: script) for UI coverage runs
import sys, random
sys.path.insert(0, __file__.rsplit('/', 1)[0])
import kseq
seed, n = int(sys.argv[1]), int(sys.argv[2])
pre = sys.argv[3] if len(sys.argv) > 3 else 'LOAD'
rnd = random.Random(seed)
keys = [('OK', 20), ('CLR', 11), ('UP', 10), ('DOWN', 12), ('LEFT', 10), ('RIGHT', 12), ('3', 8), ('1', 5), ('#', 5), ('*', 3), ('RSK', 3), ('LSK', 2)]
pool = [k for k, w in keys for _ in range(w)]
spec = [pre]
for i in range(n): spec.append('%s %d' % (rnd.choice(pool), rnd.choice([350, 450, 600, 900])))
print(kseq.build(', '.join(spec)))
