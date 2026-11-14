/*
 * Windows 外壳：构建时把 jar 追加到 exe 末尾。
 * 运行时按 zip 中央目录找到 jar 偏移，解到 exe 同目录的 .tcl 里，
 * 再用系统 Java 启动。和 HMCL 的 exe 一个路子。
 */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>

#define APP_NAME "TCraft Launcher"
#define JAR_NAME L"TCraftLauncher.jar"
/* 跟 HMCL 的 ./.hmcl 一个路子：启动器自己的文件（jar、记录）放在 exe 同目录的 .tcl 里。
   不放 %TEMP% / %LOCALAPPDATA%，免得被磁盘清理工具当临时文件删掉。 */
#define HOME_SUB L".tcl"
#define CFG_NAME L"launcher.cfg"

#define MAX_PATH_LEN 4096

/* TCC 的 windows.h 缺 winnls.h，手动声明 */
#ifndef CP_UTF8
#define CP_UTF8 65001
#endif
int __stdcall MultiByteToWideChar(unsigned int, unsigned long, const char *, int, wchar_t *, int);
int __stdcall WideCharToMultiByte(unsigned int, unsigned long, const wchar_t *, int, char *, int, const char *, int *);

static wchar_t self_path[MAX_PATH_LEN];
static wchar_t home_dir[MAX_PATH_LEN];
static wchar_t jar_path[MAX_PATH_LEN];
static wchar_t cfg_java[MAX_PATH_LEN]; /* .tcl\launcher.cfg 里记住的 java 路径 */

/* 中文按 UTF-8 转宽字符，再交给 MessageBoxW */
static void alert(const char *utf8) {
    wchar_t wide[1024];
    MultiByteToWideChar(CP_UTF8, 0, utf8, -1, wide, 1024);
    MessageBoxW(NULL, wide, L"TCraft Launcher", MB_ICONERROR | MB_OK);
}

static void copy_str(wchar_t *dst, const wchar_t *src, int cap) {
    int i = 0;
    if (cap <= 0) return;
    while (i < cap - 1 && src[i]) {
        dst[i] = src[i];
        i++;
    }
    dst[i] = 0;
}

static void cat_str(wchar_t *dst, const wchar_t *src, int cap) {
    int i = 0;
    while (i < cap - 1 && dst[i]) i++;
    while (i < cap - 1 && *src) {
        dst[i] = *src;
        i++;
        src++;
    }
    dst[i] = 0;
}

static void strip_file(wchar_t *path) {
    int i = (int)wcslen(path);
    while (i > 0 && path[i - 1] != L'\\' && path[i - 1] != L'/') i--;
    if (i > 0) path[i - 1] = 0;
}

static int file_exists(const wchar_t *path) {
    DWORD attr = GetFileAttributesW(path);
    return attr != INVALID_FILE_ATTRIBUTES && !(attr & FILE_ATTRIBUTE_DIRECTORY);
}

static int create_dirs(const wchar_t *path) {
    wchar_t buf[MAX_PATH_LEN];
    int i;
    copy_str(buf, path, MAX_PATH_LEN);
    for (i = 3; buf[i]; i++) {
        if (buf[i] == L'\\') {
            buf[i] = 0;
            CreateDirectoryW(buf, NULL);
            buf[i] = L'\\';
        }
    }
    CreateDirectoryW(buf, NULL);
    return 1;
}

/* 目录是否可写（拿一个临时文件试，比看属性可靠） */
static int dir_writable(const wchar_t *dir) {
    wchar_t probe[MAX_PATH_LEN];
    HANDLE h;

    copy_str(probe, dir, MAX_PATH_LEN);
    cat_str(probe, L"\\.write-test", MAX_PATH_LEN);
    h = CreateFileW(probe, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS,
                    FILE_ATTRIBUTE_TEMPORARY | FILE_FLAG_DELETE_ON_CLOSE, NULL);
    if (h == INVALID_HANDLE_VALUE) return 0;
    CloseHandle(h);
    return 1;
}

/* 启动器自己的工作目录：
   首选 exe 同目录的 .tcl（跟着程序走，重装/移动都在一起）；
   只有装在 Program Files、只读 U 盘这类不可写位置时，才退回 %LOCALAPPDATA%。 */
static void init_home(void) {
    wchar_t exe_dir[MAX_PATH_LEN];
    wchar_t alt[MAX_PATH_LEN];

    copy_str(exe_dir, self_path, MAX_PATH_LEN);
    strip_file(exe_dir);

    copy_str(home_dir, exe_dir, MAX_PATH_LEN);
    cat_str(home_dir, L"\\" HOME_SUB, MAX_PATH_LEN);
    create_dirs(home_dir);
    if (dir_writable(home_dir)) return;

    if (GetEnvironmentVariableW(L"LOCALAPPDATA", alt, MAX_PATH_LEN) && alt[0]) {
        copy_str(home_dir, alt, MAX_PATH_LEN);
        cat_str(home_dir, L"\\" HOME_SUB, MAX_PATH_LEN);
        create_dirs(home_dir);
    }
}

/* 递归删目录（失败就跳过，不弹任何东西） */
static void remove_tree(const wchar_t *path, int depth) {
    WIN32_FIND_DATAW data;
    wchar_t pattern[MAX_PATH_LEN];
    wchar_t sub[MAX_PATH_LEN];
    HANDLE h;

    if (depth > 8) return;
    copy_str(pattern, path, MAX_PATH_LEN);
    cat_str(pattern, L"\\*", MAX_PATH_LEN);
    h = FindFirstFileW(pattern, &data);
    if (h == INVALID_HANDLE_VALUE) return;
    do {
        if (data.cFileName[0] == L'.') {
            if (data.cFileName[1] == 0) continue;
            if (data.cFileName[1] == L'.' && data.cFileName[2] == 0) continue;
        }
        copy_str(sub, path, MAX_PATH_LEN);
        cat_str(sub, L"\\", MAX_PATH_LEN);
        cat_str(sub, data.cFileName, MAX_PATH_LEN);
        if (data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
            remove_tree(sub, depth + 1);
        } else {
            SetFileAttributesW(sub, FILE_ATTRIBUTE_NORMAL);
            DeleteFileW(sub);
        }
    } while (FindNextFileW(h, &data));
    FindClose(h);
    RemoveDirectoryW(path);
}

