// jar 追加到 exe 尾 运行时解包并启动
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>

#define APP_NAME "TCraft Launcher"
#define JAR_NAME L"TCraftLauncher.jar"
// 数据固定在 exe 同目录 .tcl 不用 TEMP
#define HOME_SUB L".tcl"
#define CFG_NAME L"launcher.cfg"

#define MAX_PATH_LEN 4096

// TCC 的 windows.h 缺 winnls.h 手动声明
#ifndef CP_UTF8
#define CP_UTF8 65001
#endif
int __stdcall MultiByteToWideChar(unsigned int, unsigned long, const char *, int, wchar_t *, int);
int __stdcall WideCharToMultiByte(unsigned int, unsigned long, const wchar_t *, int, char *, int, const char *, int *);
unsigned int __stdcall GetACP(void);

static wchar_t self_path[MAX_PATH_LEN];
static wchar_t home_dir[MAX_PATH_LEN];
static wchar_t log_dir[MAX_PATH_LEN];
static wchar_t jar_path[MAX_PATH_LEN];
static wchar_t g_args[4096];
static wchar_t cfg_java[MAX_PATH_LEN]; // .tcl\launcher.cfg 里记住的 java 路径
static int first_run;
static HANDLE instance_mutex;

// 中文按 UTF-8 转宽字符 再交给 MessageBoxW
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

// 临时文件试写判断可写
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

// 工作目录固定为 exe 同目录的 .tcl 不回退到别处
static int init_home(void) {
    wchar_t exe_dir[MAX_PATH_LEN];

    copy_str(exe_dir, self_path, MAX_PATH_LEN);
    strip_file(exe_dir);

    copy_str(home_dir, exe_dir, MAX_PATH_LEN);
    cat_str(home_dir, L"\\" HOME_SUB, MAX_PATH_LEN);
    create_dirs(home_dir);
    copy_str(log_dir, home_dir, MAX_PATH_LEN);
    cat_str(log_dir, L"\\log", MAX_PATH_LEN);
    create_dirs(log_dir);
    return dir_writable(home_dir);
}

static void stub_log(const char *line) {
    wchar_t p[MAX_PATH_LEN];
    SYSTEMTIME st;
    HANDLE h;
    DWORD wrote = 0;
    char buf[1200];
    int n;

    if (!home_dir[0]) return;
    copy_str(p, log_dir[0] ? log_dir : home_dir, MAX_PATH_LEN);
    cat_str(p, L"\\stub.log", MAX_PATH_LEN);
    h = CreateFileW(p, FILE_APPEND_DATA, FILE_SHARE_READ, NULL, OPEN_ALWAYS,
                    FILE_ATTRIBUTE_NORMAL, NULL);
    if (h == INVALID_HANDLE_VALUE) return;
    GetLocalTime(&st);
    n = sprintf(buf, "[%02d:%02d:%02d] %s\r\n", st.wHour, st.wMinute, st.wSecond, line);
    if (n > 0) WriteFile(h, buf, (DWORD)n, &wrote, NULL);
    CloseHandle(h);
}

// 二次启动提窗后退出
static void activate_existing_window(void) {
    int i;
    for (i = 0; i < 10; i++) {
        HWND w = FindWindowW(NULL, L"TCraft Launcher");
        if (w) {
            ShowWindow(w, SW_RESTORE);
            SetForegroundWindow(w);
            return;
        }
        Sleep(200);
    }
}

// 互斥量守 stub Java 侧文件锁接管
static int acquire_instance(void) {
    wchar_t name[MAX_PATH_LEN];
    wchar_t hexw[40];
    char hex[40];
    const wchar_t *p = self_path;
    unsigned long h = 5381;
    int i = 0;

    while (*p) h = ((h << 5) + h) + (unsigned long)(*p++);
    sprintf(hex, "%08lx", h);
    copy_str(name, L"Local\\TCraftLauncher-", MAX_PATH_LEN);
    while (hex[i] && i < 39) {
        hexw[i] = (wchar_t)hex[i];
        i++;
    }
    hexw[i] = 0;
    cat_str(name, hexw, MAX_PATH_LEN);

    instance_mutex = CreateMutexW(NULL, TRUE, name);
    if (instance_mutex && GetLastError() == ERROR_ALREADY_EXISTS) {
        return 1;
    }
    return 0;
}

// 递归删目录 失败跳过
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

// 清旧残留目录 失败忽略
static void cleanup_legacy_home(void) {
    wchar_t alt[MAX_PATH_LEN];
    wchar_t old[MAX_PATH_LEN];

    if (!GetEnvironmentVariableW(L"LOCALAPPDATA", alt, MAX_PATH_LEN) || !alt[0]) return;
    copy_str(old, alt, MAX_PATH_LEN);
    cat_str(old, L"\\TCraftLauncher", MAX_PATH_LEN);
    if (wcscmp(old, home_dir) == 0) return; // 路径相同则跳过
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

// 读上次 Java 失效重扫
static void load_cfg(void) {
    wchar_t p[MAX_PATH_LEN];
    char buf[2048];
    int got, i;

    cfg_java[0] = 0;
    cfg_path(p, MAX_PATH_LEN);
    got = read_file(p, buf, sizeof(buf));
    first_run = (got <= 0); // 没有 launcher.cfg 首次运行 必须给用户一个可见窗口
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
    // 从尾部扫 EOCD 最短 22 字节
    // 候选处须为 PK\x03\x04 与 headerLength 对齐
    for (i = tail_len - 22; ; i--) {
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
                unsigned long long cand = eocd - cd_size - cd_off;
                if (cand < (unsigned long long)start + i) {
                    unsigned char magic[4];
                    DWORD gotm = 0;
                    LARGE_INTEGER at;
                    at.QuadPart = (LONGLONG)cand;
                    SetFilePointer(fh, at.LowPart, &at.HighPart, FILE_BEGIN);
                    if (ReadFile(fh, magic, 4, &gotm, NULL) && gotm == 4
                        && magic[0] == 0x50 && magic[1] == 0x4b
                        && magic[2] == 0x03 && magic[3] == 0x04) {
                        offset = (unsigned long)cand;
                        break;
                    }
                }
            }
        }
        if (i == 0) break;
    }
    free(tail);
    CloseHandle(fh);
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

#define MAX_CAND 128

static wchar_t cands[MAX_CAND][MAX_PATH_LEN];
static int cand_count;
static int found_major;

static int parse_major(const char *s) {
    if (s[0] == '1' && s[1] == '.') return atoi(s + 2);
    return atoi(s);
}

// 读 release 判定位数 不起进程
static int java_is64(const wchar_t *java) {
    wchar_t home[MAX_PATH_LEN];
    wchar_t rel[MAX_PATH_LEN];
    char buf[4096];
    int got;

    copy_str(home, java, MAX_PATH_LEN);
    strip_file(home);
    strip_file(home);
    copy_str(rel, home, MAX_PATH_LEN);
    cat_str(rel, L"\\release", MAX_PATH_LEN);
    got = read_file(rel, buf, sizeof(buf));
    if (got > 0) {
        buf[got] = 0;
        if (strstr(buf, "OS_ARCH=\"x86\"")) return 0;
        if (strstr(buf, "OS_ARCH=\"x86_64\"") || strstr(buf, "OS_ARCH=\"amd64\"")
                || strstr(buf, "OS_ARCH=\"aarch64\"")) return 1;
    }
    return 1; // 读不到按 64 位处理 32 位 JRE 启动时会报错
}

static int java_feature(const wchar_t *java) {
    wchar_t home[MAX_PATH_LEN];
    wchar_t rel[MAX_PATH_LEN];
    char buf[4096];
    int got;
    char *hit;

    copy_str(home, java, MAX_PATH_LEN);
    strip_file(home);
    strip_file(home);
    copy_str(rel, home, MAX_PATH_LEN);
    cat_str(rel, L"\\release", MAX_PATH_LEN);
    got = read_file(rel, buf, sizeof(buf));
    if (got <= 0) return 0;
    buf[got] = 0;
    hit = strstr(buf, "JAVA_VERSION=\"");
    return hit ? parse_major(hit + 14) : 0;
}
// 去引号与结尾反斜杠
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
    // javapath 是转发目录 跳过
    if (wcsstr(path, L"\\Oracle\\Java")) return;
    if (wcsstr(path, L"\\WindowsApps\\")) return;
    for (i = 0; i < cand_count; i++) {
        if (wcscmp(cands[i], path) == 0) return;
    }
    copy_str(cands[cand_count], path, MAX_PATH_LEN);
    cand_count++;
}

// home 下找 bin/java 兼容 JAVA_HOME 指向 bin
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

// 安装根目录向下 depth 层找 JDK
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

// 常规位置向下两层找 JDK
static void scan_common_dirs(void) {
    static const wchar_t *bases[] = {
        L"ProgramW6432", L"ProgramFiles", L"ProgramFiles(x86)", NULL
    };
    static const wchar_t *vendors[] = {
        L"Java", L"Microsoft", L"Eclipse Adoptium", L"Eclipse Foundation",
        L"AdoptOpenJDK", L"BellSoft", L"Zulu", L"Amazon Corretto",
        L"Semeru", L"GraalVM", L"JetBrains", L"RedHat", L"SapMachine", NULL
    };
    int b, i;

    for (b = 0; bases[b]; b++) {
        wchar_t base[MAX_PATH_LEN];
        if (!GetEnvironmentVariableW(bases[b], base, MAX_PATH_LEN) || !base[0]) continue;
        for (i = 0; vendors[i]; i++) {
            wchar_t root[MAX_PATH_LEN];
            copy_str(root, base, MAX_PATH_LEN);
            cat_str(root, L"\\", MAX_PATH_LEN);
            cat_str(root, vendors[i], MAX_PATH_LEN);
            scan_tree(root, 2);
        }
    }
    scan_env_root(L"USERPROFILE", L"\\.jdks", 2);
    scan_env_root(L"APPDATA", L"\\.minecraft\\runtime", 2);
}

