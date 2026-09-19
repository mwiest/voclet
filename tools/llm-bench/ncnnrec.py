"""Does the ncnn recognizer read the same words as the ONNX one?

The detector half is `ncnncheck.py`. This is the half that decides text: it
takes the recorded detector quads, crops them the way PP-OCR does, and runs the
crop through onnxruntime, ncnn fp32 and ncnn fp16, decoding all three with the
dictionary the app ships. The comparison is per line, over all four pages.

Usage: ncnnrec.py <dir with the four <page>.jpg>
"""
import pathlib
import sys
from collections import Counter

import cv2
import ncnn
import numpy as np
import onnxruntime as ort
from PIL import Image

ROOT = pathlib.Path(__file__).parent
FIXTURES = ROOT.parent.parent / "app" / "src" / "test" / "resources" / "ocr"
DICT = ROOT.parent.parent / "app" / "src" / "main" / "assets" / "ocr" / "latin_dict.txt"
ONNX = ROOT / "data" / "ppocr"
NCNN = ROOT / "data" / "ppocr-ncnn"

REC_HEIGHT = 48


def alphabet() -> list[str]:
    """Blank at 0, the dictionary, then space - the 838 classes the model emits."""
    entries = DICT.read_text(encoding="utf-8").split("\n")
    while entries and entries[-1] == "":
        entries.pop()
    return ["<blank>"] + entries + [" "]


def quads(page: str) -> list[np.ndarray]:
    rows = (FIXTURES / f"{page}.detboxes.tsv").read_text().splitlines()
    out = []
    for row in rows:
        if row.strip():
            v = [int(t) for t in row.split("\t")]
            out.append(np.float32([[v[2 * i], v[2 * i + 1]] for i in range(4)]))
    return out


def crop(rgb: np.ndarray, quad: np.ndarray) -> np.ndarray:
    """PP-OCR's get_rotate_crop_image: warp the quad flat, stand it up if tall."""
    width = int(max(np.linalg.norm(quad[0] - quad[1]), np.linalg.norm(quad[2] - quad[3])))
    height = int(max(np.linalg.norm(quad[0] - quad[3]), np.linalg.norm(quad[1] - quad[2])))
    target = np.float32([[0, 0], [width, 0], [width, height], [0, height]])
    warped = cv2.warpPerspective(
        rgb, cv2.getPerspectiveTransform(quad, target), (width, height),
        borderMode=cv2.BORDER_REPLICATE, flags=cv2.INTER_CUBIC,
    )
    if height / max(width, 1) >= 1.5:
        warped = np.rot90(warped)
    return np.ascontiguousarray(warped)


def scaled(img: np.ndarray, padded_to: int | None = None) -> np.ndarray:
    """48 px tall; zero-padded on the right to [padded_to] when batching.

    Batched inference does not mix samples, so a crop padded to its batch's
    common width on its own is the same input the batch would have given it -
    which is what lets a batch-1 model reproduce a batched reference exactly.
    """
    h, w = img.shape[:2]
    width = max(1, int(np.ceil(REC_HEIGHT * w / h)))
    if padded_to:
        width = min(width, padded_to)
    out = cv2.resize(img, (width, REC_HEIGHT))
    if padded_to and width < padded_to:
        # Upstream pads the *normalized* tensor with zero, and normalized zero
        # is mid-gray, not black. Padding with 0 here reads as -1 and costs
        # more lines than not padding at all.
        out = np.pad(out, ((0, 0), (0, padded_to - width), (0, 0)), constant_values=128)
    return np.ascontiguousarray(out)


def batch_widths(crops: list[np.ndarray], size: int = 6) -> list[int]:
    """RecognizerInput.plan's padded width, per crop, in the crops' own order."""
    ratios = [c.shape[1] / c.shape[0] for c in crops]
    order = np.argsort(np.array(ratios), kind="stable")
    widths = [0] * len(crops)
    for beg in range(0, len(order), size):
        group = order[beg:beg + size]
        padded = int(REC_HEIGHT * max(ratios[i] for i in group))
        for i in group:
            widths[i] = padded
    return widths


