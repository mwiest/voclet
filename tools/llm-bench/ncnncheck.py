"""Does the ncnn conversion of PP-OCRv5 still read the same pages?

Answers it without Android in the way. Three comparisons per page, in the
order that tells conversion error apart from precision loss:

1. onnxruntime against the recorded `<page>.probmap.gz` — a check on this
   script, not on ncnn. If the ORT column is not ~0 the preprocessing here
   does not match the pipeline that produced the fixtures and nothing below
   means anything.
2. ncnn fp32 against onnxruntime — pure conversion error.
3. ncnn fp16 against onnxruntime — conversion plus half precision.

Then the same maps go through RapidOCR's own DBPostProcess, because a map that
differs in the third decimal still only matters if a box moves.

Usage: ncnncheck.py <dir with the four <page>.jpg> [--rec]
"""
import gzip
import pathlib
import sys

import cv2
import ncnn
import numpy as np
import onnxruntime as ort
from PIL import Image

ROOT = pathlib.Path(__file__).parent
FIXTURES = ROOT.parent.parent / "app" / "src" / "test" / "resources" / "ocr"
ONNX = ROOT / "data" / "ppocr"
NCNN = ROOT / "data" / "ppocr-ncnn"

MEAN = np.float32([0.485, 0.456, 0.406])
STD = np.float32([0.229, 0.224, 0.225])
LIMIT = 736


def det_size(w: int, h: int) -> tuple[int, int]:
    """LIMIT is a floor on the *short* side, so most pages grow."""
    ratio = LIMIT / min(w, h) if min(w, h) < LIMIT else 1.0
    return (int(round(int(w * ratio) / 32) * 32), int(round(int(h * ratio) / 32) * 32))


def page_rgb(path: pathlib.Path) -> np.ndarray:
    """PIL, not cv2 - the 129/136 was measured through PIL's JPEG decode."""
    return np.array(Image.open(path).convert("RGB"))


def resized(rgb: np.ndarray) -> np.ndarray:
    h, w = rgb.shape[:2]
    nw, nh = det_size(w, h)
    return cv2.resize(rgb, (nw, nh))


def run_onnx(rgb: np.ndarray) -> np.ndarray:
    chw = ((rgb.astype(np.float32) / 255.0 - MEAN) / STD).transpose(2, 0, 1)
    session = run_onnx.session
    name = session.get_inputs()[0].name
    return session.run(None, {name: chw[None]})[0][0, 0]


run_onnx.session = ort.InferenceSession(str(ONNX / "det.onnx"), providers=["CPUExecutionProvider"])


def run_ncnn(rgb: np.ndarray, net: "ncnn.Net") -> np.ndarray:
    h, w = rgb.shape[:2]
    mat = ncnn.Mat.from_pixels(np.ascontiguousarray(rgb), ncnn.Mat.PixelType.PIXEL_RGB, w, h)
    mat.substract_mean_normalize(
        [float(m * 255) for m in MEAN],
        [float(1.0 / (s * 255)) for s in STD],
    )
    ex = net.create_extractor()
    ex.input("in0", mat)
    _, out = ex.extract("out0")
    return np.array(out).reshape(out.h, out.w)


def load_net(kind: str, model: str) -> "ncnn.Net":
    net = ncnn.Net()
    net.opt.use_vulkan_compute = False
    net.load_param(str(NCNN / kind / f"{model}.ncnn.param"))
    net.load_model(str(NCNN / kind / f"{model}.ncnn.bin"))
    return net


def recorded_map(page: str) -> np.ndarray | None:
    path = FIXTURES / f"{page}.probmap.gz"
    if not path.is_file():
        return None
    raw = gzip.decompress(path.read_bytes())
    head, body = raw.split(b"\n", 1)
    map_w, map_h = (int(v) for v in head.split()[:2])
    return np.frombuffer(body, np.uint8).reshape(map_h, map_w).astype(np.float32) / 255.0


def boxes(prob: np.ndarray, src: tuple[int, int]) -> np.ndarray:
    """RapidOCR's own post-processing, so a map difference is judged by boxes."""
    got = boxes.op(prob[None, None], np.array([[src[0], src[1], 1.0, 1.0]]))
    quads = got[0] if isinstance(got, tuple) else got
    # a batch of one, and RapidOCR wraps each quad in {"points": ...}
    if len(quads) and isinstance(quads[0], dict):
        quads = quads[0]["points"]
    elif len(quads) and not isinstance(quads[0], dict) and np.ndim(quads[0]) == 3:
        quads = quads[0]
    if quads is None or len(quads) == 0:
        return np.zeros((0, 8), int)
    return np.array(sorted([int(v) for pt in q for v in pt] for q in quads))


def main() -> None:
    pages_dir = pathlib.Path(sys.argv[1])
    from rapidocr_onnxruntime.ch_ppocr_v3_det.text_detect import TextDetector
    from rapidocr_onnxruntime.utils import read_yaml
    import rapidocr_onnxruntime as rp

    config = read_yaml(pathlib.Path(rp.__file__).parent / "config.yaml")["Det"]
    boxes.op = TextDetector(config).postprocess_op

    fp32, fp16 = load_net("fp32", "det"), load_net("fp16", "det")

    print(f"{'page':<16} {'ORT vs fixture':>15} {'ncnn32 vs ORT':>14} {'ncnn16 vs ORT':>14}"
          f" {'boxes':>18}")
    for jpg in sorted(pages_dir.glob("*.jpg")):
        page = jpg.stem
        rgb = resized(page_rgb(jpg))
        src = page_rgb(jpg).shape[:2]

        m_ort = run_onnx(rgb)
        m_32 = run_ncnn(rgb, fp32)
        m_16 = run_ncnn(rgb, fp16)

        fixture = recorded_map(page)
        self_check = f"{np.abs(m_ort - fixture).max():.4f}" if fixture is not None else "-"

        b_ort, b_32, b_16 = boxes(m_ort, src), boxes(m_32, src), boxes(m_16, src)
        same32 = len(b_ort) == len(b_32) and np.array_equal(b_ort, b_32)
        same16 = len(b_ort) == len(b_16) and np.array_equal(b_ort, b_16)
        verdict = (f"{len(b_ort)}/{len(b_32)}/{len(b_16)}"
                   f" {'=' if same32 else '~'}{'=' if same16 else '~'}")
        print(f"{page:<16} {self_check:>15} {np.abs(m_ort - m_32).max():>14.4f}"
              f" {np.abs(m_ort - m_16).max():>14.4f} {verdict:>18}")

        for label, got in (("fp32", b_32), ("fp16", b_16)):
            if np.array_equal(b_ort, got):
                continue
            # the lists are sorted, so a single moved box misaligns every row
            # after it - compare as sets, and measure only the boxes that moved
            want = {tuple(r) for r in b_ort}
            mine = {tuple(r) for r in got}
            shared = len(want & mine)
            worst = 0
            for row in sorted(mine - want):
                near = min(want, key=lambda w: max(abs(a - b) for a, b in zip(w, row)))
                worst = max(worst, max(abs(a - b) for a, b in zip(near, row)))
            print(f"    {label}: {shared}/{len(b_ort)} boxes identical, "
                  f"{len(mine - want)} moved, worst corner {worst} px")


if __name__ == "__main__":
    main()
