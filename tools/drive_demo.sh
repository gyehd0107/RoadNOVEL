#!/usr/bin/env bash
# ROAD NOVEL 실주행 시연 (녹화용, 약 2분 20초)
#
# 가상 차량(Pleos Virtual Console)을 스크립트가 운전하고, 앱은 차량 신호에만 반응한다.
# 화면에는 디버그 패널이 나오지 않는다.
#   출발 → 주행(기어 D, 시속 40km) → 정차(기어 P, 정차 이야기) → 다시 주행 → 갈림길(버섯 마을)
#   → 내비 안내 끼어들기 → 두 번째 갈림길(노래) → 결말
#
# 준비
#   - Pleos Connect 에뮬레이터 실행 (Cold boot), ROAD NOVEL 디버그 빌드 설치
#   - curl, python(Pillow 라이브러리) 필요:  pip install pillow
# 실행
#   bash tools/drive_demo.sh
#   (adb가 PATH에 없으면  ADB=/경로/adb bash tools/drive_demo.sh)
set -u
export MSYS_NO_PATHCONV=1

# adb 찾기: 환경 변수 ADB → PATH → Android SDK 기본 위치
if [ -z "${ADB:-}" ]; then
  if command -v adb >/dev/null 2>&1; then ADB=adb
  elif [ -n "${ANDROID_HOME:-}" ]; then ADB="$ANDROID_HOME/platform-tools/adb"
  elif [ -n "${LOCALAPPDATA:-}" ]; then ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb"
  else ADB="$HOME/Library/Android/sdk/platform-tools/adb"
  fi
fi

TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
# Windows(Git Bash)에서는 /tmp/... 경로를 파이썬이 못 읽으므로 C:/... 형식으로 바꾼다
command -v cygpath >/dev/null 2>&1 && TMP=$(cygpath -m "$TMP")
VC=http://127.0.0.1:8088
PKG=com.roadnovel
START=$(date +%s)

log() { echo "  [$(( $(date +%s) - START ))초] $*"; }
wait_s() { local T=$(date +%s); until [ $(( $(date +%s) - T )) -ge "$1" ]; do :; done; }
vc() { curl -s -m 5 -X POST "$VC/browser/session?client=drive-demo" >/dev/null; curl -s -m 5 -X PUT "$VC/vehicle/$1" -o /dev/null; }
drive() { vc "gear?gear=Drive"; vc "controls?speed_kph=$1"; log "가상 차량: 기어 D, 시속 $1km"; }
park() { vc "controls?speed_kph=0"; vc "gear?gear=Park"; log "가상 차량: 정차 (기어 P)"; }
dump() { "$ADB" shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1 && "$ADB" shell cat /sdcard/u.xml > "$TMP/u.xml" || : > "$TMP/u.xml"; }
has() { dump; grep -q "$1" "$TMP/u.xml"; }
wait_text() { for _ in $(seq 1 "$2"); do has "$1" && return 0; wait_s 1; done; log "시간 초과: $1"; return 1; }
tap_has() {
  dump
  local b; b=$(python - "$TMP/u.xml" "$1" <<'PY'
import re,sys,html
x=open(sys.argv[1],encoding='utf-8').read()
for m in re.finditer(r'<node [^>]*>', x):
    n=m.group(0); t=re.search(r' text="([^"]*)"',n)
    if t and sys.argv[2] in html.unescape(t.group(1)):
        a=list(map(int,re.findall(r'\d+',re.search(r'bounds="([^"]*)"',n).group(1)))); print((a[0]+a[2])//2,(a[1]+a[3])//2); break
PY
)
  [ -n "$b" ] && "$ADB" shell input tap $b || log "못 찾음: $1"
}
# 선택지 화면은 마이크 애니메이션 때문에 화면 덤프가 안 됨 → 화면 왼쪽이 아주 어두워지는지로 판단
wait_choice() {
  for _ in $(seq 1 "$1"); do
    "$ADB" exec-out screencap -p > "$TMP/c.png"
    python -c "
from PIL import Image; import sys
im=Image.open(r'$TMP/c.png').convert('L').crop((900,300,1300,1100)).resize((40,80))
sys.exit(0 if sum(im.tobytes())/3200 < 14 else 1)" && return 0
    wait_s 1
  done; log "시간 초과: 선택지"; return 1
}
# 선택지 버튼 위치 (Pleos Connect 에뮬레이터 2560x1440 화면 기준)
CHOICE2_OF_2="1823 726"   # 첫 갈림길: 2번 "버섯 마을"
CHOICE1_OF_3="1432 750"   # 두 번째 갈림길: 1번 "노래를 불러 준다"

"$ADB" forward tcp:8088 tcp:8088 >/dev/null
park
# 디버그 빌드에 실주행 시연 명령 전달 (Pleos 차량 신호, 10분 이야기, 시간 10배, 디버그 패널 숨김)
"$ADB" shell am start -S -n $PKG/.MainActivity --es demo drive >/dev/null
wait_text "이야기 고르기" 20; wait_s 3; log "홈 화면"

# 1) 정차 상태에서 이야기 고르기 (10분)
tap_has "이야기 고르기"; wait_text "이야기 길이를" 10; wait_s 2
tap_has "10분"; wait_s 2; log "이야기 선택: 10분"
tap_has "출발! 이야기 시작"; wait_text "CHAPTER" 10; wait_s 4; log "출발"

# 2) 실제처럼 달리기 → 주행 중 화면 + 달린 만큼 진행
drive 40; wait_s 18

# 3) 신호 대기처럼 정차 → 정차 이야기
park; wait_text "정차 이야기" 20 && log "정차 이야기 시작"
wait_s 11
drive 40

# 4) 갈림길 → 동승자가 큰 버튼으로 2번(버섯 마을)
wait_choice 120 && log "갈림길 질문"; wait_s 5
"$ADB" shell input tap $CHOICE2_OF_2; log "선택: 버섯 마을"
wait_s 7

# 5) 내비 안내 끼어들기 → 이야기 멈춤 → 그 문장부터 다시
"$ADB" shell am start -n $PKG/.MainActivity --es demo nav >/dev/null; log "내비 안내 끼어듦"
wait_s 8

# 6) 두 번째 갈림길 → 1번(노래)
wait_choice 150 && log "두 번째 갈림길"; wait_s 5
"$ADB" shell input tap $CHOICE1_OF_3; log "선택: 노래를 불러 준다"

# 7) 결말 → 도착해서 정차
wait_text "이야기 끝" 150 && log "결말"
park; wait_s 5
log "끝"