def decode(probs: np.ndarray, chars: list[str]) -> tuple[str, float]:
    """Argmax, collapse runs against the raw previous slice, drop the blank."""
    best = probs.argmax(axis=1)
    text, scores, previous = [], [], -1
    for t, index in enumerate(best):
        if index != previous and index != 0:
            text.append(chars[index])
            scores.append(float(probs[t, index]))
        previous = int(index)
    # upstream averages a sentinel in, hence kept + 1
    return "".join(text), (sum(scores) / (len(scores) + 1) if scores else 0.0)


def run_onnx(img: np.ndarray, session) -> np.ndarray:
    chw = ((img.astype(np.float32) / 255.0 - 0.5) / 0.5).transpose(2, 0, 1)
    return session.run(None, {session.get_inputs()[0].name: chw[None]})[0][0]


def run_ncnn(img: np.ndarray, net) -> np.ndarray:
    h, w = img.shape[:2]
    mat = ncnn.Mat.from_pixels(img, ncnn.Mat.PixelType.PIXEL_RGB, w, h)
    mat.substract_mean_normalize([127.5, 127.5, 127.5], [1 / 127.5] * 3)
    ex = net.create_extractor()
    ex.input("in0", mat)
    _, out = ex.extract("out0")
    got = np.array(out)
    return got.reshape(out.h, out.w) if got.ndim != 2 else got


def load_net(kind: str):
    net = ncnn.Net()
    net.opt.use_vulkan_compute = False
    net.load_param(str(NCNN / kind / "latin_rec.ncnn.param"))
    net.load_model(str(NCNN / kind / "latin_rec.ncnn.bin"))
    return net


def main() -> None:
    pages_dir = pathlib.Path(sys.argv[1])
    chars = alphabet()
    session = ort.InferenceSession(str(ONNX / "latin_rec.onnx"), providers=["CPUExecutionProvider"])
    nets = {"fp32": load_net("fp32"), "fp16": load_net("fp16")}

    totals = {"fp32": 0, "fp16": 0, "recorded": 0}
    lines = 0
    examples = {"fp32": [], "fp16": []}

    for jpg in sorted(pages_dir.glob("*.jpg")):
        page = jpg.stem
        rgb = np.array(Image.open(jpg).convert("RGB"))
        agree = {"fp32": 0, "fp16": 0}
        boxes = quads(page)
        # What RapidOCR recorded, which it produced with the crops *batched* and
        # zero-padded to a common width. Running them one at a time is the only
        # shape the converted model takes, so this says whether that matters.
        recorded = [row.split("	")[0] for row in
                    (FIXTURES / f"{page}.tsv").read_text(encoding="utf-8").splitlines() if row.strip()]
        unbatched = []
        crops = [crop(rgb, quad) for quad in boxes]
        widths = [0] * len(crops) if '--nopad' in sys.argv else batch_widths(crops)
        for index, raw in enumerate(crops):
            img = scaled(raw, widths[index])
            want, _ = decode(run_onnx(img, session), chars)
            unbatched.append(want)
            for kind, net in nets.items():
                got, _ = decode(run_ncnn(img, net), chars)
                if got == want:
                    agree[kind] += 1
                elif len(examples[kind]) < 6:
                    examples[kind].append((page, want, got))
        lines += len(boxes)
        for kind in nets:
            totals[kind] += agree[kind]
        kept = [t for t in unbatched if t]
        # a multiset: a glossary repeats words, and set intersection would
        # score every repeat after the first as a miss
        same = sum((Counter(kept) & Counter(recorded)).values())
        totals["recorded"] += same
        print(f"{page:<16} {len(boxes):>4} lines   "
              f"fp32 {agree['fp32']:>4}/{len(boxes):<4}  fp16 {agree['fp16']:>4}/{len(boxes):<4}"
              f"  unbatched vs recorded {same}/{len(recorded)}")

    print(f"\n{'total':<16} {lines:>4} lines   "
          f"fp32 {totals['fp32']}/{lines}  fp16 {totals['fp16']}/{lines}"
          f"  unbatched vs recorded {totals['recorded']}")
    for kind in nets:
        if examples[kind]:
            print(f"\nwhere {kind} differs:")
            for page, want, got in examples[kind]:
                print(f"  {page:<16} onnx {want!r:<40} ncnn {got!r}")


if __name__ == "__main__":
    main()
