"""The lifter: abstract interpreter over one ARM function at a time (see lift.py for the design)."""
from lift import Core, NullEm
from ops import OpsMixin
from mem import MemMixin
from calls import CallsMixin
from drive import DriveMixin, FnCtx
from world import FRAMEWORK_STATIC


class Interp(Core, OpsMixin, MemMixin, CallsMixin, DriveMixin):
    def __init__(self, W):
        Core.__init__(self, W)
        self.static_set = set(FRAMEWORK_STATIC)
        self.fnctx = {}
        self.static_tvs = {}
        self.site_tvs = {}
        self.edges = []
        self.final = False
        self.cur_live = 0
        self.cur_addr = 0
        self.ntemps = 0

    def fn_of(self, addr):
        f = self.fnctx.get(addr)
        if f is None:
            f = self.fnctx[addr] = FnCtx(self, addr)
        return f

    def impl_class(self, sig):
        """Class whose code implements this signature's function (for the `this` type hint)."""
        if sig.virtual_slot is not None:
            for cm in self.W.cls.values():
                if sig.addr in cm.vslots.values():
                    return cm.name
        if sig.owner in self.W.cls:
            return sig.owner
        return None

    def all_functions(self):
        B = self.B
        skip = self.W.skip_fns | self.string_getters()
        lim = min(self.text_thunks) if self.text_thunks else B.game_end
        return [a for a in B.func_starts if a < lim and a not in skip]
