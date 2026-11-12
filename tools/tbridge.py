#!/usr/bin/env python3
"""TBridge 客户端 —— 与被测 App 进程内的桥通信。

通道：adb forward tcp:<port> localabstract:tb.<pkg>
协议：一行 JSON 请求 / 一行 JSON 响应

与 uiautomator 的差别（同为 900x1600 MuMu 实测）：
  uiautomator dump  2000-2200 ms   ->  tree    实测见 README
  screencap raw      273-280 ms    ->  shot    View.draw 直接出 PNG
"""

import base64
import json
import os
import socket
import subprocess
import sys
import time

# 公开仓不带本机路径：优先环境变量 ADB，缺省交给 PATH 里的 adb。
ADB = os.environ.get("ADB") or "adb"


class Bridge:
    def __init__(self, pkg, serial=None, port=27110, adb=ADB):
        self.pkg = pkg
        self.serial = serial
        self.port = port
        self.adb = adb
        self._sock = None
        self._f = None

    def connect(self, timeout=10.0):
        args = [self.adb] + (["-s", self.serial] if self.serial else [])
        subprocess.run(args + ["forward", "tcp:%d" % self.port,
                               "localabstract:tb." + self.pkg],
                       check=True, capture_output=True)
        self._sock = socket.create_connection(("127.0.0.1", self.port), timeout=timeout)
        self._f = self._sock.makefile("rwb")
        return self

    def close(self):
        try:
            if self._f:
                self._f.close()
            if self._sock:
                self._sock.close()
        except Exception:
            pass

    def cmd(self, **kw):
        line = json.dumps(kw, ensure_ascii=False).encode("utf-8") + b"\n"
        self._f.write(line)
        self._f.flush()
        resp = self._f.readline()
        if not resp:
            raise ConnectionError("bridge closed")
        return json.loads(resp.decode("utf-8"))

    # ---------------------------------------------------------------- API

    def ping(self):
        return self.cmd(op="ping")

    def act(self):
        return self.cmd(op="act")

    def tree(self):
        return self.cmd(op="tree")

    def find(self, text, exact=False, cls=None):
        kw = {"op": "find", "text": text, "exact": exact}
        if cls:
            kw["cls"] = cls
        return self.cmd(**kw)

    def tap(self, idx=None, text=None, exact=False, cls=None):
        kw = {"op": "tap"}
        if idx is not None:
            kw["idx"] = idx
        if text is not None:
            kw["text"] = text
            kw["exact"] = exact
        if cls:
            kw["cls"] = cls
        return self.cmd(**kw)

    def settext(self, text, idx=None, rid=None):
        kw = {"op": "settext", "text": text}
        if idx is not None:
            kw["idx"] = idx
        if rid is not None:
            kw["rid"] = rid
        return self.cmd(**kw)

    def shot(self, path=None):
        r = self.cmd(op="shot")
        if not r.get("ok"):
            raise RuntimeError(r.get("err"))
        data = base64.b64decode(r["b64"])
        if path:
            with open(path, "wb") as fh:
                fh.write(data)
        return data


