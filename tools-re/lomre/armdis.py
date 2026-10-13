import sys
from capstone import *
from elfimg import Img
def dis(im, start, end, thumb=True):
    md = Cs(CS_ARCH_ARM, CS_MODE_THUMB if thumb else CS_MODE_ARM)
    md.detail = False
    base,d = im.mem['.text']
    code = bytes(d[start-base:end-base])
    for i in md.disasm(code, start):
        print('%x:\t%s\t%s' % (i.address, i.mnemonic, i.op_str))
if __name__ == '__main__':
    im = Img(sys.argv[1])
    dis(im, int(sys.argv[2],16), int(sys.argv[3],16), (sys.argv[4]!='arm') if len(sys.argv)>4 else True)