/* 老版本把 jar 解在 %LOCALAPPDATA%\TCraftLauncher，现在改用 .tcl 了，
   这里顺手把那份残留清掉（只动这一个固定路径，失败也不报错）。 */
static void cleanup_legacy_home(void) {
    wchar_t alt[MAX_PATH_LEN];
    wchar_t old[MAX_PATH_LEN];

    if (!GetEnvironmentVariableW(L"LOCALAPPDATA", alt, MAX_PATH_LEN) || !alt[0]) return;
    copy_str(old, alt, MAX_PATH_LEN);
    cat_str(old, L"\\TCraftLauncher", MAX_PATH_LEN);
    if (wcscmp(old, home_dir) == 0) return; /* 正回退到那儿，别删自己 */
    if (GetFileAttributesW(old) == INVALID_FILE_ATTRIBUTES) return;
    remove_tree(old, 0);
}

static void cfg_path(wchar_t *out, int cap) {
    copy_str(out, home_dir, cap);
    cat_str(out, L"\\" CFG_NAME, cap);
}

static int read_file(const wchar_t *path, char *buf, int cap) {
    HANDLE fh;
    DWORD got = 0;

    fh = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE,
                     NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    if (fh == INVALID_HANDLE_VALUE) return 0;
    if (!ReadFile(fh, buf, (DWORD)(cap - 1), &got, NULL)) got = 0;
    CloseHandle(fh);
    return (int)got;
}

/* 记住用户手动选的 java，下次直接用它（路径失效了会自动重新扫描） */
static void load_cfg(void) {
    wchar_t p[MAX_PATH_LEN];
    char buf[2048];
    int got, i;

    cfg_java[0] = 0;
    cfg_path(p, MAX_PATH_LEN);
    got = read_file(p, buf, sizeof(buf));
    if (got <= 0) return;
    buf[got] = 0;
    if (MultiByteToWideChar(CP_UTF8, 0, buf, -1, cfg_java, MAX_PATH_LEN) <= 0) {
        cfg_java[0] = 0;
        return;
    }
    for (i = 0; cfg_java[i]; i++) {
        if (cfg_java[i] == L'\r' || cfg_java[i] == L'\n') cfg_java[i] = 0;
    }
}

