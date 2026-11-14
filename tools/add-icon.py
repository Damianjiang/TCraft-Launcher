#!/usr/bin/env python3
"""给 TCC 生成的 PE 注入 .rsrc 节（主要是程序图标）。

TCC 不支持链接 .rc/.res，而 Windows 资源管理器读的是 PE 里的 RT_GROUP_ICON /
RT_ICON，所以这里直接按 PE 规范手工构造资源节：

    python tools/add-icon.py <in.exe> <out.exe> <icon.ico>

注意：必须在「jar 追加到 exe 末尾」之前做，否则要搬动几十 MB 数据。
"""

import struct
import sys

RT_ICON = 3
RT_GROUP_ICON = 14
LANG_ZH = 0x409


def parse_ico(data):
    """返回 [(w, h, colors, planes, bpp, payload_bytes), ...]"""
    _res, typ, count = struct.unpack_from("<HHH", data, 0)
    if typ != 1:
        raise ValueError("不是 ico 文件")
    out = []
    for i in range(count):
        w, h, colors, _r, planes, bpp, size, off = struct.unpack_from("<BBBBHHII", data, 6 + i * 16)
        out.append((w, h, colors, planes, bpp, data[off:off + size]))
    return out


def align(v, a):
    return (v + a - 1) // a * a


def build_icons(images, first_id=1):
    """构造 RT_ICON 数据 与 RT_GROUP_ICON 数据"""
    icons = []  # (id, bytes)
    entries = []
    for i, (w, h, colors, planes, bpp, payload) in enumerate(images):
        rid = first_id + i
        icons.append((rid, payload))
        entries.append((w, h, colors, planes, bpp, len(payload), rid))

    grp = struct.pack("<HHH", 0, 1, len(entries))
    for w, h, colors, planes, bpp, size, rid in entries:
        grp += struct.pack("<BBBBHHIH", w, h, colors, 0, planes, bpp, size, rid)
    return icons, grp


def build_rsrc_section(images):
    """返回 (rsrc_bytes, 资源目录大小)"""
    icons, grp = build_icons(images)

    # 资源树：类型 -> ID -> 语言 -> 数据
    tree = {
        RT_ICON: {rid: {LANG_ZH: ("icon", data)} for rid, data in icons},
        RT_GROUP_ICON: {1: {LANG_ZH: ("group", grp)}},
    }

    # pass 1：计算目录结构大小，分配偏移
    dirs = []      # (offset, [(name, is_dir, target_offset_or_data_index)])
    datas = []     # data bytes，按顺序

    def walk(node, base):
        ids = sorted(node.keys())
        size = 16 + 8 * len(ids)
        entry_offsets = []
        cur = base + size
        for name in ids:
            child = node[name]
            if isinstance(child, dict):
                entry_offsets.append((name, True, cur))
                cur = walk(child, cur)
            else:
                idx = len(datas)
                datas.append(child[1])
                entry_offsets.append((name, False, idx))
        dirs.append((base, entry_offsets, len(ids)))
        return cur

    tree_end = walk(tree, 0)

    # 数据项表紧跟在目录之后
    de_base = align(tree_end, 4)
    de_end = de_base + 16 * len(datas)
    blob_base = align(de_end, 4)

    # 分配每个数据的偏移
    blob_offsets = []
    cur = blob_base
    for b in datas:
        blob_offsets.append(cur)
        cur += len(b)
        cur = align(cur, 4)
    total = cur

    buf = bytearray(total)

    # 写目录
    for base, entries, count in dirs:
        struct.pack_into("<IIHHHH", buf, base, 0, 0, 0, 0, 0, count)
        off = base + 16
        for name, is_dir, target in entries:
            if is_dir:
                struct.pack_into("<II", buf, off, name, 0x80000000 | target)
            else:
                struct.pack_into("<II", buf, off, name, 0)  # 先占位，下面 fix 成数据项偏移
            off += 8

    # 修正：叶子目录的 entry 指向数据项（需要 RVA，稍后填）；这里先写 0x80000000|de_offset
    # 重新按同样顺序遍历一次，替换为数据项偏移
    de_cursor = 0

    def fix(node, base):
        nonlocal de_cursor
        ids = sorted(node.keys())
        off = base + 16
        for name in ids:
            child = node[name]
            if isinstance(child, dict):
                target = struct.unpack_from("<I", buf, off + 4)[0] & 0x7FFFFFFF
                fix(child, target)
            else:
                # 叶子节点指向 IMAGE_RESOURCE_DATA_ENTRY，这里不能带 0x80000000
                struct.pack_into("<I", buf, off + 4, de_base + de_cursor * 16)
                de_cursor += 1
            off += 8

    fix(tree, 0)

    # 写数据项（OffsetToData 先填节内偏移，调用方再加 rva）
    for i, (b, o) in enumerate(zip(datas, blob_offsets)):
        struct.pack_into("<IIII", buf, de_base + i * 16, o, len(b), 0, 0)
        buf[o:o + len(b)] = b

    return bytes(buf), total, de_base, blob_offsets


