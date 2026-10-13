"""Fixed vtable-slot layouts of core classes in the LGT Java runtime.

Data derived from wie-lgt/data/lgt_java_abi.toml (https://github.com/dlunch/wie, MIT, created by Inseok Lee).
Keys are *method indexes* (vtable word = index + 1).  Additional entries are added only with evidence
(see EVIDENCE below) and tagged with a confidence level.
"""

CORE = {
    'java/lang/Object': dict(vtable_size=10,
        vtable={
            1: ('getClass', '()Ljava/lang/Class;'),
            3: ('equals', '(Ljava/lang/Object;)Z'),
            4: ('toString', '()Ljava/lang/String;'),
        }),
    'java/lang/Class': dict(vtable_size=None,
        vtable={
            16: ('getResourceAsStream', '(Ljava/lang/String;)Ljava/io/InputStream;'),
        }),
    'java/io/InputStream': dict(vtable_size=None,
        vtable={
            10: ('read', '()I'),
            11: ('read', '([B)I'),
            12: ('read', '([BII)I'),
            14: ('available', '()I'),
            15: ('close', '()V'),
        }),
    'java/io/Reader': dict(vtable_size=None,
        vtable={
            11: ('read', '([C)I'),
            18: ('close', '()V'),
        }),
    'java/io/DataInputStream': dict(vtable_size=None,
        vtable={
            23: ('readByte', '()B'),
        }),
    'java/lang/String': dict(vtable_size=None,
        vtable={
            10: ('length', '()I'),
            11: ('charAt', '(I)C'),
            14: ('getBytes', '()[B'),
            28: ('substring', '(II)Ljava/lang/String;'),
            33: ('trim', '()Ljava/lang/String;'),
            34: ('toCharArray', '()[C'),
        }),
    'java/lang/StringBuffer': dict(vtable_size=None,
        vtable={
            17: ('append', '(Ljava/lang/Object;)Ljava/lang/StringBuffer;'),
            18: ('append', '(Ljava/lang/String;)Ljava/lang/StringBuffer;'),
            23: ('append', '(I)Ljava/lang/StringBuffer;'),
            27: ('delete', '(II)Ljava/lang/StringBuffer;'),
        }),
    'java/io/PrintStream': dict(vtable_size=None,
        vtable={
            34: ('println', '(Ljava/lang/String;)V'),
        }),
    'java/lang/Thread': dict(vtable_size=None,
        vtable={
            10: ('start', '()V'),
            14: ('setPriority', '(I)V'),
        }),
    'java/util/Vector': dict(vtable_size=None,
        vtable={
            15: ('size', '()I'),
            23: ('elementAt', '(I)Ljava/lang/Object;'),
            27: ('removeElementAt', '(I)V'),
            28: ('insertElementAt', '(Ljava/lang/Object;I)V'),
        }),
    'java/lang/Runnable': dict(vtable_size=None,
        vtable={
            10: ('run', '()V'),
        }),
    'java/util/Random': dict(vtable_size=None,
        vtable={
            12: ('nextInt', '()I'),
        }),
    'org/kwis/msp/lcdui/Jlet': dict(vtable_size=None,
        vtable={
            15: ('startApp', '([Ljava/lang/String;)V'),
            16: ('pauseApp', '()V'),
            17: ('resumeApp', '()V'),
            18: ('destroyApp', '(Z)V'),
        }),
    'org/kwis/msp/lcdui/Card': dict(vtable_size=None,
        vtable={
            25: ('keyNotify', '(II)Z'),
            27: ('paint', '(Lorg/kwis/msp/lcdui/Graphics;)V'),
        }),
}