static void save_cfg(const wchar_t *java) {
    wchar_t p[MAX_PATH_LEN];
    char utf8[2048];
    int n;
    HANDLE h;
    DWORD wrote = 0;

    copy_str(cfg_java, java, MAX_PATH_LEN);
    cfg_path(p, MAX_PATH_LEN);
    n = WideCharToMultiByte(CP_UTF8, 0, java, -1, utf8, sizeof(utf8), NULL, NULL);
    if (n <= 0) return;
    h = CreateFileW(p, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (h == INVALID_HANDLE_VALUE) return;
    WriteFile(h, utf8, (DWORD)(n - 1), &wrote, NULL);
    CloseHandle(h);
}

/* 从末尾找 zip 的 EOCD，算出追加的 jar 从哪个偏移开始 */
static unsigned long long jar_offset(const wchar_t *exe, unsigned long long *size_out) {
    HANDLE fh;
    LARGE_INTEGER size;
    unsigned char *tail;
    unsigned long long tail_len, start, i;
    unsigned long long offset = 0;

    fh = CreateFileW(exe, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING,
                     FILE_ATTRIBUTE_NORMAL, NULL);
    if (fh == INVALID_HANDLE_VALUE) return 0;

    size.LowPart = GetFileSize(fh, &size.HighPart);
    if (size.LowPart == INVALID_FILE_SIZE) { CloseHandle(fh); return 0; }
    if ((unsigned long long)size.QuadPart < 100) { CloseHandle(fh); return 0; }
    *size_out = (unsigned long long)size.QuadPart;

    tail_len = (unsigned long long)size.QuadPart;
    if (tail_len > 66000) tail_len = 66000;
    start = (unsigned long long)size.QuadPart - tail_len;

    tail = (unsigned char *)malloc((size_t)tail_len);
    if (!tail) { CloseHandle(fh); return 0; }

    {
        DWORD got = 0;
        SetFilePointer(fh, (LONG)start, NULL, FILE_BEGIN);
        if (!ReadFile(fh, tail, (DWORD)tail_len, &got, NULL) || got != tail_len) {
            free(tail);
            CloseHandle(fh);
            return 0;
        }
    }
    CloseHandle(fh);

    /* EOCD 就在末尾附近，从后往前第一个签名可信 */
    for (i = tail_len - 4; ; i--) {
        if (tail[i] == 0x50 && tail[i + 1] == 0x4b && tail[i + 2] == 0x05 && tail[i + 3] == 0x06) {
            unsigned long long cd_size = (unsigned long long)tail[i + 12]
                    | ((unsigned long long)tail[i + 13] << 8)
                    | ((unsigned long long)tail[i + 14] << 16)
                    | ((unsigned long long)tail[i + 15] << 24);
            unsigned long long cd_off = (unsigned long long)tail[i + 16]
                    | ((unsigned long long)tail[i + 17] << 8)
                    | ((unsigned long long)tail[i + 18] << 16)
                    | ((unsigned long long)tail[i + 19] << 24);
            unsigned long long eocd = start + i;
            if (eocd >= cd_size + cd_off) {
                offset = eocd - cd_size - cd_off;
            }
            break;
        }
        if (i == 0) break;
    }
    free(tail);
    return offset;
}

static int copy_range(const wchar_t *src, unsigned long long from, unsigned long long len,
                      const wchar_t *dst) {
    HANDLE in, out;
    unsigned char *buf = (unsigned char *)malloc(65536);
    int ok = 0;

    if (!buf) return 0;
    in = CreateFileW(src, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING,
                     FILE_ATTRIBUTE_NORMAL, NULL);
    if (in == INVALID_HANDLE_VALUE) { free(buf); return 0; }

    out = CreateFileW(dst, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS,
                      FILE_ATTRIBUTE_NORMAL, NULL);
    if (out == INVALID_HANDLE_VALUE) { CloseHandle(in); free(buf); return 0; }

    SetFilePointer(in, (LONG)from, NULL, FILE_BEGIN);

    while (len > 0) {
        DWORD want = len > 65536 ? 65536 : (DWORD)len;
        DWORD got = 0, wrote = 0;
        if (!ReadFile(in, buf, want, &got, NULL) || got == 0) break;
        if (!WriteFile(out, buf, got, &wrote, NULL) || wrote != got) break;
        len -= got;
        if (len == 0) ok = 1;
    }
    CloseHandle(in);
    CloseHandle(out);
    free(buf);
    return ok;
}

/* ---------- Java 探测 ----------
 * 这里有三个坑，之前踩的就是它们：
 * 1. 这个 exe 是 32 位的（TCC 编译），64 位 Windows 会把 C:\Program Files 重定向到
 *    C:\Program Files (x86)，所以硬编码的根目录永远扫不到 64 位 JDK。
 *    根目录必须从环境变量取真身：ProgramW6432 才是真的 C:\Program Files。
 * 2. 不能"找到第一个就用"。很多机器 PATH / JAVA_HOME 里躺着老 Java 8，
 *    真正装好的 17/21 在别的目录，于是被误报成"没有 Java 17"。
 *    改成：先收集所有候选，再逐个测版本，取最高且 >= 17 的那个。
 * 3. 随包的 JavaFX 运行库是 x64（lib/javafx_font.dll 是 PE32+），
 *    所以同版本下优先 64 位 JVM，否则加载 DLL 会 UnsatisfiedLinkError。
 */

#define MAX_CAND 32

static wchar_t cands[MAX_CAND][MAX_PATH_LEN];
static int cand_count;
static int found_major; /* 探测到的最高主版本，只用于提示文案 */

/* "17.0.21" 取 17，"1.8.0_402" 取 8 */
static int parse_major(const char *s) {
    if (s[0] == '1' && s[1] == '.') return atoi(s + 2);
    return atoi(s);
}

/* release 文件里的 OS_ARCH 判断位数 */
static int java_is64(const wchar_t *java) {
    wchar_t home[MAX_PATH_LEN];
    wchar_t rel[MAX_PATH_LEN];
    char buf[4096];
    int got;

    copy_str(home, java, MAX_PATH_LEN);
    strip_file(home); /* 去掉 java(w).exe */
    strip_file(home); /* 去掉 bin */
    copy_str(rel, home, MAX_PATH_LEN);
    cat_str(rel, L"\\release", MAX_PATH_LEN);
    got = read_file(rel, buf, sizeof(buf));
    if (got <= 0) return 0;
    buf[got] = 0;
    if (strstr(buf, "OS_ARCH=\"x86_64\"") || strstr(buf, "OS_ARCH=\"amd64\"")
        || strstr(buf, "OS_ARCH=\"aarch64\"") || strstr(buf, "OS_ARCH=\"riscv64\"")) {
        return 1;
    }
    return 0;
}

/* 读主版本号：先看 release 文件（不启进程），读不到再退回 java -version */
static int java_feature(const wchar_t *java) {
    wchar_t home[MAX_PATH_LEN];
    wchar_t rel[MAX_PATH_LEN];
    wchar_t probe[MAX_PATH_LEN];
    wchar_t cmd[MAX_PATH_LEN * 2];
    wchar_t logp[MAX_PATH_LEN];
    SECURITY_ATTRIBUTES sa;
    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    HANDLE out;
    char buf[8192];
    DWORD got = 0;
    int major = 0;
    int n;
    char *hit;

    copy_str(home, java, MAX_PATH_LEN);
    strip_file(home);
    strip_file(home);

    copy_str(rel, home, MAX_PATH_LEN);
    cat_str(rel, L"\\release", MAX_PATH_LEN);
    {
        int len = read_file(rel, buf, sizeof(buf));
        if (len > 0) {
            buf[len] = 0;
            hit = strstr(buf, "JAVA_VERSION=\"");
            if (hit) {
                major = parse_major(hit + 14);
                if (major > 0) return major;
            }
        }
    }

    /* javaw.exe 在无控制台时不一定吐版本，探测统一换成同目录的 java.exe */
    copy_str(probe, java, MAX_PATH_LEN);
    n = (int)wcslen(probe);
    if (n > 9 && wcscmp(probe + n - 9, L"javaw.exe") == 0) {
        int k;
        for (k = 0; k < 8; k++) probe[n - 9 + k] = L"java.exe"[k];
        probe[n - 1] = 0; /* javaw.exe 是 9 个字符，换成 8 个后要补结尾 */
        if (!file_exists(probe)) copy_str(probe, java, MAX_PATH_LEN);
    }

    copy_str(logp, home_dir, MAX_PATH_LEN);
    cat_str(logp, L"\\java-version.txt", MAX_PATH_LEN);

    sa.nLength = sizeof(sa);
    sa.lpSecurityDescriptor = NULL;
    sa.bInheritHandle = TRUE;
    out = CreateFileW(logp, GENERIC_WRITE, FILE_SHARE_READ, &sa, CREATE_ALWAYS,
                      FILE_ATTRIBUTE_NORMAL, NULL);
    if (out == INVALID_HANDLE_VALUE) return 0;

    copy_str(cmd, L"\"", MAX_PATH_LEN * 2);
    cat_str(cmd, probe, MAX_PATH_LEN * 2);
    cat_str(cmd, L"\" -version", MAX_PATH_LEN * 2);

    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESTDHANDLES | STARTF_USESHOWWINDOW;
    si.hStdOutput = out;
    si.hStdError = out;
    si.wShowWindow = SW_HIDE;
    ZeroMemory(&pi, sizeof(pi));

    if (!CreateProcessW(probe, cmd, NULL, NULL, TRUE, CREATE_NO_WINDOW, NULL, NULL, &si, &pi)) {
        CloseHandle(out);
        DeleteFileW(logp);
        return 0;
    }
    WaitForSingleObject(pi.hProcess, 8000);
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    CloseHandle(out);

    {
        HANDLE rd = CreateFileW(logp, GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE,
                                NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
        if (rd == INVALID_HANDLE_VALUE) return 0;
        ReadFile(rd, buf, sizeof(buf) - 1, &got, NULL);
        CloseHandle(rd);
    }
    DeleteFileW(logp);
    buf[got] = 0;

    hit = strstr(buf, "version \"");
    if (hit) major = parse_major(hit + 9);
    return major;
}

/* 去引号、去结尾反斜杠（PATH 里偶尔写成 "C:\xx\bin\"） */
static void strip_path_entry(const wchar_t *in, wchar_t *out, int cap) {
    int i = 0, len;

    if (!in || !in[0]) { out[0] = 0; return; }
    while (in[i] == L'"' || in[i] == L' ') i++;
    len = (int)wcslen(in + i);
    while (len > 0 && (in[i + len - 1] == L'"' || in[i + len - 1] == L' ')) len--;
    while (len > 1 && in[i + len - 1] == L'\\') len--;
    if (len <= 0) { out[0] = 0; return; }
    if (len > cap - 1) len = cap - 1;
    {
        int k;
        for (k = 0; k < len; k++) out[k] = in[i + k];
        out[len] = 0;
    }
}

static void cand_add(const wchar_t *path) {
    int i;

    if (cand_count >= MAX_CAND) return;
    if (!file_exists(path)) return;
    for (i = 0; i < cand_count; i++) {
        if (wcscmp(cands[i], path) == 0) return;
    }
    copy_str(cands[cand_count], path, MAX_PATH_LEN);
    cand_count++;
}

/* home 目录下找 bin\javaw.exe，退一步 bin\java.exe；也兼容直接给到 bin 的 JAVA_HOME */
static void cand_add_home(const wchar_t *home_in) {
    wchar_t home[MAX_PATH_LEN];
    wchar_t p[MAX_PATH_LEN];
    const wchar_t *names[] = {L"\\bin\\javaw.exe", L"\\bin\\java.exe",
                              L"\\javaw.exe", L"\\java.exe", NULL};
    int i;

    strip_path_entry(home_in, home, MAX_PATH_LEN);
    if (!home[0]) return;
    for (i = 0; names[i]; i++) {
        copy_str(p, home, MAX_PATH_LEN);
        cat_str(p, names[i], MAX_PATH_LEN);
        if (file_exists(p)) { cand_add(p); return; }
    }
}

/* 在某个安装根目录下往下 depth 层找 JDK */
static void scan_tree(const wchar_t *root, int depth) {
    WIN32_FIND_DATAW data;
    wchar_t pattern[MAX_PATH_LEN];
    wchar_t sub[MAX_PATH_LEN];
    HANDLE h;

    if (depth <= 0) return;
    copy_str(pattern, root, MAX_PATH_LEN);
    cat_str(pattern, L"\\*", MAX_PATH_LEN);
    h = FindFirstFileW(pattern, &data);
    if (h == INVALID_HANDLE_VALUE) return;
    do {
        if (!(data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)) continue;
        if (data.cFileName[0] == L'.') continue;
        copy_str(sub, root, MAX_PATH_LEN);
        cat_str(sub, L"\\", MAX_PATH_LEN);
        cat_str(sub, data.cFileName, MAX_PATH_LEN);
        cand_add_home(sub);
        scan_tree(sub, depth - 1);
    } while (FindNextFileW(h, &data));
    FindClose(h);
}

static void scan_env_root(const wchar_t *name, const wchar_t *suffix, int depth) {
    wchar_t buf[MAX_PATH_LEN];

    if (!GetEnvironmentVariableW(name, buf, MAX_PATH_LEN) || !buf[0]) return;
    if (suffix) cat_str(buf, suffix, MAX_PATH_LEN);
    scan_tree(buf, depth);
}

static void scan_path(void) {
    static wchar_t all[32768];
    wchar_t one[MAX_PATH_LEN];
    int i = 0;

    if (!GetEnvironmentVariableW(L"PATH", all, 32768)) return;
    while (all[i]) {
        int start = i;
        int len;
        while (all[i] && all[i] != L';') i++;
        len = i - start;
        if (len > 0 && len < MAX_PATH_LEN) {
            int k;
            for (k = 0; k < len; k++) one[k] = all[start + k];
            one[len] = 0;
            cand_add_home(one);
        }
        if (all[i] == L';') i++;
    }
}

/* 收集候选：上次记录的 → JAVA_HOME → PATH → 各安装根目录。
   只做"找"，不做版本探测，UI 的扫描进度要按这一步的候选数量走。 */
static void collect_candidates(void) {
    wchar_t jh[MAX_PATH_LEN];

    cand_count = 0;

    if (cfg_java[0] && file_exists(cfg_java)) cand_add(cfg_java);

    /* JAVA_HOME 只是候选之一，它可能指向旧版 */
    if (GetEnvironmentVariableW(L"JAVA_HOME", jh, MAX_PATH_LEN) && jh[0]) cand_add_home(jh);
    scan_path();

    /* 32 位进程下 ProgramFiles 指向 (x86)，ProgramW6432 才是真身 */
    scan_env_root(L"ProgramW6432", NULL, 2);
    scan_env_root(L"ProgramFiles", NULL, 2);
    scan_env_root(L"ProgramFiles(x86)", NULL, 2);
    scan_env_root(L"LOCALAPPDATA", L"\\Programs", 3);   /* 用户级安装 */
    scan_env_root(L"USERPROFILE", L"\\.jdks", 2);       /* IDEA 下载的 JDK */
    scan_env_root(L"USERPROFILE", L"\\scoop\\apps", 3); /* scoop */
}

/* 无 UI 的快速挑选：先认上次记住的路径，再退回全盘扫描 */
static int pick_best_java(wchar_t *out, int cap) {
    int i, best = -1, best_major = 0, best64 = 0, unknown = -1;

    found_major = 0;

    /* 1. 上次用户手动选定的路径，优先使用。
          这是"记住我的选择"的语义：不能被机器上另一个更高版本的 JDK 顶掉，
          否则用户每次看到的运行时都跟上次不一样。 */
    if (cfg_java[0] && file_exists(cfg_java)) {
        int major = java_feature(cfg_java);
        if (major == 0 || major >= 17) {
            copy_str(out, cfg_java, cap);
            return 1;
        }
        found_major = major; /* 记录里的版本太低，留给弹窗文案用 */
    }

    /* 2. 没有记录、或记录里的 Java 被删/被移动/版本降级：重新全盘扫一遍 */
    collect_candidates();

    for (i = 0; i < cand_count; i++) {
        int major = java_feature(cands[i]);
        int is64 = java_is64(cands[i]);

        if (major <= 0) {
            if (unknown < 0) unknown = i;
            continue;
        }
        if (major > found_major) found_major = major;
        if (major < 17) continue;
        if (best < 0 || is64 > best64 || (is64 == best64 && major > best_major)) {
            best = i;
            best_major = major;
            best64 = is64;
        }
    }

    if (best >= 0) {
        copy_str(out, cands[best], cap);
        return 1;
    }

    /* 版本实在读不出来时宁可放行让它自己报错，也别再误报"没有 Java 17" */
    if (unknown >= 0) {
        copy_str(out, cands[unknown], cap);
        return 1;
    }
    return 0;
}

static int run(const wchar_t *java) {
    wchar_t cmd[MAX_PATH_LEN * 2];
    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    BOOL started;

    copy_str(cmd, L"\"", MAX_PATH_LEN * 2);
    cat_str(cmd, java, MAX_PATH_LEN * 2);
    /* -Dtcraft.dir 告诉 Java 侧"启动器目录在哪"，省得它去猜 user.home */
    cat_str(cmd, L"\" -Dfile.encoding=UTF-8 -Dtcraft.dir=\"", MAX_PATH_LEN * 2);
    cat_str(cmd, home_dir, MAX_PATH_LEN * 2);
    cat_str(cmd, L"\" -jar \"", MAX_PATH_LEN * 2);
    cat_str(cmd, jar_path, MAX_PATH_LEN * 2);
    cat_str(cmd, L"\"", MAX_PATH_LEN * 2);

    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    ZeroMemory(&pi, sizeof(pi));

    started = CreateProcessW(java, cmd, NULL, NULL, FALSE, CREATE_NO_WINDOW,
                             NULL, home_dir, &si, &pi);
    if (!started) return 0;
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    return 1;
}

/* ---------- Java 选择窗口 ----------
 * 缺 Java 时不该只丢一个「去下载」的框：用户可能已经装了、只是没进 PATH，
 * 或者想自己指定一个。这里给一个原生小窗口：
 *   路径可手填/浏览、「下载 JDK」、自动扫描（进度条 + 逐条结果）。
 * 注意：源文件是 UTF-8，TCC 对宽字面量里的中文不可靠，所以中文一律经 w() 转码。
 */

#define IDC_JAVA_PATH    1001
#define IDC_JAVA_BROWSE  1002
#define IDC_JAVA_DOWN    1003
#define IDC_JAVA_SCAN    1004
#define IDC_JAVA_PROG    1005
#define IDC_JAVA_LOG     1006
#define IDC_JAVA_OK      1007
#define IDC_JAVA_CANCEL  1008

/* 自己定义进度条/编辑框的消息号，免得依赖 commctrl.h、winuser.h 里的宏 */
#define PBM_SETPOS_X     0x0402
#define PBM_SETRANGE32_X 0x0406
#define EM_SETSEL_X      0x00B1
#define EM_SCROLLCARET_X 0x00B7
#define TIMER_SCAN_ID    1

/* OPENFILENAMEW 自己定义 + 动态取函数，避开 TCC 头文件缺失的风险 */
typedef struct {
    unsigned long  lStructSize;
    void          *hwndOwner;
    void          *hInstance;
    const wchar_t *lpstrFilter;
    wchar_t       *lpstrCustomFilter;
    unsigned long  nMaxCustFilter;
    unsigned long  nFilterIndex;
    wchar_t       *lpstrFile;
    unsigned long  nMaxFile;
    wchar_t       *lpstrFileTitle;
    unsigned long  nMaxFileTitle;
    const wchar_t *lpstrInitialDir;
    const wchar_t *lpstrTitle;
    unsigned long  Flags;
    unsigned short nFileOffset;
    unsigned short nFileExtension;
    const wchar_t *lpstrDefExt;
    long           lCustData;
    void          *lpfnHook;
    const wchar_t *lpTemplateName;
    void          *pvReserved;
    unsigned long  dwReserved;
    unsigned long  FlagsEx;
} OFN_W;

static HWND java_dlg;
static HWND ctl_path;
static HWND ctl_prog;
static HWND ctl_log;
static int java_dlg_result;
static wchar_t java_dlg_pick[MAX_PATH_LEN];
static int scan_idx, scan_total;
static wchar_t scan_best[MAX_PATH_LEN];
static int scan_best_major;
static wchar_t scan_text[8192];

static void open_jdk_page(void) {
    HMODULE sh = LoadLibraryW(L"shell32.dll");
    if (!sh) return;
    {
        typedef HINSTANCE (WINAPI *ShellExec)(HWND, LPCWSTR, LPCWSTR, LPCWSTR, LPCWSTR, int);
        ShellExec fn = (ShellExec)GetProcAddress(sh, "ShellExecuteW");
        if (fn) {
            fn(NULL, L"open", L"https://learn.microsoft.com/java/openjdk/download#openjdk-17",
               NULL, NULL, SW_SHOWNORMAL);
        }
    }
}

/* UTF-8 → 宽字符。用一个小缓冲池，避免同一句里两次调用互相覆盖
   （比如 MessageBoxW(w(正文), w(标题)) 这种）。 */
static const wchar_t *w(const char *utf8) {
    static wchar_t pool[4][1024];
    static int idx;
    wchar_t *buf = pool[idx & 3];

    idx++;
    if (!MultiByteToWideChar(CP_UTF8, 0, utf8, -1, buf, 1024)) buf[0] = 0;
    return buf;
}

static void init_common_controls(void) {
    HMODULE m;
    typedef int (WINAPI *InitFn)(void *);
    InitFn fn;
    unsigned long icc[2];

    m = LoadLibraryW(L"comctl32.dll");
    if (!m) return;
    fn = (InitFn)GetProcAddress(m, "InitCommonControlsEx");
    if (!fn) return;
    icc[0] = sizeof(icc); /* dwSize */
    icc[1] = 0x00000020;  /* ICC_PROGRESS_CLASS */
    fn(icc);
}

static void prog_set(int pos, int total) {
    if (!ctl_prog) return;
    if (total <= 0) total = 1;
    if (pos > total) pos = total;
    if (pos < 0) pos = 0;
    SendMessageW(ctl_prog, PBM_SETRANGE32_X, (WPARAM)0, (LPARAM)total);
    SendMessageW(ctl_prog, PBM_SETPOS_X, (WPARAM)pos, 0);
}

static void log_append(const char *utf8) {
    wchar_t line[1024];
    int len, n, i, cap;

    if (!ctl_log) return;
    if (!MultiByteToWideChar(CP_UTF8, 0, utf8, -1, line, 1024)) return;
    cap = (int)(sizeof(scan_text) / sizeof(scan_text[0]));
    n = (int)wcslen(line);
    len = (int)wcslen(scan_text);

    if (len + n + 3 >= cap) {
        int cut = len / 2;
        for (i = 0; scan_text[cut + i]; i++) scan_text[i] = scan_text[cut + i];
        scan_text[i] = 0;
        len = i;
        if (len + n + 3 >= cap) {
            n = cap - len - 3;
            if (n < 0) n = 0;
        }
    }
    for (i = 0; i < n; i++) scan_text[len + i] = line[i];
    scan_text[len + n] = L'\r';
    scan_text[len + n + 1] = L'\n';
    scan_text[len + n + 2] = 0;

    SetWindowTextW(ctl_log, scan_text);
    SendMessageW(ctl_log, EM_SETSEL_X, (WPARAM)-1, (LPARAM)-1);
    SendMessageW(ctl_log, EM_SCROLLCARET_X, 0, 0);
}

/* 选 java.exe / javaw.exe */
static int pick_file(HWND owner, wchar_t *out, int cap) {
    static wchar_t buf[MAX_PATH_LEN];
    OFN_W ofn;
    HMODULE m;
    typedef int (WINAPI *OpenFn)(OFN_W *);
    OpenFn fn;

    m = LoadLibraryW(L"comdlg32.dll");
    if (!m) return 0;
    fn = (OpenFn)GetProcAddress(m, "GetOpenFileNameW");
    if (!fn) return 0;

    ZeroMemory(&ofn, sizeof(ofn));
    buf[0] = 0;
    ofn.lStructSize = sizeof(ofn);
    ofn.hwndOwner = owner;
    ofn.lpstrFilter = L"java.exe; javaw.exe\0*.exe\0\0";
    ofn.lpstrFile = buf;
    ofn.nMaxFile = MAX_PATH_LEN;
    ofn.lpstrTitle = w("选择 java.exe 或 javaw.exe");
    /* FILEMUSTEXIST | PATHMUSTEXIST | HIDEREADONLY | EXPLORER | NOCHANGEDIR */
    ofn.Flags = 0x00001000 | 0x00000800 | 0x00000004 | 0x00080000 | 0x00000008;
    if (!fn(&ofn)) return 0;
    copy_str(out, buf, cap);
    return 1;
}

static void scan_finish(void) {
    char buf[512];

    KillTimer(java_dlg, TIMER_SCAN_ID);
    prog_set(scan_total, scan_total);
    if (scan_best[0]) {
        SetWindowTextW(ctl_path, scan_best);
        sprintf(buf, "扫描完成：已选中 Java %d，点「使用并启动」继续。", scan_best_major);
        log_append(buf);
    } else if (scan_total == 0) {
        log_append("没有扫描到任何 Java。点「浏览…」手动指定 java.exe，或先装一个 JDK。");
    } else {
        log_append("扫描完成：没有 Java 17 或更高版本。可先装 JDK，或点「浏览…」手动指定。");
    }
    EnableWindow(GetDlgItem(java_dlg, IDC_JAVA_SCAN), TRUE);
}

static void scan_begin(void) {
    char buf[256];

    collect_candidates();
    scan_total = cand_count;
    scan_idx = 0;
    scan_best[0] = 0;
    scan_best_major = 0;
    scan_text[0] = 0;
    if (ctl_log) SetWindowTextW(ctl_log, L"");
    prog_set(0, scan_total > 0 ? scan_total : 1);

    if (scan_total == 0) {
        log_append("没有找到任何 Java 安装位置。");
        log_append("点「浏览…」手动指定 java.exe，或者先装一个 JDK 17+。");
        EnableWindow(GetDlgItem(java_dlg, IDC_JAVA_SCAN), TRUE);
        return;
    }

    sprintf(buf, "开始扫描，共 %d 个位置（每条会显示版本和位数）…", scan_total);
    log_append(buf);
    EnableWindow(GetDlgItem(java_dlg, IDC_JAVA_SCAN), FALSE);
    SetTimer(java_dlg, TIMER_SCAN_ID, 20, NULL);
}

/* 一次只探测一个候选，靠定时器推进，界面不会卡住 */
static void scan_step(void) {
    static char buf[1024];
    static char path8[MAX_PATH_LEN * 3];
    int major, is64, too_long;

    if (scan_idx >= scan_total) { scan_finish(); return; }

    major = java_feature(cands[scan_idx]);
    is64 = java_is64(cands[scan_idx]);
    path8[0] = 0;
    WideCharToMultiByte(CP_UTF8, 0, cands[scan_idx], -1, path8, sizeof(path8), NULL, NULL);
    too_long = ((int)strlen(path8) > 900);
    if (too_long) path8[900] = 0;

    if (major <= 0) {
        sprintf(buf, "  ?    版本未知      %s", path8);
    } else if (major >= 17) {
        sprintf(buf, "  OK   Java %-2d %s  %s", major, is64 ? "64位" : "32位", path8);
        if (scan_best_major == 0 || major > scan_best_major
            || (major == scan_best_major && is64)) {
            scan_best_major = major;
            copy_str(scan_best, cands[scan_idx], MAX_PATH_LEN);
        }
    } else {
        sprintf(buf, "  --   Java %-2d 太低     %s", major, path8);
    }
    log_append(buf);

    scan_idx++;
    prog_set(scan_idx, scan_total);
    if (scan_idx >= scan_total) scan_finish();
}

static void java_dlg_accept(void) {
    wchar_t p[MAX_PATH_LEN];
    char path8[MAX_PATH_LEN * 3];
    char buf[1024];
    int major;

    GetWindowTextW(ctl_path, p, MAX_PATH_LEN);
    if (!p[0]) {
        MessageBoxW(java_dlg, w("请先选择 java.exe（可点「浏览…」或「自动扫描」）。"),
                    w(APP_NAME), MB_ICONINFORMATION | MB_OK);
        return;
    }
    WideCharToMultiByte(CP_UTF8, 0, p, -1, path8, sizeof(path8), NULL, NULL);
    if (strlen(path8) > 800) path8[800] = 0; /* 超长路径截断，别把下面的缓冲撑爆 */
    if (!file_exists(p)) {
        sprintf(buf, "这个路径不存在：\n%s", path8);
        MessageBoxW(java_dlg, w(buf), w(APP_NAME), MB_ICONERROR | MB_OK);
        return;
    }
    major = java_feature(p);
    if (major > 0 && major < 17) {
        sprintf(buf, "这个 Java 是 %d 版，启动器需要 17 或更高版本。\n%s", major, path8);
        MessageBoxW(java_dlg, w(buf), w(APP_NAME), MB_ICONERROR | MB_OK);
        return;
    }
    copy_str(java_dlg_pick, p, MAX_PATH_LEN);
    java_dlg_result = 1;
    DestroyWindow(java_dlg);
}

static void java_dlg_create(HWND h) {
    HFONT font = (HFONT)GetStockObject(DEFAULT_GUI_FONT);
    HWND c;

    c = CreateWindowExW(0, L"STATIC",
            w("没有找到 Java 17 或更高版本。启动器自带 JavaFX，但不带 JRE。\r\n"
              "可以点「自动扫描」让启动器找一遍，也可以点「浏览…」自己指定 java.exe。"),
            WS_CHILD | WS_VISIBLE, 16, 12, 616, 44, h, NULL, NULL, NULL);
    SendMessageW(c, WM_SETFONT, (WPARAM)font, TRUE);

    c = CreateWindowExW(0, L"STATIC", w("Java 路径"), WS_CHILD | WS_VISIBLE,
                        16, 70, 60, 20, h, NULL, NULL, NULL);
    SendMessageW(c, WM_SETFONT, (WPARAM)font, TRUE);

    ctl_path = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", cfg_java,
                               WS_CHILD | WS_VISIBLE | WS_TABSTOP | ES_AUTOHSCROLL,
                               80, 66, 376, 24, h, (HMENU)IDC_JAVA_PATH, NULL, NULL);
    SendMessageW(ctl_path, WM_SETFONT, (WPARAM)font, TRUE);

    c = CreateWindowExW(0, L"BUTTON", w("浏览…"), WS_CHILD | WS_VISIBLE | WS_TABSTOP,
                        464, 66, 84, 24, h, (HMENU)IDC_JAVA_BROWSE, NULL, NULL);
    SendMessageW(c, WM_SETFONT, (WPARAM)font, TRUE);

    c = CreateWindowExW(0, L"BUTTON", w("下载 JDK"), WS_CHILD | WS_VISIBLE | WS_TABSTOP,
                        554, 66, 84, 24, h, (HMENU)IDC_JAVA_DOWN, NULL, NULL);
    SendMessageW(c, WM_SETFONT, (WPARAM)font, TRUE);

    c = CreateWindowExW(0, L"BUTTON", w("自动扫描"), WS_CHILD | WS_VISIBLE | WS_TABSTOP,
                        16, 100, 100, 26, h, (HMENU)IDC_JAVA_SCAN, NULL, NULL);
    SendMessageW(c, WM_SETFONT, (WPARAM)font, TRUE);

    ctl_prog = CreateWindowExW(0, L"msctls_progress32", L"",
                               WS_CHILD | WS_VISIBLE | 0x01 /* PBS_SMOOTH */,
                               124, 104, 514, 18, h, (HMENU)IDC_JAVA_PROG, NULL, NULL);

    ctl_log = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"",
                              WS_CHILD | WS_VISIBLE | WS_VSCROLL | WS_TABSTOP
                              | ES_MULTILINE | ES_READONLY | ES_AUTOVSCROLL,
                              16, 136, 622, 246, h, (HMENU)IDC_JAVA_LOG, NULL, NULL);
    SendMessageW(ctl_log, WM_SETFONT, (WPARAM)font, TRUE);

    c = CreateWindowExW(0, L"BUTTON", w("使用并启动"), WS_CHILD | WS_VISIBLE | WS_TABSTOP
                        | BS_DEFPUSHBUTTON, 428, 394, 100, 28, h, (HMENU)IDC_JAVA_OK, NULL, NULL);
    SendMessageW(c, WM_SETFONT, (WPARAM)font, TRUE);

    c = CreateWindowExW(0, L"BUTTON", w("取消"), WS_CHILD | WS_VISIBLE | WS_TABSTOP,
                        538, 394, 100, 28, h, (HMENU)IDC_JAVA_CANCEL, NULL, NULL);
    SendMessageW(c, WM_SETFONT, (WPARAM)font, TRUE);
}