def inject(src, dst, ico_path):
    data = bytearray(open(src, "rb").read())
    pe = struct.unpack_from("<I", data, 0x3C)[0]
    nsec = struct.unpack_from("<H", data, pe + 6)[0]
    optsz = struct.unpack_from("<H", data, pe + 20)[0]
    opt = pe + 24
    magic = struct.unpack_from("<H", data, opt)[0]
    if magic != 0x10B:
        raise ValueError("只处理 PE32（TCC 输出）")

    secalign = struct.unpack_from("<I", data, opt + 32)[0]
    filealign = struct.unpack_from("<I", data, opt + 36)[0]
    sizeofimage = struct.unpack_from("<I", data, opt + 56)[0]
    sizeofheaders = struct.unpack_from("<I", data, opt + 60)[0]

    sec_table = opt + optsz
    if sizeofheaders - (sec_table + nsec * 40) < 40:
        raise ValueError("头部没有空间放新节表项")

    images = parse_ico(open(ico_path, "rb").read())
    rsrc, rsrc_size, de_base, blobs = build_rsrc_section(images)

    # 新节位置
    last = sec_table + (nsec - 1) * 40
    last_va = struct.unpack_from("<I", data, last + 12)[0]
    last_vsz = struct.unpack_from("<I", data, last + 8)[0]
    new_va = align(last_va + last_vsz, secalign)
    raw_off = align(len(data), filealign)
    raw_size = align(len(rsrc), filealign)

    # 数据项里的 OffsetToData 必须是 RVA：节内偏移 + new_va
    fixed = bytearray(rsrc)
    for i in range(len(blobs)):
        pos = de_base + i * 16
        inner = struct.unpack_from("<I", fixed, pos)[0]
        struct.pack_into("<I", fixed, pos, inner + new_va)

    # 写节表项
    sh = sec_table + nsec * 40
    name = b".rsrc\0\0\0"
    chars = 0x40000040  # INITIALIZED_DATA | MEM_READ
    struct.pack_into("<8sIIIIIIHHI", data, sh, name, len(fixed), new_va, raw_size, raw_off,
                     0, 0, 0, 0, chars)

    # 新节数据
    data += b"\0" * (raw_off - len(data))
    data += bytes(fixed)
    data += b"\0" * (raw_size - len(fixed))

    # 头部字段
    struct.pack_into("<H", data, pe + 6, nsec + 1)
    struct.pack_into("<I", data, opt + 56, align(new_va + len(fixed), secalign))
    init_data = struct.unpack_from("<I", data, opt + 8)[0]
    struct.pack_into("<I", data, opt + 8, init_data + raw_size)

    # 资源目录项（第 3 个）
    dd = opt + 96 + 2 * 8
    struct.pack_into("<II", data, dd, new_va, len(fixed))

    open(dst, "wb").write(bytes(data))
    return new_va, len(fixed), raw_off, raw_size


if __name__ == "__main__":
    if len(sys.argv) != 4:
        print(__doc__)
        sys.exit(1)
    va, size, off, rsize = inject(sys.argv[1], sys.argv[2], sys.argv[3])
    print(f"rsrc: rva={va:#x} size={size} rawoff={off:#x} rawsize={rsize:#x}")
