# -*- coding: utf-8 -*-
"""pixeltext - draw Vietnamese pixel text with the fontcus bitmap fonts.

    from pixeltext import Font, measure, render, draw, fit
    print(measure("Trò chơi mới", 13))
    render("Trò chơi mới", 13, scale=3).save("out.png")
"""
from .font import Font, SIZES, FONTS
from .metrics import Mask, layout, wrap, measure, fit, MODES
from .paint import (render, ascii_art, draw, patch, sheet, compare,
                    sample_style, parse_color, gradient)

from . import metrics, paint, font as fontmod, container

__all__ = ['Font', 'SIZES', 'FONTS', 'MODES', 'Mask', 'layout', 'wrap', 'measure',
           'fit', 'render', 'ascii_art', 'draw', 'patch', 'sheet', 'compare',
           'sample_style', 'parse_color', 'gradient', 'metrics', 'paint']
__version__ = '1.0'
