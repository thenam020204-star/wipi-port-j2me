package hl4.game;

/**
 * The game's global structs (.bss of the original) kept as byte arrays with the ORIGINAL offsets and widths, so each field keeps its exact
 * truncation/sign behaviour and the whole state can be compared byte for byte with the original.  Pointer-valued fields are Java
 * references held in the 'ptr*' members next to the array (the bytes of those slots stay 0).
 */
public final class G {
    /** _game (296 bytes): +1 game mode, +0x68../+0xa0.. item-data table pointers, +0x114 option table pointer, ... */
    public static final byte[] game = new byte[296];
    /** _map (896 bytes) */
    public static final byte[] map = new byte[896];
    /** _event (2388 bytes) */
    public static final byte[] event = new byte[2388];
    /** _npcUi (984 bytes) @0x14d298: NPC / shop UI state (+2 mode, +0xcb ...) */
    public static final byte[] npcUi = new byte[984];
    /** _gameMenu (36 bytes) */
    public static final byte[] gameMenu = new byte[36];
    /** _slotInfo (48 bytes: 3 slots x 16) */
    public static final byte[] slotInfo = new byte[48];
    /** _questMenu (8463 bytes) */
    public static final byte[] questMenu = new byte[8463];
    /** _guardian (8380 bytes) */
    public static final byte[] guardian = new byte[8380];

    /** _equip (7 pointers): 0..3 armour pieces, 4 weapon, 5, 6 accessories */
    public static final Obj[] equip = new Obj[7];
    /** _armsEquip (6 pointers) */
    public static final Obj[] armsEquip = new Obj[6];

    /** _itemBag (532 bytes): LinkedList of items at +4, pointer list at +0x10 */
    public static final LList itemBag = new LList();
    public static final PList itemBagPtrs = new PList();
    /** _armsBag (16 bytes) */
    public static final LList armsBag = new LList();
    public static int itemBagMax;
    public static int armsSlotCnt;
    public static int guardianSlotCnt;

    /** item data tables (_game + 0xa0 + type*4 for types 0..13; _game + 0xd8.. for the 7 skin tables; _game + 0x114 option table) */
    public static final byte[][] itemData = new byte[14][];
    public static final byte[][] itemSd = new byte[7][];
    public static byte[] itemOption;

    /** _resultItem (624 bytes): scratch copy of the last created item */
    public static final byte[] resultItem = new byte[0x270];

    /** _npcList / _monsterList / _targetEffectList (pointer lists of live objects) */
    public static final PList npcList = new PList();
    /** _globalFrameCnt (u16 game-tick counter, wraps after 9999) */
    public static int globalFrame;
    public static final PList monsterList = new PList();
    public static final PList targetEffectList = new PList();
    /** _objList (sorted draw list, holds the hero, NPCs, monsters, middle map objects), _topObjectList, _bottomObjectList, _moreBottomObjectList, _dropedItem, _tileList, _netItemList */
    public static final LList objList = new LList();
    public static final LList topObjectList = new LList();
    public static final LList bottomObjectList = new LList();
    public static final LList moreBottomObjectList = new LList();
    public static final LList dropedItem = new LList();
    public static final LList tileList = new LList();
    public static final LList netItemList = new LList();

    private G() {
    }
}