// 候选 上次记录/自带 Java/JAVA_HOME/PATH/常见目录
static void collect_candidates(void) {
    wchar_t jh[MAX_PATH_LEN];
    wchar_t tcl_java[MAX_PATH_LEN];

    cand_count = 0;
    if (cfg_java[0] && file_exists(cfg_java)) cand_add(cfg_java);

    copy_str(tcl_java, home_dir, MAX_PATH_LEN);
    cat_str(tcl_java, L"\\java\\windows-x64", MAX_PATH_LEN);
    cand_add_home(tcl_java);
    scan_tree(tcl_java, 2);

    if (GetEnvironmentVariableW(L"JAVA_HOME", jh, MAX_PATH_LEN) && jh[0]) cand_add_home(jh);
    scan_path();
    scan_common_dirs();
}

static void consider_candidate(int i, int *best, int *best_major) {
    int major = java_feature(cands[i]);
    int is64 = java_is64(cands[i]);

    if (major <= 0) return;
    if (major > found_major) found_major = major;
    if (major < 17) return;
    if (!is64) return;
    if (*best < 0 || major > *best_major) {
        *best = i;
        *best_major = major;
    }
}

// 候选一次性收齐 找不到交给自动下载
static int pick_best_java(wchar_t *out, int cap) {
    int i, best = -1, best_major = 0;

    found_major = 0;
    if (cfg_java[0] && file_exists(cfg_java)) {
        int major = java_feature(cfg_java);
        if (major >= 17 && java_is64(cfg_java)) {
            copy_str(out, cfg_java, cap);
            return 1;
        }
        if (major > 0) found_major = major;
    }

    collect_candidates();
    for (i = 0; i < cand_count; i++) consider_candidate(i, &best, &best_major);

    if (best >= 0) {
        copy_str(out, cands[best], cap);
        return 1;
    }
    return 0;
}
// 取 exe 后参数 保留引号
static void capture_args(void) {
    wchar_t *p = GetCommandLineW();
    if (!p) return;
    if (*p == L'"') {
        p++;
        while (*p && *p != L'"') p++;
        if (*p == L'"') p++;
    } else {
        while (*p && *p != L' ' && *p != L'\t') p++;
    }
    while (*p == L' ' || *p == L'\t') p++;
    copy_str(g_args, p, 4096);
}

static void append_webp_cp(wchar_t *cmd, const wchar_t *home_dir) {
    wchar_t path[MAX_PATH_LEN];
    copy_str(path, home_dir, MAX_PATH_LEN);
    cat_str(path, L"\\dependencies\\libs\\webp-0.2.0.jar", MAX_PATH_LEN);
    if (file_exists(path)) {
        cat_str(cmd, L";", MAX_PATH_LEN * 2);
        cat_str(cmd, path, MAX_PATH_LEN * 2);
    }
}

static int java_has_builtin_fx(const wchar_t *java) {
    wchar_t home[MAX_PATH_LEN];
    wchar_t prop[MAX_PATH_LEN];

    copy_str(home, java, MAX_PATH_LEN);
    strip_file(home);
    strip_file(home);
    copy_str(prop, home, MAX_PATH_LEN);
    cat_str(prop, L"\\lib\\javafx.properties", MAX_PATH_LEN);
    return file_exists(prop);
}

// 2.5s 观察 0 未起 / 1 正常退出 / 2 秒退
static int run(const wchar_t *java, DWORD *exit_code) {
    wchar_t cmd[MAX_PATH_LEN * 2];
    wchar_t logp[MAX_PATH_LEN];
    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    SECURITY_ATTRIBUTES sa;
    HANDLE logf;
    BOOL started;

    if (exit_code) *exit_code = 0;

    copy_str(cmd, L"\"", MAX_PATH_LEN * 2);
    cat_str(cmd, java, MAX_PATH_LEN * 2);
    // 向 Java 侧传入启动器目录与 exe 自身路径 自更新要替换的文件
    cat_str(cmd, L"\" -Dfile.encoding=UTF-8 -Dtcraft.dir=\"", MAX_PATH_LEN * 2);
    cat_str(cmd, home_dir, MAX_PATH_LEN * 2);
    cat_str(cmd, L"\" -Dtcraft.exe=\"", MAX_PATH_LEN * 2);
    cat_str(cmd, self_path, MAX_PATH_LEN * 2);
    {
        // JavaFX 已下载时直接带 module-path 启动
        wchar_t deps[MAX_PATH_LEN];
        wchar_t check[MAX_PATH_LEN];
        copy_str(deps, home_dir, MAX_PATH_LEN);
        cat_str(deps, L"\\dependencies\\win\\openjfx", MAX_PATH_LEN);
        copy_str(check, deps, MAX_PATH_LEN);
        cat_str(check, L"\\.ready", MAX_PATH_LEN);
        if (java_has_builtin_fx(java)) {
            // 自带 JavaFX 的 Java 17 直接用内置模块 Win7 不找外置原生库
            cat_str(cmd, L"\" --add-modules=javafx.controls,javafx.graphics,javafx.base,javafx.media,javafx.web,javafx.swing,javafx.fxml",
                    MAX_PATH_LEN * 2);
            cat_str(cmd, L" --add-exports=javafx.graphics/com.sun.prism=ALL-UNNAMED -cp \"",
                    MAX_PATH_LEN * 2);
            cat_str(cmd, jar_path, MAX_PATH_LEN * 2);
            append_webp_cp(cmd, home_dir);
            cat_str(cmd, L"\" tcraft.Boot", MAX_PATH_LEN * 2);
        } else if (file_exists(check)) {
            cat_str(cmd, L"\" -Djava.library.path=\"", MAX_PATH_LEN * 2);
            cat_str(cmd, deps, MAX_PATH_LEN * 2);
            cat_str(cmd, L"\" --module-path=\"", MAX_PATH_LEN * 2);
            cat_str(cmd, deps, MAX_PATH_LEN * 2);
            cat_str(cmd, L"\" --add-modules=javafx.controls,javafx.graphics,javafx.base,javafx.media",
                    MAX_PATH_LEN * 2);
            cat_str(cmd, L" --add-exports=javafx.graphics/com.sun.prism=ALL-UNNAMED -cp \"",
                    MAX_PATH_LEN * 2);
            cat_str(cmd, jar_path, MAX_PATH_LEN * 2);
            append_webp_cp(cmd, home_dir);
            cat_str(cmd, L"\" tcraft.Boot", MAX_PATH_LEN * 2);
        } else {
            cat_str(cmd, L"\" -jar \"", MAX_PATH_LEN * 2);
            cat_str(cmd, jar_path, MAX_PATH_LEN * 2);
            cat_str(cmd, L"\"", MAX_PATH_LEN * 2);
        }
    }
    if (g_args[0]) {
        cat_str(cmd, L" ", MAX_PATH_LEN * 2);
        cat_str(cmd, g_args, MAX_PATH_LEN * 2);
    }

    // javaw 无控制台 输出写 java-error.log
    copy_str(logp, log_dir[0] ? log_dir : home_dir, MAX_PATH_LEN);
    cat_str(logp, L"\\java-error.log", MAX_PATH_LEN);
    sa.nLength = sizeof(sa);
    sa.lpSecurityDescriptor = NULL;
    sa.bInheritHandle = TRUE;
    logf = CreateFileW(logp, GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE, &sa,
                       CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);

    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    if (logf != INVALID_HANDLE_VALUE) {
        si.dwFlags = STARTF_USESTDHANDLES;
        si.hStdOutput = logf;
        si.hStdError = logf;
        si.hStdInput = NULL;
    }
    ZeroMemory(&pi, sizeof(pi));

    started = CreateProcessW(java, cmd, NULL, NULL, logf != INVALID_HANDLE_VALUE,
                             CREATE_NO_WINDOW, NULL, home_dir, &si, &pi);
    if (logf != INVALID_HANDLE_VALUE) CloseHandle(logf); // java 已经继承了句柄
    if (!started) return 0;

    if (WaitForSingleObject(pi.hProcess, 2500) == WAIT_OBJECT_0) {
        DWORD code = 0;
        GetExitCodeProcess(pi.hProcess, &code);
        if (exit_code) *exit_code = code;
        CloseHandle(pi.hProcess);
        CloseHandle(pi.hThread);
        return code == 0 ? 1 : 2;
    }
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    return 1;
}

// Java 选择窗口 扫描 / 浏览 / 下载

#define IDC_JAVA_PATH    1001
#define IDC_JAVA_BROWSE  1002
#define IDC_JAVA_DOWN    1003
#define IDC_JAVA_SCAN    1004
#define IDC_JAVA_PROG    1005
#define IDC_JAVA_LOG     1006
#define IDC_JAVA_OK      1007
#define IDC_JAVA_CANCEL  1008
#define IDC_JAVA_STATUS  1010

// 自定义控件消息号 不依赖缺失头文件宏
#define PBM_SETPOS_X     0x0402
#define PBM_SETRANGE32_X 0x0406
#define EM_SETSEL_X      0x00B1
#define EM_SCROLLCARET_X 0x00B7
#define TIMER_SCAN_ID    1
#define TIMER_DOWNLOAD_ID 2
#define TIMER_ACCEPT_ID  3

// OPENFILENAMEW 自定义+动态取函数 避 TCC 头缺失
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
static HWND ctl_status;
static int java_dlg_result;
static int java_dlg_first;
static wchar_t java_dlg_seed[MAX_PATH_LEN]; // 打开时预填的路径
static wchar_t java_dlg_pick[MAX_PATH_LEN];
static int scan_idx, scan_total;
static wchar_t scan_best[MAX_PATH_LEN];
static int scan_best_major;
static char scan_msg[256];

static void copy_bytes(char *dst, int cap, const char *src) {
    int i = 0;

    if (!dst || cap <= 0) return;
    while (src && src[i] && i < cap - 1) {
        dst[i] = src[i];
        i++;
    }
    dst[i] = 0;
}

