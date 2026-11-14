"""
ROAD NOVEL 효과음·배경음 만들기 (외부 음원 없이 직접 합성 → 저작권 걱정 없음)

실행:  python tools/make_sounds.py
결과:  app/src/main/res/raw/*.wav  (22.05kHz, 16bit, mono)

파이썬 표준 라이브러리만 쓴다 (wave, math, random, struct).
"""
import math
import os
import random
import struct
import wave

RATE = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")


def note(name: str) -> float:
    """'A4' 같은 음 이름 → 주파수(Hz)"""
    names = {"C": -9, "C#": -8, "D": -7, "D#": -6, "E": -5, "F": -4, "F#": -3,
             "G": -2, "G#": -1, "A": 0, "A#": 1, "B": 2}
    pitch, octave = name[:-1], int(name[-1])
    return 440.0 * 2 ** ((names[pitch] + (octave - 4) * 12) / 12)


def silence(sec: float) -> list:
    return [0.0] * int(sec * RATE)


def bell(freq: float, sec: float, amp: float = 0.5, decay: float = 4.0) -> list:
    """글로켄슈필·종 느낌: 사인파 + 배음, 빠르게 울리고 천천히 사라짐"""
    out = []
    for i in range(int(sec * RATE)):
        t = i / RATE
        env = math.exp(-decay * t) * min(1.0, t / 0.004)
        s = (math.sin(2 * math.pi * freq * t)
             + 0.35 * math.sin(2 * math.pi * freq * 2.76 * t) * math.exp(-decay * 2 * t)
             + 0.15 * math.sin(2 * math.pi * freq * 5.4 * t) * math.exp(-decay * 4 * t))
        out.append(amp * env * s / 1.5)
    return out


def pad(freqs: list, sec: float, amp: float = 0.12, attack: float = 1.2, release: float = 1.2) -> list:
    """부드러운 화음 (느리게 커졌다가 작아짐). 살짝 흔들리는 두 음을 겹쳐 따뜻하게"""
    out = []
    n = int(sec * RATE)
    for i in range(n):
        t = i / RATE
        env = min(1.0, t / attack) * min(1.0, (sec - t) / release)
        s = 0.0
        for f in freqs:
            s += math.sin(2 * math.pi * f * t) + 0.5 * math.sin(2 * math.pi * f * 1.003 * t + 1.0)
        out.append(amp * env * s / (1.5 * len(freqs)))
    return out


def mix(base: list, layer: list, at: float, gain: float = 1.0) -> list:
    start = int(at * RATE)
    if len(base) < start + len(layer):
        base = base + [0.0] * (start + len(layer) - len(base))
    for i, v in enumerate(layer):
        base[start + i] += v * gain
    return base


def fade(samples: list, inn: float = 0.01, out: float = 0.05) -> list:
    n = len(samples)
    a, b = int(inn * RATE), int(out * RATE)
    for i in range(min(a, n)):
        samples[i] *= i / a
    for i in range(min(b, n)):
        samples[n - 1 - i] *= i / b
    return samples


def save(name: str, samples: list, peak: float = 0.85) -> None:
    m = max(1e-9, max(abs(s) for s in samples))
    scale = peak / m if m > peak else 1.0
    path = os.path.join(OUT, name + ".wav")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s * scale)) * 32767)) for s in samples))
    print(f"{name}.wav  {len(samples) / RATE:.1f}s")


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    random.seed(7)

    # 배경음: 밤하늘 패드 (Am - F - C - G, 각 6초 = 24초 반복) + 가끔 반짝이는 높은 음
    chords = [["A3", "C4", "E4"], ["F3", "A3", "C4"], ["C3", "E3", "G3", "C4"], ["G3", "B3", "D4"]]
    bgm = []
    for ch in chords:
        bgm += pad([note(n) for n in ch], 6.0, amp=0.16, attack=1.8, release=1.8)
    twinkles = ["E6", "A5", "C6", "G5", "E6", "D6", "C6", "B5"]
    for k, tw in enumerate(twinkles):
        bgm = mix(bgm, bell(note(tw), 1.6, amp=0.10, decay=3.0), at=1.5 + k * 2.9 + random.uniform(-0.3, 0.3))
    save("bgm_night", fade(bgm[: int(24 * RATE)], 0.3, 0.3), peak=0.5)

    # 새 장면: 도-미-솔 올라감
    s = silence(1.2)
    for k, n in enumerate(["C6", "E6", "G6"]):
        s = mix(s, bell(note(n), 0.9, amp=0.45), at=k * 0.11)
    save("sfx_scene", fade(s))

    # 갈림길 등장: 낮은 음 → 높은 음 (질문하는 느낌)
    s = mix(silence(1.0), bell(note("G4"), 0.7, amp=0.5, decay=5), at=0)
    s = mix(s, bell(note("D5"), 0.9, amp=0.5, decay=4), at=0.16)
    save("sfx_choice", fade(s))

    # 선택 완료: 짧은 반짝
    s = silence(0.7)
    for k, n in enumerate(["A5", "C#6", "E6", "A6"]):
        s = mix(s, bell(note(n), 0.5, amp=0.32, decay=7), at=k * 0.045)
    save("sfx_pick", fade(s))

    # 정차 이야기: 낮고 포근한 종 두 번
    s = mix(silence(1.6), bell(note("E4"), 1.4, amp=0.5, decay=2.5), at=0)
    s = mix(s, bell(note("B3"), 1.2, amp=0.4, decay=2.5), at=0.35)
    save("sfx_rest", fade(s, out=0.2))

    # 결말: 짧은 팡파르 (도미솔도 + 화음)
    s = silence(2.6)
    for k, n in enumerate(["C5", "E5", "G5", "C6"]):
        s = mix(s, bell(note(n), 1.0, amp=0.42, decay=3.5), at=k * 0.14)
    s = mix(s, pad([note("C4"), note("E4"), note("G4"), note("C5")], 2.0, amp=0.35, attack=0.05, release=1.2), at=0.56)
    save("sfx_ending", fade(s, out=0.3))


if __name__ == "__main__":
    main()
