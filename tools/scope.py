#!/usr/bin/env python3
"""给 LSPosed 模块勾/取消作用域，不用点界面。

LSPosed 的作用域存在 /data/adb/lspd/config/modules_config.db，里面就是三张表。
设备上有 /system/bin/sqlite3（Android 12 上是 3.32.2），所以直接在设备改，
不用 pull-modify-push —— lspd 进程持有连接也不会把库锁死。

    python tools/scope.py <模块包名> <目标包名> [更多目标...]     # 勾上
    python tools/scope.py <模块包名> <目标包名> --off              # 取消
    python tools/scope.py <模块包名> <目标包名> --no-zygote        # 改完不重启

改完默认重启 zygote：配置是在 zygote fork 时读的，不重启不生效。
"""
import os, subprocess, sys, tempfile

ADB = os.environ.get("ADB", "adb")
DEVICE = os.environ.get("DEVICE", "")
USER_ID = 0


def adb(*args, **kw):
    cmd = [ADB] + (["-s", DEVICE] if DEVICE else []) + list(args)
    return subprocess.run(cmd, capture_output=True, text=True,
                          encoding="utf-8", errors="replace", **kw)


def main():
    argv = [a for a in sys.argv[1:]]
    if len(argv) < 2:
        print(__doc__)
        return 1
    off = "--off" in argv
    zygote = "--no-zygote" not in argv
    argv = [a for a in argv if not a.startswith("--")]
    mod, targets = argv[0], argv[1:]

    sql = []
    if not off:
        sql.append("INSERT OR REPLACE INTO modules_state "
                   "VALUES('%s',%d,1,0);" % (mod, USER_ID))
    for t in targets:
        if off:
            sql.append("DELETE FROM scope WHERE module_pkg_name='%s' "
                       "AND app_pkg_name='%s' AND user_id=%d;" % (mod, t, USER_ID))
        else:
            sql.append("INSERT OR REPLACE INTO scope "
                       "VALUES('%s','%s',%d);" % (mod, t, USER_ID))
    script = "\n".join(sql)
    print("SQL:\n" + script + "\n")

    tmp = os.path.join(tempfile.gettempdir(), "_lspd_scope.sql")
    with open(tmp, "w", encoding="utf-8", newline="\n") as f:
        f.write(script + "\n")
    r = adb("push", tmp, "/data/local/tmp/_lspd_scope.sql")
    if r.returncode != 0:
        print("push 失败:", r.stderr)
        return 1
    r = adb("shell", "su -c 'sqlite3 /data/adb/lspd/config/modules_config.db "
                     "< /data/local/tmp/_lspd_scope.sql'")
    print("sqlite3 rc=%d %s" % (r.returncode, (r.stdout or r.stderr).strip()))
    if r.returncode != 0:
        return 1

    # 回读确认。查询也走文件而不是内联引号：adb shell → su -c → sqlite3
    # 三层嵌套引号在 Windows 上几乎必错（踩过，会静默返回空）。
    q = ("SELECT module_pkg_name || ' -> ' || app_pkg_name FROM scope "
         "WHERE module_pkg_name='%s' AND user_id=%d;" % (mod, USER_ID))
    tmp2 = os.path.join(tempfile.gettempdir(), "_lspd_query.sql")
    with open(tmp2, "w", encoding="utf-8", newline="\n") as f:
        f.write(q + "\n")
    adb("push", tmp2, "/data/local/tmp/_lspd_query.sql")
    r = adb("shell", "su -c 'sqlite3 /data/adb/lspd/config/modules_config.db "
                     "< /data/local/tmp/_lspd_query.sql'")
    rows = [x for x in (r.stdout or "").replace("\r", "").splitlines() if x.strip()]
    print("当前作用域 (%d):" % len(rows))
    for x in rows:
        print("  " + x)
    adb("shell", "su -c 'rm -f /data/local/tmp/_lspd_scope.sql /data/local/tmp/_lspd_query.sql'")

    if zygote:
        print("重启 zygote ...")
        adb("shell", "su -c 'setprop ctl.restart zygote'")
    return 0


if __name__ == "__main__":
    sys.exit(main())