// Java 17 自动安装 404 换源
#define JAVA17_URL_TUNA L"https://mirrors.tuna.tsinghua.edu.cn/Adoptium/17/jre/x64/windows/OpenJDK17U-jre_x64_windows_hotspot_17.0.21_9.zip"
// 华为云 OpenJDK 17
#define JAVA17_URL_HUAWEI L"https://mirrors.huaweicloud.com/openjdk/17.0.2/openjdk-17.0.2_windows-x64_bin.zip"
#define JAVA17_URL L"https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse"
// Win7 用 Liberica 17
#define JAVA17_URL_WIN7 L"https://download.bell-sw.com/java/17.0.20.1%2B1/bellsoft-jre17.0.20.1%2B1-windows-amd64-full.zip"
#define TCL_UA L"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36 TCraftLauncher/1.0"
static volatile LONG java_dl_phase;
static volatile LONG java_dl_result; // 0 未完成 / 1 成功 / -1 失败
static wchar_t java_dl_zip[MAX_PATH_LEN];
static wchar_t java_dl_dest[MAX_PATH_LEN];
static wchar_t java_dl_tmp[MAX_PATH_LEN];
static wchar_t java_dl_java[MAX_PATH_LEN];
static unsigned long java_dl_total;
static int java_dl_total_known;
static unsigned long java_dl_last_bytes;
static DWORD java_dl_last_tick;
static DWORD java_dl_start_tick;
static char java_dl_error[512];
static HANDLE java_dl_thread;
static int java_dl_extract_logged;
static volatile LONG java_dl_stalled; // 20 秒没有新数据就放弃当前源
static unsigned long java_dl_progress_bytes;
static DWORD java_dl_progress_tick;
static volatile LONG java_dl_retryable; // 0 这个源没救了 直接换源
static volatile LONG java_dl_source_idx;
static volatile LONG java_dl_attempt;
static char java_dl_source[64]; // 当前源名字 给 tick 显示
static volatile LONG java_dl_part_bytes; // 分块下载已落盘字节 各线程累加
static volatile LONG java_dl_abort; // 任一分块失败/卡死 通知其余线程退出
static volatile LONG java_dl_parallel; // 1=分块下载进行中 java_dl_bytes 走分片计数

static void java_dlg_accept(void);
static void log_append(const char *utf8);

// Win7 用 Liberica Temurin 不支持
static int window_version(int *major, int *minor) {
    DWORD v = GetVersion();
    *major = (int)(v & 0xff);
    *minor = (int)((v >> 8) & 0xff);
    return *major == 6 && *minor == 1;
}

typedef struct {
    const wchar_t *url;
    const char *label;
} JavaSource;

// 国内镜像优先 Win7 走 Liberica
static int java_sources(JavaSource *out, int max) {
    int major, minor, n = 0;
    int win7 = window_version(&major, &minor);

    if (win7) {
        out[n].url = JAVA17_URL_WIN7; out[n].label = "Liberica"; n++;
        out[n].url = JAVA17_URL_HUAWEI; out[n].label = "华为云"; n++;
        out[n].url = JAVA17_URL; out[n].label = "Adoptium"; n++;
    } else {
        // 清华镜像可能 403 置第二
        out[n].url = JAVA17_URL_HUAWEI; out[n].label = "华为云"; n++;
        out[n].url = JAVA17_URL_TUNA; out[n].label = "清华镜像"; n++;
        out[n].url = JAVA17_URL; out[n].label = "Adoptium"; n++;
        out[n].url = JAVA17_URL_WIN7; out[n].label = "Liberica"; n++;
    }
    return n > max ? max : n;
}

static const char *java17_label(void) {
    int major, minor;
    return window_version(&major, &minor)
        ? "Liberica JRE 17 Full（Windows 7 官方支持，含 JavaFX）"
        : "JDK 17";
}

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
    icc[0] = sizeof(icc);
    icc[1] = 0x00000020;
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

// 状态行单行 超长截断
static void status_set(const char *utf8) {
    wchar_t line[256];
    int n, i;

    if (!ctl_status) return;
    if (!utf8 || !utf8[0]) { SetWindowTextW(ctl_status, L""); return; }
    if (!MultiByteToWideChar(CP_UTF8, 0, utf8, -1, line, 256)) return;
    n = (int)wcslen(line);
    for (i = 0; i < n; i++) {
        if (line[i] == L'\r' || line[i] == L'\n') { n = i; break; }
    }
    if (n > 60) { line[57] = L'.'; line[58] = L'.'; line[59] = L'.'; n = 60; }
    line[n] = 0;
    SetWindowTextW(ctl_status, line);
}

static void log_append(const char *utf8) {
    status_set(utf8);
}

// Java 17 安装 下载 解压 保存

static unsigned long file_size_u(const wchar_t *path) {
    HANDLE h;
    DWORD low, high = 0;

    h = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE, NULL,
                    OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    if (h == INVALID_HANDLE_VALUE) return 0;
    low = GetFileSize(h, &high);
    CloseHandle(h);
    return high == 0 ? low : 0xFFFFFFFFUL;
}

static int file_is_zip(const wchar_t *path) {
    HANDLE h;
    unsigned char sig[4];
    DWORD got = 0;
    int ok;

    h = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING,
                    FILE_ATTRIBUTE_NORMAL, NULL);
    if (h == INVALID_HANDLE_VALUE) return 0;
    ok = ReadFile(h, sig, 4, &got, NULL) && got == 4
         && sig[0] == 'P' && sig[1] == 'K' && sig[2] == 3 && sig[3] == 4;
    CloseHandle(h);
    return ok;
}

// 安装目录找 bin/java 兼容多一层
static int find_java_exe(const wchar_t *root, wchar_t *out, int cap, int depth) {
    wchar_t p[MAX_PATH_LEN];
    wchar_t sub[MAX_PATH_LEN];
    WIN32_FIND_DATAW data;
    HANDLE h;
    const wchar_t *names[] = {L"\\bin\\java.exe", L"\\bin\\javaw.exe", NULL};
    int i;

    if (!root || !root[0] || depth <= 0) return 0;
    for (i = 0; names[i]; i++) {
        copy_str(p, root, MAX_PATH_LEN);
        cat_str(p, names[i], MAX_PATH_LEN);
        if (file_exists(p)) {
            copy_str(out, p, cap);
            return 1;
        }
    }
    copy_str(p, root, MAX_PATH_LEN);
    cat_str(p, L"\\*", MAX_PATH_LEN);
    h = FindFirstFileW(p, &data);
    if (h == INVALID_HANDLE_VALUE) return 0;
    do {
        if (!(data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)) continue;
        if (data.cFileName[0] == L'.') continue;
        copy_str(sub, root, MAX_PATH_LEN);
        cat_str(sub, L"\\", MAX_PATH_LEN);
        cat_str(sub, data.cFileName, MAX_PATH_LEN);
        if (find_java_exe(sub, out, cap, depth - 1)) {
            FindClose(h);
            return 1;
        }
    } while (FindNextFileW(h, &data));
    FindClose(h);
    return 0;
}

// 仅 64 位下载 x64 JRE
static int java_install_supported(void) {
    char arch[128];
    arch[0] = 0;
    GetEnvironmentVariableA("PROCESSOR_ARCHITEW6432", arch, sizeof(arch));
    if (arch[0] && (strstr(arch, "AMD64") || strstr(arch, "x86_64"))) return 1;
    arch[0] = 0;
    GetEnvironmentVariableA("PROCESSOR_ARCHITECTURE", arch, sizeof(arch));
    return arch[0] && (strstr(arch, "AMD64") || strstr(arch, "x86_64"));
}


static int run_hidden(const wchar_t *cmd, DWORD timeout_ms, DWORD *code_out) {
    wchar_t run[MAX_PATH_LEN * 3];
    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    DWORD waited, code = 1;

    copy_str(run, cmd, MAX_PATH_LEN * 3);
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESHOWWINDOW;
    si.wShowWindow = SW_HIDE;
    ZeroMemory(&pi, sizeof(pi));
    if (!CreateProcessW(NULL, run, NULL, NULL, FALSE, CREATE_NO_WINDOW,
                        NULL, NULL, &si, &pi)) return 0;
    waited = WaitForSingleObject(pi.hProcess, timeout_ms);
    if (waited == WAIT_TIMEOUT) {
        TerminateProcess(pi.hProcess, 1);
        code = 1;
    } else {
        GetExitCodeProcess(pi.hProcess, &code);
    }
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    if (code_out) *code_out = code;
    return waited != WAIT_TIMEOUT;
}