static LRESULT CALLBACK java_dlg_proc(HWND h, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
    case WM_CREATE:
        java_dlg_create(h);
        return 0;
    case WM_COMMAND:
        switch (LOWORD(wp)) {
        case IDC_JAVA_BROWSE: {
            wchar_t picked[MAX_PATH_LEN];
            if (pick_file(h, picked, MAX_PATH_LEN)) SetWindowTextW(ctl_path, picked);
            return 0;
        }
        case IDC_JAVA_DOWN:
            open_jdk_page();
            return 0;
        case IDC_JAVA_SCAN:
            scan_begin();
            return 0;
        case IDC_JAVA_OK:
            java_dlg_accept();
            return 0;
        case IDC_JAVA_CANCEL:
            java_dlg_result = 0;
            DestroyWindow(h);
            return 0;
        }
        return 0;
    case WM_TIMER:
        if (wp == TIMER_SCAN_ID) {
            scan_step();
            return 0;
        }
        break;
    case WM_CLOSE:
        java_dlg_result = 0;
        DestroyWindow(h);
        return 0;
    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcW(h, msg, wp, lp);
}

/* 打开选择窗口：1 = 选到了可用的 java，0 = 用户取消，-1 = 窗口都没能建起来 */
static int java_dialog(wchar_t *out, int cap) {
    WNDCLASSW wc;
    MSG msg;
    HINSTANCE inst = GetModuleHandleW(NULL);
    const int dw = 660, dh = 490;
    int x, y;

    init_common_controls();

    ZeroMemory(&wc, sizeof(wc));
    wc.lpfnWndProc = java_dlg_proc;
    wc.hInstance = inst;
    wc.lpszClassName = L"TCraftJavaPicker";
    wc.hCursor = LoadCursorW(NULL, (const wchar_t *)32512); /* IDC_ARROW */
    wc.hbrBackground = (HBRUSH)(COLOR_BTNFACE + 1);
    RegisterClassW(&wc);

    java_dlg_result = 0;
    java_dlg_pick[0] = 0;

    x = (GetSystemMetrics(0) - dw) / 2;
    y = (GetSystemMetrics(1) - dh) / 2;
    if (x < 0) x = 0; /* 屏幕比窗口还窄时别把标题栏推到屏幕外 */
    if (y < 0) y = 0;
    java_dlg = CreateWindowExW(0, L"TCraftJavaPicker", w("选择 Java 运行时"),
                               WS_POPUP | WS_CAPTION | WS_SYSMENU,
                               x, y, dw, dh, NULL, NULL, inst, NULL);
    if (!java_dlg) return -1;

    ShowWindow(java_dlg, SW_SHOW);
    UpdateWindow(java_dlg);

    /* 一打开就先扫一遍，用户不点按钮也能看到结果 */
    scan_begin();

    while (GetMessageW(&msg, NULL, 0, 0) > 0) {
        if (!IsDialogMessageW(java_dlg, &msg)) {
            TranslateMessage(&msg);
            DispatchMessageW(&msg);
        }
    }

    if (java_dlg_result) {
        copy_str(out, java_dlg_pick, cap);
        return 1;
    }
    return 0;
}

