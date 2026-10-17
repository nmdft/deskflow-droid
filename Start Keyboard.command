#!/bin/zsh
cd "${0:A:h}"
/usr/bin/python3 tools/keyboard.py
printf '\n按回车关闭窗口。'
read