def _main(argv):
    if len(argv) < 3:
        print("usage: tbridge.py <pkg> <ping|act|tree|find|tap|shot> [args]", file=sys.stderr)
        return 2
    pkg, sub = argv[1], argv[2]
    b = Bridge(pkg, serial=os.environ.get("DEVICE")).connect()
    t0 = time.time()
    try:
        if sub == "ping":
            out = b.ping()
        elif sub == "act":
            out = b.act()
        elif sub == "tree":
            out = b.tree()
            if "--json" not in argv:
                for n in out.get("nodes", []):
                    print("%4d %-28s %-40s %s%s" % (
                        n["idx"], n["cls"].split(".")[-1][:28],
                        (n["text"] or n["desc"])[:40], n["bounds"],
                        "  [click]" if n["clickable"] else ""))
                out = {"ok": True, "count": out.get("count")}
        elif sub == "find":
            out = b.find(argv[3] if len(argv) > 3 else "")
        elif sub == "tap":
            a = argv[3]
            out = b.tap(idx=int(a.lstrip("#"))) if a.lstrip("#").isdigit() else b.tap(text=a)
        elif sub == "wait":
            # 毫秒级轮询：进程内查树 1ms，所以 20ms 一轮不会把设备压垮
            # （uiautomator 时代一轮要 2.2s，等待粒度被迫拉到秒级）
            text = argv[3]
            timeout = float(argv[4]) if len(argv) > 4 else 10.0
            t0 = time.time()
            polls = 0
            found = False
            while time.time() - t0 < timeout:
                polls += 1
                if b.find(text).get("count", 0) > 0:
                    found = True
                    break
                time.sleep(0.02)
            out = {"ok": found, "found": found, "polls": polls,
                   "waited_ms": round((time.time() - t0) * 1000, 1)}
        elif sub == "settext":
            idx = int(argv[3].lstrip("#"))
            out = b.settext(argv[4], idx=idx)
        elif sub == "tapif":
            a = argv[3]
            maxwait = float(argv[4]) if len(argv) > 4 else 0.0
            t0 = time.time()
            out = {"ok": False, "err": "no such node"}
            while True:
                out = b.tap(idx=int(a.lstrip("#"))) if a.lstrip("#").isdigit() else b.tap(text=a)
                if out.get("ok") or "no such node" not in str(out.get("err", "")):
                    break
                if time.time() - t0 >= maxwait:
                    out = {"ok": True, "skipped": True, "reason": "not present",
                           "waited_ms": round((time.time() - t0) * 1000, 1)}
                    break
                time.sleep(0.02)
        elif sub == "multi":
            # 一次 python 启动跑完整条链路：python 冷启动 ~180ms 是唯一
            # 剩下的固定成本，桥本身每条指令 1ms 左右。
            # 指令用 ";;" 分隔，例：tap 知道了;;wait 登录 5;;shot a.png
            import shlex
            for o in argv[3].split(";;"):
                o = o.strip()
                if not o:
                    continue
                p = shlex.split(o)
                verb, args = p[0], p[1:]
                t = time.time()
                if verb == "tap":
                    a = args[0]
                    r = b.tap(idx=int(a.lstrip("#"))) if a.lstrip("#").isdigit() else b.tap(text=a)
                elif verb == "wait":
                    text = args[0]
                    timeout = float(args[1]) if len(args) > 1 else 10.0
                    t0 = time.time()
                    r = {"ok": False, "found": False}
                    while time.time() - t0 < timeout:
                        if b.find(text).get("count", 0) > 0:
                            r = {"ok": True, "found": True}
                            break
                        time.sleep(0.02)
                    r["waited_ms"] = round((time.time() - t0) * 1000, 1)
                elif verb == "tapif":
                    # 存在就点、不存在跳过。第二参数是"最多等多久"（秒）：
                    # 冷启动的弹窗时有时无、且比闪屏晚一步出现——给 0 会漏，
                    # 给死等 8 秒又浪费。轮询一次 1ms，等 5 秒也就几百次。
                    a = args[0]
                    maxwait = float(args[1]) if len(args) > 1 else 0.0
                    t0 = time.time()
                    r = {"ok": False, "err": "no such node"}
                    while True:
                        r = b.tap(idx=int(a.lstrip("#"))) if a.lstrip("#").isdigit() else b.tap(text=a)
                        if r.get("ok") or "no such node" not in str(r.get("err", "")):
                            break
                        if time.time() - t0 >= maxwait:
                            r = {"ok": True, "skipped": True, "reason": "not present",
                                 "waited_ms": round((time.time() - t0) * 1000, 1)}
                            break
                        time.sleep(0.02)
                elif verb == "tree":
                    r = b.tree()
                    r = {"ok": r.get("ok"), "count": r.get("count")}
                elif verb == "find":
                    r = b.find(args[0])
                    r = {"ok": r.get("ok"), "count": r.get("count")}
                elif verb == "shot":
                    p2 = args[0] if args else "shot.png"
                    r = {"ok": True, "bytes": len(b.shot(p2)), "path": p2}
                elif verb == "settext":
                    r = b.settext(args[1], idx=int(args[0].lstrip("#")))
                elif verb == "act":
                    r = b.act()
                elif verb == "sleep":
                    time.sleep(float(args[0]))
                    r = {"ok": True}
                else:
                    r = {"ok": False, "err": "unknown: " + verb}
                print("%-30s %6.1f ms  %s" % (o[:30], (time.time() - t) * 1000,
                                              json.dumps(r, ensure_ascii=False)))
            out = {"ok": True, "ops": len(argv[3].split(";;"))}
        elif sub == "shot":
            p = argv[3] if len(argv) > 3 else "shot.png"
            data = b.shot(p)
            print("saved %s (%d bytes)" % (p, len(data)))
            out = {"ok": True}
        else:
            print("unknown:", sub, file=sys.stderr)
            return 2
        print(json.dumps(out, ensure_ascii=False, indent=2))
        print("-- %.1f ms --" % ((time.time() - t0) * 1000), file=sys.stderr)
        return 0
    finally:
        b.close()


if __name__ == "__main__":
    sys.exit(_main(sys.argv))
