/*
 * Windows 外壳：构建时把 jar 追加到 exe 末尾。
 * 运行时按 zip 中央目录找到 jar 偏移，解到 %LOCALAPPDATA%\TCraftLauncher，
 * 再用系统 Java 启动。和 HMCL 的 exe 一个路子。
 */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdio.h>
#include <stdlib.h>
#include <wchar.h>

#define APP_NAME "TCraft Launcher"
#define JAR_NAME L"TCraftLauncher.jar"
#define HOME_SUB L"TCraftLauncher"

#define MAX_PATH_LEN 4096

/* TCC 的 windows.h 缺 winnls.h，手动声明 */
#ifndef CP_UTF8
#define CP_UTF8 65001
#endif
int __stdcall MultiByteToWideChar(unsigned int, unsigned long, const char *, int, wchar_t *, int);

static wchar_t self_path[MAX_PATH_LEN];
static wchar_t home_dir[MAX_PATH_LEN];
static wchar_t jar_path[MAX_PATH_LEN];

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

/* 先看 JAVA_HOME，再看 PATH，最后扫几个常见的安装目录 */
static int find_java(wchar_t *out, int cap) {
    wchar_t probe[MAX_PATH_LEN];
    const wchar_t *home = GetEnvironmentVariableW(L"JAVA_HOME", probe, MAX_PATH_LEN) ? probe : NULL;
    const wchar_t *roots[] = {
        L"C:\\Program Files\\Microsoft",
        L"C:\\Program Files\\Java",
        L"C:\\Program Files\\Eclipse Adoptium",
        L"C:\\Program Files\\BellSoft",
        L"C:\\Program Files\\Amazon Corretto",
        L"C:\\Program Files\\Zulu",
        L"C:\\Program Files\\Temurin",
        NULL
    };
    int i;

    if (home && home[0]) {
        copy_str(out, home, cap);
        cat_str(out, L"\\bin\\javaw.exe", cap);
        if (file_exists(out)) return 1;
        copy_str(out, home, cap);
        cat_str(out, L"\\bin\\java.exe", cap);
        if (file_exists(out)) return 1;
    }

    copy_str(out, L"javaw.exe", cap);
    if (SearchPathW(NULL, out, NULL, MAX_PATH_LEN, probe, NULL) && file_exists(probe)) {
        copy_str(out, probe, cap);
        return 1;
    }
    copy_str(out, L"java.exe", cap);
    if (SearchPathW(NULL, out, NULL, MAX_PATH_LEN, probe, NULL) && file_exists(probe)) {
        copy_str(out, probe, cap);
        return 1;
    }

    for (i = 0; roots[i]; i++) {
        WIN32_FIND_DATAW data;
        wchar_t pattern[MAX_PATH_LEN];
        HANDLE h;

        copy_str(pattern, roots[i], MAX_PATH_LEN);
        cat_str(pattern, L"\\*", MAX_PATH_LEN);
        h = FindFirstFileW(pattern, &data);
        if (h == INVALID_HANDLE_VALUE) continue;
        do {
            if (!(data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)) continue;
            if (data.cFileName[0] == L'.') continue;
            copy_str(out, roots[i], cap);
            cat_str(out, L"\\", cap);
            cat_str(out, data.cFileName, cap);
            cat_str(out, L"\\bin\\javaw.exe", cap);
            if (file_exists(out)) { FindClose(h); return 1; }
        } while (FindNextFileW(h, &data));
        FindClose(h);
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
    cat_str(cmd, L"\" -Dfile.encoding=UTF-8 -jar \"", MAX_PATH_LEN * 2);
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

/* 跑 java -version，输出写到文件，避免弹控制台 */
static int java_feature(const wchar_t *java) {
    wchar_t cmd[MAX_PATH_LEN * 2];
    wchar_t logp[MAX_PATH_LEN];
    SECURITY_ATTRIBUTES sa;
    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    HANDLE out;
    char buf[8192];
    DWORD got = 0;
    int major = 0;

    copy_str(logp, home_dir, MAX_PATH_LEN);
    cat_str(logp, L"\\java-version.txt", MAX_PATH_LEN);

    sa.nLength = sizeof(sa);
    sa.lpSecurityDescriptor = NULL;
    sa.bInheritHandle = TRUE;
    out = CreateFileW(logp, GENERIC_WRITE, FILE_SHARE_READ, &sa, CREATE_ALWAYS,
                      FILE_ATTRIBUTE_NORMAL, NULL);
    if (out == INVALID_HANDLE_VALUE) return 0;

    copy_str(cmd, L"\"", MAX_PATH_LEN * 2);
    cat_str(cmd, java, MAX_PATH_LEN * 2);
    cat_str(cmd, L"\" -version", MAX_PATH_LEN * 2);

    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESTDHANDLES | STARTF_USESHOWWINDOW;
    si.hStdOutput = out;
    si.hStdError = out;
    si.wShowWindow = SW_HIDE;
    ZeroMemory(&pi, sizeof(pi));

    if (!CreateProcessW(java, cmd, NULL, NULL, TRUE, CREATE_NO_WINDOW, NULL, NULL, &si, &pi)) {
        CloseHandle(out);
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
    buf[got] = 0;

    {
        char *hit = strstr(buf, "version \"");
        if (hit) {
            char *p = hit + 9;
            if (p[0] == '1' && p[1] == '.') {
                p += 2;
            }
            major = atoi(p);
        }
    }
    return major;
}

/* 缺 Java 时给下载入口 */
static void offer_download(void) {
    wchar_t msg[512];
    MultiByteToWideChar(CP_UTF8, 0,
            "没有找到 Java 17 或更高版本。\n\n启动器自己不带 JRE，需要系统里装一个。\n是否打开微软 JDK 17 下载页？",
            -1, msg, 512);
    if (MessageBoxW(NULL, msg, L"TCraft Launcher", MB_ICONERROR | MB_YESNO) != IDYES) return;

    {
        HMODULE sh = LoadLibraryW(L"shell32.dll");
        if (sh) {
            typedef HINSTANCE (WINAPI *ShellExec)(HWND, LPCWSTR, LPCWSTR, LPCWSTR, LPCWSTR, int);
            ShellExec fn = (ShellExec)GetProcAddress(sh, "ShellExecuteW");
            if (fn) {
                fn(NULL, L"open", L"https://learn.microsoft.com/java/openjdk/download#openjdk-17",
                   NULL, NULL, SW_SHOWNORMAL);
            }
        }
    }
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

    {
        const wchar_t *base = GetEnvironmentVariableW(L"LOCALAPPDATA", home_dir, MAX_PATH_LEN)
                ? home_dir : NULL;
        if (!base || !base[0]) {
            copy_str(home_dir, self_path, MAX_PATH_LEN);
            strip_file(home_dir);
        }
    }
    cat_str(home_dir, L"\\" HOME_SUB, MAX_PATH_LEN);
    create_dirs(home_dir);
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
            alert("释放内置 jar 失败，检查 %LOCALAPPDATA% 是否有写权限。");
            return 1;
        }
    }

    if (!find_java(java, MAX_PATH_LEN)) {
        offer_download();
        return 1;
    }

    /* class 按 17 编，低版本 JVM 直接抛错，先拦 */
    {
        int major = java_feature(java);
        if (major > 0 && major < 17) {
            offer_download();
            return 1;
        }
    }

    if (!run(java)) {
        alert("启动 Java 失败，可能是 Java 路径失效或权限问题。");
        return 1;
    }
    return 0;
}