/* 缺 Java 时给下载入口 */
static void offer_download(void) {
    wchar_t msg[1024];
    char utf8[1024];

    if (found_major > 0) {
        sprintf(utf8,
                "系统里只找到 Java %d，低于启动器要求的 17。\n\n"
                "启动器自己不带 JRE，需要另外装一个（请装 64 位 JDK）。\n"
                "是否打开微软 JDK 17 下载页？", found_major);
    } else {
        sprintf(utf8,
                "没有找到 Java 17 或更高版本。\n\n"
                "启动器自己不带 JRE，需要系统里装一个（请装 64 位 JDK 17+）。\n"
                "已检查 %d 个安装位置（JAVA_HOME、PATH、Program Files 等）。\n"
                "是否打开微软 JDK 17 下载页？", cand_count);
    }
    MultiByteToWideChar(CP_UTF8, 0, utf8, -1, msg, 1024);
    if (MessageBoxW(NULL, msg, L"TCraft Launcher", MB_ICONERROR | MB_YESNO) != IDYES) return;
    open_jdk_page();
}

int WINAPI WinMain(HINSTANCE inst, HINSTANCE prev, LPSTR args, int show) {
    unsigned long long size = 0, offset = 0;
    wchar_t java[MAX_PATH_LEN];
    DWORD jar_size_low = 0, jar_size_high = 0;
    HANDLE probe;

    (void)inst; (void)prev; (void)args; (void)show;

    if (!GetModuleFileNameW(NULL, self_path, MAX_PATH_LEN)) {
        alert("无法获取程序自身路径。");
        return 1;
    }

    offset = jar_offset(self_path, &size);
    if (offset == 0 || offset >= size) {
        alert("这个 exe 里没有找到内置的 jar，可能构建时没追加成功。");
        return 1;
    }

    /* 工作目录跟着 exe 走：<exe 目录>\.tcl */
    init_home();
    cleanup_legacy_home();
    copy_str(jar_path, home_dir, MAX_PATH_LEN);
    cat_str(jar_path, L"\\" JAR_NAME, MAX_PATH_LEN);

    /* 大小一致就不重复解包 */
    probe = CreateFileW(jar_path, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING,
                        FILE_ATTRIBUTE_NORMAL, NULL);
    if (probe != INVALID_HANDLE_VALUE) {
        jar_size_low = GetFileSize(probe, &jar_size_high);
        CloseHandle(probe);
    }
    if ((unsigned long long)jar_size_low
        + ((unsigned long long)jar_size_high << 32) != (size - offset)) {
        if (!copy_range(self_path, offset, size - offset, jar_path)) {
            alert("释放内置 jar 失败：exe 所在目录不可写。请把启动器放到有写权限的位置再运行。");
            return 1;
        }
    }

    load_cfg();

    if (!pick_best_java(java, MAX_PATH_LEN)) {
        /* 自动找不到：交给用户选（可浏览、可扫描），选完记到 .tcl\launcher.cfg */
        int rc = java_dialog(java, MAX_PATH_LEN);
        if (rc < 0) {
            offer_download();
            return 1;
        }
        if (rc == 0) return 1;
        save_cfg(java);
    }

    /* 兜底：版本读不出来时放行交给 Java 自己报错；读到低版本再让用户重选一次 */
    {
        int major = java_feature(java);
        if (major > 0 && major < 17) {
            int rc;
            found_major = major;
            rc = java_dialog(java, MAX_PATH_LEN);
            if (rc <= 0) {
                if (rc < 0) offer_download();
                return 1;
            }
            save_cfg(java);
        }
    }

    if (!run(java)) {
        alert("启动 Java 失败，可能是 Java 路径失效或权限问题。");
        return 1;
    }
    return 0;
}