static int write_ascii_file(const wchar_t *path, const char *text) {
    HANDLE h;
    DWORD wrote = 0;
    int n = (int)strlen(text);

    h = CreateFileW(path, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (h == INVALID_HANDLE_VALUE) return 0;
    if (n > 0) WriteFile(h, text, (DWORD)n, &wrote, NULL);
    CloseHandle(h);
    return (int)wrote == n;
}

// Win7 用 Shell.Application 解压
static int extract_java_zip_vbs(void) {
    static const char vbs_text[] =
        "Option Explicit\r\n"
        "Dim fso, shell, zip, dest, zipFolder, destFolder, item, startTime\r\n"
        "Set fso = CreateObject(\"Scripting.FileSystemObject\")\r\n"
        "zip = WScript.Arguments(0)\r\n"
        "dest = WScript.Arguments(1)\r\n"
        "If Not fso.FolderExists(dest) Then fso.CreateFolder(dest)\r\n"
        "Set shell = CreateObject(\"Shell.Application\")\r\n"
        "Set zipFolder = shell.NameSpace(zip)\r\n"
        "Set destFolder = shell.NameSpace(dest)\r\n"
        "If zipFolder Is Nothing Then WScript.Quit 2\r\n"
        "For Each item In zipFolder.Items\r\n"
        "  destFolder.CopyHere item, 1044\r\n"
        "Next\r\n"
        "startTime = Timer\r\n"
        "Do\r\n"
        "  WScript.Sleep 500\r\n"
        "  If FindJava(dest) Then WScript.Quit 0\r\n"
        "  If Timer - startTime > 120 Then WScript.Quit 1\r\n"
        "Loop\r\n"
        "Function FindJava(p)\r\n"
        "  Dim f, s\r\n"
        "  If fso.FileExists(p & \"\\bin\\java.exe\") Then\r\n"
        "    FindJava = True\r\n"
        "  Else\r\n"
        "    Set f = fso.GetFolder(p)\r\n"
        "    For Each s In f.SubFolders\r\n"
        "      If FindJava(s.Path) Then\r\n"
        "        FindJava = True\r\n"
        "        Exit Function\r\n"
        "      End If\r\n"
        "    Next\r\n"
        "  End If\r\n"
        "End Function\r\n";
    wchar_t vbs[MAX_PATH_LEN];
    wchar_t cscript[MAX_PATH_LEN];
    wchar_t cmd[MAX_PATH_LEN * 3];
    DWORD code = 1;
    int ok = 0;

    copy_str(vbs, home_dir, MAX_PATH_LEN);
    cat_str(vbs, L"\\.extract-java.vbs", MAX_PATH_LEN);
    if (!write_ascii_file(vbs, vbs_text)) return 0;

    cscript[0] = 0;
    if (GetEnvironmentVariableW(L"SystemRoot", cscript, MAX_PATH_LEN) && cscript[0]) {
        cat_str(cscript, L"\\System32\\cscript.exe", MAX_PATH_LEN);
    }
    if (!file_exists(cscript)) copy_str(cscript, L"cscript.exe", MAX_PATH_LEN);

    remove_tree(java_dl_tmp, 0);
    create_dirs(java_dl_tmp);
    copy_str(cmd, L"\"", MAX_PATH_LEN * 3);
    cat_str(cmd, cscript, MAX_PATH_LEN * 3);
    cat_str(cmd, L"\" //nologo \"", MAX_PATH_LEN * 3);
    cat_str(cmd, vbs, MAX_PATH_LEN * 3);
    cat_str(cmd, L"\" \"", MAX_PATH_LEN * 3);
    cat_str(cmd, java_dl_zip, MAX_PATH_LEN * 3);
    cat_str(cmd, L"\" \"", MAX_PATH_LEN * 3);
    cat_str(cmd, java_dl_tmp, MAX_PATH_LEN * 3);
    cat_str(cmd, L"\"", MAX_PATH_LEN * 3);
    if (run_hidden(cmd, 180000, &code) && code == 0
        && find_java_exe(java_dl_tmp, java_dl_java, MAX_PATH_LEN, 4)) {
        ok = 1;
    }
    DeleteFileW(vbs);
    return ok;
}

static int extract_java_zip(void) {
    wchar_t ps[MAX_PATH_LEN];
    wchar_t tar[MAX_PATH_LEN];
    wchar_t cmd[MAX_PATH_LEN * 3];
    DWORD code = 1;
    int ok = 0;

    remove_tree(java_dl_tmp, 0);
    create_dirs(java_dl_tmp);

    // 优先 bsdtar
    tar[0] = 0;
    if (GetEnvironmentVariableW(L"SystemRoot", tar, MAX_PATH_LEN) && tar[0]) {
        cat_str(tar, L"\\System32\\tar.exe", MAX_PATH_LEN);
    }
    if (file_exists(tar)) {
        copy_str(cmd, L"\"", MAX_PATH_LEN * 3);
        cat_str(cmd, tar, MAX_PATH_LEN * 3);
        cat_str(cmd, L"\" -xf \"", MAX_PATH_LEN * 3);
        cat_str(cmd, java_dl_zip, MAX_PATH_LEN * 3);
        cat_str(cmd, L"\" -C \"", MAX_PATH_LEN * 3);
        cat_str(cmd, java_dl_tmp, MAX_PATH_LEN * 3);
        cat_str(cmd, L"\"", MAX_PATH_LEN * 3);
        if (run_hidden(cmd, 180000, &code) && code == 0
            && find_java_exe(java_dl_tmp, java_dl_java, MAX_PATH_LEN, 4)) {
            ok = 1;
        } else {
            remove_tree(java_dl_tmp, 0);
            create_dirs(java_dl_tmp);
        }
    }

    if (!ok) {
        SetEnvironmentVariableW(L"TCL_JAVA_ZIP", java_dl_zip);
        SetEnvironmentVariableW(L"TCL_JAVA_DEST", java_dl_tmp);
        ps[0] = 0;
        if (GetEnvironmentVariableW(L"SystemRoot", ps, MAX_PATH_LEN) && ps[0]) {
            cat_str(ps, L"\\System32\\WindowsPowerShell\\v1.0\\powershell.exe", MAX_PATH_LEN);
        }
        if (!file_exists(ps)) copy_str(ps, L"powershell.exe", MAX_PATH_LEN);

        copy_str(cmd, L"\"", MAX_PATH_LEN * 3);
        cat_str(cmd, ps, MAX_PATH_LEN * 3);
        cat_str(cmd, L"\" -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command "
                       L"\"$ErrorActionPreference='Stop'; Expand-Archive -LiteralPath "
                       L"$env:TCL_JAVA_ZIP -DestinationPath $env:TCL_JAVA_DEST -Force\"",
                MAX_PATH_LEN * 3);
        if (run_hidden(cmd, 180000, &code) && code == 0
            && find_java_exe(java_dl_tmp, java_dl_java, MAX_PATH_LEN, 4)) {
            ok = 1;
        }
        SetEnvironmentVariableW(L"TCL_JAVA_ZIP", NULL);
        SetEnvironmentVariableW(L"TCL_JAVA_DEST", NULL);
    }

    if (!ok) ok = extract_java_zip_vbs();

    if (!ok) {
        if (!java_dl_error[0]) sprintf(java_dl_error, "解压 Java 失败（退出码 %lu）", code);
        return 0;
    }

    // 替换启动器目录内旧 jre17
    remove_tree(java_dl_dest, 0);
    if (!MoveFileW(java_dl_tmp, java_dl_dest)) {
        sprintf(java_dl_error, "移动 Java 目录失败（错误 %lu）", GetLastError());
        return 0;
    }

    if (!find_java_exe(java_dl_dest, java_dl_java, MAX_PATH_LEN, 4)) {
        sprintf(java_dl_error, "%s", "安装后找不到 java.exe");
        return 0;
    }
    DeleteFileW(java_dl_zip);
    return 1;
}

static const char *inet_reason(unsigned long err) {
    switch (err) {
    case 12002: return "连接或读取超时";
    case 12007: return "域名解析失败";
    case 12029: return "无法连接服务器";
    case 12030: return "连接被中止";
    case 12031: return "连接被重置";
    case 12055:
    case 12056:
    case 12057: return "HTTPS 证书校验失败";
    case 12163: return "系统显示网络已断开";
    case 12175: return "TLS/SSL 握手失败";
    default: return "网络错误";
    }
}

static int download_url(const wchar_t *url, const char *source) {
    HMODULE w = NULL;
    typedef void * (WINAPI *OpenFn)(const wchar_t *, unsigned long, const wchar_t *, const wchar_t *, unsigned long);
    typedef void * (WINAPI *OpenUrlFn)(void *, const wchar_t *, const wchar_t *, unsigned long, unsigned long);
    typedef int (WINAPI *QueryFn)(void *, unsigned long, void *, unsigned long *, unsigned long *);
    typedef int (WINAPI *ReadFn)(void *, void *, unsigned long, unsigned long *);
    typedef int (WINAPI *SetOptFn)(void *, unsigned long, void *, unsigned long);
    typedef int (WINAPI *CloseFn)(void *);
    OpenFn open_fn;
    OpenUrlFn open_url_fn;
    QueryFn query_fn;
    ReadFn read_fn;
    SetOptFn set_opt_fn;
    CloseFn close_fn;
    void *inet = NULL, *req = NULL;
    HANDLE out = INVALID_HANDLE_VALUE;
    char buf[65536];
    unsigned long got = 0, status = 0, status_len = sizeof(status), status_idx = 0;
    unsigned long total = 0, total_len = sizeof(total), total_idx = 0;
    unsigned long err = 0, timeout = 15000, protocols = 0x00000800UL | 0x00000200UL | 0x00000080UL;
    int ok = 1, read_ok = 1;

    java_dl_error[0] = 0;
    java_dl_parallel = 0;
    java_dl_part_bytes = 0;
    DeleteFileW(java_dl_zip);
    w = LoadLibraryW(L"wininet.dll");
    if (!w) {
        sprintf(java_dl_error, "无法加载 WinINet（错误 %lu）", GetLastError());
        return 0;
    }
    open_fn = (OpenFn)GetProcAddress(w, "InternetOpenW");
    open_url_fn = (OpenUrlFn)GetProcAddress(w, "InternetOpenUrlW");
    query_fn = (QueryFn)GetProcAddress(w, "HttpQueryInfoW");
    read_fn = (ReadFn)GetProcAddress(w, "InternetReadFile");
    set_opt_fn = (SetOptFn)GetProcAddress(w, "InternetSetOptionW");
    close_fn = (CloseFn)GetProcAddress(w, "InternetCloseHandle");
    if (!open_fn || !open_url_fn || !query_fn || !read_fn || !close_fn) {
        sprintf(java_dl_error, "%s", "系统缺少 WinINet 下载函数");
        FreeLibrary(w);
        return 0;
    }

    inet = open_fn(TCL_UA, 0, NULL, NULL, 0);
    if (!inet) {
        err = GetLastError();
        sprintf(java_dl_error, "下载 Java 17 失败：%s（WinINet %lu）", inet_reason(err), err);
        FreeLibrary(w);
        return 0;
    }
    if (set_opt_fn) {
        set_opt_fn(inet, 2, &timeout, sizeof(timeout));
        timeout = 30000;
        set_opt_fn(inet, 6, &timeout, sizeof(timeout));
        set_opt_fn(inet, 84, &protocols, sizeof(protocols));
    }

    req = open_url_fn(inet, url, NULL, 0,
                      0x80000000UL | 0x04000000UL | 0x00800000UL | 0x00400000UL);
    if (!req) {
        err = GetLastError();
        sprintf(java_dl_error, "%s 连接失败：%s", source, inet_reason(err));
        // 证书错误换源 超时可重试
        java_dl_retryable = (err == 12002UL || err == 12030UL || err == 12031UL) ? 1 : 0;
        close_fn(inet);
        FreeLibrary(w);
        return 0;
    }

    if (query_fn(req, 0x20000013UL, &status, &status_len, &status_idx)
            && (status < 200 || status >= 300)) {
        sprintf(java_dl_error, "%s 返回 HTTP %lu", source, status);
        java_dl_retryable = 0;
        ok = 0;
    }
    if (ok && query_fn(req, 0x20000005UL, &total, &total_len, &total_idx) && total > 0) {
        java_dl_total = total;
        java_dl_total_known = 1;
        prog_set(0, (int)total);
    }
    if (ok) {
        out = CreateFileW(java_dl_zip, GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE,
                          NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
        if (out == INVALID_HANDLE_VALUE) {
            sprintf(java_dl_error, "无法写入下载文件（错误 %lu）", GetLastError());
            java_dl_retryable = 0;
            ok = 0;
        }
    }
    while (ok && (read_ok = read_fn(req, buf, sizeof(buf), &got)) && got > 0) {
        unsigned long wrote = 0;
        if (java_dl_stalled) {
            sprintf(java_dl_error, "%s 卡住（20 秒没有数据）", source);
            java_dl_retryable = 1;
            ok = 0;
            break;
        }
        if (!WriteFile(out, buf, got, &wrote, NULL) || wrote != got) {
            sprintf(java_dl_error, "写入 Java 下载文件失败（错误 %lu）", GetLastError());
            ok = 0;
        }
    }
    if (ok && !read_ok) {
        err = GetLastError();
        sprintf(java_dl_error, "%s 下载中断：%s", source, inet_reason(err));
        java_dl_retryable = 1;
        ok = 0;
    }

    if (out != INVALID_HANDLE_VALUE) CloseHandle(out);
    close_fn(req);
    close_fn(inet);
    FreeLibrary(w);
    // Content-Length 已知时对账 提前 EOF 判失败
    if (ok && java_dl_total_known && file_size_u(java_dl_zip) != java_dl_total) {
        sprintf(java_dl_error, "%s 下载不完整", source);
        java_dl_retryable = 1;
        ok = 0;
    }
    if (!ok) {
        DeleteFileW(java_dl_zip);
        return 0;
    }
    return 1;
}

// 4 连接分块下载 失败回退单流
#define JAVA_DL_PARTS 4
typedef struct {
    const wchar_t *url;
    unsigned long start;
    unsigned long end; // 含端点
    int index;
} JavaPart;

static wchar_t java_dl_part_path[JAVA_DL_PARTS][MAX_PATH_LEN];
static const wchar_t *JAVA_PART_SUFFIX[JAVA_DL_PARTS] = { L".p0", L".p1", L".p2", L".p3" };

static DWORD WINAPI java_part_thread(void *arg)
{
    JavaPart *p = (JavaPart *)arg;
    typedef void * (WINAPI *OpenFn)(const wchar_t *, unsigned long, const wchar_t *, const wchar_t *, unsigned long);
    typedef void * (WINAPI *OpenUrlFn)(void *, const wchar_t *, const wchar_t *, unsigned long, unsigned long);
    typedef int (WINAPI *QueryFn)(void *, unsigned long, void *, unsigned long *, unsigned long *);
    typedef int (WINAPI *ReadFn)(void *, void *, unsigned long, unsigned long *);
    typedef int (WINAPI *SetOptFn)(void *, unsigned long, void *, unsigned long);
    typedef int (WINAPI *CloseFn)(void *);
    typedef int (WINAPI *AddHdrFn)(void *, const wchar_t *, unsigned long, unsigned long);
    OpenFn open_fn;
    OpenUrlFn open_url_fn;
    QueryFn query_fn;
    ReadFn read_fn;
    SetOptFn set_opt_fn;
    CloseFn close_fn;
    AddHdrFn add_hdr_fn;
    HMODULE wi = NULL;
    void *inet = NULL, *req = NULL;
    HANDLE out = INVALID_HANDLE_VALUE;
    wchar_t hdr[128];
    wchar_t range_w[128];
    char range_a[96];
    char buf[65536];
    unsigned long got = 0, status = 0, status_len = sizeof(status), status_idx = 0;
    unsigned long want, written = 0, timeout = 15000;
    DWORD exit_code = 0;

    wi = LoadLibraryW(L"wininet.dll");
    if (!wi) goto done;
    open_fn = (OpenFn)GetProcAddress(wi, "InternetOpenW");
    open_url_fn = (OpenUrlFn)GetProcAddress(wi, "InternetOpenUrlW");
    query_fn = (QueryFn)GetProcAddress(wi, "HttpQueryInfoW");
    read_fn = (ReadFn)GetProcAddress(wi, "InternetReadFile");
    set_opt_fn = (SetOptFn)GetProcAddress(wi, "InternetSetOptionW");
    add_hdr_fn = (AddHdrFn)GetProcAddress(wi, "HttpAddRequestHeadersW");
    close_fn = (CloseFn)GetProcAddress(wi, "InternetCloseHandle");
    if (!open_fn || !open_url_fn || !query_fn || !read_fn || !close_fn || !add_hdr_fn) goto done;

    inet = open_fn(TCL_UA, 0, NULL, NULL, 0);
    if (!inet) goto done;
    if (set_opt_fn) {
        set_opt_fn(inet, 2, &timeout, sizeof(timeout));
        timeout = 30000;
        set_opt_fn(inet, 6, &timeout, sizeof(timeout));
    }
    req = open_url_fn(inet, p->url, NULL, 0,
                      0x80000000UL | 0x04000000UL | 0x00800000UL | 0x00400000UL);
    if (!req) goto done;
    sprintf(range_a, "Range: bytes=%lu-%lu\r\n", p->start, p->end);
    // w 共享池非线程安全 分块线程用栈上缓冲
    if (!MultiByteToWideChar(CP_UTF8, 0, range_a, -1, range_w, 128)) goto done;
    add_hdr_fn(req, range_w, (unsigned long)-1, 0xA0000000UL);
    if (!query_fn(req, 0x20000013UL, &status, &status_len, &status_idx) || status != 206UL) goto done;

    out = CreateFileW(java_dl_part_path[p->index], GENERIC_WRITE, FILE_SHARE_READ,
                      NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (out == INVALID_HANDLE_VALUE) goto done;

    want = p->end - p->start + 1;
    while (written < want) {
        unsigned long chunk = sizeof(buf);
        unsigned long wrote = 0;
        if (chunk > want - written) chunk = want - written;
        if (java_dl_stalled || java_dl_abort) goto done;
        if (!read_fn(req, buf, chunk, &got) || got == 0) goto done;
        if (!WriteFile(out, buf, got, &wrote, NULL) || wrote != got) goto done;
        written += got;
        InterlockedExchangeAdd(&java_dl_part_bytes, (LONG)got);
    }
    exit_code = 1;
done:
    if (out != INVALID_HANDLE_VALUE) CloseHandle(out);
    if (req && close_fn) close_fn(req);
    if (inet && close_fn) close_fn(inet);
    if (wi) FreeLibrary(wi);
    if (exit_code != 1) InterlockedExchange(&java_dl_abort, 1);
    return exit_code;
}

// 探测 Range 不支持则单流
static int download_url_parallel(const wchar_t *url, const char *source)
{
    typedef void * (WINAPI *OpenFn)(const wchar_t *, unsigned long, const wchar_t *, const wchar_t *, unsigned long);
    typedef void * (WINAPI *OpenUrlFn)(void *, const wchar_t *, const wchar_t *, unsigned long, unsigned long);
    typedef int (WINAPI *QueryFn)(void *, unsigned long, void *, unsigned long *, unsigned long *);
    typedef int (WINAPI *SetOptFn)(void *, unsigned long, void *, unsigned long);
    typedef int (WINAPI *CloseFn)(void *);
    typedef int (WINAPI *AddHdrFn)(void *, const wchar_t *, unsigned long, unsigned long);
    OpenFn open_fn;
    OpenUrlFn open_url_fn;
    QueryFn query_fn;
    SetOptFn set_opt_fn;
    CloseFn close_fn;
    AddHdrFn add_hdr_fn;
    HMODULE wi = NULL;
    void *inet = NULL, *req = NULL, *out = INVALID_HANDLE_VALUE, *in = INVALID_HANDLE_VALUE;
    wchar_t crange[160];
    char merge_buf[262144];
    JavaPart parts[JAVA_DL_PARTS];
    HANDLE handles[JAVA_DL_PARTS];
    unsigned long status = 0, status_len = sizeof(status), status_idx = 0;
    unsigned long crange_len = sizeof(crange);
    unsigned long total = 0, chunk, pos, i, n;
    unsigned long timeout = 15000, protocols = 0x00000800UL | 0x00000200UL | 0x00000080UL;
    int ok = 0;

    java_dl_error[0] = 0;
    java_dl_part_bytes = 0;
    java_dl_abort = 0;
    java_dl_parallel = 0;
    DeleteFileW(java_dl_zip);
    wi = LoadLibraryW(L"wininet.dll");
    if (!wi) return 0;
    open_fn = (OpenFn)GetProcAddress(wi, "InternetOpenW");
    open_url_fn = (OpenUrlFn)GetProcAddress(wi, "InternetOpenUrlW");
    query_fn = (QueryFn)GetProcAddress(wi, "HttpQueryInfoW");
    set_opt_fn = (SetOptFn)GetProcAddress(wi, "InternetSetOptionW");
    close_fn = (CloseFn)GetProcAddress(wi, "InternetCloseHandle");
    add_hdr_fn = (AddHdrFn)GetProcAddress(wi, "HttpAddRequestHeadersW");
    if (!open_fn || !open_url_fn || !query_fn || !set_opt_fn || !close_fn || !add_hdr_fn) goto cleanup;

    inet = open_fn(TCL_UA, 0, NULL, NULL, 0);
    if (!inet) goto cleanup;
    if (set_opt_fn) {
        set_opt_fn(inet, 2, &timeout, sizeof(timeout));
        timeout = 30000;
        set_opt_fn(inet, 6, &timeout, sizeof(timeout));
        set_opt_fn(inet, 84, &protocols, sizeof(protocols));
    }
    req = open_url_fn(inet, url, NULL, 0,
                      0x80000000UL | 0x04000000UL | 0x00800000UL | 0x00400000UL);
    if (!req) goto cleanup;
    if (!add_hdr_fn(req, L"Range: bytes=0-0\r\n", (unsigned long)-1, 0xA0000000UL)) goto cleanup;
    if (!query_fn(req, 0x20000013UL, &status, &status_len, &status_idx) || status != 206UL) goto cleanup;
    crange_len = sizeof(crange);
    status_idx = 0;
    // HTTP_QUERY_CONTENT_RANGE = 51
    if (!query_fn(req, 51UL, crange, &crange_len, &status_idx)) goto cleanup;
    crange[159] = 0;
    {
        const wchar_t *slash = wcsrchr(crange, L'/');
        if (!slash) goto cleanup;
        total = (unsigned long)wcstoul(slash + 1, NULL, 10);
    }
    if (total < 32UL * 1024UL * 1024UL) goto cleanup; // 小文件单流更快

    chunk = total / JAVA_DL_PARTS;
    for (i = 0; i < JAVA_DL_PARTS; i++) {
        parts[i].url = url;
        parts[i].start = i * chunk;
        parts[i].end = (i == JAVA_DL_PARTS - 1) ? (total - 1) : ((i + 1) * chunk - 1);
        parts[i].index = (int)i;
        copy_str(java_dl_part_path[i], java_dl_zip, MAX_PATH_LEN);
        cat_str(java_dl_part_path[i], JAVA_PART_SUFFIX[i], MAX_PATH_LEN);
        DeleteFileW(java_dl_part_path[i]);
    }
    java_dl_total = total;
    java_dl_total_known = 1;
    prog_set(0, (int)total);
    java_dl_parallel = 1;
    for (i = 0; i < JAVA_DL_PARTS; i++) {
        handles[i] = CreateThread(NULL, 0, java_part_thread, &parts[i], 0, NULL);
        if (!handles[i]) {
            java_dl_abort = 1;
            for (n = 0; n < i; n++) {
                WaitForSingleObject(handles[n], 5000);
                CloseHandle(handles[n]);
            }
            goto cleanup;
        }
    }
    WaitForMultipleObjects(JAVA_DL_PARTS, handles, TRUE, INFINITE);
    for (i = 0; i < JAVA_DL_PARTS; i++) {
        DWORD code = 0;
        GetExitCodeThread(handles[i], &code);
        CloseHandle(handles[i]);
        if (code != 1) goto cleanup;
    }
    for (i = 0; i < JAVA_DL_PARTS; i++) {
        if (file_size_u(java_dl_part_path[i]) != parts[i].end - parts[i].start + 1) goto cleanup;
    }
    out = CreateFileW(java_dl_zip, GENERIC_WRITE, FILE_SHARE_READ, NULL,
                      CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (out == INVALID_HANDLE_VALUE) goto cleanup;
    ok = 1;
    for (i = 0; i < JAVA_DL_PARTS && ok; i++) {
        in = CreateFileW(java_dl_part_path[i], GENERIC_READ, FILE_SHARE_WRITE, NULL,
                         OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
        if (in == INVALID_HANDLE_VALUE) { ok = 0; break; }
        for (;;) {
            unsigned long got = 0, wrote = 0;
            if (!ReadFile(in, merge_buf, sizeof(merge_buf), &got, NULL)) { ok = 0; break; }
            if (got == 0) break;
            if (!WriteFile(out, merge_buf, got, &wrote, NULL) || wrote != got) { ok = 0; break; }
        }
        CloseHandle(in);
        in = INVALID_HANDLE_VALUE;
    }
    CloseHandle(out);
    out = INVALID_HANDLE_VALUE;
    if (!ok) goto cleanup;
    if (file_size_u(java_dl_zip) != total) {
        ok = 0;
        goto cleanup;
    }
    for (i = 0; i < JAVA_DL_PARTS; i++) DeleteFileW(java_dl_part_path[i]);

cleanup:
    if (!ok) {
        for (i = 0; i < JAVA_DL_PARTS; i++) DeleteFileW(java_dl_part_path[i]);
        DeleteFileW(java_dl_zip);
        java_dl_part_bytes = 0;
    }
    java_dl_parallel = 0;
    if (out != INVALID_HANDLE_VALUE) CloseHandle(out);
    if (in != INVALID_HANDLE_VALUE) CloseHandle(in);
    if (req && close_fn) close_fn(req);
    if (inet && close_fn) close_fn(inet);
    if (wi) FreeLibrary(wi);
    return ok;
}

static unsigned long java_dl_bytes(void) {
    // 分块进度累加 拼接前为 0
    if (java_dl_parallel) return (unsigned long)java_dl_part_bytes;
    return file_size_u(java_dl_zip);
}

static DWORD WINAPI java_install_thread(void *unused) {
    JavaSource src[4];
    int count, i, attempt, ok = 0;

    (void)unused;
    java_dl_phase = 1;
    java_dl_result = 0;
    java_dl_error[0] = 0;
    DeleteFileW(java_dl_zip);

    count = java_sources(src, 4);
    // 每源 3 次 20s 无进度换源
    for (i = 0; i < count && !ok; i++) {
        for (attempt = 0; attempt < 3 && !ok; attempt++) {
            java_dl_source_idx = i;
            java_dl_attempt = attempt + 1;
            copy_bytes(java_dl_source, sizeof(java_dl_source), src[i].label);
            java_dl_stalled = 0;
            java_dl_retryable = 1;
            java_dl_total = 0;
            java_dl_total_known = 0;
            java_dl_progress_bytes = 0;
            java_dl_progress_tick = GetTickCount();
            DeleteFileW(java_dl_zip);
            if (attempt > 0) Sleep(1200 * attempt);
            // 先分块 失败回退单流
            ok = download_url_parallel(src[i].url, src[i].label);
            if (!ok) {
                java_dl_error[0] = 0;
                java_dl_retryable = 1;
                ok = download_url(src[i].url, src[i].label);
            }
            if (!ok) stub_log(java_dl_error);
            if (!ok && !java_dl_retryable) break; // 这个源没救 直接换下一个
        }
    }
    if (!ok) {
        java_dl_phase = 3;
        java_dl_result = -1;
        return 0;
    }
    if (!file_is_zip(java_dl_zip)) {
        sprintf(java_dl_error, "%s", "下载文件不是有效的 ZIP");
        java_dl_result = -1;
        java_dl_phase = 3;
        return 0;
    }

    java_dl_phase = 2;
    if (!extract_java_zip()) {
        java_dl_result = -1;
        java_dl_phase = 3;
        return 0;
    }
    java_dl_result = 1;
    java_dl_phase = 3;
    return 0;
}

static void java_dl_tick(void) {
    char buf[512];
    unsigned long bytes, elapsed, speed;

    if (java_dl_phase == 1) {
        bytes = java_dl_bytes();
        // 20s 无数据换源
        if (bytes > java_dl_progress_bytes) {
            java_dl_progress_bytes = bytes;
            java_dl_progress_tick = GetTickCount();
        } else if (bytes < java_dl_progress_bytes) {
            java_dl_progress_bytes = bytes;
            java_dl_progress_tick = GetTickCount();
        } else if (GetTickCount() - java_dl_progress_tick > 20000) {
            java_dl_stalled = 1;
        }
        if (java_dl_total > 0) prog_set((int)(bytes > java_dl_total ? java_dl_total : bytes),
                                        (int)java_dl_total);
        elapsed = (GetTickCount() - java_dl_last_tick) / 1000;
        if (elapsed >= 1) {
            speed = (bytes > java_dl_last_bytes ? bytes - java_dl_last_bytes : 0) / elapsed / 1024;
            if (java_dl_stalled) {
                sprintf(buf, "连接卡住，正在换源…");
            } else if (java_dl_total > 0) {
                sprintf(buf, "下载 %s（第 %d 次）：%.1f / %.1f MB · %lu KB/s",
                        java_dl_source[0] ? java_dl_source : "Java 17", (int)java_dl_attempt,
                        bytes / 1048576.0, java_dl_total / 1048576.0, (unsigned long)speed);
            } else {
                sprintf(buf, "下载 %s（第 %d 次）：%.1f MB · %lu KB/s",
                        java_dl_source[0] ? java_dl_source : "Java 17", (int)java_dl_attempt,
                        bytes / 1048576.0, (unsigned long)speed);
            }
            log_append(buf);
            java_dl_last_bytes = bytes;
            java_dl_last_tick = GetTickCount();
        }
        return;
    }
    if (java_dl_phase == 2) {
        if (!java_dl_extract_logged) {
            java_dl_extract_logged = 1;
            log_append("下载完成，正在解压 Java 17 到 .tcl/java/windows-x64/jre17…");
        }
        return;
    }
    if (java_dl_phase == 3) {
        KillTimer(java_dlg, TIMER_DOWNLOAD_ID);
        if (java_dl_thread) {
            CloseHandle(java_dl_thread);
            java_dl_thread = NULL;
        }
        if (java_dl_result == 1) {
            unsigned long got = file_size_u(java_dl_zip);
            if (got > 0) java_dl_total = got;
            prog_set(java_dl_total > 0 ? (int)java_dl_total : 1,
                     java_dl_total > 0 ? (int)java_dl_total : 1);
            SetWindowTextW(ctl_path, java_dl_java);
            log_append("Java 17 安装完成，路径已保存，正在启动…");
            // 显示完成后自动确认
            SetTimer(java_dlg, TIMER_ACCEPT_ID, 700, NULL);
        } else {
            prog_set(0, 1);
            log_append(java_dl_error[0] ? java_dl_error : "所有下载线路都失败了");
            {
                int wm, wn;
                if (window_version(&wm, &wn) && strstr(java_dl_error, "TLS"))
                    log_append("Windows 7 请先安装 SP1 与 TLS 1.2 更新（KB3140245），否则 HTTPS 下载会失败。");
            }
            log_append("可以点「手动选择」指定已装好的 Java，或过一会儿再点「自动下载」。");
            EnableWindow(GetDlgItem(java_dlg, IDC_JAVA_DOWN), TRUE);
            EnableWindow(GetDlgItem(java_dlg, IDC_JAVA_SCAN), TRUE);
            EnableWindow(GetDlgItem(java_dlg, IDC_JAVA_BROWSE), TRUE);
            EnableWindow(GetDlgItem(java_dlg, IDC_JAVA_OK), TRUE);
            java_dl_phase = 0;
        }
        return;
    }
}

static void java_dl_start(void) {
    wchar_t existing[MAX_PATH_LEN];
    wchar_t base[MAX_PATH_LEN];
    char buf[512];

    if (java_dl_phase != 0) return;
    KillTimer(java_dlg, TIMER_SCAN_ID);
    if (!java_install_supported()) {
        log_append("当前 Windows 不是 64 位 x64，JavaFX 17 没有对应的原生运行库，不能自动安装。");
        EnableWindow(GetDlgItem(java_dlg, IDC_JAVA_SCAN), TRUE);
        EnableWindow(GetDlgItem(java_dlg, IDC_JAVA_DOWN), TRUE);
        MessageBoxW(java_dlg, w("当前 Windows 不是 64 位 x64，无法自动安装 Java 17。\n"
                               "请手动安装与系统匹配的 Java 17。"),
                    w(APP_NAME), MB_ICONERROR | MB_OK);
        return;
    }

    copy_str(base, home_dir, MAX_PATH_LEN);
    cat_str(base, L"\\java\\windows-x64", MAX_PATH_LEN);
    create_dirs(base);
    copy_str(java_dl_dest, base, MAX_PATH_LEN);
    cat_str(java_dl_dest, L"\\jre17", MAX_PATH_LEN);
    copy_str(java_dl_tmp, base, MAX_PATH_LEN);
    cat_str(java_dl_tmp, L"\\.jre17.tmp", MAX_PATH_LEN);
    copy_str(java_dl_zip, base, MAX_PATH_LEN);
    cat_str(java_dl_zip, L"\\.jre17.zip", MAX_PATH_LEN);

    if (find_java_exe(java_dl_dest, existing, MAX_PATH_LEN, 4)) {
        int major = java_feature(existing);
        if (major >= 17 && java_is64(existing)) {
            copy_str(java_dl_java, existing, MAX_PATH_LEN);
            SetWindowTextW(ctl_path, java_dl_java);
            log_append("已找到 .tcl 内的 64 位 Java 17，直接保存并启动。");
            java_dlg_accept();
            return;
        }
        remove_tree(java_dl_dest, 0);
    }

    java_dl_extract_logged = 0;
    java_dl_last_bytes = 0;
    java_dl_last_tick = GetTickCount();
    java_dl_start_tick = java_dl_last_tick;
    java_dl_total = 0;
    java_dl_total_known = 0;
    java_dl_stalled = 0;
    java_dl_attempt = 0;
    java_dl_source[0] = 0;
    prog_set(0, 1);
    // 立即反馈点击
    status_set("正在连接下载服务器…");

    EnableWindow(GetDlgItem(java_dlg, IDC_JAVA_DOWN), FALSE);
    EnableWindow(GetDlgItem(java_dlg, IDC_JAVA_SCAN), FALSE);
    EnableWindow(GetDlgItem(java_dlg, IDC_JAVA_BROWSE), FALSE);
    EnableWindow(GetDlgItem(java_dlg, IDC_JAVA_OK), FALSE);

    java_dl_phase = 1;
    java_dl_thread = CreateThread(NULL, 0, java_install_thread, NULL, 0, NULL);
    if (!java_dl_thread) {
        sprintf(java_dl_error, "%s", "无法创建 Java 下载线程");
        java_dl_result = -1;
        java_dl_phase = 3;
    }
    SetTimer(java_dlg, TIMER_DOWNLOAD_ID, 250, NULL);
}

// 选 java.exe / javaw.exe
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
    ofn.Flags = 0x00001000 | 0x00000800 | 0x00000004 | 0x00080000 | 0x00000008;
    if (!fn(&ofn)) return 0;
    copy_str(out, buf, cap);
    return 1;
}

static void scan_finish(void) {
    KillTimer(java_dlg, TIMER_SCAN_ID);
    prog_set(scan_total, scan_total);
    if (scan_best[0]) {
        SetWindowTextW(ctl_path, scan_best);
        sprintf(scan_msg, "已找到 Java %d（64 位），正在启动…", scan_best_major);
        status_set(scan_msg);
        // 已找到 Java 直接使用
        SetTimer(java_dlg, TIMER_ACCEPT_ID, 600, NULL);
    } else {
        status_set("没有找到可用的 Java，正在自动下载 Java 17…");
        java_dl_start();
        // 下载中禁用扫描/下载按钮
        return;
    }
    EnableWindow(GetDlgItem(java_dlg, IDC_JAVA_SCAN), TRUE);
    EnableWindow(GetDlgItem(java_dlg, IDC_JAVA_DOWN), TRUE);
}

static void scan_begin(void) {
    collect_candidates();
    scan_total = cand_count;
    scan_idx = 0;
    scan_best[0] = 0;
    scan_best_major = 0;
    prog_set(0, scan_total > 0 ? scan_total : 1);

    if (scan_total == 0) {
        status_set("没有找到可用的 Java，正在自动下载 Java 17…");
        EnableWindow(GetDlgItem(java_dlg, IDC_JAVA_SCAN), TRUE);
        java_dl_start();
        return;
    }

    sprintf(scan_msg, "正在查找 Java… 0/%d", scan_total);
    status_set(scan_msg);
    EnableWindow(GetDlgItem(java_dlg, IDC_JAVA_SCAN), FALSE);
    SetTimer(java_dlg, TIMER_SCAN_ID, 20, NULL);
}

static void scan_step(void) {
    int major, is64;

    if (scan_idx >= scan_total) { scan_finish(); return; }

    major = java_feature(cands[scan_idx]);
    is64 = java_is64(cands[scan_idx]);

    if (major >= 17 && is64) {
        if (scan_best_major == 0 || major > scan_best_major) {
            scan_best_major = major;
            copy_str(scan_best, cands[scan_idx], MAX_PATH_LEN);
        }
    }

    scan_idx++;
    prog_set(scan_idx, scan_total);
    sprintf(scan_msg, "正在查找 Java… %d/%d", scan_idx, scan_total);
    status_set(scan_msg);
    if (scan_idx >= scan_total) scan_finish();
}

static int java_usable(const wchar_t *path) {
    if (!path || !path[0] || !file_exists(path)) return 0;
    return java_feature(path) >= 17 && java_is64(path);
}

static void java_check_pick(const wchar_t *path) {
    char buf[256];
    int major = java_feature(path);

    if (major <= 0) {
        status_set("这个文件不是可用的 Java，请重新选择。");
        return;
    }
    if (major < 17) {
        sprintf(buf, "这个 Java 是 %d 版，需要 17 或更高。", major);
        status_set(buf);
        return;
    }
    if (!java_is64(path)) {
        status_set("这个 Java 是 32 位的，需要 64 位版本。");
        return;
    }
    sprintf(buf, "已找到 Java %d（64 位），点「启动」继续。", major);
    status_set(buf);
}

static void java_dlg_accept(void) {
    wchar_t p[MAX_PATH_LEN];
    int major;

    GetWindowTextW(ctl_path, p, MAX_PATH_LEN);
    if (!p[0]) {
        MessageBoxW(java_dlg, w("还没有选好 Java。\r\n请点「自动下载」，或「手动选择」指定 java.exe。"),
                    w(APP_NAME), MB_ICONINFORMATION | MB_OK);
        return;
    }
    if (!file_exists(p)) {
        MessageBoxW(java_dlg, w("这个文件不存在，请重新选择。"),
                    w(APP_NAME), MB_ICONERROR | MB_OK);
        return;
    }
    major = java_feature(p);
    if (major <= 0) {
        MessageBoxW(java_dlg, w("这个文件不是可用的 Java，请重新选择。"),
                    w(APP_NAME), MB_ICONERROR | MB_OK);
        return;
    }
    if (major < 17) {
        char buf[256];
        sprintf(buf, "这个 Java 是 %d 版，启动器需要 17 或更高版本。", major);
        MessageBoxW(java_dlg, w(buf), w(APP_NAME), MB_ICONERROR | MB_OK);
        return;
    }
    if (!java_is64(p)) {
        MessageBoxW(java_dlg, w("这个 Java 是 32 位的，启动器需要 64 位版本。"),
                    w(APP_NAME), MB_ICONERROR | MB_OK);
        return;
    }
    copy_str(java_dlg_pick, p, MAX_PATH_LEN);
    java_dlg_result = 1;
    DestroyWindow(java_dlg);
}

static void java_dlg_create(HWND h) {
    HFONT font = (HFONT)GetStockObject(DEFAULT_GUI_FONT);
    HWND c;

    c = CreateWindowExW(0, L"BUTTON", w("自动下载"), WS_CHILD | WS_VISIBLE | WS_TABSTOP,
                        18, 16, 132, 32, h, (HMENU)IDC_JAVA_DOWN, NULL, NULL);
    SendMessageW(c, WM_SETFONT, (WPARAM)font, TRUE);

    c = CreateWindowExW(0, L"BUTTON", w("手动选择"), WS_CHILD | WS_VISIBLE | WS_TABSTOP,
                        160, 16, 132, 32, h, (HMENU)IDC_JAVA_BROWSE, NULL, NULL);
    SendMessageW(c, WM_SETFONT, (WPARAM)font, TRUE);

    c = CreateWindowExW(0, L"BUTTON", w("重新搜索"), WS_CHILD | WS_VISIBLE | WS_TABSTOP,
                        302, 16, 140, 32, h, (HMENU)IDC_JAVA_SCAN, NULL, NULL);
    SendMessageW(c, WM_SETFONT, (WPARAM)font, TRUE);

    ctl_prog = CreateWindowExW(0, L"msctls_progress32", L"",
                               WS_CHILD | WS_VISIBLE | 0x01,
                               18, 60, 424, 12, h, (HMENU)IDC_JAVA_PROG, NULL, NULL);

    ctl_status = CreateWindowExW(0, L"STATIC", w("正在查找 Java…"), WS_CHILD | WS_VISIBLE,
                                 18, 78, 424, 20, h, (HMENU)IDC_JAVA_STATUS, NULL, NULL);
    SendMessageW(ctl_status, WM_SETFONT, (WPARAM)font, TRUE);

    ctl_path = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT",
                               java_dlg_seed[0] ? java_dlg_seed : cfg_java,
                               WS_CHILD | WS_VISIBLE | ES_AUTOHSCROLL | ES_READONLY,
                               18, 104, 424, 22, h, (HMENU)IDC_JAVA_PATH, NULL, NULL);
    SendMessageW(ctl_path, WM_SETFONT, (WPARAM)font, TRUE);

    c = CreateWindowExW(0, L"BUTTON", w("启动"), WS_CHILD | WS_VISIBLE | WS_TABSTOP
                        | BS_DEFPUSHBUTTON, 248, 140, 100, 30, h, (HMENU)IDC_JAVA_OK, NULL, NULL);
    SendMessageW(c, WM_SETFONT, (WPARAM)font, TRUE);

    c = CreateWindowExW(0, L"BUTTON", w("退出"), WS_CHILD | WS_VISIBLE | WS_TABSTOP,
                        356, 140, 86, 30, h, (HMENU)IDC_JAVA_CANCEL, NULL, NULL);
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
            if (pick_file(h, picked, MAX_PATH_LEN)) {
                SetWindowTextW(ctl_path, picked);
                if (java_usable(picked)) {
                    if (java_dlg_first) {
                        status_set("已选择可用的 Java，点「启动」继续。");
                    } else {
                        status_set("已选择可用的 Java，正在启动…");
                        java_dlg_accept();
                    }
                } else {
                    java_check_pick(picked);
                }
            }
            return 0;
        }
        case IDC_JAVA_DOWN:
            java_dl_start();
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
        if (wp == TIMER_DOWNLOAD_ID) {
            java_dl_tick();
            return 0;
        }
        if (wp == TIMER_ACCEPT_ID) {
            KillTimer(h, TIMER_ACCEPT_ID);
            java_dlg_accept();
            if (java_dlg_result != 1) {
                // 路径校验失败时恢复按钮
                EnableWindow(GetDlgItem(h, IDC_JAVA_DOWN), TRUE);
                EnableWindow(GetDlgItem(h, IDC_JAVA_SCAN), TRUE);
                EnableWindow(GetDlgItem(h, IDC_JAVA_BROWSE), TRUE);
                EnableWindow(GetDlgItem(h, IDC_JAVA_OK), TRUE);
                java_dl_phase = 0;
            }
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

// 返回 1 = 选中 0 = 取消 -1 = 窗口创建失败
static int java_dialog(wchar_t *out, int cap, const wchar_t *preset, int first) {
    WNDCLASSW wc;
    MSG msg;
    HINSTANCE inst = GetModuleHandleW(NULL);
    const int dw = 460, dh = 210;
    int x, y;

    init_common_controls();

    ZeroMemory(&wc, sizeof(wc));
    wc.lpfnWndProc = java_dlg_proc;
    wc.hInstance = inst;
    wc.lpszClassName = L"TCraftJavaPicker";
    wc.hCursor = LoadCursorW(NULL, (const wchar_t *)32512);
    wc.hbrBackground = (HBRUSH)(COLOR_BTNFACE + 1);
    RegisterClassW(&wc);

    java_dlg_result = 0;
    java_dlg_first = first;
    java_dlg_seed[0] = 0;
    if (preset && preset[0] && file_exists(preset)) copy_str(java_dlg_seed, preset, MAX_PATH_LEN);
    java_dlg_pick[0] = 0;

    x = (GetSystemMetrics(0) - dw) / 2;
    y = (GetSystemMetrics(1) - dh) / 2;
    if (x < 0) x = 0;
    if (y < 0) y = 0;
    java_dlg = CreateWindowExW(0, L"TCraftJavaPicker", w("需要 Java 环境"),
                               WS_POPUP | WS_CAPTION | WS_SYSMENU,
                               x, y, dw, dh, NULL, NULL, inst, NULL);
    if (!java_dlg) return -1;

    ShowWindow(java_dlg, SW_SHOW);
    UpdateWindow(java_dlg);
    // 取消置顶 避免压住新窗口
    SetWindowPos(java_dlg, HWND_NOTOPMOST, 0, 0, 0, 0,
                 SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE);
    SetForegroundWindow(java_dlg);

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

// 缺 Java 时显示下载入口
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
                "启动器需要 Java 17 或 Java 17 以上版本。\n\n"
                "启动器自己不带 JRE，需要系统里装一个（请装 64 位 JDK 17+）。\n"
                "已检查 %d 个安装位置（JAVA_HOME、PATH、Program Files 等）。\n"
                "是否打开微软 JDK 17 下载页？", cand_count);
    }
    MultiByteToWideChar(CP_UTF8, 0, utf8, -1, msg, 1024);
    if (MessageBoxW(NULL, msg, L"TCraft Launcher", MB_ICONERROR | MB_YESNO) != IDYES) return;
    open_jdk_page();
}

// 高分屏按真实 DPI 渲染
static void enable_dpi_awareness(void) {
    HMODULE u = GetModuleHandleW(L"user32.dll");
    if (!u) u = LoadLibraryW(L"user32.dll");
    if (u) {
        typedef int (WINAPI *CtxFn)(void *);
        CtxFn ctx = (CtxFn)GetProcAddress(u, "SetProcessDpiAwarenessContext");
        if (ctx) {
            // DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2 = -4
            if (ctx((void *)(long)-4)) return;
        }
        {
            typedef int (WINAPI *AwareFn)(void);
            AwareFn aware = (AwareFn)GetProcAddress(u, "SetProcessDPIAware");
            if (aware) aware();
        }
    }
}

int WINAPI WinMain(HINSTANCE inst, HINSTANCE prev, LPSTR args, int show) {
    unsigned long long size = 0, offset = 0;
    wchar_t java[MAX_PATH_LEN];
    DWORD jar_size_low = 0, jar_size_high = 0;
    HANDLE probe;

    (void)inst; (void)prev; (void)args; (void)show;

    enable_dpi_awareness();
    capture_args();

    if (!GetModuleFileNameW(NULL, self_path, MAX_PATH_LEN)) {
        alert("无法获取程序自身路径。");
        return 1;
    }

    offset = jar_offset(self_path, &size);
    if (offset == 0 || offset >= size) {
        alert("这个 exe 里没有找到内置的 jar，可能构建时没追加成功。");
        return 1;
    }

    // 工作目录固定为 .tcl 不可写则提示换位置
    if (!init_home()) {
        alert("无法在启动器所在目录创建 .tcl 文件夹。\n\n"
              "启动器的程序文件、配置和运行库都会放在这个 .tcl 里（跟着 exe 走）。\n"
              "请把 TCraftLauncher.exe 挪到有写入权限的地方再运行，比如：\n"
              "  · D 盘下任意文件夹\n"
              "  · 桌面\n"
              "  · U 盘（可写）\n\n"
              "不要放在 Program Files、系统盘根目录，也不要放在只读介质里。");
        return 1;
    }
    if (acquire_instance()) {
        activate_existing_window();
        return 0;
    }
    cleanup_legacy_home();
    copy_str(jar_path, home_dir, MAX_PATH_LEN);
    cat_str(jar_path, L"\\" JAR_NAME, MAX_PATH_LEN);

    probe = CreateFileW(jar_path, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING,
                        FILE_ATTRIBUTE_NORMAL, NULL);
    if (probe != INVALID_HANDLE_VALUE) {
        jar_size_low = GetFileSize(probe, &jar_size_high);
        CloseHandle(probe);
    }
    if ((unsigned long long)jar_size_low
        + ((unsigned long long)jar_size_high << 32) != (size - offset)) {
        if (!copy_range(self_path, offset, size - offset, jar_path)) {
            stub_log("解包内置 jar 失败");
            alert("释放内置 jar 失败：.tcl 目录写不进去。\n\n"
                  "常见原因：安全软件拦截、磁盘已满、或 exe 放在只读位置。\n"
                  "请换个位置放 exe 后重试。");
            return 1;
        }
    }

    load_cfg();

    {
        int found = pick_best_java(java, MAX_PATH_LEN);

        if (!found) {
            const wchar_t *seed = NULL;
            int rc = java_dialog(java, MAX_PATH_LEN, seed, 1);
            if (rc < 0) {
                stub_log("Java 选择窗口创建失败");
                offer_download();
                return 1;
            }
            if (rc == 0) {
                return 1;
            }
            save_cfg(java);
        } else if (first_run) {
            // 自动找到的 Java 直接记住
            save_cfg(java);
        }
    }

    {
        int major = java_feature(java);
        if (major > 0 && major < 17) {
            int rc;
            found_major = major;
            rc = java_dialog(java, MAX_PATH_LEN, NULL, 0);
            if (rc <= 0) {
                if (rc < 0) offer_download();
                return 1;
            }
            save_cfg(java);
        }
    }

    {
        DWORD code = 0;
        int r = run(java, &code);

        if (r == 0) {
            stub_log("CreateProcess 失败");
            alert("启动 Java 失败：这个 Java 路径可能已经失效，或者没有执行权限。\n\n"
                  "删掉启动器目录下的 .tcl\\launcher.cfg 再运行一次，就能重新选择 Java。");
            return 1;
        }
        if (r == 2) {
            // 秒退时给出退出码与日志路径
            char detail[2000];
            char log8[MAX_PATH_LEN * 3];
            char head[800];
            char line[128];
            wchar_t logp[MAX_PATH_LEN];
            int got;

            WideCharToMultiByte(CP_UTF8, 0, log_dir[0] ? log_dir : home_dir, -1, log8, sizeof(log8), NULL, NULL);
            copy_str(logp, log_dir[0] ? log_dir : home_dir, MAX_PATH_LEN);
            cat_str(logp, L"\\java-error.log", MAX_PATH_LEN);
            got = read_file(logp, head, sizeof(head) / 3);
            if (got > 0) {
                // JDK 17/18 的 stderr 随 -Dfile.encoding 走 UTF-8 19+ 默认 ACP
                // 先 UTF-8 严格解码 0x8=MB_ERR_INVALID_CHARS 失败再按 ACP
                wchar_t wtmp[400];
                int n = MultiByteToWideChar(CP_UTF8, 0x8, head, got, wtmp, 400);
                if (n <= 0) n = MultiByteToWideChar(GetACP(), 0, head, got, wtmp, 400);
                if (n > 0) {
                    wtmp[n] = 0;
                    head[0] = 0;
                    WideCharToMultiByte(CP_UTF8, 0, wtmp, -1, head, sizeof(head), NULL, NULL);
                }
            } else {
                head[0] = 0;
            }

            sprintf(line, "java 启动后立即退出（退出码 %lu）", (unsigned long)code);
            stub_log(line);
            stub_log(got > 0 ? "java-error.log 有内容" : "java-error.log 为空");

            sprintf(detail,
                    "启动器启动后立即退出了（退出码 %lu）。\n\n"
                    "Java 自己的报错：\n%s\n\n"
                    "如果上面写着 UnsupportedClassVersionError，说明这个 Java 版本低于 17；"
                    "删掉 .tcl\\launcher.cfg 后重新运行，启动器会自动重新检测或下载 Java。",
                    (unsigned long)code,
                    got > 0 ? head : "（java 没有输出任何内容）");
            alert(detail);
            return 1;
        }
    }
    return 0;
}
