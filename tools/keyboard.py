#!/usr/bin/env python3
"""Legacy experimental shell UHID broker; not needed by the accessibility backend."""
import json, pathlib, re, secrets, shlex, shutil, subprocess, sys, time
ROOT = pathlib.Path(__file__).resolve().parent.parent
PACKAGE = 'cn.nmdft.deskflowclient.debug'
ADB = shutil.which('adb') or '/opt/homebrew/bin/adb'
STATE = ROOT / 'local-test' / 'keyboard-launch.json'
def adb(*args, check=True, input=None):
    p = subprocess.run([ADB, *args], input=input, text=True, capture_output=True, timeout=25)
    if check and p.returncode: raise RuntimeError(p.stderr.strip() or p.stdout.strip())
    return p.stdout.strip()
def online():
    return [line.split()[0] for line in adb('devices','-l').splitlines()[1:]
            if re.search(r'\sdevice\s', line)]
def main():
    stop = '--stop' in sys.argv
    devices = online()
    if not devices: raise RuntimeError('请先通过 USB 或无线调试连接并授权一台 Android 设备。')
    if len(devices) != 1: raise RuntimeError('检测到多台设备；请仅保留目标设备连接。')
    serial=devices[0]
    def shell(cmd, **kwargs): return adb('-s',serial,'shell',cmd,**kwargs)
    saved=shell('run-as '+PACKAGE+' cat files/keyboard-helper-state',check=False)
    previous=json.loads(saved) if saved.startswith('{') else (json.loads(STATE.read_text()) if STATE.exists() else {})
    if previous:
        pid=str(int(previous['pid']));token=previous['token']
        cmdline=shell('cat /proc/'+pid+'/cmdline',check=False)
        if token in cmdline and 'KeyboardServer' in cmdline:
            shell('kill '+pid)
    if stop:
        shell('run-as '+PACKAGE+" sh -c 'if [ -f files/keyboard-endpoint ]; then mv files/keyboard-endpoint files/keyboard-endpoint.disabled; fi'")
        print('完整键盘已停止，Fix 回到原文本输入模式。');return
    jar=ROOT/'keyboard-helper'/'build'/'keyboard.dex.jar'
    if not jar.exists(): raise RuntimeError('缺少 keyboard.dex.jar，请使用完整发布文件夹。')
    info=shell('dumpsys package '+PACKAGE)
    uid=re.search(r'userId=(\d+)',info)
    if not uid: raise RuntimeError('请先安装同目录的 Deskflow-Droid-Keyboard.apk。')
    token='deskflow-kbd-'+secrets.token_hex(16)
    adb('-s',serial,'push',str(jar),'/data/local/tmp/deskflow-keyboard.dex.jar')
    cmd='umask 077; CLASSPATH=/data/local/tmp/deskflow-keyboard.dex.jar nohup app_process / KeyboardServer '+shlex.quote(token)+' >/data/local/tmp/deskflow-keyboard.log 2>&1 </dev/null & echo $!'
    pid=shell(cmd).splitlines()[-1]
    if not pid.isdigit(): raise RuntimeError('启动键盘服务失败：'+pid)
    STATE.parent.mkdir(exist_ok=True)
    state=json.dumps(dict(serial=serial,pid=int(pid),token=token))
    STATE.write_text(state)
    shell('run-as '+PACKAGE+" sh -c 'mkdir -p files; cat > files/keyboard-helper-state'",input=state)
    for _ in range(20):
        log=shell('cat /data/local/tmp/deskflow-keyboard.log',check=False)
        if 'READY '+token in log: break
        time.sleep(.25)
    else: raise RuntimeError('键盘服务未就绪：'+log)
    shell('run-as '+PACKAGE+" sh -c 'mkdir -p files; cat > files/keyboard-endpoint'",input=log.split('READY '+token+' ')[1].splitlines()[0]+':'+token+'\n')
    shell('am start -n '+PACKAGE+'/com.itesaurabh.deskflowclient.MainActivity')
    print('键盘服务已就绪：'+serial+'\n在 Fix 中连接 Deskflow，将鼠标移入手机即可启用。\nMac 用 ABC 输入源；中文用手机搜狗输入法。手机重启后再双击本启动文件。')
if __name__=='__main__':
    try: main()
    except (RuntimeError, subprocess.SubprocessError, OSError, ValueError) as e:
        print('启动失败：'+str(e),file=sys.stderr);sys.exit(1)
